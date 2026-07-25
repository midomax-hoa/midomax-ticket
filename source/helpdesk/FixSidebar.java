import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FixSidebar {
    public static void main(String[] args) {
        File dir = new File("src/main/resources/templates");
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("Templates directory not found");
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".html"));
        if (files == null) return;

        Pattern p1 = Pattern.compile("<a href=\"[^\"]*\"[^>]*title=\"Quản Lý User\"[^>]*>\\s*<div class=\"icon-wrapper\"><i class=\"fa-solid fa-users-gear\"></i></div>\\s*<span>Quản Lý User</span>\\s*</a>", Pattern.DOTALL);
        
        String replacement1 = "<a href=\"/admin/users\" title=\"Quản Lý User\" sec:authorize=\"hasRole('ADMIN')\">\n" +
                              "                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-users-gear\"></i></div>\n" +
                              "                <span>Quản Lý User</span>\n" +
                              "            </a>\n" +
                              "            <a href=\"/admin/users\" title=\"Phân quyền user\" sec:authorize=\"hasRole('ADMIN')\" style=\"padding-left: 45px; font-size: 14px; height: 40px;\">\n" +
                              "                <div class=\"icon-wrapper\" style=\"width: 25px; height: 25px; font-size: 14px;\"><i class=\"fa-solid fa-user-shield\"></i></div>\n" +
                              "                <span>Phân quyền user</span>\n" +
                              "            </a>";

        // For layout.html
        String layoutFind = "<a href=\"/admin/users\" class=\"nav-link\"><i class=\"fa-solid fa-users-gear me-2\"></i> Quản Lý User</a>";
        String layoutFind2 = "<a href=\"#\" class=\"nav-link\"><i class=\"fa-solid fa-users-gear me-2\"></i> Quản Lý User</a>";
        String layoutReplace = "<a href=\"/admin/users\" class=\"nav-link\"><i class=\"fa-solid fa-users-gear me-2\"></i> Quản Lý User</a>\n" +
                               "            <a href=\"/admin/users\" class=\"nav-link\" style=\"padding-left: 30px; font-size: 14px;\"><i class=\"fa-solid fa-user-shield me-2\"></i> Phân Quyền User</a>";

        int count = 0;
        for (File file : files) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
                String original = content;

                Matcher m = p1.matcher(content);
                content = m.replaceAll(replacement1);

                content = content.replace(layoutFind, layoutReplace);
                content = content.replace(layoutFind2, layoutReplace);

                if (!content.equals(original)) {
                    Files.write(Paths.get(file.getAbsolutePath()), content.getBytes("UTF-8"));
                    System.out.println("Updated " + file.getName());
                    count++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Total updated: " + count);
    }
}
