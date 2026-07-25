import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestoreSidebar {
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
                
                // Fix CSS syntax error
                // In files, we have:
                //         
                //
                //
                //
                //            .main-content {
                
                // A safer regex replacement:
                String brokenCssPattern = "        \\s*\\.main-content \\{\\s*margin-left: 0 !important;";
                if (content.matches("(?s).*" + brokenCssPattern + ".*")) {
                    content = content.replaceFirst("(?s)        \\s*\\.main-content \\{\\s*margin-left: 0 !important;", 
                        "        @media (max-width: 991px) {\n            #mobile-toggle-btn { display: block !important; }\n            .main-content {\n                margin-left: 0 !important;");
                    modified = true;
                }
                
                // Fix JS syntax error / dangling listeners
                String jsPattern = "(?s)        document\\.addEventListener\\('click', function \\(e\\) \\{\\s*if \\(window\\.innerWidth <= 991.*sidebar\\.classList\\.remove\\('active'\\);\\s*\\}\\s*\\}\\);";
                if (content.matches("(?s).*" + jsPattern + ".*")) {
                    content = content.replaceFirst(jsPattern, "");
                    modified = true;
                }

                if (modified) {
                    Files.write(file, content.getBytes("UTF-8"));
                    System.out.println("Repaired syntax in: " + file.getFileName());
                }
            }
        }
    }
}
