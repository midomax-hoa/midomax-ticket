package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class EmailService {

    /** Địa chỉ thật của hệ thống, dùng cho các nút bấm trong email gửi đi. */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private GraphEmailService graphEmailService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    public String resolveEmail(String nameOrUsername) {
        if (nameOrUsername == null || nameOrUsername.trim().isEmpty()) {
            return null;
        }
        String cleanName = nameOrUsername.trim();
        if (cleanName.contains("@")) {
            return cleanName;
        }

        String lower = cleanName.toLowerCase();

        // 1. Specific known staff keywords & short names in Midomax (substring & fuzzy match)
        if (lower.equals("admin") || lower.equals("it01") || lower.equals("it") || lower.equals("it admin") || lower.equals("quản trị viên") || lower.startsWith("admin ") || lower.endsWith(" admin")) return "it01@midomax.vn";
        if (lower.contains("kim")) return "Kim@midomax.vn";
        if (lower.contains("tín") || lower.contains("tin")) return "tinnt@midomax.vn";
        if (lower.contains("hưng") || lower.contains("hung")) return "hung@midomax.vn";
        if (lower.contains("bảo") || lower.contains("bao")) return "bao@midomax.vn";
        if (lower.contains("trinh")) return "trinh@midomax.vn";

        // 2. Search in AppUser (by email prefix, exact email, or full name substring)
        for (AppUser u : appUserRepository.findAll()) {
            if (u.getEmail() != null) {
                String dbEmail = u.getEmail().trim();
                String prefix = dbEmail.split("@")[0].toLowerCase();
                if (lower.equalsIgnoreCase(dbEmail) || lower.equalsIgnoreCase(prefix) || lower.contains(prefix)) {
                    return dbEmail;
                }
            }
            if (u.getFullName() != null) {
                String fullName = u.getFullName().trim().toLowerCase();
                if (fullName.equalsIgnoreCase(lower) || fullName.contains(lower) || lower.contains(fullName)) {
                    if (u.getEmail() != null && !u.getEmail().trim().isEmpty()) {
                        return u.getEmail().trim();
                    }
                }
            }
        }
        
        // 3. Search in Employee (by code, email prefix, or full name substring)
        for (Employee e : employeeRepository.findAll()) {
            if (e.getEmployeeCode() != null && e.getEmployeeCode().equalsIgnoreCase(cleanName)) {
                return e.getCompanyEmail() != null && !e.getCompanyEmail().isEmpty() ? e.getCompanyEmail() : e.getPersonalEmail();
            }
            if (e.getCompanyEmail() != null) {
                String compEmail = e.getCompanyEmail().trim();
                String prefix = compEmail.split("@")[0].toLowerCase();
                if (lower.equalsIgnoreCase(compEmail) || lower.equalsIgnoreCase(prefix) || lower.contains(prefix)) {
                    return compEmail;
                }
            }
            if (e.getFullName() != null) {
                String fullName = e.getFullName().trim().toLowerCase();
                if (fullName.equalsIgnoreCase(lower) || fullName.contains(lower) || lower.contains(fullName)) {
                    return e.getCompanyEmail() != null && !e.getCompanyEmail().isEmpty() ? e.getCompanyEmail() : e.getPersonalEmail();
                }
            }
        }
        
        // 4. Default fallback: remove diacritics / spaces and append @midomax.vn
        String asciiName = java.text.Normalizer.normalize(cleanName, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        return asciiName + "@midomax.vn";
    }

    public void sendTicketNotification(Ticket ticket, String eventType) {
        if (ticket == null) return;

        String reporterName = ticket.getReporterName() != null ? ticket.getReporterName() : "Nhân viên";
        String assigneeName = ticket.getAssignee();
        
        String reporterEmail = resolveEmail(reporterName);
        String assigneeEmail = resolveEmail(assigneeName);

        String title = ticket.getTitle() != null ? ticket.getTitle() : "Yêu cầu hỗ trợ IT";
        String ticketIdStr = "#" + ticket.getId();
        String priority = ticket.getPriority() != null ? ticket.getPriority() : "LOW";
        String linkUrl = "/ticket-management";

        try {
            if ("CREATED".equalsIgnoreCase(eventType)) {
                // 1. Notify Reporter
                String repTitle = "🎟️ [MIDOMAX IT] Đã tiếp nhận Ticket " + ticketIdStr + ": " + title;
                String repMsg = "Chào <b>" + reporterName + "</b>,<br><br>Yêu cầu hỗ trợ IT <b>'" + title + "'</b> của bạn đã được Hệ thống Helpdesk Midomax tiếp nhận thành công. Mức ưu tiên: <b>" + priority + "</b>.<br>Bộ phận IT sẽ sớm phân công nhân sự kiểm tra và xử lý theo cam kết chất lượng dịch vụ.";
                saveNotification(reporterName, repTitle, "Yêu cầu '" + title + "' của bạn đã được hệ thống IT tiếp nhận. Mức ưu tiên: " + priority, "TICKET_CREATED", linkUrl);
                sendHtmlEmail(reporterEmail, repTitle, buildEmailTemplate("XÁC NHẬN TIẾP NHẬN YÊU CẦU IT", repTitle, repMsg, ticket));

                // 2. If already assigned, notify Assignee
                if (assigneeName != null && !assigneeName.isEmpty()) {
                    String assTitle = "🎯 [MIDOMAX IT] Bạn được phân công Ticket " + ticketIdStr + ": " + title;
                    String assMsg = "Chào <b>" + assigneeName + "</b>,<br><br>Bạn vừa được Quản trị viên chỉ định phụ trách xử lý yêu cầu hỗ trợ mới <b>'" + title + "'</b> từ người yêu cầu <b>" + reporterName + "</b>.<br>Vui lòng kiểm tra trên cổng Helpdesk và tiến hành khắc phục kịp thời.";
                    saveNotification(assigneeName, assTitle, "Bạn được chỉ định xử lý yêu cầu mới: '" + title + "' từ " + reporterName, "TICKET_ASSIGNED", linkUrl);
                    sendHtmlEmail(assigneeEmail, assTitle, buildEmailTemplate("PHÂN CÔNG NHIỆM VỤ IT MỚI", assTitle, assMsg, ticket));
                }

                // 3. Notify Admin in UI Notification Bell only (do not send email to system sender it01@midomax.vn)
                String adminTitle = "📢 [MIDOMAX IT] Ticket Mới " + ticketIdStr + " từ " + reporterName + ": " + title;
                saveNotification("admin", adminTitle, "Có yêu cầu hỗ trợ mới từ '" + reporterName + "': '" + title + "'", "TICKET_CREATED", linkUrl);
                saveNotification("it01@midomax.vn", adminTitle, "Có yêu cầu hỗ trợ mới từ '" + reporterName + "': '" + title + "'", "TICKET_CREATED", linkUrl);
            } else if ("ASSIGNED".equalsIgnoreCase(eventType) || "PROGRESS".equalsIgnoreCase(eventType)) {
                // 1. Notify Reporter that ticket is being handled
                String repTitle = "👨‍💻 [MIDOMAX IT] Ticket " + ticketIdStr + " đang được xử lý";
                String repMsg = "Chào <b>" + reporterName + "</b>,<br><br>Chuyên viên IT <b>" + (assigneeName != null ? assigneeName : "Chuyên viên IT") + "</b> đã tiếp nhận và đang tiến hành xử lý yêu cầu <b>'" + title + "'</b> của bạn.";
                saveNotification(reporterName, repTitle, "Nhân sự IT " + (assigneeName != null ? assigneeName : "Chuyên viên IT") + " đã tiếp nhận xử lý yêu cầu", "TICKET_ASSIGNED", linkUrl);
                sendHtmlEmail(reporterEmail, repTitle, buildEmailTemplate("ĐANG TIẾN HÀNH XỬ LÝ YÊU CẦU", repTitle, repMsg, ticket));

                // 2. Notify Assignee
                if (assigneeName != null && !assigneeName.isEmpty()) {
                    String assTitle = "🎯 [MIDOMAX IT] Bạn được phân công Ticket " + ticketIdStr + ": " + title;
                    String assMsg = "Chào <b>" + assigneeName + "</b>,<br><br>Bạn vừa được Quản trị viên chỉ định phụ trách xử lý yêu cầu hỗ trợ <b>'" + title + "'</b> (Mức ưu tiên: <b>" + priority + "</b>) từ <b>" + reporterName + "</b>.<br><br>Vui lòng kiểm tra trên Cổng thông tin Helpdesk để tiến hành xử lý kịp thời.";
                    saveNotification(assigneeName, assTitle, "Bạn được chỉ định xử lý yêu cầu: '" + title + "' từ " + reporterName, "TICKET_ASSIGNED", linkUrl);
                    sendHtmlEmail(assigneeEmail, assTitle, buildEmailTemplate("NHIỆM VỤ IT MỚI ĐƯỢC PHÂN CÔNG", assTitle, assMsg, ticket));
                }
            } else if ("RESOLVED".equalsIgnoreCase(eventType) || "COMPLETED".equalsIgnoreCase(eventType)) {
                String fixNote = ticket.getFixNote() != null && !ticket.getFixNote().trim().isEmpty() ? ticket.getFixNote() : "Đã hoàn thành xử lý yêu cầu và kiểm tra kỹ thuật.";
                
                // 1. Notify Reporter that ticket is completed by IT & pending confirmation
                String repTitle = "✅ [MIDOMAX IT] Ticket " + ticketIdStr + " đã được IT xử lý xong - Vui lòng kiểm tra & xác nhận";
                String repMsg = "Chào <b>" + reporterName + "</b>,<br><br>Yêu cầu hỗ trợ <b>'" + title + "'</b> của bạn đã được chuyên viên IT <b>" + (assigneeName != null ? assigneeName : "") + "</b> xử lý xong.<br><br><b>💡 Giải pháp / Ghi chú kỹ thuật từ IT:</b><br>" + fixNote + "<br><br>👉 <b>Vui lòng truy cập Cổng Helpdesk để kiểm tra và bấm 'Xác nhận hoàn thành (hết lỗi)' hoặc ghi chú phản hồi nếu còn lỗi.</b><br><i style='color: #64748b; font-size: 13px;'>* Lưu ý: Hệ thống sẽ tự động đóng Ticket này sau 3 ngày nếu không nhận được phản hồi từ bạn.</i>";
                saveNotification(reporterName, repTitle, "Yêu cầu '" + title + "' đã được IT xử lý xong. Vui lòng xác nhận hoặc phản hồi.", "TICKET_RESOLVED", linkUrl);
                sendHtmlEmail(reporterEmail, repTitle, buildEmailTemplate("XÁC NHẬN KẾT QUẢ XỬ LÝ IT", repTitle, repMsg, ticket));

                // 2. Notify Assignee
                if (assigneeName != null && !assigneeName.isEmpty()) {
                    String assTitle = "🎯 [MIDOMAX IT] Bạn đã hoàn thành Ticket " + ticketIdStr + " (Chờ User xác nhận)";
                    String assMsg = "Chào <b>" + assigneeName + "</b>,<br><br>Hệ thống ghi nhận bạn đã hoàn thành xử lý Ticket #" + ticket.getId() + " - <b>'" + title + "'</b>.<br>Email xác nhận đã được gửi tới người dùng " + reporterName + ".";
                    saveNotification(assigneeName, assTitle, "Bạn đã hoàn thành Ticket #" + ticket.getId() + " (Đã gửi thông báo cho User)", "TICKET_RESOLVED", linkUrl);
                }
            } else if ("CLOSED".equalsIgnoreCase(eventType)) {
                String closeReason = ticket.getCloseReason() != null ? ticket.getCloseReason() : "Ticket đã được đóng thành công.";
                
                String repTitle = "🔒 [MIDOMAX IT] Ticket " + ticketIdStr + " ĐÃ ĐÓNG TÁC VỤ";
                String repMsg = "Chào <b>" + reporterName + "</b>,<br><br>Ticket #" + ticket.getId() + " - <b>'" + title + "'</b> đã chính thức được đóng.<br><b>Ghi chú đóng:</b> " + closeReason;
                saveNotification(reporterName, repTitle, "Ticket #" + ticket.getId() + " đã chính thức đóng", "TICKET_CLOSED", linkUrl);
                sendHtmlEmail(reporterEmail, repTitle, buildEmailTemplate("TICKET ĐÃ ĐÓNG CHÍNH THỨC", repTitle, repMsg, ticket));

                if (assigneeName != null && !assigneeName.isEmpty()) {
                    String assTitle = "🔒 [MIDOMAX IT] Ticket " + ticketIdStr + " ĐÃ ĐÓNG";
                    String assMsg = "Chào <b>" + assigneeName + "</b>,<br><br>Ticket #" + ticket.getId() + " - <b>'" + title + "'</b> đã được đóng.<br><b>Lý do/Trạng thái:</b> " + closeReason;
                    saveNotification(assigneeName, assTitle, "Ticket #" + ticket.getId() + " đã đóng: " + closeReason, "TICKET_CLOSED", linkUrl);
                    sendHtmlEmail(assigneeEmail, assTitle, buildEmailTemplate("TICKET ĐÃ ĐÓNG", assTitle, assMsg, ticket));
                }
            } else if ("REOPENED".equalsIgnoreCase(eventType)) {
                String userFeedback = ticket.getUserFeedback() != null ? ticket.getUserFeedback() : "Người dùng phản hồi chưa hết lỗi.";
                
                if (assigneeName != null && !assigneeName.isEmpty()) {
                    String assTitle = "⚠️ [MIDOMAX IT] Phản hồi lỗi còn tồn tại trên Ticket " + ticketIdStr;
                    String assMsg = "Chào <b>" + assigneeName + "</b>,<br><br>Người dùng <b>" + reporterName + "</b> phản hồi công việc <b>'" + title + "'</b> chưa hết lỗi và cần xử lý tiếp.<br><br><b>📌 Ghi chú phản hồi lỗi từ User:</b><br><span style='color: #dc2626; font-weight: bold;'>" + userFeedback + "</span><br><br>Vui lòng kiểm tra và tiếp tục khắc phục cho người dùng.";
                    saveNotification(assigneeName, assTitle, "Người dùng " + reporterName + " phản hồi Ticket #" + ticket.getId() + " còn lỗi: " + userFeedback, "TICKET_REOPENED", linkUrl);
                    sendHtmlEmail(assigneeEmail, assTitle, buildEmailTemplate("PHẢN HỒI LỖI TỪ NGƯỜI DÙNG", assTitle, assMsg, ticket));
                }
            }
        } catch (Exception e) {
            System.err.println("[EMAIL NOTIFICATION ERROR] Error processing notification: " + e.getMessage());
        }
    }

    private void saveNotification(String recipient, String title, String message, String type, String linkUrl) {
        if (recipient == null || recipient.trim().isEmpty()) return;
        try {
            Notification notif = new Notification(recipient.trim(), title, message, type, linkUrl);
            notificationRepository.save(notif);
            
            // Also create an ALL broadcast if it's a general notification or high priority
            if ("HIGH".equalsIgnoreCase(type) || "BROADCAST".equalsIgnoreCase(type)) {
                Notification allNotif = new Notification("ALL", title, message, type, linkUrl);
                notificationRepository.save(allNotif);
            }
        } catch (Exception e) {
            System.err.println("[NOTIFICATION SAVE ERROR] " + e.getMessage());
        }
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        if (toEmail == null || toEmail.trim().isEmpty()) return;

        // Gọi sang service Graph API để thực hiện gửi email thực tế
        graphEmailService.sendEmail(toEmail, subject, htmlContent);
    }

    private String buildEmailTemplate(String headerTitle, String subject, String messageContent, Ticket ticket) {
        String priorityColor = "#3b82f6";
        String priorityLabel = "Bình thường (LOW)";
        if ("HIGH".equalsIgnoreCase(ticket.getPriority())) {
            priorityColor = "#ef4444";
            priorityLabel = "Khẩn cấp (HIGH)";
        } else if ("MED".equalsIgnoreCase(ticket.getPriority())) {
            priorityColor = "#f59e0b";
            priorityLabel = "Trung bình (MED)";
        }

        String dateStr = ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "N/A";
        String slaStr = ticket.getSlaDeadline() != null ? ticket.getSlaDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "Đang cập nhật";

        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='font-family: \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 30px 10px; color: #1e293b; -webkit-font-smoothing: antialiased;'>"
                + "<div style='max-width: 640px; margin: 0 auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.08), 0 8px 10px -6px rgba(0,0,0,0.04); border: 1px solid #e2e8f0;'>"
                + "  <!-- HEADER -->"
                + "  <div style='background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%); padding: 35px 30px; color: white; text-align: center; position: relative;'>"
                + "    <div style='font-size: 13px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase; color: #93c5fd; margin-bottom: 8px;'>HỆ THỐNG QUẢN LÝ DỊCH VỤ IT</div>"
                + "    <h1 style='margin: 0; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;'>MIDOMAX IT PORTAL</h1>"
                + "    <div style='display: inline-block; background: rgba(255,255,255,0.15); backdrop-filter: blur(4px); padding: 5px 16px; border-radius: 20px; font-size: 13px; font-weight: 600; margin-top: 15px; border: 1px solid rgba(255,255,255,0.2);'>" + headerTitle + "</div>"
                + "  </div>"
                + "  <!-- BODY -->"
                + "  <div style='padding: 35px 35px 25px;'>"
                + "    <h2 style='color: #0f172a; margin-top: 0; font-size: 20px; font-weight: 700; border-bottom: 2px solid #f1f5f9; padding-bottom: 12px;'>" + subject + "</h2>"
                + "    <div style='font-size: 15px; line-height: 1.7; color: #334155; background: #f8fafc; padding: 20px; border-left: 4px solid #3b82f6; border-radius: 8px; margin: 20px 0;'>" + messageContent + "</div>"
                + "    <!-- TICKET DETAILS TABLE -->"
                + "    <div style='margin-top: 30px; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;'>"
                + "      <div style='background: #f8fafc; padding: 12px 20px; border-bottom: 1px solid #e2e8f0; font-weight: 700; font-size: 14px; color: #475569; display: flex; align-items: center;'>"
                + "        <span>📋 THÔNG TIN CHI TIẾT TICKET #" + ticket.getId() + "</span>"
                + "      </div>"
                + "      <table style='width: 100%; border-collapse: collapse; font-size: 14px; text-align: left;'>"
                + "        <tr style='border-bottom: 1px solid #f1f5f9;'><td style='padding: 12px 20px; color: #64748b; width: 35%; font-weight: 600;'>Mã yêu cầu:</td><td style='padding: 12px 20px; color: #0f172a; font-weight: 700;'>#" + ticket.getId() + "</td></tr>"
                + "        <tr style='border-bottom: 1px solid #f1f5f9;'><td style='padding: 12px 20px; color: #64748b; font-weight: 600;'>Tiêu đề:</td><td style='padding: 12px 20px; color: #1e3a8a; font-weight: 700;'>" + (ticket.getTitle() != null ? ticket.getTitle() : "") + "</td></tr>"
                + "        <tr style='border-bottom: 1px solid #f1f5f9;'><td style='padding: 12px 20px; color: #64748b; font-weight: 600;'>Người yêu cầu:</td><td style='padding: 12px 20px; color: #0f172a; font-weight: 600;'>" + (ticket.getReporterName() != null ? ticket.getReporterName() : "") + " <span style='color:#64748b; font-weight:normal;'>(" + (ticket.getReporterDepartment() != null ? ticket.getReporterDepartment() : "Nhân viên") + ")</span></td></tr>"
                + "        <tr style='border-bottom: 1px solid #f1f5f9;'><td style='padding: 12px 20px; color: #64748b; font-weight: 600;'>Người phụ trách:</td><td style='padding: 12px 20px; color: #0f172a; font-weight: 700;'>" + (ticket.getAssignee() != null && !ticket.getAssignee().isEmpty() ? ticket.getAssignee() : "<span style='color:#94a3b8; font-style:italic;'>Chưa phân công</span>") + "</td></tr>"
                + "        <tr style='border-bottom: 1px solid #f1f5f9;'><td style='padding: 12px 20px; color: #64748b; font-weight: 600;'>Danh mục:</td><td style='padding: 12px 20px; color: #334155; text-transform: capitalize;'>" + (ticket.getCategory() != null ? ticket.getCategory() : "Chung") + "</td></tr>"
                + "        <tr style='border-bottom: 1px solid #f1f5f9;'><td style='padding: 12px 20px; color: #64748b; font-weight: 600;'>Mức độ ưu tiên:</td><td style='padding: 12px 20px;'><span style='background: " + priorityColor + "; color: white; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 700; display: inline-block;'>" + priorityLabel + "</span></td></tr>"
                + "        <tr style='border-bottom: 1px solid #f1f5f9;'><td style='padding: 12px 20px; color: #64748b; font-weight: 600;'>Thời gian tiếp nhận:</td><td style='padding: 12px 20px; color: #475569;'>" + dateStr + "</td></tr>"
                + "        <tr><td style='padding: 12px 20px; color: #64748b; font-weight: 600;'>Hạn xử lý (SLA):</td><td style='padding: 12px 20px; color: #dc2626; font-weight: 600;'>" + slaStr + "</td></tr>"
                + "      </table>"
                + "    </div>"
                + "    <!-- CTA BUTTON -->"
                + "    <div style='text-align: center; margin: 35px 0 15px;'>"
                + "      <a href='" + baseUrl + "/ticket-management' style='background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); color: #ffffff; padding: 14px 32px; border-radius: 10px; text-decoration: none; font-weight: 700; font-size: 15px; display: inline-block; box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25); letter-spacing: 0.3px;'>Truy Cập Helpdesk Portal &amp; Xử Lý Ngay</a>"
                + "    </div>"
                + "  </div>"
                + "  <!-- FOOTER -->"
                + "  <div style='background: #f8fafc; padding: 25px 30px; text-align: center; border-top: 1px solid #e2e8f0; font-size: 13px; color: #64748b; line-height: 1.6;'>"
                + "    <div style='font-weight: 700; color: #334155; margin-bottom: 4px;'>BỘ PHẬN CÔNG NGHỆ THÔNG TIN MIDOMAX</div>"
                + "    <div>Hệ thống Quản lý Helpdesk &amp; Hỗ trợ Kỹ thuật Nội bộ</div>"
                + "    <div style='margin-top: 10px; font-size: 12px; color: #94a3b8;'>Đây là email thông báo tự động từ Midomax IT Portal. Vui lòng không trả lời trực tiếp email này.<br>&copy; 2026 Midomax Vietnam. All rights reserved.</div>"
                + "  </div>"
                + "</div>"
                + "</body></html>";
    }
}
