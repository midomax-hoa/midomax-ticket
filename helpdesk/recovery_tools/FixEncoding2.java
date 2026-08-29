package vn.midomax.helpdesk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class FixEncoding2 {
    public static void main(String[] args) throws Exception {
        Map<String, String> replacements = new LinkedHashMap<>();
        
        String FFFD = "\uFFFD";
        
        replacements.put("Qu?n l" + FFFD + " Ticket", "Quản lý Ticket");
        replacements.put("Theo d" + FFFD + "i v" + FFFD + " ph" + FFFD + "n b? y" + FFFD + "u c?u h? tr?", "Theo dõi và phân bổ yêu cầu hỗ trợ");
        replacements.put("T?ng s? Ticket", "Tổng số Ticket");
        replacements.put("Chua ph" + FFFD + "n c" + FFFD + "ng", "Chưa phân công");
        replacements.put(FFFD + "ang x? l" + FFFD, "Đang xử lý");
        replacements.put("Qu" + FFFD + " h?n (SLA)", "Quá hạn (SLA)");
        replacements.put("Ticket " + FFFD + FFFD + " G?i", "Ticket Đã Gửi");
        replacements.put(FFFD + "ang Ch?", "Đang Chờ");
        replacements.put(FFFD + "ang X? L" + FFFD, "Đang Xử Lý");
        replacements.put(FFFD + FFFD + " Ho" + FFFD + "n Th" + FFFD + "nh", "Đã Hoàn Thành");
        replacements.put("C?a t" + FFFD + "i", "Của tôi");
        replacements.put(FFFD + FFFD + " d" + FFFD + "ng", "Đã đóng");
        replacements.put("T" + FFFD + "m m?, n?i dung...", "Tìm mã, nội dung...");
        replacements.put(">M" + FFFD + "<", ">Mã<");
        replacements.put("N?i dung y" + FFFD + "u c?u", "Nội dung yêu cầu");
        replacements.put("Danh m?c", "Danh mục");
        replacements.put("Ngu?i g?i", "Người gửi");
        replacements.put("M?c d?", "Mức độ");
        replacements.put("Tr?ng th" + FFFD + "i", "Trạng thái");
        replacements.put("Ph" + FFFD + "n c" + FFFD + "ng", "Phân công");
        replacements.put("SLA / T?o l" + FFFD + "c", "SLA / Tạo lúc");
        replacements.put("M?t m?ng to" + FFFD + "n b? t?ng 2, nghi l?i", "Mất mạng toàn bộ tầng 2, nghi lỗi");
        replacements.put("M" + FFFD + "y ch?", "Máy chủ");
        replacements.put("Nguy?n Van A", "Nguyễn Văn A");
        replacements.put("K? to" + FFFD + "n", "Kế toán");
        replacements.put("Chua c" + FFFD, "Chưa có");
        replacements.put("Th" + FFFD + "nh T" + FFFD + "n", "Thành Tín");
        replacements.put("Ch?n", "Chọn");
        replacements.put("Qu" + FFFD + " h?n", "Quá hạn");
        replacements.put("C" + FFFD + "n h?n", "Còn hạn");
        
        // Additional fixes for other files
        replacements.put("T?ng quan", "Tổng quan");
        replacements.put("Qu?n l" + FFFD + " User", "Quản lý User");
        replacements.put("Nh" + FFFD + "n s?", "Nhân sự");
        replacements.put("M" + FFFD + "ng", "Mạng");
        replacements.put("Ph" + FFFD + "n c" + FFFD + "ng", "Phần cứng");
        replacements.put("Ph" + FFFD + "n m" + FFFD + "m", "Phần mềm");
        replacements.put("L" + FFFD + "ch tr" + FFFD + "nh", "Lịch trình");
        replacements.put("Th" + FFFD + "m User M?i", "Thêm User Mới");

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
