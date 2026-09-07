package vn.midomax.helpdesk;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tool TÍNH CÔNG cho nhân sự: mỗi nhân viên MỘT dòng tổng kết cả tháng
 * (tổng công, ngày làm, nghỉ phép, vắng, đi trễ, OT) — khác trang /attendance
 * là lịch từng ngày. Số liệu cộng thẳng từ AttendanceRecord đã chốt (nút
 * "Tính lại công" bên Lịch Chấm Công), không tự tính lại ở đây.
 * URL nằm dưới /attendance nên tự ăn quyền phân hệ HR trong ModuleAccessInterceptor.
 */
@Controller
@RequestMapping("/attendance/timesheet")
public class AttendanceTimesheetController {

    private final AttendanceService attendanceService;
    private final AttendanceDeviceRepository deviceRepo;
    private final ShiftRepository shiftRepo;
    private final ExcelService excelService;
    private final AppUserRepository appUserRepository;
    private final TimesheetMailService timesheetMailService;

    public AttendanceTimesheetController(AttendanceService attendanceService,
                                         AttendanceDeviceRepository deviceRepo,
                                         ShiftRepository shiftRepo,
                                         ExcelService excelService,
                                         AppUserRepository appUserRepository,
                                         TimesheetMailService timesheetMailService) {
        this.attendanceService = attendanceService;
        this.deviceRepo = deviceRepo;
        this.shiftRepo = shiftRepo;
        this.excelService = excelService;
        this.appUserRepository = appUserRepository;
        this.timesheetMailService = timesheetMailService;
    }

    /** Một dòng bảng công: nhân viên + các tổng của tháng. */
    public static class TimesheetRow {
        String employeeCode;
        String email;
        String fullName;
        String department;
        String deviceName;
        String shiftName;
        int standardDays;       // số ngày làm việc theo ca trong tháng (công chuẩn)
        double totalWorkDays;   // tổng công (0/0.5/1 cộng dồn)
        int presentDays;        // số ngày có đi làm (công > 0, không tính nghỉ phép)
        int leaveDays;          // số ngày nghỉ phép được duyệt
        int absentDays;         // số ngày vắng không phép
        int lateCount;          // số lần đi trễ
        int lateMinutes;        // tổng phút đi trễ
        int otMinutes;          // tổng phút tăng ca
        int missingDays;        // số ngày chỉ quét 1 lần (thiếu giờ vào/ra) — phải xử tay

        public String getEmployeeCode() { return employeeCode; }
        public String getEmail() { return email; }
        public String getFullName() { return fullName; }
        public String getDepartment() { return department; }
        public String getDeviceName() { return deviceName; }
        public String getShiftName() { return shiftName; }
        public int getStandardDays() { return standardDays; }
        public double getTotalWorkDays() { return totalWorkDays; }
        public int getPresentDays() { return presentDays; }
        public int getLeaveDays() { return leaveDays; }
        public int getAbsentDays() { return absentDays; }
        public int getLateCount() { return lateCount; }
        public int getLateMinutes() { return lateMinutes; }
        public int getOtMinutes() { return otMinutes; }
        /** Tăng ca đổi ra giờ cho dễ đọc, ví dụ 90 phút -> 1.5. */
        public double getOtHours() { return Math.round(otMinutes / 60.0 * 10) / 10.0; }
        public int getMissingDays() { return missingDays; }
        /** Công còn thiếu so với công chuẩn (đã trừ phần nghỉ phép có công). */
        public double getShortDays() { return Math.max(0, standardDays - totalWorkDays); }
        /**
         * Dòng cần nhân sự để mắt: có ngày thiếu chấm hoặc vắng không phép.
         * Những dòng còn lại coi như sạch — khỏi phải dò từng ngày.
         */
        public boolean isNeedsReview() { return missingDays > 0 || absentDays > 0; }
    }

