import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class InjectSidebar {
    public static void main(String[] args) throws IOException {
        Path backupPath = Paths.get("d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/admin-home.html.backup");
        String backupContent = new String(Files.readAllBytes(backupPath), "UTF-8");
        
        // Extract CSS
        String cssRegex = "(?s)(/\\* SIDEBAR CSS.*?\\.sidebar\\.active \\{[^}]+\\})";
        Matcher cssMatcher = Pattern.compile(cssRegex).matcher(backupContent);
        String css = "";
        if (cssMatcher.find()) {
            css = cssMatcher.group(1);
        } else {
            System.out.println("CSS NOT FOUND!");
        }

        // Extract JS
        String jsRegex = "(?s)(const toggleBtn = document\\.getElementById\\('toggle-btn'\\);.*?sidebar\\.classList\\.remove\\('active'\\);\\s*\\}\\s*\\}\\);)";
        Matcher jsMatcher = Pattern.compile(jsRegex).matcher(backupContent);
        String js = "";
        if (jsMatcher.find()) {
            js = jsMatcher.group(1);
        } else {
            System.out.println("JS NOT FOUND!");
        }

        Path sidebarPath = Paths.get("d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/fragments/sidebar.html");
        String sidebarContent = new String(Files.readAllBytes(sidebarPath), "UTF-8");

        // Prepare the new sidebar content
        // Inject <style> before <div class="sidebar">
        // Inject <script> after </div> (end of sidebar fragment)

        String newSidebarContent = sidebarContent.replace("<div th:fragment=\"sidebar(activePage)\" class=\"sidebar\" id=\"sidebar\">", 
            "<style>\n" + css + "\n</style>\n<div th:fragment=\"sidebar(activePage)\" class=\"sidebar\" id=\"sidebar\">");
        
        String jsWrapper = "\n<script>\ndocument.addEventListener('DOMContentLoaded', function() {\n" + js + "\n});\n</script>\n";
        
        // Inject JS right before the closing </div> of the fragment. Wait, where is the closing </div>?
        // It's right before </body>.
        newSidebarContent = newSidebarContent.replace("</body>", jsWrapper + "\n</body>");

        Files.write(sidebarPath, newSidebarContent.getBytes("UTF-8"));
        System.out.println("Sidebar injected successfully!");
    }
}
