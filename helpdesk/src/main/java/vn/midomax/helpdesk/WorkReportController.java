package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/work-reports")
public class WorkReportController {

    @Autowired
    private WorkReportService workReportService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private WorkCommentRepository workCommentRepository;

    @Autowired
    private WorkSubTaskRepository workSubTaskRepository;

    @Autowired
    private ExcelService excelService;

    @Autowired
    private EmailService emailService;

    // ===== Chuông thông báo cho báo cáo công việc (chỉ trong app, không email) =====

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "PLANNING", "Lên kế hoạch", "PROGRESS", "Đang thực hiện",
            "TESTING", "Đang kiểm thử", "COMPLETED", "Hoàn thành");

    private String statusLabel(String status) {
        if (status == null) return "";
        return STATUS_LABELS.getOrDefault(status.toUpperCase(), status);
    }

    private String actorOf(Authentication auth) {
        return auth == null || auth.getName() == null ? "" : auth.getName().split("@")[0].trim().toLowerCase();
    }

    /** Tách chuỗi watchers CSV thành tập username sạch. */
    private Set<String> watcherSet(String watchers) {
        Set<String> s = new LinkedHashSet<>();
        if (watchers != null) {
            for (String w : watchers.split(",")) {
                String c = w.trim().toLowerCase();
                if (!c.isEmpty()) s.add(c);
            }
        }
        return s;
    }

    /** Người liên quan một báo cáo: người làm + người tạo + các watcher. */
    private Set<String> relatedOf(WorkReport r) {
        Set<String> s = new LinkedHashSet<>();
        if (r == null) return s;
        if (r.getAssignee() != null) s.add(r.getAssignee());
        if (r.getCreatedBy() != null) s.add(r.getCreatedBy());
        s.addAll(watcherSet(r.getWatchers()));
        return s;
    }

    /** Gửi chuông cho danh sách người nhận, bỏ người thao tác và bỏ trùng. Lỗi không làm hỏng nghiệp vụ chính. */
    private void bell(Collection<String> recipients, String actor, String title, String message) {
        Set<String> sent = new HashSet<>();
        for (String r : recipients) {
            if (r == null) continue;
            String c = r.trim().toLowerCase();
            if (c.isEmpty() || c.equals(actor) || !sent.add(c)) continue;
            try {
                emailService.notifyBell(c, title, message, "REPORT", "/work-reports");
            } catch (Exception ignored) {
            }
        }
    }

    @GetMapping
    public String showWorkReports(
            @RequestParam(name = "assignee", required = false, defaultValue = "ALL") String assignee,
            @RequestParam(name = "project", required = false, defaultValue = "ALL") String project,
            @RequestParam(name = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(name = "dateFilter", required = false, defaultValue = "ALL") String dateFilter,
            @RequestParam(name = "view", required = false, defaultValue = "list") String view,
            Model model,
            Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "Guest";
        boolean isManagerOrIT = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_IT") || a.getAuthority().equals("ROLE_MANAGER"));

        // Áp phạm vi nhóm chuyên môn cho cả danh sách hiển thị lẫn số liệu thống kê,
        // để con số trên đầu trang khớp với những gì user thực sự thấy.
        List<WorkReport> rawReports = filterByGroupScope(
                workReportService.getReportsFiltered(assignee, project, status), authentication);
        List<WorkReport> allReports = filterByGroupScope(
                workReportService.getAllReports(), authentication);

        LocalDate today = LocalDate.now();
        List<WorkReport> reports = rawReports.stream().filter(r -> {
            if ("ALL".equalsIgnoreCase(dateFilter) || dateFilter == null || dateFilter.isEmpty()) return true;
            LocalDateTime dt = r.getStartDate() != null ? r.getStartDate() : r.getCreatedAt();
            if (dt == null) return true;
            LocalDate date = dt.toLocalDate();
            if ("TODAY".equalsIgnoreCase(dateFilter)) {
                return date.isEqual(today);
            } else if ("YESTERDAY".equalsIgnoreCase(dateFilter)) {
                return date.isEqual(today.minusDays(1));
            } else if ("WEEK".equalsIgnoreCase(dateFilter)) {
                return !date.isBefore(today.minusDays(7));
            }
            return true;
        }).collect(Collectors.toList());

        // Statistics
        long total = allReports.size();
        long planning = allReports.stream().filter(r -> "PLANNING".equalsIgnoreCase(r.getStatus())).count();
        long progress = allReports.stream().filter(r -> "PROGRESS".equalsIgnoreCase(r.getStatus())).count();
        long testing = allReports.stream().filter(r -> "TESTING".equalsIgnoreCase(r.getStatus())).count();
        long completed = allReports.stream().filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus())).count();

        // Distinct project names for dropdown
        Set<String> projectNames = allReports.stream()
                .map(WorkReport::getProjectName)
                .filter(p -> p != null && !p.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        // Assignees (from reports + IT/Admin users)
        Set<String> assignees = new TreeSet<>();
        allReports.forEach(r -> {
            if (r.getAssignee() != null && !r.getAssignee().isEmpty()) {
                assignees.add(r.getAssignee());
            }
        });
        appUserRepository.findAll().forEach(u -> {
            if (u.getRole() != null && (u.getRole().contains("IT") || u.getRole().contains("ADMIN") || u.getRole().contains("MANAGER"))) {
                if (u.getEmail() != null) {
                    assignees.add(u.getEmail().split("@")[0]);
                }
            }
        });
        if (assignees.isEmpty()) {
            assignees.addAll(Arrays.asList("tin", "trinh", "dinh", "admin"));
        }

        Map<Long, List<WorkSubTask>> subtasksMap = new HashMap<>();
        Map<Long, List<WorkReport>> childReportsMap = new HashMap<>();
        List<WorkReport> parentReports = reports.stream()
                .filter(r -> r.getParentId() == null || r.getParentId() <= 0)
                .sorted(Comparator.comparing(WorkReport::getId).reversed())
                .collect(Collectors.toList());
        for (WorkReport r : parentReports) {
            workReportService.recalculateParentProgress(r.getId());
            WorkReport fresh = workReportService.getReportById(r.getId());
            if (fresh != null) {
                r.setProgressPercentage(fresh.getProgressPercentage());
                r.setStatus(fresh.getStatus());
            }
            subtasksMap.put(r.getId(), workReportService.getSubTasks(r.getId()));
            childReportsMap.put(r.getId(), allReports.stream()
                    .filter(c -> c.getParentId() != null && c.getParentId() > 0 && r.getId().equals(c.getParentId()))
                    .sorted(Comparator.comparing(WorkReport::getId))
                    .collect(Collectors.toList()));
        }

        Map<String, List<WorkReport>> reportsByProjectMap = parentReports.stream()
                .collect(Collectors.groupingBy(
                        r -> (r.getProjectName() == null || r.getProjectName().trim().isEmpty()) ? "Dự án chung" : r.getProjectName().trim(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        model.addAttribute("reports", parentReports);
        model.addAttribute("reportsByProjectMap", reportsByProjectMap);
        model.addAttribute("subtasksMap", subtasksMap);
        model.addAttribute("childReportsMap", childReportsMap);
        model.addAttribute("totalCount", total);
        model.addAttribute("planningCount", planning);
        model.addAttribute("progressCount", progress);
        model.addAttribute("testingCount", testing);
        model.addAttribute("completedCount", completed);
        model.addAttribute("projectNames", projectNames);
        model.addAttribute("assignees", assignees);
        model.addAttribute("allUsers", appUserRepository.findAll());
        model.addAttribute("currentAssignee", assignee);
        model.addAttribute("currentProject", project);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentDateFilter", dateFilter);
        model.addAttribute("currentView", view);
        model.addAttribute("activePage", "work-reports");
        model.addAttribute("isAdmin", isManagerOrIT);
        model.addAttribute("currentUser", username);

        return "work-reports";
    }

    /** Xuất báo cáo công việc ra Excel (theo đúng bộ lọc đang chọn). */
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> exportExcel(
            @RequestParam(name = "assignee", required = false, defaultValue = "ALL") String assignee,
            @RequestParam(name = "project", required = false, defaultValue = "ALL") String project,
            @RequestParam(name = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(name = "dateFilter", required = false, defaultValue = "ALL") String dateFilter,
            Authentication authentication) throws java.io.IOException {

        // Xuất Excel cũng theo đúng phạm vi nhóm chuyên môn như trang danh sách
        List<WorkReport> filtered = applyDateFilter(
                filterByGroupScope(workReportService.getReportsFiltered(assignee, project, status), authentication),
                dateFilter);

        java.io.ByteArrayInputStream in = excelService.exportWorkReportsToExcel(filtered);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=BaoCaoCongViec.xlsx");

        return org.springframework.http.ResponseEntity
                .ok()
                .headers(headers)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new org.springframework.core.io.InputStreamResource(in));
    }

    /**
     * Phạm vi nhìn thấy báo cáo theo nhóm chuyên môn IT:
     * - Admin / Manager: thấy tất cả.
     * - IT: thấy việc của chính mình + việc của người CÙNG nhóm chuyên môn
     *   (Helpdesk thấy việc nhau, Phần mềm thấy việc Phần mềm, Báo cáo thấy việc Báo cáo).
     *   Người thuộc nhiều nhóm thấy việc của mọi nhóm mình tham gia.
     * - Còn lại (user được cấp phân hệ qua phòng ban): chỉ thấy việc mình làm,
     *   mình tạo hoặc mình đang theo dõi (watcher).
     */
    private List<WorkReport> filterByGroupScope(List<WorkReport> reports, Authentication auth) {
        if (auth == null || reports == null || reports.isEmpty()) return reports;

        Set<String> authorities = auth.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        if (authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_MANAGER")) {
            return reports;
        }

        String me = auth.getName().split("@")[0].trim().toLowerCase();
        Set<String> myGroups = authorities.stream()
                .filter(a -> a.startsWith(UserAuthorityMapper.GROUP_PREFIX))
                .map(a -> a.substring(UserAuthorityMapper.GROUP_PREFIX.length()))
                .collect(Collectors.toSet());

        // username -> các nhóm chuyên môn của người đó (để biết assignee thuộc nhóm nào)
        Map<String, Set<String>> groupsOf = new HashMap<>();
        for (AppUser u : appUserRepository.findAll()) {
            if (u.getEmail() == null) continue;
            String uname = u.getEmail().split("@")[0].trim().toLowerCase();
            Set<String> gs = new HashSet<>();
            for (ItGroup g : u.getItGroups()) gs.add(g.name());
            groupsOf.put(uname, gs);
        }

        return reports.stream().filter(r -> {
            String assignee = r.getAssignee() == null ? "" : r.getAssignee().trim().toLowerCase();
            String creator = r.getCreatedBy() == null ? "" : r.getCreatedBy().trim().toLowerCase();
            if (assignee.equals(me) || creator.equals(me)) return true;
            if (r.getWatchers() != null) {
                for (String w : r.getWatchers().split(",")) {
                    if (w.trim().toLowerCase().equals(me)) return true;
                }
            }
            if (!myGroups.isEmpty()) {
                Set<String> assigneeGroups = groupsOf.get(assignee);
                if (assigneeGroups != null) {
                    for (String g : assigneeGroups) {
                        if (myGroups.contains(g)) return true;
                    }
                }
            }
            return false;
        }).collect(Collectors.toList());
    }

    /** Lọc danh sách theo khoảng thời gian (ALL / TODAY / YESTERDAY / WEEK). */
    private List<WorkReport> applyDateFilter(List<WorkReport> list, String dateFilter) {
        LocalDate today = LocalDate.now();
        return list.stream().filter(r -> {
            if ("ALL".equalsIgnoreCase(dateFilter) || dateFilter == null || dateFilter.isEmpty()) return true;
            LocalDateTime dt = r.getStartDate() != null ? r.getStartDate() : r.getCreatedAt();
            if (dt == null) return true;
            LocalDate date = dt.toLocalDate();
            if ("TODAY".equalsIgnoreCase(dateFilter)) {
                return date.isEqual(today);
            } else if ("YESTERDAY".equalsIgnoreCase(dateFilter)) {
                return date.isEqual(today.minusDays(1));
            } else if ("WEEK".equalsIgnoreCase(dateFilter)) {
                return !date.isBefore(today.minusDays(7));
            }
            return true;
        }).collect(Collectors.toList());
    }

    @PostMapping("/create")
    public String createReport(
            @RequestParam(value = "parentId", required = false) String parentIdStr,
            @RequestParam(value = "projectName", required = false, defaultValue = "Dự án chung") String projectName,
            @RequestParam(value = "taskTitle", required = false, defaultValue = "Nhiệm vụ mới") String taskTitle,
            @RequestParam(value = "assignee", required = false, defaultValue = "tin") String assignee,
            @RequestParam(value = "watchers", required = false) List<String> watchersList,
            @RequestParam(value = "status", required = false, defaultValue = "PLANNING") String status,
            @RequestParam(value = "progressPercentage", required = false, defaultValue = "0") String progressPercentageStr,
            @RequestParam(value = "dueDateStr", required = false) String dueDateStr,
            @RequestParam(value = "dailyReport", required = false) String dailyReport,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        if (projectName == null || projectName.trim().isEmpty()) projectName = "Dự án chung";
        if (taskTitle == null || taskTitle.trim().isEmpty()) taskTitle = "Nhiệm vụ mới";
        if (assignee == null || assignee.trim().isEmpty()) assignee = "tin";
        if (status == null || status.trim().isEmpty()) status = "PLANNING";

        Long parentId = null;
        if (parentIdStr != null && !parentIdStr.trim().isEmpty() && !parentIdStr.trim().equalsIgnoreCase("null")) {
            try {
                parentId = Long.parseLong(parentIdStr.trim());
            } catch (Exception e) {
                // ignore
            }
        }

        int progressPercentage = 0;
        if (progressPercentageStr != null && !progressPercentageStr.trim().isEmpty()) {
            try {
                progressPercentage = Integer.parseInt(progressPercentageStr.trim());
            } catch (Exception e) {
                progressPercentage = 0;
            }
        }

        WorkReport report = new WorkReport();
        if (parentId != null && parentId > 0) {
            report.setParentId(parentId);
        } else {
            report.setParentId(null);
        }
        report.setProjectName(projectName.trim());
        report.setTaskTitle(taskTitle.trim());
        report.setAssignee(assignee.trim().toLowerCase());
        report.setCreatedBy((authentication != null) ? authentication.getName().split("@")[0] : assignee.trim().toLowerCase());
        if (watchersList != null && !watchersList.isEmpty()) {
            report.setWatchers(String.join(",", watchersList));
        }
        report.setStatus(status.trim().toUpperCase());
        report.setProgressPercentage(progressPercentage);
        report.setDailyReport(dailyReport != null ? dailyReport.trim() : "");
        report.setStartDate(LocalDateTime.now());

        if (dueDateStr != null && !dueDateStr.trim().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                report.setDueDate(LocalDateTime.parse(dueDateStr.trim(), formatter));
            } catch (Exception e) {
                // ignore
            }
        }

        WorkReport saved = workReportService.saveReport(report);
        System.out.println(">>> [DB SUCCESS] Đã lưu công việc mới vào Database: ID=" + saved.getId() + ", Title=" + saved.getTaskTitle() + ", Status=" + saved.getStatus());

        // Chuông cho người được giao (nếu không phải tự giao cho mình)
        bell(List.of(saved.getAssignee() == null ? "" : saved.getAssignee()), actorOf(authentication),
                "📋 Bạn được giao việc mới: " + saved.getTaskTitle(),
                (saved.getProjectName() != null && !saved.getProjectName().isEmpty()
                        ? "Dự án " + saved.getProjectName() + " — " : "")
                        + "giao bởi " + saved.getCreatedBy());

        redirectAttributes.addFlashAttribute("successMessage", "Đã tạo nhiệm vụ & báo cáo công việc thành công!");
        return "redirect:/work-reports";
    }

    @PostMapping("/update")
    public String updateReport(
            @RequestParam("id") Long id,
            @RequestParam(value = "taskTitle", required = false) String taskTitle,
            @RequestParam(value = "projectName", required = false) String projectName,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "progressPercentage", required = false) String progressPercentageStr,
            @RequestParam(value = "watchers", required = false) String watchers,
            @RequestParam(value = "dueDateStr", required = false) String dueDateStr,
            @RequestParam(value = "dailyReport", required = false) String dailyReport,
            @RequestParam(value = "delayReason", required = false) String delayReason,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        Integer progressPercentage = null;
        if (progressPercentageStr != null && !progressPercentageStr.trim().isEmpty()) {
            try {
                progressPercentage = Integer.parseInt(progressPercentageStr.trim());
            } catch (Exception e) {}
        }
        // Ghi lại trạng thái CŨ trước khi cập nhật để biết cái gì thay đổi mà báo chuông
        WorkReport before = workReportService.getReportById(id);
        String oldAssignee = before != null ? before.getAssignee() : null;
        String oldStatus = before != null ? before.getStatus() : null;
        Set<String> oldWatchers = before != null ? watcherSet(before.getWatchers()) : new HashSet<>();
        String creator = before != null ? before.getCreatedBy() : null;
        String reportTitle = before != null && before.getTaskTitle() != null ? before.getTaskTitle() : ("#" + id);

        workReportService.updateFullReport(id, taskTitle, projectName, assignee, status, progressPercentage, dailyReport, watchers, dueDateStr, delayReason);

        notifyReportChanges(authentication, reportTitle, creator,
                oldAssignee, assignee, oldStatus, status, oldWatchers, watchers);

        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật công việc thành công!");
        return "redirect:/work-reports";
    }

    /** Bắn chuông cho các thay đổi của một báo cáo: giao việc mới, gắn watcher mới, đổi trạng thái. */
    private void notifyReportChanges(Authentication authentication, String reportTitle, String creator,
                                     String oldAssignee, String newAssignee,
                                     String oldStatus, String newStatus,
                                     Set<String> oldWatchers, String newWatchersRaw) {
        String actor = actorOf(authentication);

        // 1. Đổi người làm -> báo người được giao mới
        if (newAssignee != null && !newAssignee.isBlank()
                && (oldAssignee == null || !newAssignee.trim().equalsIgnoreCase(oldAssignee.trim()))) {
            bell(List.of(newAssignee), actor,
                    "📋 Bạn được giao việc: " + reportTitle,
                    "Chuyển giao bởi " + (actor.isEmpty() ? "hệ thống" : actor));
        }

        // 2. Watcher mới được gắn -> báo từng người mới
        if (newWatchersRaw != null) {
            Set<String> added = watcherSet(newWatchersRaw);
            added.removeAll(oldWatchers);
            bell(added, actor,
                    "👀 Bạn được gắn theo dõi: " + reportTitle,
                    (actor.isEmpty() ? "Bạn" : actor) + " đã thêm bạn vào danh sách theo dõi công việc này");
        }

        // 3. Đổi trạng thái -> báo người tạo + người làm + watchers cũ
        if (newStatus != null && !newStatus.isBlank() && oldStatus != null
                && !newStatus.trim().equalsIgnoreCase(oldStatus.trim())) {
            Set<String> recipients = new LinkedHashSet<>();
            if (creator != null) recipients.add(creator);
            if (oldAssignee != null) recipients.add(oldAssignee);
            recipients.addAll(oldWatchers);
            bell(recipients, actor,
                    "🔄 " + reportTitle + " → " + statusLabel(newStatus),
                    (actor.isEmpty() ? "Hệ thống" : actor) + " đã chuyển trạng thái từ "
                            + statusLabel(oldStatus) + " sang " + statusLabel(newStatus));
        }
    }

    @PostMapping("/update-inline")
    @ResponseBody
    public String updateInline(
            @RequestParam("id") Long id,
            @RequestParam(value = "progress", required = false) String progressStr,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "dailyReport", required = false) String dailyReport,
            @RequestParam(value = "watchers", required = false) String watchers,
            @RequestParam(value = "dueDate", required = false) String dueDateStr,
            @RequestParam(value = "delayReason", required = false) String delayReason,
            Authentication authentication) {
        try {
            Integer progress = null;
            if (progressStr != null && !progressStr.trim().isEmpty()) {
                try {
                    progress = Integer.parseInt(progressStr.trim());
                } catch (Exception e) {}
            }

            WorkReport before = workReportService.getReportById(id);
            String oldAssignee = before != null ? before.getAssignee() : null;
            String oldStatus = before != null ? before.getStatus() : null;
            Set<String> oldWatchers = before != null ? watcherSet(before.getWatchers()) : new HashSet<>();
            String creator = before != null ? before.getCreatedBy() : null;
            String reportTitle = before != null && before.getTaskTitle() != null ? before.getTaskTitle() : ("#" + id);

            if (progress != null || dailyReport != null || (delayReason != null && !delayReason.trim().isEmpty())) {
                workReportService.updateProgressAndReport(id, progress, status, dailyReport, watchers, delayReason);
            }
            if (priority != null || assignee != null || dueDateStr != null || (status != null && progress == null)) {
                workReportService.updateInline(id, status, priority, assignee, watchers, dueDateStr, delayReason);
            }

            notifyReportChanges(authentication, reportTitle, creator,
                    oldAssignee, assignee, oldStatus, status, oldWatchers, watchers);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    @RequestMapping(value = "/delete/{id}", method = {RequestMethod.GET, RequestMethod.POST})
    public String deleteReport(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        System.out.println(">>> [DELETE CONTROLLER] Bắt đầu xóa ID: " + id);
        workReportService.deleteReport(id);
        System.out.println(">>> [DELETE CONTROLLER] Đã gọi Service xóa ID: " + id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa hạng mục báo cáo thành công!");
        return "redirect:/work-reports";
    }

    @PostMapping("/subtasks/add")
    @ResponseBody
    public WorkSubTask addSubTask(
            @RequestParam("workReportId") Long workReportId,
            @RequestParam("title") String title,
            @RequestParam(value = "assignee", required = false, defaultValue = "") String assignee,
            Authentication authentication) {
        WorkSubTask subTask = workReportService.addSubTask(workReportId, title, assignee);
        if (assignee != null && !assignee.isBlank()) {
            WorkReport parent = workReportService.getReportById(workReportId);
            bell(List.of(assignee), actorOf(authentication),
                    "📌 Bạn được giao việc con: " + title,
                    parent != null ? "Thuộc công việc: " + parent.getTaskTitle() : "");
        }
        return subTask;
    }

    @PostMapping("/subtasks/toggle")
    @ResponseBody
    public WorkSubTask toggleSubTask(
            @RequestParam("subTaskId") Long subTaskId,
            @RequestParam("completed") Boolean completed) {
        return workReportService.toggleSubTask(subTaskId, completed);
    }

    @PostMapping("/subtasks/delete")
    @ResponseBody
    public String deleteSubTask(@RequestParam("subTaskId") Long subTaskId) {
        try {
            workReportService.deleteSubTask(subTaskId);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    @GetMapping("/comments")
    @ResponseBody
    public List<WorkComment> getComments(@RequestParam("workReportId") Long workReportId) {
        return workReportService.getComments(workReportId);
    }

    @PostMapping("/comments/add")
    @ResponseBody
    public WorkComment addComment(
            @RequestParam("workReportId") Long workReportId,
            @RequestParam("author") String author,
            @RequestParam("content") String content,
            Authentication auth) {
        String commenter = (auth != null && auth.getName() != null) ? auth.getName() : author;
        if (commenter == null || commenter.trim().isEmpty()) commenter = "Anonymous";
        WorkComment comment = workReportService.addComment(workReportId, commenter, content);

        // Chuông cho những người liên quan (người làm, người tạo, watchers) — trừ người bình luận
        WorkReport report = workReportService.getReportById(workReportId);
        if (report != null && content != null) {
            String preview = content.length() > 80 ? content.substring(0, 80) + "..." : content;
            bell(relatedOf(report), commenter.split("@")[0].trim().toLowerCase(),
                    "💬 Bình luận mới ở: " + report.getTaskTitle(),
                    commenter.split("@")[0] + ": " + preview);
        }
        return comment;
    }


    @GetMapping("/detail/{id}")
    @ResponseBody
    public Map<String, Object> getDetail(@PathVariable("id") Long id, Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        WorkReport r = workReportService.getReportById(id);
        // Chặn đọc lén qua API: chỉ trả chi tiết nếu báo cáo nằm trong phạm vi
        // user được thấy (cùng luật với danh sách — mình làm/tạo/theo dõi/cùng nhóm).
        if (r != null && !filterByGroupScope(List.of(r), authentication).isEmpty()) {
            res.put("report", r);
            res.put("subtasks", workReportService.getSubTasks(id));
            res.put("childReports", workReportService.getChildReports(id));
            res.put("comments", workReportService.getComments(id));
        }
        return res;
    }

    @PostMapping("/api/quick-create")
    @ResponseBody
    public Map<String, Object> quickCreateReport(
            @RequestParam("taskTitle") String taskTitle,
            @RequestParam(value = "status", required = false, defaultValue = "PLANNING") String status,
            @RequestParam(value = "projectName", required = false, defaultValue = "Dự án chung") String projectName,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "parentId", required = false) String parentIdStr,
            Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        try {
            if (assignee == null || assignee.trim().isEmpty()) {
                assignee = (authentication != null) ? authentication.getName().split("@")[0] : "admin";
            }
            Long parentId = null;
            if (parentIdStr != null && !parentIdStr.trim().isEmpty() && !parentIdStr.trim().equalsIgnoreCase("null")) {
                try {
                    parentId = Long.parseLong(parentIdStr.trim());
                } catch (Exception e) {}
            }
            WorkReport report = new WorkReport();
            if (parentId != null && parentId > 0) {
                report.setParentId(parentId);
            } else {
                report.setParentId(null);
            }
            report.setProjectName(projectName != null ? projectName.trim() : "Dự án chung");
            report.setTaskTitle(taskTitle != null ? taskTitle.trim() : "Nhiệm vụ mới");
            report.setAssignee(assignee.trim().toLowerCase());
            if (status == null || status.trim().isEmpty() || "null".equalsIgnoreCase(status.trim())) {
                status = "PLANNING";
            }
            report.setStatus(status.trim().toUpperCase());
            report.setProgressPercentage(0);
            report.setStartDate(LocalDateTime.now());
            report.setCreatedBy((authentication != null) ? authentication.getName().split("@")[0] : assignee.trim().toLowerCase());
            WorkReport saved = workReportService.saveReport(report);
            System.out.println(">>> [QUICK CREATE DB SUCCESS] Đã lưu nhanh công việc vào Database: ID=" + saved.getId() + ", ParentId=" + parentId + ", Title=" + saved.getTaskTitle() + ", Status=" + saved.getStatus());
            res.put("status", "success");
            res.put("id", saved.getId());
        } catch (Exception e) {
            System.err.println(">>> [QUICK CREATE ERROR] Lỗi lưu database: " + e.getMessage());
            e.printStackTrace();
            res.put("status", "error");
            res.put("message", e.getMessage());
        }
        return res;
    }

    @PostMapping("/api/create-subtask")
    @ResponseBody
    public Map<String, Object> createSubtaskApi(
            @RequestParam("parentId") Long parentId,
            @RequestParam("taskTitle") String taskTitle,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dueDate", required = false) String dueDateStr) {
        Map<String, Object> res = new HashMap<>();
        try {
            WorkReport child = workReportService.createSubReport(parentId, taskTitle, assignee, status, dueDateStr);
            res.put("status", "success");
            res.put("id", child.getId());
            res.put("report", child);
        } catch (Exception e) {
            res.put("status", "error");
            res.put("message", e.getMessage());
        }
        return res;
    }
}
