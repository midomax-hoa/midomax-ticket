package vn.midomax.helpdesk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class FixEncoding {
    public static void main(String[] args) throws Exception {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("Qu?n l? Ticket", "Quản lý Ticket");
        replacements.put("Qu?n l Ticket", "Quản lý Ticket");
        replacements.put("Qu?n ly Ticket", "Quản lý Ticket");
        replacements.put("Qu?n ly User", "Quản lý User");
        replacements.put("Qu?n l? User", "Quản lý User");
        replacements.put("Theo d?i v? ph?n b? y?u c?u h? tr?", "Theo dõi và phân bổ yêu cầu hỗ trợ");
        replacements.put("Theo d?i v? ph?n b?", "Theo dõi và phân bổ");
        replacements.put("Theo di v phn b? yu cu h? tr?", "Theo dõi và phân bổ yêu cầu hỗ trợ");
        replacements.put("Theo d.i v. ph.n b. y.u c.u h. tr.", "Theo dõi và phân bổ yêu cầu hỗ trợ");
        replacements.put("T?o Ticket M?i", "Tạo Ticket Mới");
        replacements.put("T?o Ticket", "Tạo Ticket");
        replacements.put("??ng X? L?", "Đang Xử Lý");
        replacements.put("?? Hon Thnh", "Đã Hoàn Thành");
        replacements.put("?? Ho?n Th?nh", "Đã Hoàn Thành");
        replacements.put("T?t c?", "Tất cả");
        replacements.put("N?I DUNG Y?U C?U", "NỘI DUNG YÊU CẦU");
        replacements.put("DANH M?C", "DANH MỤC");
        replacements.put("NGU?I G?I", "NGƯỜI GỬI");
        replacements.put("M?C D?", "MỨC ĐỘ");
        replacements.put("TR?NG TH?I", "TRẠNG THÁI");
        replacements.put("PH?N C?NG", "PHÂN CÔNG");
        replacements.put("M?y ch?", "Máy chủ");
        replacements.put("Ph?n c?ng", "Phần cứng");
        replacements.put("Ph?n m?m", "Phần mềm");
        replacements.put("C?n h?n", "Còn hạn");
        replacements.put("Qu? h?n", "Quá hạn");
        replacements.put("Trang tru?c", "Trang trước");
        replacements.put("Ti?p theo", "Tiếp theo");
        replacements.put("H? tr?", "Hỗ trợ");
        replacements.put("T?m m?, n?i dung", "Tìm mã, nội dung");
        replacements.put("L?ch tr?nh", "Lịch trình");
        replacements.put("T?ng quan", "Tổng quan");
        replacements.put("Nh?n s?", "Nhân sự");
        replacements.put("M?ng", "Mạng");
        replacements.put("Th?nh T?n", "Thành Tín");
        replacements.put("Tu?n Hung", "Tuấn Hưng");
        replacements.put("Qu?c B?o", "Quốc Bảo");

        String[] files = {
            "ticket-management.html",
            "user-management.html",
            "admin-home-test2.html",
            "user-home.html",
            "dashboard.html",
            "fragments/sidebar.html"
        };

        String dir = "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/";

        for (String fileName : files) {
            File f = new File(dir + fileName);
            if (!f.exists()) continue;
            
            String content = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())), StandardCharsets.UTF_8);
            
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                String target = entry.getKey();
                String replacement = entry.getValue();
                content = content.replace(target, replacement);
            }
            
            Files.write(Paths.get(f.getAbsolutePath()), content.getBytes(StandardCharsets.UTF_8));
            System.out.println("Processed " + fileName);
        }
    }
}