    @GetMapping
    public String timesheet(@RequestParam(required = false) Integer month,
                            @RequestParam(required = false) Integer year,
                            @RequestParam(required = false) String department,
                            @RequestParam(required = false) Long deviceId,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) Boolean issuesOnly,
                            Model model) {
        YearMonth ym = resolveMonth(month, year);
        List<TimesheetRow> rows = buildRows(ym, department, deviceId, search);

        // Đếm tổng quan TRƯỚC khi lọc "cần kiểm tra" để 3 ô số luôn đúng toàn cảnh
        long reviewCount = rows.stream().filter(TimesheetRow::isNeedsReview).count();
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("okCount", rows.size() - reviewCount);

        boolean onlyIssues = Boolean.TRUE.equals(issuesOnly);
        if (onlyIssues) {
            rows = rows.stream().filter(TimesheetRow::isNeedsReview).toList();
        }
        model.addAttribute("issuesOnly", onlyIssues);

        model.addAttribute("rows", rows);
        model.addAttribute("month", ym.getMonthValue());
        model.addAttribute("year", ym.getYear());
        model.addAttribute("prevMonth", ym.minusMonths(1));
        model.addAttribute("nextMonth", ym.plusMonths(1));
        // Danh sách 11 phòng ban CHUẨN của công ty (cùng nguồn với Quản lý user),
        // không đổ free-text từ hồ sơ nữa — hồ sơ gõ tay đủ kiểu nên dropdown loạn.
        model.addAttribute("departments", Department.values());
        model.addAttribute("devices", deviceRepo.findAll());
        model.addAttribute("selectedDept", department);
        model.addAttribute("selectedDeviceId", deviceId);
        model.addAttribute("searchQuery", search);
        model.addAttribute("activePage", "timesheet");
        return "attendance-timesheet";
    }

    /**
     * XEM TRƯỚC bảng công của một nhân viên — đúng nội dung mail sẽ gửi (dùng chung
     * hàm dựng HTML với lúc gửi thật). Nhân sự duyệt xong mới bấm nút Gửi trong trang này.
     */
    @GetMapping("/preview")
    public ResponseEntity<String> preview(@RequestParam Integer month, @RequestParam Integer year,
                                          @RequestParam String employeeCode) {
        YearMonth ym = resolveMonth(month, year);
        TimesheetRow row = buildRows(ym, null, null, null).stream()
                .filter(r -> employeeCode.equals(r.getEmployeeCode()))
                .findFirst().orElse(null);
        if (row == null) {
            return ResponseEntity.status(404).contentType(MediaType.TEXT_HTML)
                    .body("<p style='font-family:sans-serif;'>Không tìm thấy bảng công của mã " + employeeCode + ".</p>");
        }

        String mailHtml = timesheetMailService.html(row, ym);
        // Nhúng qua iframe srcdoc để nội dung mail giữ nguyên, không đụng CSS trang ngoài
        String srcdoc = mailHtml.replace("&", "&amp;").replace("\"", "&quot;");
        String email = row.getEmail() == null ? "(chưa có email)" : row.getEmail();
        String page = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Xem trước bảng công</title></head>"
                + "<body style='margin:0; font-family:Segoe UI,Arial,sans-serif;'>"
                + "<div style='position:sticky; top:0; background:#1e3a8a; color:#fff; padding:12px 20px;"
                + " display:flex; align-items:center; gap:14px; flex-wrap:wrap;'>"
                + "<b>XEM TRƯỚC</b> — bảng công tháng " + ym.getMonthValue() + "/" + ym.getYear()
                + " của " + row.getFullName() + " · sẽ gửi đến <b>" + email + "</b>"
                + (row.getEmail() == null ? "" :
                    "<form method='post' action='/attendance/timesheet/send-mail' style='margin:0 0 0 auto;'"
                    + " onsubmit=\"return confirm('Xác nhận gửi mail bảng công cho " + row.getFullName() + "?');\">"
                    + "<input type='hidden' name='month' value='" + ym.getMonthValue() + "'>"
                    + "<input type='hidden' name='year' value='" + ym.getYear() + "'>"
                    + "<input type='hidden' name='employeeCode' value='" + row.getEmployeeCode() + "'>"
                    + "<button style='background:#15803d; color:#fff; border:none; padding:8px 18px;"
                    + " border-radius:8px; font-weight:700; cursor:pointer;'>✉ Xác nhận gửi mail</button></form>")
                + "</div>"
                + "<iframe style='width:100%; height:calc(100vh - 58px); border:0; background:#fff;'"
                + " srcdoc=\"" + srcdoc + "\"></iframe>"
                + "</body></html>";
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(page);
    }

    /**
     * Gửi mail "Bảng chi tiết chấm công" cho nhân viên theo mẫu HCNS.
     * Có employeeCode -> gửi một người; không có -> gửi TẤT CẢ theo bộ lọc đang xem.
     * Người không có email trong hệ thống được đếm riêng để nhân sự biết mà bổ sung.
     */
    @org.springframework.web.bind.annotation.PostMapping("/send-mail")
    public String sendMail(@RequestParam Integer month, @RequestParam Integer year,
                           @RequestParam(required = false) String department,
                           @RequestParam(required = false) Long deviceId,
                           @RequestParam(required = false) String search,
                           @RequestParam(required = false) String employeeCode,
                           org.springframework.security.core.Authentication authentication,
                           org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        YearMonth ym = resolveMonth(month, year);
        // Mail đứng tên NHÂN SỰ đang thao tác. Tên đăng nhập không phải email
        // (tài khoản local kiểu "admin") thì để null -> rơi về hộp thư hệ thống.
        String sender = authentication != null && authentication.getName() != null
                && authentication.getName().contains("@") ? authentication.getName() : null;
        List<TimesheetRow> rows = buildRows(ym, department, deviceId, search);
        if (employeeCode != null && !employeeCode.isBlank()) {
            rows = rows.stream().filter(r -> employeeCode.equals(r.getEmployeeCode())).toList();
        }

        int sent = 0, noEmail = 0, failed = 0;
        for (TimesheetRow r : rows) {
            if (r.getEmail() == null || r.getEmail().isBlank()) { noEmail++; continue; }
            try {
                timesheetMailService.send(r, r.getEmail(), ym, sender);
                sent++;
            } catch (Exception e) {
                failed++;
            }
        }
        StringBuilder msg = new StringBuilder("Đã gửi bảng công tháng " + ym.getMonthValue() + "/" + ym.getYear()
                + " cho " + sent + " nhân viên.");
        if (noEmail > 0) msg.append(" ").append(noEmail).append(" người chưa có email trong hệ thống.");
        if (failed > 0) msg.append(" ").append(failed).append(" mail gửi LỖI — kiểm tra kết nối Microsoft 365.");
        redirect.addFlashAttribute(failed > 0 ? "errorMessage" : "successMessage", msg.toString());
        return "redirect:/attendance/timesheet?month=" + ym.getMonthValue() + "&year=" + ym.getYear();
    }

    /** Tính lại công cả tháng ngay tại đây — khỏi phải chạy qua trang Lịch Chấm Công. */
    @org.springframework.web.bind.annotation.PostMapping("/rebuild")
    public String rebuild(@RequestParam Integer month, @RequestParam Integer year,
                          org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        YearMonth ym = resolveMonth(month, year);
        int touched = attendanceService.rebuild(ym.atDay(1), ym.atEndOfMonth());
        redirect.addFlashAttribute("successMessage", "Đã tính lại công tháng "
                + ym.getMonthValue() + "/" + ym.getYear() + ": " + touched + " dòng.");
        return "redirect:/attendance/timesheet?month=" + ym.getMonthValue() + "&year=" + ym.getYear();
    }

    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> export(@RequestParam(required = false) Integer month,
                                                      @RequestParam(required = false) Integer year,
                                                      @RequestParam(required = false) String department,
                                                      @RequestParam(required = false) Long deviceId,
                                                      @RequestParam(required = false) String search) throws IOException {
        YearMonth ym = resolveMonth(month, year);
        ByteArrayInputStream in = excelService.exportTimesheetToExcel(buildRows(ym, department, deviceId, search), ym);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition",
                "attachment; filename=BangCong_" + ym.getMonthValue() + "_" + ym.getYear() + ".xlsx");
        return ResponseEntity.ok().headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    /**
     * Mỗi dòng là một USER trong hệ thống có gắn mã chấm công (Quản lý user):
     * tên + phòng ban lấy từ app_users, số công cộng từ AttendanceRecord theo
     * (máy, mã) của user đó. Người chỉ có trên máy mà chưa gắn user KHÔNG hiện
     * ở đây — muốn soát họ thì xem trang Lịch Chấm Công.
     */
    private List<TimesheetRow> buildRows(YearMonth ym, String department, Long deviceId, String search) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Map<String, List<AttendanceRecord>> byPerson = new HashMap<>();
        Map<String, List<AttendanceRecord>> legacyByCode = new HashMap<>();
        for (AttendanceRecord r : attendanceService.findRecords(from, to)) {
            byPerson.computeIfAbsent(r.personKey(), k -> new ArrayList<>()).add(r);
            if (r.getDeviceId() == null) {
                legacyByCode.computeIfAbsent(r.getEmployeeCode(), k -> new ArrayList<>()).add(r);
            }
        }

        Map<Long, Shift> shiftsById = new HashMap<>();
        for (Shift s : shiftRepo.findAll()) shiftsById.put(s.getId(), s);
        Shift defaultShift = shiftRepo.findFirstByIsDefaultTrue().orElse(null);

        Map<Long, String> deviceNames = new HashMap<>();
        for (AttendanceDevice d : deviceRepo.findAll()) deviceNames.put(d.getId(), d.getName());

        Department deptFilter = Department.fromString(department);
        String needle = search == null ? null : search.trim().toLowerCase(Locale.ROOT);

        List<TimesheetRow> rows = new ArrayList<>();
        for (AppUser u : appUserRepository.findAll()) {
            // Mã "0" là quy ước chưa có mã chấm công (dữ liệu cũ có thể còn lưu) — bỏ qua như trống
            if (u.getEmployeeCode() == null || u.getEmployeeCode().isBlank()
                    || u.getEmployeeCode().trim().equals("0")) continue;
            if (deviceId != null && !deviceId.equals(u.getAttendanceDeviceId())) continue;
            // app_users.department lưu sẵn MÃ chuẩn (B2B, ITD...) nên so thẳng
            if (deptFilter != null && Department.fromString(u.getDepartment()) != deptFilter) continue;
            if (needle != null && !needle.isBlank() && !matchesUser(u, needle)) continue;

            String code = u.getEmployeeCode().trim();
            TimesheetRow row = new TimesheetRow();
            row.employeeCode = code;
            row.email = u.getEmail();
            row.fullName = u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail();
            Department d = Department.fromString(u.getDepartment());
            row.department = d != null ? d.getLabel() : u.getDepartment();
            row.deviceName = deviceNames.get(u.getAttendanceDeviceId());

            Shift shift = attendanceService.assignedShiftId(code, u.getAttendanceDeviceId())
                    .map(shiftsById::get).orElse(defaultShift);
            row.shiftName = shift == null ? "— chưa gán ca —" : shift.getName();
            if (shift != null) {
                for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                    if (shift.isWorkingDay(day.getDayOfWeek())) row.standardDays++;
                }
            }

            String personKey = (u.getAttendanceDeviceId() == null ? "-" : u.getAttendanceDeviceId()) + "|" + code;
            List<AttendanceRecord> records = byPerson.get(personKey);
            if ((records == null || records.isEmpty()) && u.getAttendanceDeviceId() != null) {
                records = legacyByCode.get(code);
            }
            for (AttendanceRecord r : records == null ? Collections.<AttendanceRecord>emptyList() : records) {
                double workDays = r.getWorkDays() == null ? 0 : r.getWorkDays();
                row.totalWorkDays += workDays;
                boolean leave = AttendanceRecord.ST_LEAVE.equals(r.getStatus());
                if (leave) row.leaveDays++;
                else if (workDays > 0) row.presentDays++;
                if (AttendanceRecord.ST_ABSENT.equals(r.getStatus())) row.absentDays++;
                if (AttendanceRecord.ST_MISSING.equals(r.getStatus())) row.missingDays++;
                int late = r.getLateMinutes() == null ? 0 : r.getLateMinutes();
                if (late > 0) row.lateCount++;
                row.lateMinutes += late;
                row.otMinutes += r.getOtMinutes() == null ? 0 : r.getOtMinutes();
            }
            rows.add(row);
        }
        // Người CẦN KIỂM TRA nổi lên đầu, còn lại xếp theo tên — nhân sự xử từ trên xuống
        rows.sort(Comparator.comparing((TimesheetRow r) -> !r.isNeedsReview())
                .thenComparing(r -> r.fullName == null ? "" : r.fullName));
        return rows;
    }

    private boolean matchesUser(AppUser u, String needle) {
        return (u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(needle))
                || (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
                || u.getEmployeeCode().toLowerCase(Locale.ROOT).contains(needle);
    }

    private YearMonth resolveMonth(Integer month, Integer year) {
        YearMonth now = YearMonth.now();
        int m = month == null ? now.getMonthValue() : Math.min(Math.max(month, 1), 12);
        int y = year == null ? now.getYear() : year;
        return YearMonth.of(y, m);
    }
}
