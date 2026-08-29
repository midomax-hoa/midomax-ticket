package vn.midomax.helpdesk;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

public class ExtractHistory {
    public static void main(String[] args) throws Exception {
        String transcriptPath = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\.system_generated\\logs\\transcript.jsonl";
        String outDir = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\scratch\estore";
        Files.createDirectories(Paths.get(outDir));
        
        List<String> lines = Files.readAllLines(Paths.get(transcriptPath));
        Map<String, String> fileContents = new HashMap<>();
        
        for (String line : lines) {
            if (line.contains("\"created_at\"")) {
                Matcher m = Pattern.compile("\"created_at\":\"(.*?)\"").matcher(line);
                if (m.find()) {
                    String time = m.group(1);
                    if (time.compareTo("2026-06-28T13:00:00Z") > 0) {
                        break;
                    }
                }
            }
            
            if (line.contains("\"name\":\"write_to_file\"")) {
                Matcher targetM = Pattern.compile("\"TargetFile\":\"(.*?)\"").matcher(line);
                Matcher contentM = Pattern.compile("\"CodeContent\":\"(.*?)\",\"").matcher(line);
                if (targetM.find() && contentM.find()) {
                    String target = targetM.group(1).replace("\\\\", "\\");
                    String content = contentM.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                    
                    String[] tracking = {"schedule.html", "dashboard.html", "admin-home.html", "user-home.html", "ticket-management.html", "ticket-modal-fragment.html", "HomeController.java", "TicketController.java", "fragments\\\\sidebar.html"};
                    for (String t : tracking) {
                        if (target.contains(t)) {
                            fileContents.put(t, content);
                        }
                    }
                }
            }
        }
        
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            Files.write(Paths.get(outDir, entry.getKey().replace("\\\\", "_")), entry.getValue().getBytes());
            System.out.println("Restored: " + entry.getKey());
        }
    }
}