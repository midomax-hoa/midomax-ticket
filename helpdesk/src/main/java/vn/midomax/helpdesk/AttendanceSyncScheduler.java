package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tự động tải dữ liệu từ máy chấm công rồi tổng hợp lại công, để nhân viên vào xem
 * lịch của mình là thấy số mới nhất mà không phải chờ nhân sự bấm nút.
 *
 * Hai mốc chạy trong ngày:
 *  - 12h trưa: lấy chấm công BUỔI SÁNG, tính lại tháng hiện tại.
 *  - 12h khuya (00:00): lấy chấm công BUỔI CHIỀU của ngày vừa kết thúc, đồng thời CHỐT
 *    ngày đó. Phải có mốc này vì trong ngày hệ thống cố tình chưa kết luận vắng / thiếu
 *    quét (xem AttendanceService.compute) — qua nửa đêm mới đủ căn cứ chốt.
 *
 * Lưu ý mốc nửa đêm: lúc 00:00 thì "hôm nay" đã sang ngày mới, nên ngày cần chốt là
 * NGÀY HÔM QUA. Tính theo tháng của hôm qua để đêm mùng 1 vẫn chốt được ngày cuối
 * tháng trước, nếu lấy tháng hiện tại thì ngày cuối tháng không bao giờ được chốt.
 */
@Component
public class AttendanceSyncScheduler {

    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Autowired
    private AttendanceService attendanceService;

    /** Tắt lịch tự động bằng cách đặt attendance.auto-sync.enabled=false. */
    @Value("${attendance.auto-sync.enabled:true}")
    private boolean enabled;

    /**
     * Máy chấm công phản hồi chậm, một lượt tải có thể lâu hơn khoảng cách giữa hai lần
     * chạy. Khoá lại để không có hai lượt đồng bộ chồng lên nhau cùng ghi vào một bảng.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile LocalDateTime lastRunAt;
    private volatile String lastResult;

    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public String getLastResult() { return lastResult; }

    /** 12h trưa: lấy chấm công buổi sáng. */
    @Scheduled(cron = "${attendance.auto-sync.noon-cron:0 0 12 * * *}")
    public void noonSync() {
        if (!enabled) return;
        YearMonth now = YearMonth.now();
        runOnce("chấm công sáng", now.atDay(1), now.atEndOfMonth());
    }

    /** 12h khuya: lấy chấm công buổi chiều của ngày vừa qua và chốt ngày đó. */
    @Scheduled(cron = "${attendance.auto-sync.midnight-cron:0 0 0 * * *}")
    public void midnightSync() {
        if (!enabled) return;
        LocalDate yesterday = LocalDate.now().minusDays(1);
        YearMonth ym = YearMonth.from(yesterday);
        runOnce("chấm công chiều + chốt ngày " + yesterday, ym.atDay(1), ym.atEndOfMonth());

        // Sang tháng mới thì tháng hiện tại chưa có bản ghi nào cho tới trưa mai.
        // Dựng luôn để lịch tháng mới không rỗng khi nhân viên mở xem sáng hôm sau.
        YearMonth thisMonth = YearMonth.now();
        if (!thisMonth.equals(ym)) {
            runOnce("dựng lịch tháng mới " + thisMonth,
                    thisMonth.atDay(1), thisMonth.atEndOfMonth());
        }
    }

    private void runOnce(String label, LocalDate from, LocalDate to) {
        if (!running.compareAndSet(false, true)) {
            System.out.println("[CHAM CONG] Bỏ qua lượt " + label + " — lượt trước còn đang chạy.");
            return;
        }
        try {
            int inserted = 0;
            StringBuilder failures = new StringBuilder();

            List<AttendanceService.SyncResult> results = attendanceService.syncAllDevices();
            for (AttendanceService.SyncResult r : results) {
                if (r.isOk()) {
                    inserted += r.getInserted();
                } else {
                    if (failures.length() > 0) failures.append("; ");
                    failures.append(r.getDeviceName()).append(": ").append(r.getError());
                }
            }

            // Vẫn tính lại kể cả khi không có lần quét mới: trạng thái phụ thuộc ngày hiện tại
            // (hôm qua đang là "đang trong ca" thì đêm nay phải chuyển thành đủ công / vắng).
            int touched = attendanceService.rebuild(from, to);

            lastRunAt = LocalDateTime.now();
            lastResult = "Đồng bộ " + results.size() + " máy, thêm " + inserted
                    + " lần quét, tính lại " + touched + " ngày công."
                    + (failures.length() == 0 ? "" : " Máy lỗi — " + failures);
            System.out.println("[CHAM CONG] " + lastRunAt.format(LOG_FMT)
                    + " (" + label + ") " + lastResult);

        } catch (Exception e) {
            // Máy chấm công mất mạng là chuyện thường; nuốt lỗi để lượt sau vẫn chạy tiếp.
            lastRunAt = LocalDateTime.now();
            lastResult = "Lỗi: " + e.getMessage();
            System.err.println("[CHAM CONG] Lượt " + label + " lỗi: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
