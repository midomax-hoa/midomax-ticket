package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.midomax.helpdesk.storage.StorageService;
import vn.midomax.helpdesk.storage.StoredFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chấm công bằng GPS trên điện thoại.
 *
 * Lần chấm hợp lệ ghi MỘT dòng attendance_log với đúng (máy, mã NV) của người bấm —
 * từ đó trở đi mọi thứ (ghép ca, tính công, đi muộn, lịch, chốt 12h khuya) chạy y hệt
 * lần quét vân tay, không phải sửa logic tính công nào.
 *
 * Giới hạn phải biết: GPS từ trình duyệt CÓ THỂ bị giả (app fake GPS). Web không chặn
 * tuyệt đối được — nên mỗi lần chấm đều lưu selfie + toạ độ + độ chính xác + IP để
 * nhân sự soát lại, và tính năng chỉ cấp cho người được duyệt từng người một.
 */
@Service
public class GpsCheckinService {

    /** verifyMode riêng cho GPS, để phân biệt với vân tay (1) / thẻ (4) / khuôn mặt (15). */
    static final int VERIFY_MODE_GPS = 99;

    /** Hai lần bấm cách nhau dưới mức này thì từ chối — chống bấm liên tục. */
    private static final int MIN_GAP_MINUTES = 1;

    private static final long MAX_SELFIE_BYTES = 10L * 1024 * 1024;

    /**
     * Thư mục con trên kho file (MinIO khi production) chứa selfie. File RIÊNG TƯ: chỉ trả về
     * qua /attendance/gps/selfie/{id} sau khi kiểm chính chủ / người duyệt, không đi qua /uploads.
     */
    public static final String STORAGE_FOLDER = "gps-selfies";

    @Autowired private GpsCheckinRepository gpsRepo;
    @Autowired private AttendanceLogRepository logRepo;
    @Autowired private AttendanceDeviceRepository deviceRepo;
    @Autowired private AttendanceService attendanceService;
    @Autowired private StorageService storageService;

    /** Kết quả một lần chấm — ok/lý do từ chối + bản ghi để hiển thị lại cho người bấm. */
    public record Result(boolean ok, String message, GpsCheckin checkin) {
        static Result fail(String msg) { return new Result(false, msg, null); }
    }

    /**
     * Điểm độ nét của ảnh: phương sai Laplacian trên ảnh thu nhỏ 160px.
     * Ảnh nét có nhiều cạnh sắc -> điểm cao; ảnh mờ/rung -> điểm thấp.
     * Trả về -1 nếu không đọc được ảnh.
     */
    static double sharpnessOf(byte[] imageBytes) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
            if (img == null || img.getWidth() < 2 || img.getHeight() < 2) return -1;
            int w = 160;
            int h = Math.max(2, img.getHeight() * w / Math.max(1, img.getWidth()));
            java.awt.image.BufferedImage small =
                    new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = small.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();

