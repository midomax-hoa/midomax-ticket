import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FixDanglingBrackets {
    public static void main(String[] args) throws IOException {
        Path templatesDir = Paths.get("d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates");
        
        try (Stream<Path> stream = Files.walk(templatesDir)) {
            List<Path> htmlFiles = stream.filter(Files::isRegularFile)
                                         .filter(p -> p.toString().endsWith(".html"))
                                         .collect(Collectors.toList());
                                         
            for (Path file : htmlFiles) {
                if (file.toString().contains("fragments")) continue;

                String content = new String(Files.readAllBytes(file), "UTF-8");
                boolean modified = false;
                
                // Find all instances of:
                // <script>
                //        /* Sidebar JS moved to fragment */
                //        });
                // Or simply <script> followed by some whitespace/comments and then });
                String pattern = "(?s)(<script>[\\s\\r\\n]*(?:/\\*.*?\\*/[\\s\\r\\n]*)?)\\}\\);";
                if (content.matches("(?s).*" + pattern + ".*")) {
                    content = content.replaceAll(pattern, "$1");
                    modified = true;
                }

                if (modified) {
                    Files.write(file, content.getBytes("UTF-8"));
                    System.out.println("Fixed dangling brackets in: " + file.getFileName());
                }
            }
        }
    }
}
