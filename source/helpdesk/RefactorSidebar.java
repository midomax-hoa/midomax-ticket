import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class RefactorSidebar {
    public static void main(String[] args) throws IOException {
        Path templatesDir = Paths.get("d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates");
        
        try (Stream<Path> stream = Files.walk(templatesDir)) {
            List<Path> htmlFiles = stream.filter(Files::isRegularFile)
                                         .filter(p -> p.toString().endsWith(".html"))
                                         .collect(Collectors.toList());
                                         
            for (Path file : htmlFiles) {
                // Skip the fragment itself
                if (file.toString().contains("fragments\\sidebar.html") || file.toString().contains("fragments/sidebar.html")) {
                    continue;
                }
                
                String content = new String(Files.readAllBytes(file), "UTF-8");
                boolean modified = false;
                
                // Remove CSS
                String cssRegex = "(?s)/\\* SIDEBAR CSS.*?\\.sidebar\\.active \\{[^}]+\\}";
                Matcher cssMatcher = Pattern.compile(cssRegex).matcher(content);
                if (cssMatcher.find()) {
                    content = cssMatcher.replaceAll("");
                    modified = true;
                }
                // Some files might not have the exact comment, try a broader regex for .sidebar
                String fallbackCssRegex = "(?s)\\.sidebar \\{.*?\\.sidebar\\.active \\{[^}]+\\}";
                Matcher fallbackCssMatcher = Pattern.compile(fallbackCssRegex).matcher(content);
                if (fallbackCssMatcher.find()) {
                    content = fallbackCssMatcher.replaceAll("");
                    modified = true;
                }

                // Remove JS
                String jsRegex = "(?s)const toggleBtn = document\\.getElementById\\('toggle-btn'\\);.*?sidebar\\.classList\\.remove\\('active'\\);\\s*\\}\\s*\\}\\);";
                Matcher jsMatcher = Pattern.compile(jsRegex).matcher(content);
                if (jsMatcher.find()) {
                    content = jsMatcher.replaceAll("");
                    modified = true;
                }
                
                if (modified) {
                    Files.write(file, content.getBytes("UTF-8"));
                    System.out.println("Cleaned up: " + file.getFileName());
                }
            }
        }
    }
}
