package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TicketAutoCloseScheduler {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EmailService emailService;

    // Tự động kiểm tra mỗi tiếng 1 lần để đóng Ticket quá 3 ngày không nhận được phản hồi của User
    @Scheduled(cron = "0 0 * * * *")
    public void autoCloseUnconfirmedTickets() {
        try {
            LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
            List<Ticket> resolvedTickets = ticketRepository.findByStatus("RESOLVED");

            for (Ticket t : resolvedTickets) {
                LocalDateTime refTime = t.getItCompletedAt() != null ? t.getItCompletedAt() : t.getCompletedAt();
                if (refTime != null && refTime.isBefore(threeDaysAgo)) {
                    t.setStatus("CLOSED");
                    t.setClosedAt(LocalDateTime.now());
                    String completedStr = !t.getItCompletedAtStr().isEmpty() ? t.getItCompletedAtStr() : t.getCompletedAtStr();
                    t.setCloseReason("Tự động đóng do người dùng không phản hồi sau 3 ngày kể từ khi IT hoàn thành (IT hoàn thành lúc: " + completedStr + ")");
                    ticketRepository.save(t);

                    // Gửi mail & thông báo ghi nhận đóng tự động
                    emailService.sendTicketNotification(t, "CLOSED");
                }
            }
        } catch (Exception e) {
            System.err.println("[TICKET AUTO CLOSE SCHEDULER ERROR] " + e.getMessage());
        }
    }
}
