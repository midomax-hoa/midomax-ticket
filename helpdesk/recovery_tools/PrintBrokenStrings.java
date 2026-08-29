package vn.midomax.helpdesk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class PrintBrokenStrings {
    public static void main(String[] args) throws Exception {
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
            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("\uFFFD") || lines[i].contains("?")) {
                    System.out.println(fileName + ":" + (i+1) + " -> " + lines[i].trim());
                }
            }
        }
    }
}
