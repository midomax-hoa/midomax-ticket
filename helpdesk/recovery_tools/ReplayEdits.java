package vn.midomax.helpdesk;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

public class ReplayEdits {
    public static void main(String[] args) throws Exception {
        String transcriptPath = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\.system_generated\\logs\\transcript.jsonl";
        String outDir = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\scratch\eplayed";
        Files.createDirectories(Paths.get(outDir));
        
        // Copy .backup files to outDir
        String templateDir = "d:\\Midomax\\MIDOMAX PROJECT\\helpdesk\\helpdesk\\src\\main\esources\\templates";
        Files.list(Paths.get(templateDir)).filter(p -> p.toString().endsWith(".backup")).forEach(p -> {
            try {
                String name = p.getFileName().toString().replace(".backup", "");
                Files.copy(p, Paths.get(outDir, name), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Copied " + name);
            } catch(Exception e){}
        });
        
        List<String> lines = Files.readAllLines(Paths.get(transcriptPath));
        
        for (String line : lines) {
            // Stop parsing at 13:46Z
            if (line.contains("\"created_at\"")) {
                Matcher m = Pattern.compile("\"created_at\":\"(.*?)\"").matcher(line);
                if (m.find()) {
                    String time = m.group(1);
                    if (time.compareTo("2026-06-28T13:46:00Z") > 0) {
                        break;
                    }
                }
            }
            
            if (line.contains("\"name\":\"replace_file_content\"") || line.contains("\"name\":\"multi_replace_file_content\"")) {
                Matcher pathMatcher = Pattern.compile("\"TargetFile\":\"(.*?)\"").matcher(line);
                if (pathMatcher.find()) {
                    String fullPath = pathMatcher.group(1).replace("\\\\", "\\").replace("%20", " ");
                    Path p = Paths.get(fullPath);
                    if (p.getFileName() == null) continue;
                    String fileName = p.getFileName().toString();
                    
                    Path targetFile = Paths.get(outDir, fileName);
                    
                    if (!Files.exists(targetFile)) {
                        try {
                            Files.copy(Paths.get(fullPath), targetFile);
                        } catch(Exception e) {}
                    }
                    
                    if (Files.exists(targetFile)) {
                        String content = Files.readString(targetFile);
                        
                        String[] chunks = line.split("\"TargetContent\":\"");
                        for (int i = 1; i < chunks.length; i++) {
                            int endIdx = chunks[i].indexOf("\",\"ReplacementContent\":\"");
                            if (endIdx != -1) {
                                String target = chunks[i].substring(0, endIdx);
                                String replPart = chunks[i].substring(endIdx + 24);
                                int replEnd = replPart.indexOf("\",\"StartLine\"");
                                if (replEnd == -1) replEnd = replPart.indexOf("\"}"); 
                                if (replEnd != -1) {
                                    String replacement = replPart.substring(0, replEnd);
                                    
                                    target = unescape(target);
                                    replacement = unescape(replacement);
                                    
                                    if (content.contains(target)) {
                                        content = content.replace(target, replacement);
                                        System.out.println("Applied diff to " + fileName);
                                    } else {
                                        System.out.println("Warning: Target not found in " + fileName);
                                    }
                                }
                            }
                        }
                        Files.writeString(targetFile, content);
                    }
                }
            }
        }
    }
    
    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").replace("\", "").replace("\\t", "\t");
    }
}