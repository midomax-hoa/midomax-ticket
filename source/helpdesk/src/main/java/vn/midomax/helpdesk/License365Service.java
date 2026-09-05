package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Theo dõi license Microsoft 365: mỗi loại đã mua bao nhiêu, đang cấp cho ai,
 * còn dư bao nhiêu.
 *
 * Số ĐÃ CẤP luôn đọc thẳng từ Microsoft 365 (quyền User.Read.All app đã có sẵn) nên
 * không cần ai cập nhật tay và không bao giờ lệch. Số ĐÃ MUA thì Microsoft không cho
 * đọc — endpoint /subscribedSkus trả 403 vì app chưa có quyền Organization.Read.All —
 * nên admin nhập tay theo hợp đồng. Nếu sau này được cấp quyền đó thì bỏ phần nhập tay
 * đi là chuyển sang tự động hoàn toàn, phần đếm đã cấp giữ nguyên.
 */
@Service
public class License365Service {

    /** Gọi Graph khá chậm (275 user), giữ lại kết quả một lúc cho trang mở nhanh. */
    private static final long CACHE_MILLIS = 5 * 60 * 1000L;

    /**
     * Tên gợi ý cho các loại license phổ biến, vì Microsoft chỉ trả về GUID.
     * Chỉ dùng lúc TẠO dòng mới — admin đổi tên rồi thì không bao giờ ghi đè.
     * Loại nào không có trong danh sách này thì để trống cho admin tự đặt,
     * thà bỏ trống còn hơn đoán sai tên rồi kế toán tin nhầm.
     */
    private static final Map<String, String[]> KNOWN_SKUS = Map.of(
            // skuId -> { tên hiển thị, "1" nếu mất phí / "0" nếu miễn phí }
            "3b555118-da6a-4418-894f-7df1e2096870", new String[]{"Microsoft 365 Business Basic", "1"},
            "80b2d799-d2ba-4d2a-8842-fb0d0f3a4b82", new String[]{"Exchange Online (Plan 1)", "1"},
            "f8a1db68-be16-40ed-86d5-cb42ce701560", new String[]{"Power BI Pro", "1"},
            "f30db892-07e9-47e9-837c-80727f46fd3d", new String[]{"Power Automate Free", "0"},
            "a403ebcc-fae0-4ca2-8c8c-7a907fd6c235", new String[]{"Power BI (miễn phí)", "0"}
    );

    @Autowired
    private AzureUserSyncService azureUserSyncService;

    @Autowired
    private License365Repository licenseRepository;

    private volatile Map<String, List<Holder>> cachedHolders = null;
    private volatile long cachedAt = 0L;
    private volatile String lastError = null;

    /** Một người đang giữ license. */
    public static class Holder {
        private final String displayName;
        private final String email;
        private final boolean enabled;

        public Holder(String displayName, String email, boolean enabled) {
            this.displayName = displayName;
            this.email = email;
            this.enabled = enabled;
        }

        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public boolean isEnabled() { return enabled; }
    }

    /** Một dòng trên bảng theo dõi: gộp số hợp đồng (DB) với số thực tế (Microsoft). */
    public static class LicenseRow {
        private final License365 license;
        private final List<Holder> holders;

        public LicenseRow(License365 license, List<Holder> holders) {
            this.license = license;
            this.holders = holders == null ? new ArrayList<>() : holders;
        }

        public License365 getLicense() { return license; }
        public String getSkuId() { return license.getSkuId(); }
        public String getNote() { return license.getNote(); }
        public boolean isPaid() { return license.getPaid(); }
        public List<Holder> getHolders() { return holders; }

        /** Tên do admin đặt; chưa đặt thì hiện tạm GUID để còn nhận ra mà đặt tên. */
        public String getDisplayName() {
            String n = license.getDisplayName();
            return (n == null || n.isBlank()) ? license.getSkuId() : n;
        }

