package vn.midomax.helpdesk;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceDeviceRepository deviceRepo;
    private final ShiftRepository shiftRepo;
    private final EmployeeShiftRepository employeeShiftRepo;
    private final EmployeeRepository employeeRepo;
    private final AppUserRepository appUserRepository;
    private final LeaveRequestRepository leaveRequestRepo;
    private final LeaveRequestDocService leaveRequestDocService;
    private final EmailService emailService;
    private final AttendanceDeviceUserRepository deviceUserRepo;

    public AttendanceController(AttendanceService attendanceService,
                                AttendanceDeviceRepository deviceRepo,
                                ShiftRepository shiftRepo,
                                EmployeeShiftRepository employeeShiftRepo,
                                EmployeeRepository employeeRepo,
                                AppUserRepository appUserRepository,
                                LeaveRequestRepository leaveRequestRepo,
                                LeaveRequestDocService leaveRequestDocService,
                                EmailService emailService,
                                AttendanceDeviceUserRepository deviceUserRepo) {
        this.leaveRequestDocService = leaveRequestDocService;
        this.attendanceService = attendanceService;
        this.deviceRepo = deviceRepo;
        this.shiftRepo = shiftRepo;
        this.employeeShiftRepo = employeeShiftRepo;
        this.employeeRepo = employeeRepo;
        this.appUserRepository = appUserRepository;
        this.leaveRequestRepo = leaveRequestRepo;
        this.emailService = emailService;
        this.deviceUserRepo = deviceUserRepo;
    }

    /** Một dòng trên lịch: nhân viên + công từng ngày trong tháng + tổng kết. */
    public static class EmployeeRow {
        private String employeeCode;
        private String fullName;
        private String department;
        private Long deviceId;      // máy chấm công (văn phòng) — phân biệt người trùng mã
        private String deviceName;
        private String shiftName;

        public Long getDeviceId() { return deviceId; }
        public String getDeviceName() { return deviceName; }
        private final Map<Integer, AttendanceRecord> days = new HashMap<>();
        private double totalWorkDays;
        private int totalLateMinutes;
        private int totalOtMinutes;
        private int absentDays;

        public String getEmployeeCode() { return employeeCode; }
        public String getFullName() { return fullName; }
        public String getDepartment() { return department; }
        public String getShiftName() { return shiftName; }
        public Map<Integer, AttendanceRecord> getDays() { return days; }
        public double getTotalWorkDays() { return totalWorkDays; }
        public int getTotalLateMinutes() { return totalLateMinutes; }
        public int getTotalOtMinutes() { return totalOtMinutes; }
        public int getAbsentDays() { return absentDays; }
    }

    /** Một ô ngày trên tiêu đề lịch. */
    public static class DayHeader {
        private final int day;
        private final String weekdayLabel;
        private final boolean weekend;

        DayHeader(int day, String weekdayLabel, boolean weekend) {
            this.day = day;
            this.weekdayLabel = weekdayLabel;
            this.weekend = weekend;
        }

        public int getDay() { return day; }
        public String getWeekdayLabel() { return weekdayLabel; }
        public boolean isWeekend() { return weekend; }
    }

    @GetMapping
    public String calendar(@RequestParam(required = false) Integer month,
                           @RequestParam(required = false) Integer year,
                           @RequestParam(required = false) String department,
                           @RequestParam(required = false) Long deviceId,
                           @RequestParam(required = false) String search,
                           Model model) {

        YearMonth ym = resolveMonth(month, year);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // Tiêu đề các cột ngày
        List<DayHeader> dayHeaders = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            dayHeaders.add(new DayHeader(d, weekdayLabel(date), isWeekend(date)));
        }

        // Gom bản ghi công theo NGƯỜI = (máy, mã) — các văn phòng trùng dải mã.
        // Bản ghi cũ chưa gắn máy gom riêng theo mã, dùng làm dự phòng để không mất dữ liệu.
        Map<String, List<AttendanceRecord>> byEmployee = new HashMap<>();
        Map<String, List<AttendanceRecord>> legacyByCode = new HashMap<>();
        for (AttendanceRecord record : attendanceService.findRecords(from, to)) {
            byEmployee.computeIfAbsent(record.personKey(), k -> new ArrayList<>()).add(record);
            if (record.getDeviceId() == null) {
                legacyByCode.computeIfAbsent(record.getEmployeeCode(), k -> new ArrayList<>()).add(record);
            }
        }

        Map<Long, Shift> shiftsById = new HashMap<>();
        for (Shift s : shiftRepo.findAll()) shiftsById.put(s.getId(), s);
        Shift defaultShift = shiftRepo.findFirstByIsDefaultTrue().orElse(null);

        List<AttendanceService.Person> roster = attendanceService.roster();

        List<EmployeeRow> rows = new ArrayList<>();
        for (AttendanceService.Person person : roster) {
            String code = person.getCode();
            // Lọc theo văn phòng (máy): roster giờ mỗi (máy, mã) một dòng nên so trực tiếp
            if (deviceId != null && !deviceId.equals(person.getDeviceId())) continue;
            if (department != null && !department.isBlank() && !department.equals(person.getDepartment())) continue;
            if (search != null && !search.isBlank() && !matches(person, search)) continue;

            EmployeeRow row = new EmployeeRow();
            row.employeeCode = code;
            row.fullName = person.getName();
            row.department = person.getDepartment();
            row.deviceId = person.getDeviceId();
            row.deviceName = person.getDeviceName();

            Shift shift = attendanceService.assignedShiftId(code, person.getDeviceId())
                    .map(shiftsById::get)
                    .orElse(defaultShift);
            row.shiftName = shift == null ? "— chưa gán ca —" : shift.getName();

            List<AttendanceRecord> personRecords = byEmployee.get(person.personKey());
            if ((personRecords == null || personRecords.isEmpty()) && person.getDeviceId() != null) {
                personRecords = legacyByCode.get(code); // dữ liệu tạo trước khi phân theo máy
            }
            for (AttendanceRecord record : personRecords == null ? Collections.<AttendanceRecord>emptyList() : personRecords) {
                row.days.put(record.getWorkDate().getDayOfMonth(), record);
                row.totalWorkDays += record.getWorkDays() == null ? 0 : record.getWorkDays();
                row.totalLateMinutes += record.getLateMinutes() == null ? 0 : record.getLateMinutes();
                row.totalOtMinutes += record.getOtMinutes() == null ? 0 : record.getOtMinutes();
                if (AttendanceRecord.ST_ABSENT.equals(record.getStatus())) row.absentDays++;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> r.fullName == null ? "" : r.fullName));

        model.addAttribute("rows", rows);
        model.addAttribute("dayHeaders", dayHeaders);
        model.addAttribute("month", ym.getMonthValue());
        model.addAttribute("year", ym.getYear());
        model.addAttribute("prevMonth", ym.minusMonths(1));
        model.addAttribute("nextMonth", ym.plusMonths(1));
        // Lấy từ chính danh sách đang hiện, để lọc không loại mất người chỉ có trên máy.
        model.addAttribute("departments", roster.stream()
                .map(AttendanceService.Person::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .distinct().sorted().toList());
        model.addAttribute("selectedDept", department);
        model.addAttribute("selectedDeviceId", deviceId);
        model.addAttribute("searchQuery", search);
        model.addAttribute("devices", deviceRepo.findAll());

        // Số người của từng văn phòng, hiện trên dãy nút chọn văn phòng
        Map<Long, Integer> peopleCountByDevice = new HashMap<>();
        for (AttendanceService.Person p : roster) {
            if (p.getDeviceId() != null) {
                peopleCountByDevice.merge(p.getDeviceId(), 1, Integer::sum);
            }
        }
        model.addAttribute("peopleCountByDevice", peopleCountByDevice);
        model.addAttribute("totalPeople", roster.size());

        // Đơn nghỉ phép / bổ sung công của kỳ này, tra theo "máy|mã|ngày" để ô ngày
        // trên bảng công cho nhân sự biết người đó có nộp đơn hay chưa.
        Map<String, LeaveRequest> requestByPersonDay = new HashMap<>();
        for (LeaveRequest req : leaveRequestRepo.findOverlapping(from, to)) {
            for (LocalDate d = req.getRequestDate(); !d.isAfter(req.getEffectiveToDate()); d = d.plusDays(1)) {
                if (d.isBefore(from) || d.isAfter(to)) continue;
                String key = (req.getDeviceId() == null ? "-" : req.getDeviceId()) + "|" + req.getEmployeeCode() + "|" + d;
                LeaveRequest current = requestByPersonDay.get(key);
                // Đơn đã duyệt được ưu tiên hiển thị hơn đơn đang chờ / bị từ chối
                if (current == null || LeaveRequest.ST_APPROVED.equals(req.getStatus())) {
                    requestByPersonDay.put(key, req);
                }
            }
        }
        model.addAttribute("requestByPersonDay", requestByPersonDay);
        model.addAttribute("shifts", shiftRepo.findByActiveTrueOrderByStartTimeAsc());
        return "attendance";
    }

    private boolean matches(AttendanceService.Person person, String search) {
        String needle = search.toLowerCase(Locale.ROOT);
        return (person.getName() != null && person.getName().toLowerCase(Locale.ROOT).contains(needle))
                || person.getCode().toLowerCase(Locale.ROOT).contains(needle);
    }

    private YearMonth resolveMonth(Integer month, Integer year) {
        YearMonth now = YearMonth.now();
        int m = month == null ? now.getMonthValue() : Math.min(Math.max(month, 1), 12);
        int y = year == null ? now.getYear() : year;
        return YearMonth.of(y, m);
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
    }

    private String weekdayLabel(LocalDate date) {
        switch (date.getDayOfWeek()) {
            case MONDAY: return "T2";
            case TUESDAY: return "T3";
            case WEDNESDAY: return "T4";
            case THURSDAY: return "T5";
            case FRIDAY: return "T6";
            case SATURDAY: return "T7";
            default: return "CN";
        }
    }

    // --- Tải dữ liệu từ máy ---

    @PostMapping("/sync")
    public String sync(@RequestParam(required = false) Long deviceId,
                       @RequestParam Integer month,
                       @RequestParam Integer year,
                       RedirectAttributes redirect) {

        List<AttendanceService.SyncResult> results = deviceId == null
                ? attendanceService.syncAllDevices()
                : Collections.singletonList(attendanceService.syncDevice(deviceId));

        if (results.isEmpty()) {
            redirect.addFlashAttribute("errorMessage", "Chưa khai báo máy chấm công nào đang bật.");
            return redirectToMonth(month, year);
        }

        int inserted = 0;
        List<String> failures = new ArrayList<>();
        for (AttendanceService.SyncResult r : results) {
            if (r.isOk()) {
                inserted += r.getInserted();
            } else {
                failures.add(r.getDeviceName() + ": " + r.getError());
            }
        }

        // Có dữ liệu mới thì tổng hợp lại luôn tháng đang xem cho người dùng thấy ngay.
        YearMonth ym = resolveMonth(month, year);
        attendanceService.rebuild(ym.atDay(1), ym.atEndOfMonth());

        if (failures.isEmpty()) {
            redirect.addFlashAttribute("successMessage",
                    "Đã tải xong " + results.size() + " máy, thêm mới " + inserted + " lần quét.");
        } else {
            redirect.addFlashAttribute("errorMessage",
                    "Thêm mới " + inserted + " lần quét. Máy lỗi — " + String.join("; ", failures));
        }
        return redirectToMonth(month, year);
    }

    @PostMapping("/rebuild")
    public String rebuild(@RequestParam Integer month, @RequestParam Integer year, RedirectAttributes redirect) {
        YearMonth ym = resolveMonth(month, year);
        int touched = attendanceService.rebuild(ym.atDay(1), ym.atEndOfMonth());
        redirect.addFlashAttribute("successMessage", "Đã tính lại công: " + touched + " dòng.");
        return redirectToMonth(month, year);
    }

    private String redirectToMonth(Integer month, Integer year) {
        YearMonth ym = resolveMonth(month, year);
        return "redirect:/attendance?month=" + ym.getMonthValue() + "&year=" + ym.getYear();
    }

    // --- Sửa tay một ngày công ---

    @PostMapping("/record/save")
    public String saveRecord(@RequestParam Long id,
                             @RequestParam String status,
                             @RequestParam(required = false) Double workDays,
                             @RequestParam(required = false) String note,
                             @RequestParam Integer month,
                             @RequestParam Integer year,
                             @RequestParam(required = false) String employeeCode,
                             RedirectAttributes redirect) {
        attendanceService.saveManual(id, status, workDays, note);
        redirect.addFlashAttribute("successMessage", "Đã cập nhật ngày công.");
        if (employeeCode != null && !employeeCode.isBlank()) {
            return "redirect:/attendance/user?employeeCode=" + employeeCode + "&month=" + month + "&year=" + year;
        }
        return redirectToMonth(month, year);
    }

    @PostMapping("/record/reset")
    public String resetRecord(@RequestParam Long id,
                              @RequestParam Integer month,
                              @RequestParam Integer year,
                              @RequestParam(required = false) String employeeCode,
                              RedirectAttributes redirect) {
        attendanceService.clearManual(id);
        redirect.addFlashAttribute("successMessage", "Đã bỏ sửa tay, bấm Tính lại công để lấy lại số từ máy.");
        if (employeeCode != null && !employeeCode.isBlank()) {
            return "redirect:/attendance/user?employeeCode=" + employeeCode + "&month=" + month + "&year=" + year;
        }
        return redirectToMonth(month, year);
    }

    // --- Máy chấm công ---

    @GetMapping("/devices")
    public String devices(Model model) {
        model.addAttribute("devices", deviceRepo.findAll());
        model.addAttribute("newDevice", new AttendanceDevice());
        return "attendance-devices";
    }

    @PostMapping("/devices/save")
    public String saveDevice(@ModelAttribute AttendanceDevice device, RedirectAttributes redirect) {
        if (device.getPort() == null) device.setPort(4370);
        if (device.getCommKey() == null) device.setCommKey(0);
        if (device.getActive() == null) device.setActive(true);
        deviceRepo.save(device);
        redirect.addFlashAttribute("successMessage", "Đã lưu thiết bị.");
        return "redirect:/attendance/devices";
    }

    @GetMapping("/devices/delete/{id}")
    public String deleteDevice(@PathVariable Long id, RedirectAttributes redirect) {
        deviceRepo.deleteById(id);
        redirect.addFlashAttribute("successMessage", "Đã xoá thiết bị.");
        return "redirect:/attendance/devices";
    }

    @PostMapping("/devices/test/{id}")
    public String testDevice(@PathVariable Long id, RedirectAttributes redirect) {
        AttendanceDevice device = deviceRepo.findById(id).orElse(null);
        if (device == null) {
            redirect.addFlashAttribute("errorMessage", "Không tìm thấy thiết bị.");
            return "redirect:/attendance/devices";
        }
        try {
            attendanceService.testConnection(device);
            redirect.addFlashAttribute("successMessage", "Kết nối tới " + device.getName() + " thành công.");
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage",
                    "Không kết nối được " + device.getName() + ": " + e.getMessage());
        }
        return "redirect:/attendance/devices";
    }

    @PostMapping("/devices/sync/{id}")
    public String syncOneDevice(@PathVariable Long id, RedirectAttributes redirect) {
        AttendanceService.SyncResult result = attendanceService.syncDevice(id);
        if (result.isOk()) {
            redirect.addFlashAttribute("successMessage",
                    "Tải xong: " + result.getFetched() + " log, thêm mới " + result.getInserted() + ".");
        } else {
            redirect.addFlashAttribute("errorMessage", "Lỗi: " + result.getError());
        }
        return "redirect:/attendance/devices";
    }

    // --- Ca làm việc ---

    @GetMapping("/shifts")
    public String shifts(Model model) {
        model.addAttribute("shifts", shiftRepo.findAll());
        model.addAttribute("newShift", new Shift());

        // Bảng gán ca theo NGƯỜI = (máy, mã): khóa map là personKey để trùng mã
        // khác văn phòng không đè ca của nhau. Dòng gán cũ chưa phân máy vẫn hiện
        // nhờ fallback trong assignedShiftId.
        List<AttendanceService.Person> people = attendanceService.roster();
        Map<String, Long> assignments = new HashMap<>();
        for (AttendanceService.Person p : people) {
            attendanceService.assignedShiftId(p.getCode(), p.getDeviceId())
                    .ifPresent(sid -> assignments.put(p.personKey(), sid));
        }
        model.addAttribute("assignments", assignments);
        model.addAttribute("people", people);
        return "attendance-shifts";
    }

    @PostMapping("/shifts/save")
    public String saveShift(@ModelAttribute Shift shift, RedirectAttributes redirect) {
        if (Boolean.TRUE.equals(shift.getIsDefault())) {
            // Chỉ được một ca mặc định; bỏ cờ ở các ca còn lại.
            for (Shift other : shiftRepo.findAll()) {
                if (!other.getId().equals(shift.getId()) && Boolean.TRUE.equals(other.getIsDefault())) {
                    other.setIsDefault(false);
                    shiftRepo.save(other);
                }
            }
        }
        shiftRepo.save(shift);
        redirect.addFlashAttribute("successMessage", "Đã lưu ca làm việc.");
        return "redirect:/attendance/shifts";
    }

    @GetMapping("/shifts/delete/{id}")
    public String deleteShift(@PathVariable Long id, RedirectAttributes redirect) {
        shiftRepo.deleteById(id);
        redirect.addFlashAttribute("successMessage", "Đã xoá ca làm việc.");
        return "redirect:/attendance/shifts";
    }

    @PostMapping("/shifts/assign")
    public String assignShift(@RequestParam String employeeCode,
                              @RequestParam(required = false) Long deviceId,
                              @RequestParam(required = false) Long shiftId,
                              RedirectAttributes redirect) {
        // Gán ca theo NGƯỜI = (máy, mã) — ca kho khác ca văn phòng dù trùng mã
        EmployeeShift assignment = employeeShiftRepo.findByPerson(employeeCode, deviceId)
                .orElseGet(() -> {
                    EmployeeShift fresh = new EmployeeShift();
                    fresh.setEmployeeCode(employeeCode);
                    fresh.setDeviceId(deviceId);
                    return fresh;
                });

        if (shiftId == null) {
            // Bỏ gán: nhân viên quay về dùng ca mặc định.
            if (assignment.getId() != null) employeeShiftRepo.delete(assignment);
        } else {
            assignment.setShiftId(shiftId);
            employeeShiftRepo.save(assignment);
        }
        redirect.addFlashAttribute("successMessage", "Đã cập nhật ca cho nhân viên.");
        return "redirect:/attendance/shifts";
    }

    /**
     * Lịch chấm công CỦA CHÍNH nhân viên đang đăng nhập — ai cũng vào được
     * (ModuleAccessInterceptor miễn khóa HR cho đường dẫn này). Mã chấm công do
     * admin nhập trong Phân quyền User (AppUser.employeeCode); chưa có mã thì báo hướng dẫn.
     */
    @GetMapping("/my")
    public String myCalendar(@RequestParam(required = false) Integer month,
                             @RequestParam(required = false) Integer year,
                             org.springframework.security.core.Authentication authentication,
                             Model model) {
        AppUser me = currentAppUser(authentication);
        String code = me != null ? me.getEmployeeCode() : null;
        if (code == null || code.isBlank()) {
            model.addAttribute("activePage", "my-attendance");
            model.addAttribute("canApprove", canApproveRequests(me));
            return "attendance-no-code";
        }
        String view = userCalendar(code.trim(), me.getAttendanceDeviceId(), month, year, model);
        model.addAttribute("activePage", "my-attendance");
        model.addAttribute("selfView", true); // trang cá nhân: ẩn các nút quay về màn quản trị
        model.addAttribute("canApprove", canApproveRequests(me));

        return view;
    }

    // ===================== ĐƠN NGHỈ PHÉP / ĐI TRỄ =====================

    /** Ai được duyệt đơn: trưởng phòng (cấp 1), phòng NSHC hoặc Admin (cấp 2 - nhân sự). */
    private boolean isHr(AppUser u) {
        if (u == null) return false;
        return "ROLE_ADMIN".equals(u.getRole()) || "NSHC".equals(u.getDepartment());
    }

    private boolean canApproveRequests(AppUser u) {
        return u != null && (u.isDeptHead() || isHr(u));
    }

    /** Nhân viên gửi đơn từ lịch cá nhân (Phiếu đăng ký nghỉ / Giấy bổ sung công). */
    @PostMapping("/my/request")
    public String submitRequest(@RequestParam String type,
                                @RequestParam String date,
                                @RequestParam(required = false) String toDateStr,
                                @RequestParam String reason,
                                @RequestParam(required = false) String leaveType,
                                @RequestParam(required = false) String duration,
                                @RequestParam(required = false) String supporter,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) Integer month,
                                @RequestParam(required = false) Integer year,
                                org.springframework.security.core.Authentication authentication,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        String back = "redirect:/attendance/my" + (month != null && year != null ? "?month=" + month + "&year=" + year : "");
        AppUser me = currentAppUser(authentication);
        if (me == null || me.getEmployeeCode() == null || me.getEmployeeCode().isBlank()) {
            ra.addFlashAttribute("errorMessage", "Tài khoản chưa được gán mã chấm công.");
            return back;
        }
        LocalDate fromDate;
        try {
            fromDate = LocalDate.parse(date.trim());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Ngày không hợp lệ.");
            return back;
        }
        LocalDate toDate = fromDate;
        if (toDateStr != null && !toDateStr.isBlank()) {
            try {
                toDate = LocalDate.parse(toDateStr.trim());
            } catch (Exception ignored) { }
        }
        if (toDate.isBefore(fromDate)) toDate = fromDate;
        if (reason == null || reason.trim().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Vui lòng nhập lý do.");
            return back;
        }
        String cleanType = LeaveRequest.TYPE_SUPPLEMENT.equalsIgnoreCase(type)
                ? LeaveRequest.TYPE_SUPPLEMENT : LeaveRequest.TYPE_LEAVE;

        // Chặn gửi trùng: đã có đơn chưa bị từ chối GIAO NHAU với khoảng ngày xin
        final LocalDate f = fromDate, t = toDate;
        boolean duplicated = leaveRequestRepo
                .findByEmployeeCodeAndRequestDateBetween(me.getEmployeeCode(), fromDate.minusDays(62), toDate).stream()
                .anyMatch(r -> !LeaveRequest.ST_REJECTED.equals(r.getStatus())
                        && !r.getEffectiveToDate().isBefore(f) && !r.getRequestDate().isAfter(t));
        if (duplicated) {
            ra.addFlashAttribute("errorMessage", "Khoảng ngày này đã có đơn đang xử lý hoặc đã duyệt.");
            return back;
        }

        // Tổng số ngày: mỗi ngày trong khoảng tính 1 (cả ngày) hoặc 0.5 (sáng/chiều)
        long dayCount = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        boolean halfDay = "MORNING".equals(duration) || "AFTERNOON".equals(duration);
        double totalDays = dayCount * (halfDay ? 0.5 : 1.0);

        LeaveRequest req = new LeaveRequest();
        req.setEmployeeCode(me.getEmployeeCode());
        req.setDeviceId(me.getAttendanceDeviceId());
        req.setRequesterName(me.getEmail() != null ? me.getEmail().split("@")[0] : authentication.getName());
        req.setRequesterFullName(me.getFullName());
        req.setDepartment(me.getDepartment());
        req.setType(cleanType);
        req.setRequestDate(fromDate);
        req.setToDate(toDate);
        req.setReason(reason.trim());
        req.setTotalDays(totalDays);
        if (LeaveRequest.TYPE_LEAVE.equals(cleanType)) {
            req.setLeaveType(leaveType == null || leaveType.isBlank() ? "NAM" : leaveType.trim());
            req.setSupporter(supporter == null || supporter.isBlank() ? null : supporter.trim());
        } else {
            req.setDuration(duration == null || duration.isBlank() ? "FULL" : duration.trim());
        }
        req.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        leaveRequestRepo.save(req);

        // Chuông cho các trưởng phòng cùng phòng ban
        if (me.getDepartment() != null) {
            for (AppUser u : appUserRepository.findAll()) {
                if (u.isDeptHead() && me.getDepartment().equals(u.getDepartment()) && u.getEmail() != null) {
                    emailService.notifyBell(u.getEmail().split("@")[0],
                            "📝 Đơn " + req.getTypeLabel().toLowerCase() + " mới từ " + (me.getFullName() != null ? me.getFullName() : req.getRequesterName()),
                            "Từ " + fromDate + " đến " + toDate + " (" + totalDays + " ngày) — " + req.getReason(),
                            "LEAVE_REQUEST", "/attendance/requests");
                }
            }
        }

        ra.addFlashAttribute("successMessage", "Đã gửi đơn " + req.getTypeLabel().toLowerCase()
                + " từ " + fromDate + " đến " + toDate + " (" + totalDays
                + " ngày). Đơn sẽ qua trưởng bộ phận rồi tới phòng NSHT duyệt.");
        return back;
    }

    /** Trang duyệt đơn cho trưởng phòng và nhân sự, kèm tổng kết tháng. */
    @GetMapping("/requests")
    public String requestsPage(@RequestParam(required = false) Integer month,
                               @RequestParam(required = false) Integer year,
                               org.springframework.security.core.Authentication authentication,
                               Model model) {
        AppUser me = currentAppUser(authentication);
        if (!canApproveRequests(me)) {
            return "redirect:/attendance/my";
        }
        boolean hr = isHr(me);

        // Danh sách chờ TÔI duyệt
        List<LeaveRequest> waitingForMe = new ArrayList<>();
        if (me.isDeptHead() && me.getDepartment() != null) {
            waitingForMe.addAll(leaveRequestRepo.findByDepartmentAndStatusOrderByCreatedAtDesc(
                    me.getDepartment(), LeaveRequest.ST_PENDING));
        }
        if (hr) {
            waitingForMe.addAll(leaveRequestRepo.findByStatusOrderByCreatedAtDesc(LeaveRequest.ST_HEAD_APPROVED));
            // Nhân sự / Admin thấy luôn đơn chưa qua trưởng phòng để nắm tình hình (duyệt vượt cấp được)
            for (LeaveRequest r : leaveRequestRepo.findByStatusOrderByCreatedAtDesc(LeaveRequest.ST_PENDING)) {
                if (waitingForMe.stream().noneMatch(x -> x.getId().equals(r.getId()))) {
                    waitingForMe.add(r);
                }
            }
        }

        // Tổng kết tháng theo đơn ĐÃ DUYỆT: mỗi nhân viên bao nhiêu ngày phép, bao nhiêu lần trễ
        YearMonth ym = resolveMonth(month, year);
        List<LeaveRequest> monthApproved = leaveRequestRepo
                .findByRequestDateBetweenOrderByRequestDateAsc(ym.atDay(1), ym.atEndOfMonth()).stream()
                .filter(r -> LeaveRequest.ST_APPROVED.equals(r.getStatus()))
                .collect(java.util.stream.Collectors.toList());
        Map<String, double[]> summary = new java.util.TreeMap<>(); // tên -> [ngày nghỉ phép, ngày bổ sung công]
        for (LeaveRequest r : monthApproved) {
            String key = (r.getRequesterFullName() != null ? r.getRequesterFullName() : r.getRequesterName())
                    + " (" + r.getEmployeeCode() + ")";
            double[] c = summary.computeIfAbsent(key, k -> new double[2]);
            double days = r.getTotalDays() != null ? r.getTotalDays() : 1.0;
            if (LeaveRequest.TYPE_LEAVE.equals(r.getType())) c[0] += days; else c[1] += days;
        }

        model.addAttribute("waitingForMe", waitingForMe);
        model.addAttribute("recentRequests", leaveRequestRepo.findTop30ByOrderByCreatedAtDesc());
        model.addAttribute("summary", summary);
        model.addAttribute("monthApproved", monthApproved);
        model.addAttribute("isHr", hr);
        model.addAttribute("month", ym.getMonthValue());
        model.addAttribute("year", ym.getYear());
        model.addAttribute("prevMonth", ym.minusMonths(1));
        model.addAttribute("nextMonth", ym.plusMonths(1));
        model.addAttribute("activePage", "leave-requests");
        return "attendance-requests";
    }

    /**
     * Tải phiếu .docx của một đơn theo đúng mẫu công ty, để nhân sự xem và in.
     * Người xin xem được phiếu của chính mình; trưởng phòng / nhân sự xem mọi phiếu.
     */
    @GetMapping("/requests/{id}/file")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadRequestFile(
            @PathVariable Long id,
            org.springframework.security.core.Authentication authentication) throws java.io.IOException {

        LeaveRequest req = leaveRequestRepo.findById(id).orElse(null);
        AppUser me = currentAppUser(authentication);
        if (req == null || me == null) {
            return ResponseEntity.notFound().build();
        }
        boolean own = req.getEmployeeCode() != null && req.getEmployeeCode().equals(me.getEmployeeCode());
        if (!own && !canApproveRequests(me)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        java.io.ByteArrayInputStream in = leaveRequestDocService.build(req);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + leaveRequestDocService.fileName(req));
        return ResponseEntity.ok().headers(headers)
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(new org.springframework.core.io.InputStreamResource(in));
    }

    /** Duyệt một đơn: trưởng phòng đẩy lên HEAD_APPROVED, nhân sự chốt APPROVED. */
    @PostMapping("/requests/{id}/approve")
    public String approveRequest(@PathVariable Long id,
                                 org.springframework.security.core.Authentication authentication,
                                 org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        AppUser me = currentAppUser(authentication);
        LeaveRequest r = leaveRequestRepo.findById(id).orElse(null);
        if (r == null || !canApproveRequests(me)) {
            return "redirect:/attendance/requests";
        }
        String approver = me.getEmail() != null ? me.getEmail().split("@")[0] : authentication.getName();

        if (LeaveRequest.ST_PENDING.equals(r.getStatus())) {
            boolean headOfDept = me.isDeptHead() && me.getDepartment() != null
                    && me.getDepartment().equals(r.getDepartment());
            if (!headOfDept && !isHr(me)) {
                ra.addFlashAttribute("errorMessage", "Đơn này thuộc phòng khác, bạn không duyệt được.");
                return "redirect:/attendance/requests";
            }
            r.setStatus(LeaveRequest.ST_HEAD_APPROVED);
            r.setHeadBy(approver);
            r.setHeadAt(LocalDateTime.now());
            // Nhân sự duyệt thẳng từ PENDING thì coi như qua luôn cả 2 cấp
            if (isHr(me)) {
                r.setStatus(LeaveRequest.ST_APPROVED);
                r.setHrBy(approver);
                r.setHrAt(LocalDateTime.now());
            }
        } else if (LeaveRequest.ST_HEAD_APPROVED.equals(r.getStatus())) {
            if (!isHr(me)) {
                ra.addFlashAttribute("errorMessage", "Đơn đang chờ nhân sự chốt — trưởng phòng đã duyệt rồi.");
                return "redirect:/attendance/requests";
            }
            r.setStatus(LeaveRequest.ST_APPROVED);
            r.setHrBy(approver);
            r.setHrAt(LocalDateTime.now());
        } else {
            return "redirect:/attendance/requests";
        }
        leaveRequestRepo.save(r);

        // Chuông cho người xin; nếu mới qua cấp 1 thì báo thêm phòng nhân sự
        String stageMsg = LeaveRequest.ST_APPROVED.equals(r.getStatus())
                ? "đã được DUYỆT (đủ 2 cấp)" : "đã được trưởng phòng xác nhận, đang chờ nhân sự";
        emailService.notifyBell(r.getRequesterName(),
                "✅ Đơn " + r.getTypeLabel().toLowerCase() + " ngày " + r.getRequestDate() + " " + stageMsg,
                "Duyệt bởi " + approver, "LEAVE_REQUEST", "/attendance/my");
        if (LeaveRequest.ST_HEAD_APPROVED.equals(r.getStatus())) {
            for (AppUser u : appUserRepository.findAll()) {
                if ("NSHC".equals(u.getDepartment()) && u.getEmail() != null) {
                    emailService.notifyBell(u.getEmail().split("@")[0],
                            "📝 Đơn " + r.getTypeLabel().toLowerCase() + " chờ nhân sự chốt",
                            (r.getRequesterFullName() != null ? r.getRequesterFullName() : r.getRequesterName())
                                    + " — ngày " + r.getRequestDate(), "LEAVE_REQUEST", "/attendance/requests");
                }
            }
        }

        // Duyệt đủ 2 cấp thì tính lại công ngay cho khoảng ngày của đơn — nhân sự
        // thấy số công đúng luôn, không phải bấm "Tính lại công" thủ công.
        String extra = "";
        if (LeaveRequest.ST_APPROVED.equals(r.getStatus())) {
            try {
                attendanceService.rebuild(r.getRequestDate(), r.getEffectiveToDate());
                extra = " Đã cập nhật công cho ngày trong đơn.";
            } catch (Exception e) {
                extra = " (Chưa tính lại được công, vui lòng bấm \"Tính lại công\".)";
            }
        }

        ra.addFlashAttribute("successMessage", "Đã duyệt đơn của "
                + (r.getRequesterFullName() != null ? r.getRequesterFullName() : r.getRequesterName()) + "." + extra);
        return "redirect:/attendance/requests";
    }

    /** Từ chối một đơn ở bất kỳ cấp nào (kèm lý do). */
    @PostMapping("/requests/{id}/reject")
    public String rejectRequest(@PathVariable Long id,
                                @RequestParam(required = false) String rejectReason,
                                org.springframework.security.core.Authentication authentication,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        AppUser me = currentAppUser(authentication);
        LeaveRequest r = leaveRequestRepo.findById(id).orElse(null);
        if (r == null || !canApproveRequests(me)) {
            return "redirect:/attendance/requests";
        }
        if (LeaveRequest.ST_APPROVED.equals(r.getStatus()) && !isHr(me)) {
            ra.addFlashAttribute("errorMessage", "Đơn đã chốt, chỉ nhân sự mới hủy được.");
            return "redirect:/attendance/requests";
        }
        String approver = me.getEmail() != null ? me.getEmail().split("@")[0] : authentication.getName();
        r.setStatus(LeaveRequest.ST_REJECTED);
        r.setRejectBy(approver);
        r.setRejectReason(rejectReason == null || rejectReason.isBlank() ? null : rejectReason.trim());
        leaveRequestRepo.save(r);

        emailService.notifyBell(r.getRequesterName(),
                "❌ Đơn " + r.getTypeLabel().toLowerCase() + " ngày " + r.getRequestDate() + " bị từ chối",
                (r.getRejectReason() != null ? "Lý do: " + r.getRejectReason() : "Từ chối bởi " + approver),
                "LEAVE_REQUEST", "/attendance/my");

        ra.addFlashAttribute("successMessage", "Đã từ chối đơn.");
        return "redirect:/attendance/requests";
    }

    /** Tìm AppUser theo tên đăng nhập: khớp email đầy đủ hoặc phần trước dấu @ (cùng luật đăng nhập). */
    private AppUser currentAppUser(org.springframework.security.core.Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        String login = auth.getName().trim().toLowerCase();
        String prefix = login.split("@")[0];
        String email365 = ReporterIdentity.emailOf(auth);
        String email365Lower = email365 == null ? null : email365.trim().toLowerCase();
        for (AppUser u : appUserRepository.findAll()) {
            if (u.getEmail() == null) continue;
            String dbEmail = u.getEmail().trim().toLowerCase();
            String dbPrefix = dbEmail.split("@")[0];
            if (dbEmail.equals(login) || dbPrefix.equals(prefix)
                    || (email365Lower != null && (dbEmail.equals(email365Lower) || dbPrefix.equals(email365Lower.split("@")[0])))) {
                return u;
            }
        }
        return null;
    }

    @GetMapping("/user")
    public String userCalendar(@RequestParam String employeeCode,
                               @RequestParam(required = false) Long deviceId,
                               @RequestParam(required = false) Integer month,
                               @RequestParam(required = false) Integer year,
                               Model model) {
        YearMonth ym = resolveMonth(month, year);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // 1. Định danh người = (máy, mã) — các văn phòng trùng dải mã. Không truyền máy
        //    thì lấy người đầu tiên khớp mã (tương thích link cũ / mã không trùng).
        List<AttendanceService.Person> roster = attendanceService.roster();
        AttendanceService.Person person = roster.stream()
                .filter(p -> p.getCode().equals(employeeCode))
                .filter(p -> deviceId == null || deviceId.equals(p.getDeviceId()))
                .findFirst()
                .orElse(null);
        Long personDeviceId = person != null ? person.getDeviceId() : deviceId;

        // 2. Resolve shift
        Shift defaultShift = shiftRepo.findFirstByIsDefaultTrue().orElse(null);
        Map<Long, Shift> shiftsById = new HashMap<>();
        for (Shift s : shiftRepo.findAll()) shiftsById.put(s.getId(), s);
        Shift shift = attendanceService.assignedShiftId(employeeCode, personDeviceId)
                .map(shiftsById::get)
                .orElse(defaultShift);

        // 3. Find records — lọc theo đúng máy của người này
        List<AttendanceRecord> records = attendanceService.findRecordsForPerson(employeeCode, personDeviceId, from, to);
        model.addAttribute("selectedDeviceId", personDeviceId);

        // Đơn nghỉ phép / bổ sung công: đánh dấu MỌI ngày trong dải từ-đến của đơn.
        // Đặt ở đây để cả nhân viên tự xem lẫn nhân sự xem hộ đều thấy.
        Map<LocalDate, LeaveRequest> requestsByDate = new HashMap<>();
        for (LeaveRequest r : leaveRequestRepo.findOverlapping(from, to)) {
            if (!employeeCode.equals(r.getEmployeeCode())) continue;
            if (personDeviceId != null && r.getDeviceId() != null && !personDeviceId.equals(r.getDeviceId())) continue;
            for (LocalDate d = r.getRequestDate(); !d.isAfter(r.getEffectiveToDate()); d = d.plusDays(1)) {
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    LeaveRequest current = requestsByDate.get(d);
                    if (current == null || LeaveRequest.ST_APPROVED.equals(r.getStatus())) {
                        requestsByDate.put(d, r);
                    }
                }
            }
        }
        model.addAttribute("requestsByDate", requestsByDate);
        Map<LocalDate, AttendanceRecord> recordsByDate = new HashMap<>();
        for (AttendanceRecord r : records) {
            recordsByDate.put(r.getWorkDate(), r);
        }

        // 4. Calculate standard work days
        int standardWorkDays = 0;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            if (shift != null && shift.isWorkingDay(date.getDayOfWeek())) {
                standardWorkDays++;
            }
        }
        double standardWorkHours = 0.0;
        if (shift != null) {
            standardWorkHours = standardWorkDays * (shift.getStandardMinutes() / 60.0);
        }

        // 5. Construct calendar weeks
        List<List<CalendarDay>> weeks = new ArrayList<>();
        List<CalendarDay> currentWeek = new ArrayList<>();
        weeks.add(currentWeek);

        LocalDate gridStart = from;
        while (gridStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            gridStart = gridStart.minusDays(1);
        }
        LocalDate gridEnd = to;
        while (gridEnd.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            gridEnd = gridEnd.plusDays(1);
        }

        double actualWorkDays = 0.0;
        double actualWorkHours = 0.0;
        int earlyCount = 0;
        int earlyMinutes = 0;
        int absentCount = 0;

        for (LocalDate day = gridStart; !day.isAfter(gridEnd); day = day.plusDays(1)) {
            if (currentWeek.size() == 7) {
                currentWeek = new ArrayList<>();
                weeks.add(currentWeek);
            }

            CalendarDay cd = new CalendarDay();
            cd.setDate(day);
            cd.setDayOfMonth(day.getDayOfMonth());
            cd.setCurrentMonth(day.getMonthValue() == ym.getMonthValue() && day.getYear() == ym.getYear());
            cd.setWeekend(day.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day.getDayOfWeek() == java.time.DayOfWeek.SUNDAY);

            AttendanceRecord rec = recordsByDate.get(day);
            boolean isCurrentMonthDay = day.getMonthValue() == ym.getMonthValue() && day.getYear() == ym.getYear();

            if (isCurrentMonthDay) {
                if (rec == null) {
                    rec = new AttendanceRecord();
                    rec.setWorkDate(day);
                    rec.setEmployeeCode(employeeCode);
                    if (person != null) {
                        rec.setEmployeeName(person.getName());
                        rec.setDepartment(person.getDepartment());
                    }
                    if (shift != null) {
                        rec.setShiftId(shift.getId());
                        rec.setShiftName(shift.getName());
                    }
                    
                    boolean isWorking = shift != null && shift.isWorkingDay(day.getDayOfWeek());
                    if (!isWorking) {
                        rec.setStatus(AttendanceRecord.ST_OFF);
                        rec.setWorkDays(0.0);
                    } else if (day.isAfter(LocalDate.now())) {
                        rec.setStatus("NONE");
                        rec.setWorkDays(0.0);
                    } else {
                        rec.setStatus(AttendanceRecord.ST_ABSENT);
                        rec.setWorkDays(0.0);
                        absentCount++;
                    }
                } else {
                    actualWorkDays += rec.getWorkDays() == null ? 0.0 : rec.getWorkDays();
                    actualWorkHours += (rec.getWorkedMinutes() == null ? 0.0 : rec.getWorkedMinutes()) / 60.0;
                    if (rec.getEarlyMinutes() != null && rec.getEarlyMinutes() > 0) {
                        earlyCount++;
                        earlyMinutes += rec.getEarlyMinutes();
                    }
                    if (AttendanceRecord.ST_ABSENT.equals(rec.getStatus())) {
                        absentCount++;
                    }
                }
            } else {
                if (rec == null) {
                    rec = new AttendanceRecord();
                    rec.setWorkDate(day);
                    rec.setStatus("NONE");
                    rec.setWorkDays(0.0);
                }
            }

            cd.setRecord(rec);

            if (shift != null) {
                cd.setWorkingDay(shift.isWorkingDay(day.getDayOfWeek()));
                cd.setShiftName(shift.getName());
            } else {
                cd.setWorkingDay(true);
                cd.setShiftName("—");
            }

            // Build time label
            if (rec.getFirstIn() != null) {
                if (rec.getLastOut() != null) {
                    cd.setTimeLabel(formatTime(rec.getFirstIn().toLocalTime()) + " - " + formatTime(rec.getLastOut().toLocalTime()));
                } else {
                    cd.setTimeLabel(formatTime(rec.getFirstIn().toLocalTime()) + " - ...");
                }
            } else if (shift != null && shift.getStartTime() != null && cd.isWorkingDay() && !"OFF".equals(rec.getStatus())) {
                cd.setTimeLabel(formatTime(shift.getStartTime()) + " - " + formatTime(shift.getEndTime()));
            } else {
                cd.setTimeLabel("");
            }

            currentWeek.add(cd);
        }

        // Stats Map for sidebar
        Map<String, Object> stats = new HashMap<>();
        stats.put("standardWorkDays", standardWorkDays);
        stats.put("standardWorkHours", String.format(Locale.US, "%.1f", standardWorkHours));
        stats.put("actualWorkDays", String.format(Locale.US, "%.1f", actualWorkDays));
        stats.put("actualWorkHours", String.format(Locale.US, "%.1f", actualWorkHours));
        
        stats.put("workedDays", String.format(Locale.US, "%.4f", actualWorkDays));
        stats.put("actualWorkedHours", String.format(Locale.US, "%.4f", actualWorkHours));
        stats.put("earlyCount", earlyCount);
        stats.put("earlyMinutes", earlyMinutes);
        stats.put("absentCount", absentCount);
        stats.put("actualDaytimeWorkedHours", String.format(Locale.US, "%.4f", actualWorkHours));
        stats.put("workedDaysByShift", String.format(Locale.US, "%.4f", actualWorkDays));

        model.addAttribute("employeeCode", employeeCode);
        model.addAttribute("employeeName", person != null ? person.getName() : employeeCode);
        model.addAttribute("department", person != null ? person.getDepartment() : (shift != null ? shift.getName() : ""));
        model.addAttribute("shiftName", shift != null ? shift.getName() : "—");
        model.addAttribute("month", ym.getMonthValue());
        model.addAttribute("year", ym.getYear());
        model.addAttribute("prevMonth", ym.minusMonths(1));
        model.addAttribute("nextMonth", ym.plusMonths(1));
        model.addAttribute("weeks", weeks);
        model.addAttribute("stats", stats);

        return "attendance-user";
    }

    private String formatTime(java.time.LocalTime time) {
        if (time == null) return "";
        return String.format("%d:%02d", time.getHour(), time.getMinute());
    }

    public static class CalendarDay {
        private LocalDate date;
        private int dayOfMonth;
        private boolean currentMonth;
        private AttendanceRecord record;
        private String shiftName;
        private boolean weekend;
        private boolean workingDay;
        private String timeLabel;

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public int getDayOfMonth() { return dayOfMonth; }
        public void setDayOfMonth(int dayOfMonth) { this.dayOfMonth = dayOfMonth; }

        public boolean isCurrentMonth() { return currentMonth; }
        public void setCurrentMonth(boolean currentMonth) { this.currentMonth = currentMonth; }

        public AttendanceRecord getRecord() { return record; }
        public void setRecord(AttendanceRecord record) { this.record = record; }

        public String getShiftName() { return shiftName; }
        public void setShiftName(String shiftName) { this.shiftName = shiftName; }

        public boolean isWeekend() { return weekend; }
        public void setWeekend(boolean weekend) { this.weekend = weekend; }

        public boolean isWorkingDay() { return workingDay; }
        public void setWorkingDay(boolean workingDay) { this.workingDay = workingDay; }

        public String getTimeLabel() { return timeLabel; }
        public void setTimeLabel(String timeLabel) { this.timeLabel = timeLabel; }
    }
}
