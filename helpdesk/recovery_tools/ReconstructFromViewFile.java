package vn.midomax.helpdesk;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

public class ReconstructFromViewFile {
    public static void main(String[] args) throws Exception {
        String transcriptPath = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\.system_generated\\logs\\transcript.jsonl";
        String outDir = "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\scratch\econstructed";
        Files.createDirectories(Paths.get(outDir));
        
        List<String> lines = Files.readAllLines(Paths.get(transcriptPath));
        Map<String, Map<Integer, String>> fileLines = new HashMap<>();
        
        for (String line : lines) {
            // Stop parsing if we reach the point where things went wrong (e.g. 14:30Z)
            if (line.contains("\"created_at\"")) {
                Matcher m = Pattern.compile("\"created_at\":\"(.*?)\"").matcher(line);
                if (m.find()) {
                    String time = m.group(1);
                    if (time.compareTo("2026-06-28T23:59:00Z") > 0) {
                        break;
                    }
                }
            }
            
            if (line.contains("\"type\":\"VIEW_FILE\"")) {
                int contentIdx = line.indexOf("\"content\":\"");
                if (contentIdx != -1) {
                    String contentRaw = line.substring(contentIdx + 11);
                    if (contentRaw.endsWith("\"}")) {
                        contentRaw = contentRaw.substring(0, contentRaw.length() - 2);
                    }
                    String content = contentRaw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").replace("\", "").replace("\\t", "\t");
                    
                    // Look for File Path
                    Matcher pathMatcher = Pattern.compile("File Path: ile:///(.*?)").matcher(content);
                    if (pathMatcher.find()) {
                        String filePathStr = pathMatcher.group(1).replace("%20", " ");
                        // get filename only
                        String fileName = Paths.get(filePathStr).getFileName().toString();
                        
                        fileLines.putIfAbsent(fileName, new TreeMap<>());
                        
                        // Parse lines
                        String[] contentLines = content.split("\n");
                        for (String cLine : contentLines) {
                            Matcher lineMatcher = Pattern.compile("^(\\d+): (.*)$").matcher(cLine);
                            if (lineMatcher.find()) {
                                int lineNum = Integer.parseInt(lineMatcher.group(1));
                                String text = lineMatcher.group(2);
                                fileLines.get(fileName).put(lineNum, text);
                            }
                        }
                    }
                }
            }
        }
        
        for (Map.Entry<String, Map<Integer, String>> entry : fileLines.entrySet()) {
            String fileName = entry.getKey();
            Map<Integer, String> linesMap = entry.getValue();
            
            if (linesMap.isEmpty()) continue;
            
            int maxLine = Collections.max(linesMap.keySet());
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= maxLine; i++) {
                sb.append(linesMap.getOrDefault(i, "")).append("\n");
            }
            
            Files.write(Paths.get(outDir, fileName), sb.toString().getBytes());
            System.out.println("Reconstructed " + fileName + " with " + maxLine + " lines.");
        }
    }
}