            // Chuyển xám rồi tính Laplacian từng điểm ảnh
            double[][] gray = new double[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = small.getRGB(x, y);
                    gray[y][x] = 0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255);
                }
            }
            double sum = 0, sumSq = 0;
            int n = 0;
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    double lap = 4 * gray[y][x] - gray[y - 1][x] - gray[y + 1][x]
                               - gray[y][x - 1] - gray[y][x + 1];
                    sum += lap;
                    sumSq += lap * lap;
                    n++;
                }
            }
            double mean = sum / n;
            return sumSq / n - mean * mean; // phương sai
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Độ nét TÁCH VÙNG: [0] = vùng giữa (khuôn mặt), [1] = vành rìa (phông nền).
     * Ảnh xóa phông / chân dung: giữa nét mà rìa mượt bất thường -> tỉ lệ chênh rất lớn.
     * Ảnh thật trước tường trơn vẫn còn NHIỄU CẢM BIẾN ở rìa nên không rơi về ~0.
     */
    static double[] regionSharpnessOf(byte[] imageBytes) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
            if (img == null || img.getWidth() < 4 || img.getHeight() < 4) return new double[]{-1, -1};
            int w = 160;
            int h = Math.max(4, img.getHeight() * w / Math.max(1, img.getWidth()));
            java.awt.image.BufferedImage small =
                    new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = small.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();

            double[][] gray = new double[h][w];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int rgb = small.getRGB(x, y);
                    gray[y][x] = 0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255);
                }

            // Vùng giữa = 30-70% mỗi chiều; vành rìa = 20% mép ngoài
            int cx1 = (int) (w * 0.30), cx2 = (int) (w * 0.70);
            int cy1 = (int) (h * 0.30), cy2 = (int) (h * 0.70);
            int bx = (int) (w * 0.20), by = (int) (h * 0.20);

            double cSum = 0, cSq = 0; int cN = 0;
            double bSum = 0, bSq = 0; int bN = 0;
            for (int y = 1; y < h - 1; y++)
                for (int x = 1; x < w - 1; x++) {
                    double lap = 4 * gray[y][x] - gray[y - 1][x] - gray[y + 1][x]
                               - gray[y][x - 1] - gray[y][x + 1];
                    boolean center = x >= cx1 && x < cx2 && y >= cy1 && y < cy2;
                    boolean border = x < bx || x >= w - bx || y < by || y >= h - by;
                    if (center) { cSum += lap; cSq += lap * lap; cN++; }
                    else if (border) { bSum += lap; bSq += lap * lap; bN++; }
                }
            double cMean = cSum / Math.max(1, cN), bMean = bSum / Math.max(1, bN);
            return new double[]{ cSq / Math.max(1, cN) - cMean * cMean,
                                 bSq / Math.max(1, bN) - bMean * bMean };
        } catch (Exception e) {
            return new double[]{-1, -1};
        }
    }

    /** Độ sáng trung bình 0-255 — ảnh tối om thì có nét mấy cũng vô dụng. */
    static double brightnessOf(byte[] imageBytes) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
            if (img == null) return -1;
            long total = 0; int n = 0;
            int stepX = Math.max(1, img.getWidth() / 64), stepY = Math.max(1, img.getHeight() / 64);
            for (int y = 0; y < img.getHeight(); y += stepY) {
                for (int x = 0; x < img.getWidth(); x += stepX) {
                    int rgb = img.getRGB(x, y);
                    total += (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
                    n++;
                }
            }
            return n == 0 ? -1 : (double) total / n;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 850 -> "850m", 1143934 -> "1144km" — số mét dài quá đọc không nổi. */
    static String humanDistance(double meters) {
        return meters >= 1000 ? Math.round(meters / 1000.0) + "km" : Math.round(meters) + "m";
    }

    /**
     * Dịch ngược toạ độ -> địa chỉ chữ (OpenStreetMap Nominatim, miễn phí).
     * Trả null nếu dịch không được — người gọi tự lo phương án hiển thị thay thế.
     * Chỉ gọi cho lần chấm CÔNG TÁC (hiếm), không đụng giới hạn 1 request/giây của Nominatim.
     */
    static String reverseGeocode(double lat, double lng) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3)).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat="
                            + lat + "&lon=" + lng + "&zoom=16&accept-language=vi"))
                    // Header phải thuần ASCII — có dấu tiếng Việt là Java ném lỗi và geocode luôn null
                    .header("User-Agent", "MidomaxHelpdesk/1.0 (internal attendance)")
                    .timeout(java.time.Duration.ofSeconds(4))
                    .GET().build();
            String body = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"display_name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
            if (!m.find()) return null;
            String name = m.group(1).replace("\\\"", "\"").replace("\\\\", "\\").trim();
            if (name.isEmpty()) return null;
            return name.length() > 290 ? name.substring(0, 290) : name;
        } catch (Exception e) {
            return null;
        }
    }

    /** Khoảng cách hai toạ độ theo mét (công thức haversine). */
    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    public Result checkin(AppUser user, double latitude, double longitude, Double accuracy,
                          MultipartFile selfie, String ip) {
        // Cờ "công tác" (chấm tự do vị trí) tự nó đã là quyền chấm GPS
        if (user == null || (!user.isGpsCheckinAllowed() && !user.isGpsFreeLocation())) {
            return Result.fail("Tài khoản chưa được cấp quyền chấm công GPS. Liên hệ IT/nhân sự.");
        }
        String code = user.getEmployeeCode();
        Long deviceId = user.getAttendanceDeviceId();
        if (code == null || code.isBlank() || deviceId == null) {
            return Result.fail("Tài khoản chưa gán mã chấm công + văn phòng. Liên hệ IT để gán trước.");
        }
        AttendanceDevice device = deviceRepo.findById(deviceId).orElse(null);
        if (device == null) {
            return Result.fail("Văn phòng gán cho tài khoản không còn tồn tại. Liên hệ IT.");
        }
        // Toạ độ vô nghĩa (0,0 giữa biển) gần như chắc chắn là lỗi/giả
        if (Math.abs(latitude) < 0.01 && Math.abs(longitude) < 0.01) {
            return Result.fail("Không lấy được vị trí hợp lệ. Bật GPS rồi thử lại.");
        }
        if (selfie == null || selfie.isEmpty()) {
            return Result.fail("Thiếu ảnh selfie — quy định chấm GPS phải kèm ảnh.");
        }
        if (selfie.getSize() > MAX_SELFIE_BYTES) {
            return Result.fail("Ảnh selfie quá nặng (trên 10 MB).");
        }

        // Chống bấm dồn dập
        List<GpsCheckin> last = gpsRepo.findTop1ByEmployeeCodeAndDeviceIdOrderByPunchTimeDesc(code, deviceId);
        if (!last.isEmpty() && Duration.between(last.get(0).getPunchTime(), LocalDateTime.now())
                .toMinutes() < MIN_GAP_MINUTES) {
            return Result.fail("Vừa chấm xong, chờ ít nhất " + MIN_GAP_MINUTES + " phút rồi hãy chấm tiếp.");
        }

        // Chống fake GPS kiểu "dịch chuyển tức thời": so với lần chấm trước, nếu vị trí
        // nhảy xa hơn mức một người di chuyển thực tế được (150 km/h — dư cho ô tô/tàu,
        // dưới máy bay) thì chặn. Chỉ xét khi nhảy trên 3km để không chặn oan nhiễu GPS.
        if (!last.isEmpty() && last.get(0).getLatitude() != null && last.get(0).getLongitude() != null) {
            GpsCheckin prev = last.get(0);
            double jump = distanceMeters(prev.getLatitude(), prev.getLongitude(), latitude, longitude);
            long minutes = Math.max(1, Duration.between(prev.getPunchTime(), LocalDateTime.now()).toMinutes());
            double maxTravel = minutes * (150_000.0 / 60); // mét đi được với 150 km/h
            if (jump > 3000 && jump > maxTravel) {
                return Result.fail("Vị trí bất thường: cách lần chấm trước " + humanDistance(jump)
                        + " chỉ sau " + minutes + " phút — không thể di chuyển kịp."
                        + " Nếu bạn KHÔNG dùng fake GPS, chờ thêm rồi chấm lại và báo IT kiểm tra.");
            }
        }

        boolean free = user.isGpsFreeLocation();
        Double distance = null;
        if (device.hasGpsLocation()) {
            distance = distanceMeters(latitude, longitude, device.getLatitude(), device.getLongitude());
        }
        if (!free) {
            if (!device.hasGpsLocation()) {
                return Result.fail("Văn phòng " + device.getName()
                        + " chưa khai báo toạ độ GPS. Nhờ IT khai ở trang Máy chấm công.");
            }
            if (distance > device.getRadiusMeters()) {
                return Result.fail("Bạn đang cách " + device.getName() + " khoảng "
                        + Math.round(distance) + "m — ngoài bán kính cho phép ("
                        + device.getRadiusMeters() + "m). Tới gần văn phòng rồi chấm lại.");
            }
        }

        // Lưu selfie (chỉ nhận ảnh; tên file là UUID nên không dính path traversal)
        String selfieName;
        try {
            String ct = selfie.getContentType() == null ? "" : selfie.getContentType().toLowerCase();
            if (!ct.startsWith("image/")) {
                return Result.fail("Selfie phải là ảnh.");
            }
            byte[] selfieBytes = selfie.getBytes();

            // Chặn ảnh mờ / quá tối — ảnh không nhìn ra mặt thì nhân sự soát vô nghĩa.
            // Ngưỡng hiệu chuẩn bằng ảnh mẫu: ảnh nét ~4.000+ điểm, ảnh mờ ~4 điểm,
            // nên 15 là ranh rất an toàn (webcam hơi soft vẫn qua, rung/mờ hẳn mới chặn).
            // Kiểm ĐỘ SÁNG trước: ảnh tối om cũng không có cạnh nào nên nếu kiểm nét
            // trước sẽ báo nhầm "mờ" — người dùng cần biết đúng lỗi để sửa đúng cách.
            double bright = brightnessOf(selfieBytes);
            if (bright >= 0 && bright < 35) {
                return Result.fail("Ảnh selfie quá TỐI — ra chỗ đủ sáng rồi chụp lại.");
            }
            double sharp = sharpnessOf(selfieBytes);
            if (sharp >= 0 && sharp < 15) {
                return Result.fail("Ảnh selfie bị MỜ — giữ điện thoại yên, nhìn thẳng camera rồi chụp lại.");
            }

            // Chặn ảnh XÓA PHÔNG (chế độ chân dung / app làm mờ nền): mặt nét mà phông
            // mượt bất thường thì không xác minh được bối cảnh — kẽ hở gian lận vị trí.
            // Hiệu chuẩn: xóa phông cho rìa ~3 điểm; tường trơn THẬT vẫn ~9 nhờ nhiễu
            // cảm biến camera, nên ngưỡng 4.5 không chặn oan người đứng trước tường.
            double[] region = regionSharpnessOf(selfieBytes);
            if (region[0] > 100 && region[1] >= 0 && region[1] < 4.5) {
                return Result.fail("Ảnh đang bật chế độ XÓA PHÔNG / làm mờ nền — tắt hiệu ứng camera đi rồi chụp lại (phông nền phải thấy rõ).");
            }

            // Lưu lên kho file (MinIO khi production) trong thư mục RIÊNG của từng nhân viên
            // (máy_mã) thay vì dồn một đống: dễ soát/dọn theo người.
            String personDir = (deviceId + "_" + code).replaceAll("[^A-Za-z0-9_-]", "");
            selfieName = storageService.storePrivate(STORAGE_FOLDER + "/" + personDir, selfieBytes, ".jpg", ct);
            if (selfieName == null) {
                return Result.fail("Không lưu được ảnh selfie lên kho file. Thử lại hoặc báo IT.");
            }
        } catch (IOException e) {
            return Result.fail("Không đọc được ảnh selfie: " + e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Dòng log cho phần tính công — y như máy vân tay đẩy về
        AttendanceLog log = new AttendanceLog();
        log.setEmployeeCode(code);
        log.setDeviceId(deviceId);
        log.setDeviceName(device.getName());
        log.setPunchTime(now);
        log.setVerifyMode(VERIFY_MODE_GPS);
        logRepo.save(log);

        // 2. Bản ghi GPS đầy đủ cho nhân sự soát
        GpsCheckin c = new GpsCheckin();
        c.setEmployeeCode(code);
        c.setDeviceId(deviceId);
        c.setDeviceName(device.getName());
        c.setUserEmail(user.getEmail());
        c.setPunchTime(now);
        c.setLatitude(latitude);
        c.setLongitude(longitude);
        c.setAccuracyM(accuracy);
        c.setDistanceM(distance);
        c.setFreeLocation(free);
        c.setIpAddress(ip);
        c.setSelfieFile(selfieName);
        // Chấm công tác: dịch toạ độ ra địa chỉ thật để hiển thị "đang ở đâu"
        // thay vì "cách văn phòng bao xa" — dịch lỗi thì để trống, nơi hiển thị tự lo.
        String locationName = free ? reverseGeocode(latitude, longitude) : null;
        c.setLocationName(locationName);
        gpsRepo.save(c);

        // Tính lại công NGAY cho ngày hôm nay, để nhân viên mở lịch là thấy giờ vừa chấm
        // thay vì chờ đợt tổng hợp 12h trưa / 12h khuya.
        try {
            attendanceService.rebuild(now.toLocalDate(), now.toLocalDate());
        } catch (Exception e) {
            System.err.println("[GPS] Chấm xong nhưng tính lại công lỗi: " + e.getMessage());
        }

        // Công tác: nói rõ ĐANG Ở ĐÂU theo định vị, không so khoảng cách với văn phòng
        String where = free
                ? "tại " + (locationName != null ? locationName
                        : "vị trí " + String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude))
                        + " (công tác)"
                : "tại " + device.getName() + " (cách " + humanDistance(distance) + ")";
        return new Result(true, "Đã chấm công " + where + " lúc "
                + now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + ".", c);
    }

    public GpsCheckin get(Long id) {
        return id == null ? null : gpsRepo.findById(id).orElse(null);
    }

    /**
     * Mở ảnh selfie của một lần chấm từ kho file. Bản ghi cũ (trước khi chuyển sang MinIO)
     * lưu đường dẫn thiếu thư mục gốc "gps-selfies/" nên bù vào trước khi tìm.
     */
    public StoredFile openSelfie(GpsCheckin c) {
        if (c == null || c.getSelfieFile() == null || c.getSelfieFile().isBlank()) return null;
        String key = c.getSelfieFile();
        if (!key.startsWith(STORAGE_FOLDER + "/")) key = STORAGE_FOLDER + "/" + key;
        return storageService.load(key);
    }

    /** Vài lần chấm gần nhất của một người — hiện trên trang chấm công cá nhân. */
    public List<GpsCheckin> recentOf(String code, Long deviceId) {
        return gpsRepo.findTop5ByEmployeeCodeAndDeviceIdOrderByPunchTimeDesc(code, deviceId);
    }

    public List<GpsCheckin> recent() {
        return gpsRepo.findTop200ByOrderByPunchTimeDesc();
    }

    /** Các lần chấm trong MỘT NGÀY bất kỳ của một người — lịch cá nhân bấm ngày nào xem ngày đó. */
    public List<GpsCheckin> dayOf(String code, Long deviceId, java.time.LocalDate day) {
        java.time.LocalDateTime dayStart = day.atStartOfDay();
        return gpsRepo.findByEmployeeCodeAndDeviceIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                code, deviceId, dayStart, dayStart.plusDays(1));
    }

    /** Các lần chấm HÔM NAY — nhân viên tự đối chiếu hình + vị trí + giờ trên trang chấm. */
    public List<GpsCheckin> todayOf(String code, Long deviceId) {
        return dayOf(code, deviceId, java.time.LocalDate.now());
    }

    /** Toàn bộ lịch sử chấm của một người (tối đa 100 lần gần nhất) — trang soát theo người. */
    public List<GpsCheckin> historyOf(String code, Long deviceId) {
        return gpsRepo.findTop100ByEmployeeCodeAndDeviceIdOrderByPunchTimeDesc(code, deviceId);
    }

    /** Một dòng tóm tắt trên danh sách người đã chấm GPS. */
    public record PersonSummary(String employeeCode, Long deviceId, String deviceName,
                                long count, LocalDateTime lastPunch) {
        public String getEmployeeCode() { return employeeCode; }
        public Long getDeviceId() { return deviceId; }
        public String getDeviceName() { return deviceName; }
        public long getCount() { return count; }
        public String getLastPunchStr() {
            return lastPunch == null ? "" : lastPunch.format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
    }

    /** Danh sách người đã từng chấm GPS kèm số lần + lần gần nhất — không kèm ảnh nên rất nhẹ. */
    public List<PersonSummary> summary() {
        List<PersonSummary> out = new java.util.ArrayList<>();
        for (Object[] r : gpsRepo.summarizeByPerson()) {
            out.add(new PersonSummary((String) r[0], (Long) r[1], (String) r[2],
                    (Long) r[3], (LocalDateTime) r[4]));
        }
        return out;
    }
}