        public boolean isUnnamed() {
            return license.getDisplayName() == null || license.getDisplayName().isBlank();
        }

        public int getPurchased() { return license.getPurchasedQty(); }

        public int getAssigned() { return holders.size(); }

        /** Số tài khoản đang giữ license nhưng đã bị khoá — tiền trả mà không ai dùng. */
        public int getAssignedToDisabled() {
            int n = 0;
            for (Holder h : holders) if (!h.isEnabled()) n++;
            return n;
        }

        /** Âm nghĩa là đang cấp vượt số đã mua — hoặc nhập thiếu, hoặc đang dùng lố. */
        public int getRemaining() { return getPurchased() - getAssigned(); }

        /** Chưa nhập số đã mua thì không kết luận thừa/thiếu được. */
        public boolean isPurchasedUnknown() { return getPurchased() <= 0; }

        public int getUsagePercent() {
            if (getPurchased() <= 0) return 0;
            long p = Math.round(getAssigned() * 100.0 / getPurchased());
            return (int) Math.min(100, Math.max(0, p));
        }

        /** OVER = cấp vượt số mua, WARN = gần hết, OK = còn thoải mái. */
        public String getStatus() {
            if (isPurchasedUnknown()) return "UNKNOWN";
            if (getRemaining() < 0) return "OVER";
            if (getRemaining() == 0) return "FULL";
            if (getUsagePercent() >= 90) return "WARN";
            return "OK";
        }
    }

    /** Lần gọi Microsoft gần nhất có lỗi gì không (null là ổn). */
    public String getLastError() { return lastError; }

