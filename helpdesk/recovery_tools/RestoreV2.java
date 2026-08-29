package vn.midomax.helpdesk;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

/**
 * V2 Restore: Extract file content from CODE_ACTION (write_to_file results) 
 * and VIEW_FILE entries in transcripts. CODE_ACTION contains "Created file" 
 * messages which confirm successful file writes.
 */
public class RestoreV2 {
    static final String TARGET_TIME = "2026-06-28T13:00:00Z"; // 20:00 local
    static final String PROJECT_BASE = "d:\\Midomax\\MIDOMAX PROJECT\\helpdesk\\helpdesk";
    static final String OUT_DIR = PROJECT_BASE + "\estored_20h";

    static Map<String, String> fileContents = new LinkedHashMap<>();
    static Map<String, String> fileTimestamps = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        String[] transcripts = {
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\c98c96d2-b02f-44b1-8550-c7b5f19ff053\\.system_generated\\logs\\transcript.jsonl",
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\c8686d29-6ac6-4cee-aac5-1657e8957596\\.system_generated\\logs\\transcript.jsonl",
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\.system_generated\\logs\\transcript.jsonl"
        };

        Files.createDirectories(Paths.get(OUT_DIR));

        for (String tPath : transcripts) {
            Path p = Paths.get(tPath);
            if (!Files.exists(p)) continue;
            System.out.println("=== Processing: " + p.getParent().getParent().getParent().getFileName() + " ===");
            processTranscript(tPath);
        }

        // Write files
        System.out.println("\n=== Restored Files ===");
        int count = 0;
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String relPath = entry.getKey();
            String content = entry.getValue();
            if (content == null || content.isEmpty()) continue;
            
