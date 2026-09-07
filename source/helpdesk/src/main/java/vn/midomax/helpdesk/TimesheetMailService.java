package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Dựng và gửi mail "Bảng chi tiết chấm công" hằng tháng cho từng nhân viên,
 * theo mẫu của phòng HCNS (bảng định mức + chi tiết công + hạn phản hồi).
 * Gửi qua GraphEmailService (Microsoft 365) như mọi mail khác của hệ thống.
 */
@Service
public class TimesheetMailService {

    private final GraphEmailService graphEmailService;

    @Value("${app.base-url}")
    private String baseUrl;

    public TimesheetMailService(GraphEmailService graphEmailService) {
        this.graphEmailService = graphEmailService;
    }

    /**
     * Gửi bảng công của một người, ĐỨNG TÊN mailbox của nhân sự đang thao tác
     * (senderEmail) thay vì hộp thư hệ thống it01 — nhân viên phản hồi là tới
     * thẳng nhân sự. Ném exception nếu Graph từ chối — controller gom đếm.
     */
    public void send(AttendanceTimesheetController.TimesheetRow row, String email,
                     YearMonth ym, String senderEmail) {
        String subject = "[MIDOMAX] Bảng chi tiết chấm công tháng "
                + String.format(Locale.US, "%02d/%d", ym.getMonthValue(), ym.getYear());
        graphEmailService.sendEmailFrom(senderEmail, email, subject, html(row, ym));
    }

    /** Nội dung mail y như bản gửi đi — trang XEM TRƯỚC dùng chung để không lệch. */
    public String html(AttendanceTimesheetController.TimesheetRow r, YearMonth ym) {
        String monthLabel = String.format(Locale.US, "%02d/%d", ym.getMonthValue(), ym.getYear());
        // Hạn phản hồi: 12h00 ngày 05 tháng kế tiếp — theo quy ước của mẫu HCNS
        LocalDate deadline = ym.plusMonths(1).atDay(5);
        String deadlineStr = String.format(Locale.US, "12h00 ngày %02d/%02d/%d",
                deadline.getDayOfMonth(), deadline.getMonthValue(), deadline.getYear());

        int sundays = 0;
        for (LocalDate d = ym.atDay(1); !d.isAfter(ym.atEndOfMonth()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) sundays++;
        }
        double extraDays = Math.max(0, r.getTotalWorkDays() - r.getStandardDays());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style='font-family:Segoe UI,Arial,sans-serif; color:#1f2937; max-width:720px;'>");
        sb.append("<h2 style='color:#c2410c; text-align:center; margin-bottom:2px;'>BẢNG CHI TIẾT CHẤM CÔNG</h2>");
        sb.append("<h3 style='color:#c2410c; text-align:center; margin-top:0;'>THÁNG ").append(monthLabel).append("</h3>");

        sb.append("<p>Kính gửi Anh/Chị <b>").append(esc(r.getFullName())).append("</b>,</p>");
        sb.append("<p>Phòng HCNS đính kèm bảng chốt chấm công chi tiết bên dưới.<br>");
        sb.append("Mọi thắc mắc vui lòng phản hồi email này trước <b style='color:#dc2626;'>")
          .append(deadlineStr).append("</b>.<br>");
        sb.append("<i>Vui lòng <b style='color:#dc2626;'>không phản hồi mail</b> nếu dữ liệu chấm công đã chính xác.</i></p>");

        sb.append("<table cellspacing='0' cellpadding='7' style='border-collapse:collapse; width:100%; font-size:14px;'>");
        info(sb, "Họ tên", "<b>" + esc(r.getFullName()) + "</b>");
        info(sb, "Mã chấm công", "<b>" + esc(r.getEmployeeCode()) + "</b>");
        info(sb, "Bộ phận – Phòng ban", esc(r.getDepartment() == null ? "—" : r.getDepartment()));
        info(sb, "Ca làm việc", esc(r.getShiftName()));
        sb.append("<tr><td colspan='2' style='border:1px solid #9ca3af; background:#fef9c3;'>")
          .append("Để xem chi tiết thời gian chấm công từng ngày, vui lòng đăng nhập hệ thống: ")
          .append("<a href='").append(baseUrl).append("/attendance/my'><b>Lịch Công Của Tôi</b></a></td></tr>");

        section(sb, "Các định mức");
        num(sb, "1", "Số ngày của tháng " + monthLabel, String.valueOf(ym.lengthOfMonth()), false);
        num(sb, "2", "Số ngày chủ nhật", String.valueOf(sundays), false);
        num(sb, "3", "Định mức ngày công chuẩn (theo ca làm việc)", String.valueOf(r.getStandardDays()), true);

        section(sb, "Bảng chi tiết tháng đang xét");
        num(sb, "", "Số ngày nghỉ có phép (đã gửi đơn xác nhận)", fmt(r.getLeaveDays()), false);
        num(sb, "", "<span style='color:#dc2626;'><b>Nghỉ không phép</b></span>"
                + "<br><small>Nếu trong tháng không có ngày nghỉ không phép, vui lòng nhanh chóng bổ sung"
                + " xác nhận của trưởng Bộ phận đến P. HCNS</small>",
                fmt(r.getAbsentDays()), r.getAbsentDays() > 0);
        num(sb, "", "<span style='color:#dc2626;'><b>Số ngày thiếu chấm công (thiếu giờ vào/ra)</b></span>"
                + "<br><small>Nếu đi trễ/quên chấm công có lý do, vui lòng bổ sung xác nhận của trưởng"
                + " Bộ phận đến P. HCNS trước ngày cuối cùng của tháng</small>",
                fmt(r.getMissingDays()), r.getMissingDays() > 0);
        num(sb, "4", "<b>Ngày công được tính lương cơ bản</b>", fmt2(r.getTotalWorkDays()), true);
        num(sb, "", "Số ngày công dư (4) – (3) <small>(lương nhân hệ số ngày công dư – tùy Bộ phận)</small>",
                fmt2(extraDays), false);
        num(sb, "", "Tổng số giờ tăng ca", fmt2(r.getOtHours()), false);
        num(sb, "", "Số lần đi trễ (tổng " + r.getLateMinutes() + " phút)", fmt(r.getLateCount()), false);
        sb.append("</table>");

        sb.append("<p style='color:#6b7280; font-size:12.5px; margin-top:14px;'>")
          .append("Email gửi tự động từ hệ thống Midomax IT Portal — Phòng HCNS Công ty Midomax.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void info(StringBuilder sb, String label, String valueHtml) {
        sb.append("<tr><td style='border:1px solid #9ca3af; width:190px; background:#f8fafc;'>").append(label)
          .append("</td><td style='border:1px solid #9ca3af;'>").append(valueHtml).append("</td></tr>");
    }

    private static void section(StringBuilder sb, String title) {
        sb.append("<tr><td colspan='2' style='border:1px solid #9ca3af; background:#e2e8f0; font-weight:700;'>")
          .append(title).append("</td></tr>");
    }

    private static void num(StringBuilder sb, String no, String labelHtml, String value, boolean bold) {
        sb.append("<tr><td style='border:1px solid #9ca3af;'>")
          .append(no.isEmpty() ? "" : "<b>" + no + ".</b> ").append(labelHtml)
          .append("</td><td style='border:1px solid #9ca3af; width:90px; text-align:center;")
          .append(bold ? " font-weight:800;" : "").append("'>").append(value).append("</td></tr>");
    }

    private static String fmt(int v) { return String.valueOf(v); }

    private static String fmt2(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v)
                : String.format(Locale.US, "%.2f", v).replace('.', ',');
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