    public LocalDateTime getLastSyncedAt() {
        return cachedAt == 0 ? null
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(cachedAt), java.time.ZoneId.systemDefault());
    }

    public void clearCache() {
        cachedAt = 0;
        cachedHolders = null;
    }

    /**
     * Đọc toàn bộ user 365 kèm license đang gán, gom theo skuId.
     * Có phân trang: tenant 275 user vượt 1 trang là mất dữ liệu nếu không lần nextLink.
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<Holder>> fetchHolders() {
        Map<String, List<Holder>> result = new HashMap<>();
        String token = azureUserSyncService.getAppAccessToken();
        if (token == null) {
            lastError = "Không lấy được token Microsoft 365. Kiểm tra client-id/client-secret trong cấu hình.";
            return result;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        String url = "https://graph.microsoft.com/v1.0/users"
                + "?$select=displayName,mail,userPrincipalName,accountEnabled,assignedLicenses&$top=999";
        int guard = 0;
        try {
            while (url != null && guard++ < 20) {
                ResponseEntity<Map> response =
                        restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                Map<String, Object> body = response.getBody();
                if (body == null) break;

                List<Map<String, Object>> users = (List<Map<String, Object>>) body.get("value");
                if (users != null) {
                    for (Map<String, Object> u : users) {
                        List<Map<String, Object>> licenses =
                                (List<Map<String, Object>>) u.get("assignedLicenses");
                        if (licenses == null || licenses.isEmpty()) continue;

                        String name = str(u.get("displayName"));
                        String mail = str(u.get("mail"));
                        if (mail == null || mail.isBlank()) mail = str(u.get("userPrincipalName"));
                        Object enabledRaw = u.get("accountEnabled");
                        boolean enabled = !(enabledRaw instanceof Boolean) || (Boolean) enabledRaw;

                        for (Map<String, Object> lic : licenses) {
                            String skuId = str(lic.get("skuId"));
                            if (skuId == null) continue;
                            result.computeIfAbsent(skuId, k -> new ArrayList<>())
                                    .add(new Holder(name, mail, enabled));
                        }
                    }
                }
                url = str(body.get("@odata.nextLink"));
            }
            lastError = null;
        } catch (Exception e) {
            lastError = "Không đọc được dữ liệu license từ Microsoft 365: " + e.getMessage();
        }

        for (List<Holder> list : result.values()) {
            list.sort(Comparator.comparing(h -> h.getDisplayName() == null ? "" : h.getDisplayName(),
                    String.CASE_INSENSITIVE_ORDER));
        }
        return result;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Map<String, List<Holder>> holders(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedHolders != null && (now - cachedAt) < CACHE_MILLIS) {
            return cachedHolders;
        }
        Map<String, List<Holder>> fresh = fetchHolders();
        // Gọi lỗi thì giữ lại bản cũ còn hơn xoá trắng bảng của người đang xem
        if (fresh.isEmpty() && cachedHolders != null && lastError != null) {
            return cachedHolders;
        }
        cachedHolders = fresh;
        cachedAt = now;
        return fresh;
    }

    /**
     * Bảng theo dõi. Loại license nào Microsoft có mà DB chưa biết thì tự thêm vào
     * để admin đặt tên — khỏi phải khai báo trước, mua thêm loại mới là tự hiện ra.
     */
    public List<LicenseRow> overview(boolean paidOnly, boolean forceRefresh) {
        Map<String, List<Holder>> byS = holders(forceRefresh);

        Map<String, License365> known = new HashMap<>();
        List<License365> toName = new ArrayList<>();
        for (License365 l : licenseRepository.findAll()) {
            // Điền tên gợi ý cho dòng còn TRỐNG tên (dòng tạo trước khi có bảng gợi ý).
            // Chỉ đụng khi đang trống nên tên admin đã đặt không bao giờ bị ghi đè.
            String[] hint = KNOWN_SKUS.get(l.getSkuId());
            if (hint != null && (l.getDisplayName() == null || l.getDisplayName().isBlank())) {
                l.setDisplayName(hint[0]);
                l.setPaid("1".equals(hint[1]));
                toName.add(l);
            }
            known.put(l.getSkuId(), l);
        }
        if (!toName.isEmpty()) licenseRepository.saveAll(toName);

        List<License365> toCreate = new ArrayList<>();
        for (String skuId : byS.keySet()) {
            if (!known.containsKey(skuId)) {
                String[] hint = KNOWN_SKUS.get(skuId);
                License365 fresh = hint == null
                        ? new License365(skuId, null, true)
                        : new License365(skuId, hint[0], "1".equals(hint[1]));
                toCreate.add(fresh);
            }
        }
        if (!toCreate.isEmpty()) {
            for (License365 l : licenseRepository.saveAll(toCreate)) known.put(l.getSkuId(), l);
        }

        List<LicenseRow> rows = new ArrayList<>();
        for (License365 l : known.values()) {
            if (paidOnly && !l.getPaid()) continue;
            rows.add(new LicenseRow(l, byS.get(l.getSkuId())));
        }
        // Đang cấp nhiều nhất lên trước; cùng số thì theo tên
        rows.sort(Comparator.comparingInt(LicenseRow::getAssigned).reversed()
                .thenComparing(LicenseRow::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public LicenseRow rowOf(String skuId) {
        if (skuId == null) return null;
        License365 l = licenseRepository.findBySkuId(skuId).orElse(null);
        if (l == null) return null;
        return new LicenseRow(l, holders(false).get(skuId));
    }

    public void save(String skuId, String displayName, Integer purchasedQty, Boolean paid,
                     String note, String actor) {
        License365 l = licenseRepository.findBySkuId(skuId)
                .orElseGet(() -> new License365(skuId, null, true));
        l.setDisplayName(displayName == null || displayName.isBlank() ? null : displayName.trim());
        l.setPurchasedQty(purchasedQty == null || purchasedQty < 0 ? 0 : purchasedQty);
        l.setPaid(paid != null && paid);
        l.setNote(note == null || note.isBlank() ? null : note.trim());
        l.setUpdatedAt(LocalDateTime.now());
        l.setUpdatedBy(actor);
        licenseRepository.save(l);
    }
}
