package vn.midomax.helpdesk;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

public class FullRestorer {
    public static void main(String[] args) throws Exception {
        String[] transcripts = {
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\.system_generated\\logs\\transcript.jsonl",
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\c98c96d2-b02f-44b1-8550-c7b5f19ff053\\.system_generated\\logs\\transcript.jsonl",
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\c8686d29-6ac6-4cee-aac5-1657e8957596\\.system_generated\\logs\\transcript.jsonl"
        };
        
        Map<String, String> filesContent = new HashMap<>();
        Path baseDir = Paths.get("d:\\Midomax\\MIDOMAX PROJECT\\helpdesk\\helpdesk\\backup_temp");
        
        Files.walk(baseDir).filter(Files::isRegularFile).forEach(p -> {
            String name = p.getFileName().toString();
            if(name.endsWith(".java") || name.endsWith(".html") || name.endsWith(".properties") || name.endsWith(".xml")) {
                String relPath = baseDir.relativize(p).toString().replace("\\", "/");
                try {
                    filesContent.put(relPath, Files.readString(p));
                } catch(Exception e){}
            }
        });
        
        for (String tPath : transcripts) {
            Path p = Paths.get(tPath);
            if (!Files.exists(p)) continue;
            
            List<String> lines = Files.readAllLines(p);
            for (String line : lines) {
                if (line.contains("Expand-Archive") && tPath.contains("c8686d29")) {
                    break;
                }
                
                if (line.contains("\"type\":\"VIEW_FILE\"")) {
                    int contentIdx = line.indexOf("\"content\":\"");
                    if (contentIdx != -1) {
                        String contentRaw = line.substring(contentIdx + 11);
                        if (contentRaw.endsWith("\"}")) {
                            contentRaw = contentRaw.substring(0, contentRaw.length() - 2);
                        }
                        String content = unescape(contentRaw);
                        
                        Matcher pathMatcher = Pattern.compile("File Path: file:///(.*?)(\n|$)").matcher(content);
                        if (pathMatcher.find()) {
                            String fpath = pathMatcher.group(1).replace("%20", " ");
                            if (fpath.contains("helpdesk/helpdesk/")) {
                                String relPath = fpath.substring(fpath.indexOf("helpdesk/helpdesk/") + 18).replace("\\", "/");
                                
                                List<String> cleanLines = new ArrayList<>();
                                String[] contentLines = content.split("\n");
                                for (String cLine : contentLines) {
                                    Matcher lm = Pattern.compile("^\\d+: (.*)$").matcher(cLine);
                                    if (lm.find()) {
                                        cleanLines.add(lm.group(1));
                                    }
                                }
                                if (!cleanLines.isEmpty()) {
                                    filesContent.put(relPath, String.join("\n", cleanLines));
                                }
                            }
                        }
                    }
                }
                
                if (line.contains("\"name\":\"write_to_file\"")) {
                    Matcher m1 = Pattern.compile("\"TargetFile\":\"(.*?)\"").matcher(line);
                    Matcher m2 = Pattern.compile("\"CodeContent\":\"(.*?)\"(,|})").matcher(line);
                    if (m1.find() && m2.find()) {
                        String target = m1.group(1).replace("\\\\", "\\").replace("%20", " ");
                        if (target.contains("helpdesk\\helpdesk\\") || target.contains("helpdesk/helpdesk/")) {
                            int idx = target.indexOf("helpdesk\\helpdesk\\");
                            if (idx == -1) idx = target.indexOf("helpdesk/helpdesk/");
                            String relPath = target.substring(idx + 18).replace("\\", "/");
                            filesContent.put(relPath, unescape(m2.group(1)));
                        }
                    }
                }
                
                if (line.contains("\"name\":\"replace_file_content\"") || line.contains("\"name\":\"multi_replace_file_content\"")) {
                    Matcher m1 = Pattern.compile("\"TargetFile\":\"(.*?)\"").matcher(line);
                    if (m1.find()) {
                        String target = m1.group(1).replace("\\\\", "\\").replace("%20", " ");
                        if (target.contains("helpdesk\\helpdesk\\") || target.contains("helpdesk/helpdesk/")) {
                            int idx = target.indexOf("helpdesk\\helpdesk\\");
                            if (idx == -1) idx = target.indexOf("helpdesk/helpdesk/");
                            String relPath = target.substring(idx + 18).replace("\\", "/");
                            
                            if (filesContent.containsKey(relPath)) {
                                String content = filesContent.get(relPath);
                                String[] chunks = line.split("\"TargetContent\":\"");
                                for (int i = 1; i < chunks.length; i++) {
                                    int endIdx = chunks[i].indexOf("\",\"ReplacementContent\":\"");
                                    if (endIdx != -1) {
                                        String targetStr = chunks[i].substring(0, endIdx);
                                        String replPart = chunks[i].substring(endIdx + 24);
                                        int replEnd = replPart.indexOf("\",\"StartLine\"");
                                        if (replEnd == -1) replEnd = replPart.indexOf("\"}"); 
                                        if (replEnd != -1) {
                                            String replacementStr = replPart.substring(0, replEnd);
                                            targetStr = unescape(targetStr);
                                            replacementStr = unescape(replacementStr);
                                            content = content.replace(targetStr, replacementStr);
                                        }
                                    }
                                }
                                filesContent.put(relPath, content);
                            }
                        }
                    }
                }
            }
        }
        
        Path outDir = Paths.get("d:\\Midomax\\MIDOMAX PROJECT\\helpdesk\\helpdesk\\src");
        int restored = 0;
        for (Map.Entry<String, String> entry : filesContent.entrySet()) {
            if (entry.getKey().startsWith("src/")) {
                Path dest = Paths.get("d:\\Midomax\\MIDOMAX PROJECT\\helpdesk\\helpdesk", entry.getKey());
                Files.createDirectories(dest.getParent());
                Files.writeString(dest, entry.getValue());
                restored++;
            }
        }
        System.out.println("Restored " + restored + " files to src!");
    }
    
    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").replace("\", "").replace("\\t", "\t");
    }
}
