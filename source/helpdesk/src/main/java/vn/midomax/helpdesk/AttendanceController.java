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

    /** Số người mỗi trang trên lịch chấm công. Vẽ hết một lúc thì trang nặng ~8 MB. */
    private static final int ROWS_PER_PAGE = 25;

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
    private final AttendanceSyncScheduler syncScheduler;
    private final GpsCheckinService gpsCheckinService;

    public AttendanceController(AttendanceService attendanceService,
                                AttendanceDeviceRepository deviceRepo,
                                ShiftRepository shiftRepo,
                                EmployeeShiftRepository employeeShiftRepo,
                                EmployeeRepository employeeRepo,
                                AppUserRepository appUserRepository,
                                LeaveRequestRepository leaveRequestRepo,
                                LeaveRequestDocService leaveRequestDocService,
                                EmailService emailService,
                                AttendanceDeviceUserRepository deviceUserRepo,
                                AttendanceSyncScheduler syncScheduler,
                                GpsCheckinService gpsCheckinService) {
        this.gpsCheckinService = gpsCheckinService;
        this.syncScheduler = syncScheduler;
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
                           @RequestParam(required = false) Integer page,
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

        // Phân trang danh sách người. Vẽ hết 239 người x 31 ngày là hơn 29.000 thẻ HTML,
        // trang nặng ~8 MB — máy yếu và điện thoại tải rất lâu. Mỗi trang một số ít người
        // thì nhẹ đi hàng chục lần mà vẫn xem đủ, vì đã có sẵn bộ lọc phòng ban / văn phòng.
        int totalRows = rows.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalRows / (double) ROWS_PER_PAGE));
        int currentPage = page == null ? 1 : Math.min(Math.max(page, 1), totalPages);
        int fromIdx = (currentPage - 1) * ROWS_PER_PAGE;
        int toIdx = Math.min(fromIdx + ROWS_PER_PAGE, totalRows);
        List<EmployeeRow> pageRows = fromIdx >= totalRows
                ? Collections.<EmployeeRow>emptyList() : rows.subList(fromIdx, toIdx);

        // Cho nhân sự thấy hệ thống có tự tải dữ liệu không, và máy nào đang lỗi
        model.addAttribute("autoSyncAt", syncScheduler.getLastRunAt());
        model.addAttribute("autoSyncResult", syncScheduler.getLastResult());

        // Đánh dấu cột HÔM NAY trên lịch — chỉ khi đang xem đúng tháng hiện tại
        LocalDate today = LocalDate.now();
        model.addAttribute("todayDay",
                ym.equals(YearMonth.from(today)) ? today.getDayOfMonth() : null);

        model.addAttribute("totalRows", totalRows);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("fromIdx", totalRows == 0 ? 0 : fromIdx + 1);
        model.addAttribute("toIdx", toIdx);
        model.addAttribute("rows", pageRows);
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
        if (device.getId() != null) {
            // Form của IT không có các trường dưới đây — nếu lưu nguyên entity thì chúng
            // bind null và bị GHI ĐÈ TRẮNG: mất toạ độ GPS nhân sự vừa đặt (trang Địa Điểm
            // Chấm Công) và mất lịch sử lần tải gần nhất. Chép lại từ bản ghi hiện có.
            AttendanceDevice existing = deviceRepo.findById(device.getId()).orElse(null);
            if (existing != null) {
                device.setLatitude(existing.getLatitude());
                device.setLongitude(existing.getLongitude());
                device.setRadiusMeters(existing.getRadiusMeters());
                device.setLastSyncAt(existing.getLastSyncAt());
                device.setLastSyncStatus(existing.getLastSyncStatus());
            }
        }
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
        // Nút chấm công GPS: chỉ hiện với người được cấp quyền
        model.addAttribute("gpsAllowed", me.isGpsCheckinAllowed() || me.isGpsFreeLocation());
        model.addAttribute("gpsFree", me.isGpsFreeLocation());

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
                    } else if (!day.isBefore(LocalDate.now())) {
                        // Gồm cả HÔM NAY: ca làm chưa kết thúc thì chưa kết luận vắng được.
                        // Trước đây dùng isAfter nên hôm nay vẫn bị ghi vắng từ sáng sớm.
                        rec.setStatus(AttendanceRecord.ST_NONE);
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
                    rec.setStatus(AttendanceRecord.ST_NONE);
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

    // ===================== CHẤM CÔNG GPS =====================

    /**
     * Trang chấm công riêng của NHÂN VIÊN (kiểu MISA/1Office): đồng hồ, nút chấm to,
     * trạng thái hôm nay (giờ vào/ra, đã chấm mấy lần), các lần chấm GPS gần nhất.
     * Miễn phân hệ HR trong ModuleAccessInterceptor — ai đăng nhập cũng vào được,
     * còn được BẤM chấm hay không thì theo cờ cấp từng người.
     */
    @GetMapping("/checkin")
    public String checkinPage(org.springframework.security.core.Authentication authentication, Model model) {
        AppUser me = currentAppUser(authentication);
        // Cờ "công tác" tự nó đã là cấp quyền chấm — khỏi phải bật thêm cờ thứ hai
        boolean allowed = me != null && (me.isGpsCheckinAllowed() || me.isGpsFreeLocation());
        String code = me != null ? me.getEmployeeCode() : null;
        Long deviceId = me != null ? me.getAttendanceDeviceId() : null;

        model.addAttribute("fullName", me != null && me.getFullName() != null ? me.getFullName()
                : (authentication != null ? authentication.getName() : ""));
        model.addAttribute("gpsAllowed", allowed);
        model.addAttribute("gpsFree", me != null && me.isGpsFreeLocation());
        model.addAttribute("hasCode", code != null && !code.isBlank() && deviceId != null);

        AttendanceDevice device = deviceId == null ? null : deviceRepo.findById(deviceId).orElse(null);
        model.addAttribute("officeName", device != null ? device.getName() : null);
        model.addAttribute("officeHasGps", device != null && device.hasGpsLocation());
        model.addAttribute("officeRadius", device != null ? device.getRadiusMeters() : null);

        // Hôm nay đã chấm gì (mọi nguồn: máy vân tay + GPS)
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        List<AttendanceLog> todayLogs = (code == null || code.isBlank())
                ? Collections.emptyList()
                : attendanceService.findLogsForPerson(code, deviceId, dayStart, dayStart.plusDays(1));
        model.addAttribute("firstIn", todayLogs.isEmpty() ? null : todayLogs.get(0).getPunchTime());
        model.addAttribute("lastOut", todayLogs.size() < 2 ? null
                : todayLogs.get(todayLogs.size() - 1).getPunchTime());
        model.addAttribute("punchCount", todayLogs.size());

        // Chỉ các lần chấm HÔM NAY — đủ để tự đối chiếu hình/vị trí/giờ trong ngày;
        // các ngày trước xem trong Lịch Công Của Tôi.
        model.addAttribute("myCheckins", (code == null || deviceId == null)
                ? Collections.emptyList()
                : gpsCheckinService.todayOf(code, deviceId));
        model.addAttribute("activePage", "my-attendance");
        return "attendance-checkin";
    }

    /**
     * Nhân viên bấm chấm công trên điện thoại: gửi toạ độ + selfie.
     * Quyền kiểm ở GpsCheckinService (cờ cấp từng người); URL này được miễn phân hệ HR
     * trong ModuleAccessInterceptor vì người dùng là nhân viên thường.
     */
    @PostMapping("/gps/checkin")
    @ResponseBody
    public Map<String, Object> gpsCheckin(@RequestParam("latitude") double latitude,
                                          @RequestParam("longitude") double longitude,
                                          @RequestParam(value = "accuracy", required = false) Double accuracy,
                                          @RequestParam(value = "selfie", required = false)
                                          org.springframework.web.multipart.MultipartFile selfie,
                                          jakarta.servlet.http.HttpServletRequest request,
                                          org.springframework.security.core.Authentication authentication) {
        AppUser me = currentAppUser(authentication);
        // Sau reverse proxy thì RemoteAddr là IP proxy; ưu tiên X-Forwarded-For nếu có
        String ip = request.getHeader("X-Forwarded-For");
        ip = (ip == null || ip.isBlank()) ? request.getRemoteAddr() : ip.split(",")[0].trim();

        GpsCheckinService.Result r = gpsCheckinService.checkin(me, latitude, longitude, accuracy, selfie, ip);
        Map<String, Object> res = new HashMap<>();
        res.put("ok", r.ok());
        res.put("message", r.message());
        return res;
    }

    /**
     * Các lần chấm GPS CỦA CHÍNH MÌNH trong một ngày bất kỳ — lịch cá nhân bấm ngày
     * nào thì popup gọi endpoint này để hiện hình + giờ + vị trí ngày đó.
     * Chỉ trả dữ liệu của người đang đăng nhập nên không lộ của ai khác.
     */
    @GetMapping("/gps/my-day")
    @ResponseBody
    public List<Map<String, Object>> gpsMyDay(@RequestParam("date") String date,
                                              org.springframework.security.core.Authentication authentication) {
        AppUser me = currentAppUser(authentication);
        if (me == null || me.getEmployeeCode() == null || me.getAttendanceDeviceId() == null) {
            return Collections.emptyList();
        }
        LocalDate day;
        try { day = LocalDate.parse(date); } catch (Exception e) { return Collections.emptyList(); }

        List<Map<String, Object>> out = new ArrayList<>();
        for (GpsCheckin c : gpsCheckinService.dayOf(me.getEmployeeCode(), me.getAttendanceDeviceId(), day)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("time", c.getPunchTime() == null ? "" :
                    c.getPunchTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            m.put("deviceName", c.getDeviceName());
            m.put("distance", c.getDistanceM() == null ? null : Math.round(c.getDistanceM()));
            m.put("free", c.isFreeLocation());
            m.put("location", c.getLocationName());
            m.put("mapUrl", c.getMapUrl());
            out.add(m);
        }
        return out;
    }

    /** Ảnh selfie của một lần chấm — chỉ chính chủ hoặc người có quyền duyệt xem được. */
    @GetMapping("/gps/selfie/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> gpsSelfie(
            @PathVariable Long id,
            org.springframework.security.core.Authentication authentication) {
        GpsCheckin c = gpsCheckinService.get(id);
        AppUser me = currentAppUser(authentication);
        if (c == null || me == null || c.getSelfieFile() == null) {
            return ResponseEntity.notFound().build();
        }
        boolean own = c.getEmployeeCode() != null && c.getEmployeeCode().equals(me.getEmployeeCode())
                && Objects.equals(c.getDeviceId(), me.getAttendanceDeviceId());
        if (!own && !canApproveRequests(me)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        vn.midomax.helpdesk.storage.StoredFile file = gpsCheckinService.openSelfie(c);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        // Ảnh riêng tư: trình duyệt cache riêng một lúc cho danh sách lướt mượt, proxy không giữ lại
        return vn.midomax.helpdesk.storage.StoredFileResponses.of(file,
                org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate());
    }

    /**
     * Trang nhân sự đặt ĐỊA ĐIỂM chấm công GPS cho từng văn phòng — bản đồ to, bấm chọn
     * điểm + bán kính. Tách khỏi form Máy chấm công (phần đó của IT: IP, cổng, khoá kết
     * nối) vì người đặt địa điểm là nhân sự, không nên phải mò vào cấu hình kỹ thuật.
     * URL thuộc /attendance nên phân hệ HR tự áp.
     */
    @GetMapping("/locations")
    public String gpsLocations(Model model) {
        List<AttendanceDevice> devices = deviceRepo.findAll();
        // Số người thuộc từng văn phòng, cho nhân sự thấy phạm vi ảnh hưởng
        Map<Long, Integer> peopleCount = new HashMap<>();
        for (AttendanceService.Person p : attendanceService.roster()) {
            if (p.getDeviceId() != null) peopleCount.merge(p.getDeviceId(), 1, Integer::sum);
        }
        model.addAttribute("devices", devices);
        model.addAttribute("peopleCount", peopleCount);
        model.addAttribute("activePage", "gps-locations");
        return "attendance-locations";
    }

    /**
     * Lưu RIÊNG toạ độ + bán kính. Không dùng /devices/save vì nó bind cả entity —
     * form thiếu trường nào là trường đó bị ghi đè thành null (mất IP, cổng...).
     */
    @PostMapping("/locations/save")
    public String saveGpsLocation(@RequestParam Long id,
                                  @RequestParam(required = false) Double latitude,
                                  @RequestParam(required = false) Double longitude,
                                  @RequestParam(required = false) Integer radiusMeters,
                                  RedirectAttributes redirect) {
        AttendanceDevice device = deviceRepo.findById(id).orElse(null);
        if (device == null) {
            redirect.addFlashAttribute("errorMessage", "Không tìm thấy văn phòng.");
            return "redirect:/attendance/locations";
        }
        device.setLatitude(latitude);
        device.setLongitude(longitude);
        device.setRadiusMeters(radiusMeters == null || radiusMeters < 30 ? 150 : radiusMeters);
        deviceRepo.save(device);
        redirect.addFlashAttribute("successMessage",
                latitude == null
                    ? "Đã tắt chấm công GPS cho " + device.getName() + "."
                    : "Đã lưu địa điểm chấm công cho " + device.getName()
                      + " (bán kính " + device.getRadiusMeters() + "m).");
        return "redirect:/attendance/locations";
    }

    /**
     * Trang nhân sự soát chấm GPS — xem THEO TỪNG NGƯỜI, không đổ cả công ty ra một
     * bảng: mặc định chỉ hiện danh sách người (không ảnh nào — rất nhẹ), bấm chọn một
     * người mới tải lịch sử + selfie của riêng người đó.
     */
    @GetMapping("/gps-audit")
    public String gpsAudit(@RequestParam(required = false) String code,
                           @RequestParam(required = false) Long deviceId,
                           org.springframework.security.core.Authentication authentication, Model model) {
        AppUser me = currentAppUser(authentication);
        if (!canApproveRequests(me) && (me == null || !"ROLE_ADMIN".equals(me.getRole()))) {
            return "redirect:/attendance";
        }
        model.addAttribute("people", gpsCheckinService.summary());
        // Tên hiển thị theo (máy, mã) — danh sách GPS chỉ có mã, tra tên từ roster
        Map<String, String> names = new HashMap<>();
        for (AttendanceService.Person p : attendanceService.roster()) {
            names.put((p.getDeviceId() == null ? "-" : p.getDeviceId()) + "|" + p.getCode(), p.getName());
        }
        model.addAttribute("personNames", names);
        model.addAttribute("selCode", code);
        model.addAttribute("selDeviceId", deviceId);
        model.addAttribute("checkins", (code == null || deviceId == null)
                ? Collections.emptyList() : gpsCheckinService.historyOf(code, deviceId));
        model.addAttribute("activePage", "attendance");
        return "attendance-gps";
    }
}
