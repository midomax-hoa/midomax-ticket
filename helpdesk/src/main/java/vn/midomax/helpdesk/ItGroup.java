package vn.midomax.helpdesk;

/**
 * Nhóm chuyên môn của nhân viên IT.
 *
 * Vừa là nhãn phân loại nhân sự IT, vừa là DANH MỤC của ticket: tên hằng ở đây
 * chính là giá trị lưu trong Ticket.category. Nhờ vậy chọn danh mục nào thì ô
 * "Người xử lý" lọc ra đúng IT thuộc nhóm đó — hai bên không thể lệch nhau.
 *
 * Nhóm không cấp quyền truy cập (quyền do cột role của AppUser quyết định), trừ
 * việc nhóm HELPDESK được vào Công Cụ Dụng Cụ. Một user có thể thuộc nhiều nhóm.
 */
public enum ItGroup {

    SOFTWARE("Phần mềm", "Phần mềm", "fa-solid fa-code", "#8b5cf6"),
    HELPDESK("Helpdesk", "Helpdesk - Mạng", "fa-solid fa-headset", "#3b82f6"),
    REPORT("Báo cáo", "Báo cáo", "fa-solid fa-chart-column", "#f59e0b");

    private final String label;
    private final String categoryLabel;
    private final String icon;
    private final String color;

    ItGroup(String label, String categoryLabel, String icon, String color) {
        this.label = label;
        this.categoryLabel = categoryLabel;
        this.icon = icon;
        this.color = color;
    }

    /** Tên nhóm khi nói về con người (trang phân quyền user). */
    public String getLabel() {
        return label;
    }

    /** Tên nhóm khi nói về loại việc (ô Danh mục của ticket). */
    public String getCategoryLabel() {
        return categoryLabel;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    /** Trả về null thay vì ném exception khi giá trị không hợp lệ. */
    public static ItGroup fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ItGroup.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
