package vn.midomax.helpdesk;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @PostConstruct
    public void seedNotifications() {
        if (notificationRepository.count() == 0) {
            notificationRepository.save(new Notification("ALL", "🎟️ Ticket Mới: #1 Cấp phát màn hình rời", "Yêu cầu cần màn hình 24 inch từ hành chính đã được hệ thống tiếp nhận.", "TICKET_CREATED", "/ticket-management"));
            notificationRepository.save(new Notification("ALL", "👨‍💻 IT Tiếp Nhận: Ticket #2 Hỗ trợ phần mềm Misa", "IT Trịnh đã tiếp nhận yêu cầu cài đặt phần mềm kế toán.", "TICKET_ASSIGNED", "/ticket-management"));
            notificationRepository.save(new Notification("ALL", "✅ Hoàn Thành: Ticket #3 Kiểm tra mạng tầng 2", "IT Dinh đã xử lý xong sự cố mạng wifi tầng 2. Khắc phục: Đã thay switch mới.", "TICKET_RESOLVED", "/ticket-management"));
        }
    }

    /**
     * Danh tính người nhận LUÔN lấy từ phiên đăng nhập. Trước đây có nhận thêm tham số
     * ?user= để thay thế khi chưa đăng nhập — cộng với việc endpoint để permitAll thì
     * người ngoài chỉ cần đoán email là đọc được chuông của nhân viên. Đã bỏ hẳn.
     */
    private List<String> getRecipientKeys(Authentication auth) {
        Set<String> keys = new HashSet<>();
        keys.add("ALL");

        if (auth == null || auth.getName() == null || auth.getName().isEmpty()) {
            return new ArrayList<>(keys); // chưa đăng nhập: chỉ thấy thông báo chung
        }
        String username = auth.getName();

        keys.add(username);
        keys.add(username.toLowerCase());

        if (username.contains("@")) {
            keys.add(username.split("@")[0]);
        }

        // Với tài khoản 365, auth.getName() là tên hiển thị nên không khớp thông báo
        // gửi theo email — bổ sung email lấy từ claim. Xem ReporterIdentity.
        String email = ReporterIdentity.emailOf(auth);
        if (email != null) {
            keys.add(email);
            keys.add(email.toLowerCase());
            keys.add(email.split("@")[0]);
        }
        
        // Find by email in AppUser
        AppUser u = appUserRepository.findByEmail(username).orElse(null);
        if (u != null) {
            if (u.getEmail() != null) {
                keys.add(u.getEmail());
                keys.add(u.getEmail().split("@")[0]);
            }
            if (u.getFullName() != null) keys.add(u.getFullName());
        }
        
        return new ArrayList<>(keys);
    }

    @GetMapping("/list")
    public ResponseEntity<List<Notification>> getNotifications(Authentication auth) {
        List<String> recipients = getRecipientKeys(auth);
        List<Notification> list = notificationRepository.findTop20ByRecipientInOrderByCreatedAtDesc(recipients);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication auth) {
        List<String> recipients = getRecipientKeys(auth);
        long count = notificationRepository.countByRecipientInAndReadStatusFalse(recipients);
        Map<String, Object> res = new HashMap<>();
        res.put("count", count);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/mark-read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication auth) {
        List<String> recipients = getRecipientKeys(auth);
        List<Notification> unread = notificationRepository.findByRecipientInAndReadStatusFalse(recipients);
        for (Notification n : unread) {
            n.setReadStatus(true);
        }
        notificationRepository.saveAll(unread);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/mark-read/{id}")
    public ResponseEntity<Map<String, Object>> markOneAsRead(@PathVariable("id") Long id, Authentication auth) {
        Optional<Notification> opt = notificationRepository.findById(id);
        // Chỉ đánh dấu được thông báo gửi cho chính mình — trước đây nhận ID trần nên
        // ai cũng tắt được chuông của người khác.
        if (opt.isPresent() && getRecipientKeys(auth).contains(opt.get().getRecipient())) {
            Notification n = opt.get();
            n.setReadStatus(true);
            notificationRepository.save(n);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }
}