            Path dest = Paths.get(OUT_DIR, relPath);
            Files.createDirectories(dest.getParent());
            Files.writeString(dest, content);
            System.out.println("  " + relPath + " (" + content.length() + " bytes, ts=" + fileTimestamps.get(relPath) + ")");
            count++;
        }
        System.out.println("\nTotal: " + count + " files -> " + OUT_DIR);
    }

    static void processTranscript(String path) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(path));
        System.out.println("  Total entries: " + lines.size());

        int writes = 0, views = 0, codeActions = 0;

        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            String line = lines.get(lineIdx);
            String createdAt = extractJsonField(line, "created_at");
            if (createdAt == null) continue;
            if (createdAt.compareTo(TARGET_TIME) > 0) continue;

            String type = extractJsonField(line, "type");

            // 1. CODE_ACTION = result of write_to_file, contains full file content
            if ("CODE_ACTION".equals(type)) {
                if (processCodeAction(line, createdAt)) codeActions++;
            }

            // 2. VIEW_FILE = viewing file content
            if ("VIEW_FILE".equals(type)) {
                if (processViewFile(line, createdAt)) views++;
            }

            // 3. REPLACE_FILE_CONTENT/MULTI_REPLACE = edit results
            if ("REPLACE_FILE_CONTENT".equals(type) || "MULTI_REPLACE_FILE_CONTENT".equals(type)) {
                processReplaceResult(line, createdAt);
            }

            // 4. PLANNER_RESPONSE with replace tool_calls
            if (line.contains("\"name\":\"replace_file_content\"") || line.contains("\"name\":\"multi_replace_file_content\"")) {
                processReplaceToolCall(line, createdAt);
            }
        }

        System.out.println("  Found: " + codeActions + " code_actions, " + views + " views");
    }

    static boolean processCodeAction(String line, String createdAt) {
        try {
            // CODE_ACTION content contains the file path and sometimes truncated content
            // Example: "Created file file:///path/to/file with requested content."
            // The actual CodeContent was in the preceding PLANNER_RESPONSE tool_calls
            // But CODE_ACTION confirms the write succeeded.
            
            // We need to look at the content of CODE_ACTION which sometimes includes
            // the file content after "Created file..." 
            String content = extractContentField(line);
            if (content == null) return false;
            content = unescape(content);

            // Find file path
            Matcher pm = Pattern.compile("Created file file:///(.*?) with").matcher(content);
            if (!pm.find()) return false;
            String filePath = pm.group(1).replace("%20", " ");
            String relPath = getRelativePath(filePath);
            if (relPath == null) return false;

            // CODE_ACTION doesn't contain the file content itself,
            // just the confirmation. We need to get content from the
            // preceding PLANNER_RESPONSE tool_calls.
            // However, the CodeContent in tool_calls may be truncated.
            // We mark this file as "written" so we know it exists.
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean processViewFile(String line, String createdAt) {
        try {
            String content = extractContentField(line);
            if (content == null) return false;
            content = unescape(content);

            boolean isComplete = content.contains("The above content shows the entire, complete file contents");

            Matcher pathMatcher = Pattern.compile("File Path: `?file:///(.*?)`?\\n").matcher(content);
            if (!pathMatcher.find()) return false;

            String filePath = pathMatcher.group(1).replace("%20", " ");
            String relPath = getRelativePath(filePath);
            if (relPath == null) return false;

            // Extract numbered lines
            List<String> cleanLines = new ArrayList<>();
            for (String cLine : content.split("\\n")) {
                Matcher lm = Pattern.compile("^(\\d+): (.*)$").matcher(cLine);
                if (lm.find()) {
                    int lineNum = Integer.parseInt(lm.group(1));
                    // Extend list if needed
                    while (cleanLines.size() < lineNum) cleanLines.add("");
                    cleanLines.set(lineNum - 1, lm.group(2));
                } else {
                    Matcher emptyLm = Pattern.compile("^(\\d+): ?$").matcher(cLine);
                    if (emptyLm.find()) {
                        int lineNum = Integer.parseInt(emptyLm.group(1));
                        while (cleanLines.size() < lineNum) cleanLines.add("");
                        cleanLines.set(lineNum - 1, "");
                    }
                }
            }

            if (cleanLines.isEmpty()) return false;
            String fileContent = String.join("\n", cleanLines) + "\n";

            String existing = fileTimestamps.get(relPath);
            if (isComplete && (existing == null || createdAt.compareTo(existing) >= 0)) {
                fileContents.put(relPath, fileContent);
                fileTimestamps.put(relPath, createdAt);
                return true;
            } else if (!isComplete && !fileContents.containsKey(relPath)) {
                // Partial view, only use if we have nothing
                fileContents.put(relPath, fileContent);
                fileTimestamps.put(relPath, createdAt);
                return true;
            }
        } catch (Exception e) {}
        return false;
    }

    static void processReplaceResult(String line, String createdAt) {
        try {
            String content = extractContentField(line);
            if (content == null) return;
            content = unescape(content);

            // Extract file path from replace result
            Matcher pm = Pattern.compile("replace_file_content tool to: (.*?)\\. If").matcher(content);
            if (!pm.find()) {
                pm = Pattern.compile("replace_file_content tool to: (.*?)\\.").matcher(content);
                if (!pm.find()) return;
            }
            String filePath = pm.group(1).replace("%20", " ").replace("\\\\", "\\");
            String relPath = getRelativePath(filePath);
            if (relPath == null) return;

            // Mark that this file was modified at this time
            // The actual content change was already applied on disk at this point
            // We rely on a subsequent VIEW_FILE to capture the new state
        } catch (Exception e) {}
    }

    static void processReplaceToolCall(String line, String createdAt) {
        try {
            String targetFile = extractJsonField(line, "TargetFile");
            if (targetFile == null) return;
            targetFile = targetFile.replace("\\\\", "\\").replace("%20", " ");
            String relPath = getRelativePath(targetFile);
            if (relPath == null) return;

            String currentContent = fileContents.get(relPath);
            if (currentContent == null) return;

            String[] chunks = line.split("\"TargetContent\":\"");
            boolean modified = false;
            for (int i = 1; i < chunks.length; i++) {
                int endIdx = chunks[i].indexOf("\",\"ReplacementContent\":\"");
                if (endIdx == -1) continue;

                String targetStr = chunks[i].substring(0, endIdx);
                String replPart = chunks[i].substring(endIdx + 24);
                int replEnd = findEndOfJsonString(replPart);
                if (replEnd == -1) continue;

                String replacementStr = replPart.substring(0, replEnd);
                String unescTarget = unescape(targetStr);
                String unescRepl = unescape(replacementStr);

                if (currentContent.contains(unescTarget)) {
                    currentContent = currentContent.replace(unescTarget, unescRepl);
                    modified = true;
                }
            }

            if (modified) {
                fileContents.put(relPath, currentContent);
                fileTimestamps.put(relPath, createdAt);
            }
        } catch (Exception e) {}
    }

    // === Utility ===

    static String extractJsonField(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = findEndOfJsonString(json.substring(start));
        if (end == -1) return null;
        return json.substring(start, start + end);
    }

    static String extractContentField(String line) {
        String marker = "\"content\":\"";
        int idx = line.indexOf(marker);
        if (idx == -1) return null;
        int start = idx + marker.length();
        int end = findEndOfJsonString(line.substring(start));
        if (end == -1) return null;
        return line.substring(start, start + end);
    }

    static int findEndOfJsonString(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') { i += 2; }
            else if (c == '"') { return i; }
            else { i++; }
        }
        return -1;
    }

    static String getRelativePath(String fullPath) {
        fullPath = fullPath.replace("\\", "/").replace("%20", " ");
        int idx = fullPath.indexOf("helpdesk/helpdesk/src/");
        if (idx != -1) return fullPath.substring(idx + 18);
        idx = fullPath.indexOf("helpdesk/helpdesk/pom.xml");
        if (idx != -1) return "pom.xml";
        return null;
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append(''); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                int cp = Integer.parseInt(s.substring(i+2, i+6), 16);
                                sb.append((char)cp);
                                i += 6;
                                continue;
                            } catch(Exception e) { sb.append('\\'); sb.append('u'); }
                        }
                        break;
                    default: sb.append('\\'); sb.append(next); break;
                }
                i += 2;
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
