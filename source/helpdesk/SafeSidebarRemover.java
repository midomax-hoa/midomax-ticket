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
                    String backupStyle = backupContent.substring(styleStart, styleEnd + 8);
                    
                    // Carefully remove sidebar CSS from backupStyle
                    // Sidebar CSS usually starts with "/* SIDEBAR CSS" and ends around "display: none;\n        }" for .sidebar.collapsed .sidebar-logout
                    int sidebarStart = backupStyle.indexOf("/* SIDEBAR CSS");
                    if (sidebarStart != -1) {
                        int sidebarEnd = backupStyle.indexOf(".sidebar.collapsed .sidebar-logout");
                        if (sidebarEnd != -1) {
                            int actualEnd = backupStyle.indexOf("}", sidebarEnd) + 1;
                            
                            // Also need to remove the @media parts for sidebar if any
                            // But let's just remove the main sidebar block to avoid layout shifts.
                            String part1 = backupStyle.substring(0, sidebarStart);
                            String part2 = backupStyle.substring(actualEnd);
                            backupStyle = part1 + "\n        /* Sidebar CSS moved to fragment */\n" + part2;
                        }
                    }
                    
                    // Replace current file's <style> with backupStyle
                    int curStyleStart = currentContent.indexOf("<style>");
                    int curStyleEnd = currentContent.indexOf("</style>");
                    if (curStyleStart != -1 && curStyleEnd != -1) {
                        currentContent = currentContent.substring(0, curStyleStart) + backupStyle + currentContent.substring(curStyleEnd + 8);
                    }
                }

                // Extract JS from backup
                // Specifically the block containing "toggleBtn"
                int jsSearchIndex = backupContent.indexOf("const toggleBtn = document.getElementById('toggle-btn');");
                if (jsSearchIndex != -1) {
                    // Find the <script> tags surrounding this
                    int scriptStart = backupContent.lastIndexOf("<script>", jsSearchIndex);
                    int scriptEnd = backupContent.indexOf("</script>", jsSearchIndex);
                    if (scriptStart != -1 && scriptEnd != -1) {
                        String backupScript = backupContent.substring(scriptStart, scriptEnd + 9);
                        
                        // Remove the sidebar JS from it
                        String cleanedScript = backupScript.replaceAll("(?s)const toggleBtn = document\\.getElementById\\('toggle-btn'\\);.*?sidebar\\.classList\\.remove\\('active'\\);\\s*\\}\\s*\\}\\);", "/* Sidebar JS moved to fragment */");
                        
                        // Replace in current file
                        int curJsSearchIndex = currentContent.indexOf("/* Sidebar JS moved to fragment */");
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
