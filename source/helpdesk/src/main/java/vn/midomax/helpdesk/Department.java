package vn.midomax.helpdesk;

/**
 * 11 phòng ban của công ty (theo cây thư mục SharePoint chính thức).
 * Mã ngắn (name()) lưu trong cột app_users.department; label là tên đầy đủ.
 */
public enum Department {

    B2B("Kinh doanh B2B"),
    BLE("Bán lẻ & Ecom"),
    CU("Cung ứng"),
    ITD("Công nghệ thông tin"),
    KDQT("Kinh doanh quốc tế"),
    MKT("Marketing"),
    NSHC("Nhân sự Hành chính"),
    NH("Ngành hàng"),
    QA("Đảm bảo chất lượng"),
    TCKT("Tài chính Kế toán"),
    TKSP("Thiết kế & Phát triển sản phẩm");

    private final String label;

    Department(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Mã + tên đầy đủ, dùng cho dropdown: "ITD (Công nghệ thông tin)". */
    public String getDisplay() {
        return name() + " (" + label + ")";
    }

    /** Trả về null nếu chuỗi không khớp mã phòng ban nào (dữ liệu cũ / bỏ trống). */
    public static Department fromString(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Đoán phòng ban từ chuỗi department của Microsoft 365 (nhập tay nên đủ kiểu:
     * "IT", "Công nghệ thông tin", "Marketing"...). Khớp mã hoặc tên, không phân biệt hoa thường.
     */
    public static Department guess(String m365Department) {
        if (m365Department == null || m365Department.isBlank()) return null;
        String s = m365Department.trim().toLowerCase();
        for (Department d : values()) {
            if (s.equals(d.name().toLowerCase()) || s.equals(d.label.toLowerCase())
                    || s.contains(d.label.toLowerCase()) || d.label.toLowerCase().contains(s)) {
                return d;
            }
        }
        // Vài tên hay gặp không trùng label
        if (s.contains("it") || s.contains("công nghệ")) return ITD;
        if (s.contains("nhân sự") || s.contains("hành chính") || s.contains("hr")) return NSHC;
        if (s.contains("kế toán") || s.contains("tài chính")) return TCKT;
        return null;
    }
}
