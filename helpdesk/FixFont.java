import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class FixFont {
    public static void main(String[] args) throws IOException {
        String templatesDir = "src/main/resources/templates";
        File dir = new File(templatesDir);

        Map<String, String> map = new HashMap<>();
        
        // UTF-8 Replacement character
        String R = "\uFFFD";
        
        map.put("T" + R + "m m" + R + ", n" + R + "i dung...", "Tìm mã, nội dung...");
        map.put("T" + R + "m m" + R + ", n" + R + "i dung", "Tìm mã, nội dung");
        
        map.put("Chua ph" + R + "n c" + R + "ng", "Chưa phân công");
        
        map.put("Ph" + R + "n c" + R + "ng", "Phần cứng");
        
        map.put("SLA / T" + R + "o l" + R + "c", "SLA / Tạo lúc");
        
        map.put("Ph" + R + "n m" + R + "m / T" + R + "i kho" + R + "n", "Phần mềm / Tài khoản");
        
        map.put("M" + R + "y ch" + R, "Máy chủ");
        
        map.put("M" + R + "ng", "Mạng");
        
        map.put("Kh" + R + "n C" + R + "p", "Khẩn Cấp");
        
        map.put("Kh" + R + "ng t" + R + "m th" + R + "y ticket n" + R + "o ph" + R + " h" + R + "p.", "Không tìm thấy ticket nào phù hợp.");
        
        map.put("Ho" + R + "n th" + R + "nh", "Hoàn thành");
        
        map.put("Qu" + R + " h" + R + "n", "Quá hạn");
        
        map.put("Nh" + R + "n s" + R, "Nhân sự");
        
        map.put("Ng" + R + "i x" + R + " l" + R + " (Ph" + R + "n c" + R + "ng)", "Người xử lý (Phân công)");
        
        map.put("Cao - " + R + "nh h" + R + "ng m" + R + "t ph" + R + "n c" + R + "ng vi" + R + "c", "Cao - Ảnh hưởng một phần công việc");
        
        map.put("Kh" + R + "n C" + R + "p - D" + R + "ng vi" + R + "c ho" + R + "n to" + R + "n", "Khẩn Cấp - Dừng việc hoàn toàn");
        
        map.put("D" + R + "nh k" + R + "m h" + R + "nh " + R + "nh s" + R + " c" + R + " (Ch" + R + " cho ph" + R + "p file " + R + "nh)", "Đính kèm hình ảnh sự cố (Chỉ cho phép file ảnh)");
        
        map.put("Qu" + R + "n l" + R + " Ph" + R + "n Quy" + R + "n T" + R + "i Kho" + R + "n", "Quản lý Phân Quyền Tài Khoản");
        
        map.put("Dang k" + R + "t n" + R + "i Microsoft Graph API...", "Đang kết nối Microsoft Graph API...");
        
        map.put("L" + R + "i x" + R + "c th" + R + "c: Kh" + R + "ng c" + R + " quy" + R + "n g" + R + "i API ho" + R + "c phi" + R + "n dang nh" + R + "p da h" + R + "t h" + R + "n.", "Lỗi xác thực: Không có quyền gọi API hoặc phiên đăng nhập đã hết hạn.");
        
        map.put("Danh s" + R + "ch t" + R + "i kho" + R + "n v" + R + " ph" + R + "n quy" + R + "n truy c" + R + "p h" + R + " th" + R + "ng", "Danh sách tài khoản và phân quyền truy cập hệ thống");
        
        map.put("Thao t" + R + "c", "Thao tác");
        
        map.put("Ph" + R + "n quy" + R + "n (Role)", "Phân quyền (Role)");
        
        map.put("Ph" + R + "ng ban", "Phòng ban");
        
        map.put("Qu" + R + "n l" + R + " User", "Quản lý User");
        map.put("Qu" + R + "n l" + R + " Nh" + R + "n s" + R, "Quản lý Nhân sự");
        map.put("C" + R + "ng c" + R + " d" + R + "ng c" + R, "Công cụ dụng cụ");
        
        map.put("H" + R + "m nay l" + R + " m" + R + "t ng" + R + "y tuy" + R + "t v" + R + "i d" + R + " t" + R + "i uu h" + R + "a h" + R + " th" + R + "ng. Hi" + R + "n t" + R + "i kh" + R + "ng c" + R + " s" + R + " c" + R + " m" + R + "ng n" + R + "o nghi" + R + "m tr" + R + "ng. H" + R + "y b" + R + "t d" + R + "u c" + R + "ng vi" + R + "c c" + R + "a b" + R + "n b" + R + "ng c" + R + "ch ch" + R + "n c" + R + "c ph" + R + "n h" + R + " b" + R + "n d" + R + "i.", "Hôm nay là một ngày tuyệt vời để tối ưu hóa hệ thống. Hiện tại không có sự cố mạng nào nghiêm trọng. Hãy bắt đầu công việc của bạn bằng cách chọn các phân hệ bên dưới.");
        map.put("Qu" + R + "n l" + R + " kho m" + R + "y t" + R + "nh, thi" + R + "t b" + R + " ngo" + R + "i vi v" + R + " c" + R + "p ph" + R + "t t" + R + "i s" + R + "n.", "Quản lý kho máy tính, thiết bị ngoại vi và cấp phát tài sản.");
        map.put("Th" + R + "m m" + R + "i, ph" + R + "n quy" + R + "n v" + R + " reset m" + R + "t kh" + R + "u cho ng" + R + "i d" + R + "ng.", "Thêm mới, phân quyền và reset mật khẩu cho người dùng.");

        for (File f : dir.listFiles()) {
            if (f.isFile() && f.getName().endsWith(".html")) {
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                boolean changed = false;
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    if (content.contains(entry.getKey())) {
                        content = content.replace(entry.getKey(), entry.getValue());
                        changed = true;
                    }
                }
                if (changed) {
                    Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
                    System.out.println("Fixed: " + f.getName());
                }
            }
        }
        System.out.println("Done.");
    }
}
