import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SafeSidebarRemover {
    public static void main(String[] args) throws IOException {
        Path templatesDir = Paths.get("d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates");
        
        try (Stream<Path> stream = Files.walk(templatesDir)) {
            List<Path> backupFiles = stream.filter(Files::isRegularFile)
                                         .filter(p -> p.toString().endsWith(".backup"))
                                         .collect(Collectors.toList());
                                         
            for (Path backup : backupFiles) {
                Path currentFile = Paths.get(backup.toString().replace(".backup", ""));
                if (!Files.exists(currentFile)) continue;

                String backupContent = new String(Files.readAllBytes(backup), "UTF-8");
                String currentContent = new String(Files.readAllBytes(currentFile), "UTF-8");
                
                // Extract <style>...</style> from backup
                int styleStart = backupContent.indexOf("<style>");
                int styleEnd = backupContent.indexOf("</style>");
                if (styleStart != -1 && styleEnd != -1) {
                    String backupStyle = backupContent.substring(styleStart, s
                        if (curJsSearchIndex == -1) {
                             // Wait, current file might have broken JS. 
                             // Find the script tag in current file that is roughly at the end
                             int curScriptStart = currentContent.lastIndexOf("<script>");
                             int curScriptEnd = currentContent.lastIndexOf("</script>");
                             if (curScriptStart != -1 && curScriptEnd != -1) {
                                 // To be safe, let's just replace the exact broken script if we can find it
                                 // But since the current file might have a broken script block, let's just find the last script block and replace it? No, could be tom-select.
                                 // Let's replace the script block that contains "mobileToggleBtn" or "document.querySelectorAll('.sidebar-menu a')"
                                 int brokenJsIdx = currentContent.indexOf("document.querySelectorAll('.sidebar-menu a')");
                                 if (brokenJsIdx != -1) {
                                     int bScriptStart = currentContent.lastIndexOf("<script>", brokenJsIdx);
                                     int bScriptEnd = currentContent.indexOf("</script>", brokenJsIdx);
                                     currentContent = currentContent.substring(0, bScriptStart) + cleanedScript + currentContent.substring(bScriptEnd + 9);
                                 }
                             }
                        }
                    }
                }

                Files.write(currentFile, currentContent.getBytes("UTF-8"));
                System.out.println("Safely restored styles and scripts in: " + currentFile.getFileName());
            }
        }
    }
}
