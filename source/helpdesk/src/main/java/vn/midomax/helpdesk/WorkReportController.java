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

        List<WorkReport> rawReports = workReportService.getReportsFiltered(assignee, project, status);
        List<WorkReport> allReports = workReportService.getAllReports();

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
            @RequestParam(name = "dateFilter", required = false, defaultValue = "ALL") String dateFilter) throws java.io.IOException {

        List<WorkReport> filtered = applyDateFilter(
                workReportService.getReportsFiltered(assignee, project, status), dateFilter);

        java.io.ByteArrayInputStream in = excelService.exportWorkReportsToExcel(filtered);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=BaoCaoCongViec.xlsx");

        return org.springframework.http.ResponseEntity
                .ok()
                .headers(headers)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new org.springframework.core.io.InputStreamResource(in));
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
            RedirectAttributes redirectAttributes) {
        Integer progressPercentage = null;
        if (progressPercentageStr != null && !progressPercentageStr.trim().isEmpty()) {
            try {
                progressPercentage = Integer.parseInt(progressPercentageStr.trim());
            } catch (Exception e) {}
        }
        workReportService.updateFullReport(id, taskTitle, projectName, assignee, status, progressPercentage, dailyReport, watchers, dueDateStr);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật công việc thành công!");
        return "redirect:/work-reports";
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
            @RequestParam(value = "dueDate", required = false) String dueDateStr) {
        try {
            Integer progress = null;
            if (progressStr != null && !progressStr.trim().isEmpty()) {
                try {
                    progress = Integer.parseInt(progressStr.trim());
                } catch (Exception e) {}
            }
            if (progress != null || dailyReport != null) {
                workReportService.updateProgressAndReport(id, progress, status, dailyReport, watchers);
            }
            if (priority != null || assignee != null || dueDateStr != null || (status != null && progress == null)) {
                workReportService.updateInline(id, status, priority, assignee, watchers, dueDateStr);
            }
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
            @RequestParam(value = "assignee", required = false, defaultValue = "") String assignee) {
        return workReportService.addSubTask(workReportId, title, assignee);
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
        return workReportService.addComment(workReportId, commenter, content);
    }


    @GetMapping("/detail/{id}")
    @ResponseBody
    public Map<String, Object> getDetail(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        WorkReport r = workReportService.getReportById(id);
        if (r != null) {
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
