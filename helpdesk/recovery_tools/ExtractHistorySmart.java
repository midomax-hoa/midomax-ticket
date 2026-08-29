package vn.midomax.helpdesk;

import java.nio.file.*;
import java.util.*;
import java.io.*;

public class ExtractHistorySmart {
    public static void main(String[] args) throws Exception {
        String transcriptPath = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\.system_generated\\logs\\transcript.jsonl";
        String outDir = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\scratch\estore2";
        Files.createDirectories(Paths.get(outDir));
        
        List<String> lines = Files.readAllLines(Paths.get(transcriptPath));
        Map<String, String> fileContents = new HashMap<>();
        
        for (String line : lines) {
            // Check time
            if (line.contains("\"created_at\"")) {
                int idx = line.indexOf("\"created_at\":\"");
                if (idx != -1) {
                    String time = line.substring(idx + 14, idx + 34);
                    // 13:00:00Z is 20:00 UTC+7. The user wants it around 20:00.
                    // The mobile issue started around 13:30Z to 14:00Z.
                    if (time.compareTo("2026-06-28T13:30:00Z") > 0) {
                        break;
                    }
                }
            }
            
            // We look for TOOL_RESPONSE from view_file
            if (line.contains("\"type\":\"TOOL_RESPONSE\"") || line.contains("\"type\":\"RUN_COMMAND\"")) {
                int contentIdx = line.indexOf("\"content\":\"");
                if (contentIdx != -1) {
                    String contentRaw = line.substring(contentIdx + 11);
                    // extract until the last quote (crude but works for jsonl)
                    if (contentRaw.endsWith("\"}")) {
                        contentRaw = contentRaw.substring(0, contentRaw.length() - 2);
                    }
                    
                    String content = contentRaw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").replace("\", "").replace("\\t", "\t");
                    
                    if (content.contains("<!DOCTYPE html>")) {
                        if (content.contains("calendarEl") && content.contains("itSelector")) {
                            fileContents.put("schedule.html", content);
                        } else if (content.contains("pieChartCompletion") || content.contains("donutChart")) {
                            fileContents.put("dashboard.html", content);
                        } else if (content.contains("admin-home") || content.contains("openCount")) {
                            fileContents.put("admin-home.html", content);
                        } else if (content.contains("hero") && !content.contains("admin-home")) {
                            fileContents.put("user-home.html", content);
                        } else if (content.contains("ticketTable") || content.contains("Thêm Ticket Mới")) {
                            fileContents.put("ticket-management.html", content);
                        }
                    }
                    if (content.contains("package vn.midomax.helpdesk;")) {
                        if (content.contains("public class HomeController")) {
                            fileContents.put("HomeController.java", content);
                        } else if (content.contains("public class TicketController")) {
                            fileContents.put("TicketController.java", content);
                        } else if (content.contains("public class AdminUserController")) {
                            fileContents.put("AdminUserController.java", content);
                        }
                    }
                }
            }
        }
        
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            // Clean up the string to remove any JSON wrapper prefix if it exists
            String val = entry.getValue();
            if (val.contains("<!DOCTYPE html>")) {
                val = val.substring(val.indexOf("<!DOCTYPE html>"));
            } else if (val.contains("package vn.midomax.helpdesk;")) {
                val = val.substring(val.indexOf("package vn.midomax.helpdesk;"));
            }
            Files.write(Paths.get(outDir, entry.getKey()), val.getBytes());
            System.out.println("Restored: " + entry.getKey());
        }
    }
}