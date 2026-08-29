package vn.midomax.helpdesk;

/**
 * Các phân hệ (menu lớn trên sidebar) có thể cấp cho từng phòng ban qua ma trận
 * "Phân hệ theo phòng ban" trong trang Quản lý User.
 *
 * Chỉ liệt kê những phân hệ đang bị giới hạn quyền. Trang chủ, Dashboard, Ticket,
 * Lịch Trình luôn hiện với mọi người nên không nằm ở đây.
 *
 * Quy tắc: user thấy phân hệ nếu role vốn đã cho (Admin thấy hết, IT thấy Báo cáo,
 * IT nhóm Helpdesk thấy Công cụ dụng cụ) HOẶC phòng ban của user được tick trong ma trận.
 */
public enum AppModule {

    NETWORK("Mạng", "fa-solid fa-network-wired"),
    ASSETS("Công Cụ Dụng Cụ", "fa-solid fa-boxes-stacked"),
    FINANCE("Quản Lý Tài Chính", "fa-solid fa-wallet"),
    REPORTS("Báo Cáo Công Việc", "fa-solid fa-layer-group"),
    HR("Quản Lý Nhân Sự", "fa-solid fa-users-gear");

    private final String label;
    private final String icon;

    AppModule(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public static AppModule fromString(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
