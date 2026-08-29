package vn.midomax.helpdesk;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

/**
 * Comprehensive restore: reads ALL transcripts, extracts file content
 * from write_to_file and VIEW_FILE operations before 13:00 UTC (= 20:00 local),
 * and writes the final state to the source directory.
 */
public class ComprehensiveRestore {

    // Target time: 20:00 local = 13:00 UTC on June 28, 2026
    static final String TARGET_TIME = "2026-06-28T13:00:00Z";

    // Base project directory
    static final String PROJECT_BASE = "d:\\Midomax\\MIDOMAX PROJECT\\helpdesk\\helpdesk";

    // Output directory for restored files (separate dir first, then copy)
    static final String OUT_DIR = "d:\\Midomax\\MIDOMAX PROJECT\\helpdesk\\helpdesk\estored_20h";

    // Map: relative path -> file content (latest version wins)
    static Map<String, String> fileContents = new LinkedHashMap<>();
    // Map: relative path -> timestamp of last update
    static Map<String, String> fileTimestamps = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        String[] transcripts = {
            // Conversation c98c96d2 (OAuth2 setup, 14:45-14:49 local)
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\c98c96d2-b02f-44b1-8550-c7b5f19ff053\\.system_generated\\logs\\transcript.jsonl",
            // Conversation c8686d29 (fixing errors, 14:51-23:04 local)
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\c8686d29-6ac6-4cee-aac5-1657e8957596\\.system_generated\\logs\\transcript.jsonl",
            // Conversation 38583f59 (main dev, 16:03+ local)
            "C:\\Users\\LENOVO\\.gemini\\antigravity-ide\\brain\\38583f59-4332-4d8b-b9ea-b09ce37171c8\\.system_generated\\logs\\transcript.jsonl"
        };

        Files.createDirectories(Paths.get(OUT_DIR));

        for (String tPath : transcripts) {
            Path p = Paths.get(tPath);
            if (!Files.exists(p)) {
                System.out.println("SKIP (not found): " + tPath);
                continue;
            }
            System.out.println("=== Processing: " + p.getParent().getParent().getParent().getFileName() + " ===");
            processTranscript(tPath);
        }

        // Write all restored files
        System.out.println("\n=== Writing restored files ===");
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
        System.out.println("\nTotal files restored to " + OUT_DIR + ": " + count);
    }

    static void processTranscript(String path) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(path));
        System.out.println("  Total entries: " + lines.size());

        int writeCount = 0;
        int viewCount = 0;
        int replaceCount = 0;

        for (String line : lines) {
            // Extract created_at
            String createdAt = extractJsonString(line, "created_at");
            if (createdAt == null) continue;
            if (createdAt.compareTo(TARGET_TIME) > 0) continue;

            // 1. Handle write_to_file in tool_calls
            if (line.contains("\"name\":\"write_to_file\"")) {
                if (processWriteToFile(line, createdAt)) writeCount++;
            }

            // 2. Handle VIEW_FILE results (complete file content)
            if (line.contains("\"type\":\"VIEW_FILE\"")) {
                if (processViewFile(line, createdAt)) viewCount++;
            }

            // 3. Handle replace_file_content
            if (line.contains("\"type\":\"REPLACE_FILE_CONTENT\"") || line.contains("\"type\":\"MULTI_REPLACE_FILE_CONTENT\"")) {
                // These are result entries - the actual edits were already applied to files
                // We rely on subsequent VIEW_FILE to capture the result
            }

            // 4. Handle replace in tool_calls (try to apply)
            if (line.contains("\"name\":\"replace_file_content\"") || line.contains("\"name\":\"multi_replace_file_content\"")) {
                if (processReplaceFileContent(line, createdAt)) replaceCount++;
            }
        }

        System.out.println("  Processed: " + writeCount + " writes, " + viewCount + " views, " + replaceCount + " replaces");
    }

    static boolean processWriteToFile(String line, String createdAt) {
        try {
            // Extract TargetFile
            String targetFile = extractJsonString(line, "TargetFile");
            if (targetFile == null) return false;
            targetFile = unescapePath(targetFile);

            // Only process project files
            String relPath = getRelativePath(targetFile);
            if (relPath == null) return false;

            // Extract CodeContent
            String codeContent = extractCodeContent(line);
            if (codeContent == null || codeContent.isEmpty()) return false;

            String unescaped = unescape(codeContent);

            // Check if this is newer than what we have
            String existing = fileTimestamps.get(relPath);
            if (existing == null || createdAt.compareTo(existing) >= 0) {
                fileContents.put(relPath, unescaped);
                fileTimestamps.put(relPath, createdAt);
                return true;
            }
        } catch (Exception e) {
            // Skip problematic entries
        }
        return false;
    }

    static boolean processViewFile(String line, String createdAt) {
        try {
            // Extract content field
            int contentIdx = line.indexOf("\"content\":\"");
            if (contentIdx == -1) return false;

            String rawContent = extractContentField(line, contentIdx);
            if (rawContent == null) return false;

            String content = unescape(rawContent);

            // Check if it shows the "entire, complete file contents"
            boolean isComplete = content.contains("The above content shows the entire, complete file contents");

            // Extract file path
            Matcher pathMatcher = Pattern.compile("File Path: `?file:///(.*?)`?\\n").matcher(content);
            if (!pathMatcher.find()) return false;

            String filePath = pathMatcher.group(1).replace("%20", " ");
            String relPath = getRelativePath(filePath);
            if (relPath == null) return false;

            // Extract numbered lines
            List<String> cleanLines = new ArrayList<>();
            String[] contentLines = content.split("\\n");
            for (String cLine : contentLines) {
                Matcher lm = Pattern.compile("^(\\d+): (.*)$").matcher(cLine);
                if (lm.find()) {
                    cleanLines.add(lm.group(2));
                } else {
                    // Also match empty lines like "2: " (just the number and colon)
                    Matcher emptyLm = Pattern.compile("^(\\d+): ?$").matcher(cLine);
                    if (emptyLm.find()) {
                        cleanLines.add("");
                    }
                }
            }

            if (cleanLines.isEmpty()) return false;

            String fileContent = String.join("\n", cleanLines);
            // Add trailing newline if original had it
            if (content.contains("Total Lines:")) {
                fileContent += "\n";
            }

            // Only overwrite if complete file OR if we don't have this file yet
            String existing = fileTimestamps.get(relPath);
            if (isComplete && (existing == null || createdAt.compareTo(existing) >= 0)) {
                fileContents.put(relPath, fileContent);
                fileTimestamps.put(relPath, createdAt);
                return true;
            }
        } catch (Exception e) {
            // Skip
        }
        return false;
    }

    static boolean processReplaceFileContent(String line, String createdAt) {
        try {
            String targetFile = extractJsonString(line, "TargetFile");
            if (targetFile == null) return false;
            targetFile = unescapePath(targetFile);
            String relPath = getRelativePath(targetFile);
            if (relPath == null) return false;

            String currentContent = fileContents.get(relPath);
            if (currentContent == null) return false;

            // Find all TargetContent/ReplacementContent pairs
            String[] chunks = line.split("\"TargetContent\":\"");
            boolean applied = false;
            for (int i = 1; i < chunks.length; i++) {
                int endIdx = chunks[i].indexOf("\",\"ReplacementContent\":\"");
                if (endIdx == -1) continue;

                String targetStr = chunks[i].substring(0, endIdx);
                String replPart = chunks[i].substring(endIdx + 24);

                // Find end of ReplacementContent
                int replEnd = findEndOfJsonString(replPart);
                if (replEnd == -1) continue;

                String replacementStr = replPart.substring(0, replEnd);

                String unescTarget = unescape(targetStr);
                String unescRepl = unescape(replacementStr);

                if (currentContent.contains(unescTarget)) {
                    currentContent = currentContent.replace(unescTarget, unescRepl);
                    applied = true;
                }
            }

            if (applied) {
                fileContents.put(relPath, currentContent);
                fileTimestamps.put(relPath, createdAt);
                return true;
            }
        } catch (Exception e) {
            // Skip
        }
        return false;
    }

    // === UTILITY METHODS ===

    static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = findEndOfJsonString(json.substring(start));
        if (end == -1) return null;
        return json.substring(start, start + end);
    }

    static String extractCodeContent(String line) {
        String marker = "\"CodeContent\":\"";
        int idx = line.indexOf(marker);
        if (idx == -1) return null;
        int start = idx + marker.length();
        int end = findEndOfJsonString(line.substring(start));
        if (end == -1) return null;
        return line.substring(start, start + end);
    }

    static String extractContentField(String line, int contentIdx) {
        int start = contentIdx + 11; // length of "content":"
        int end = findEndOfJsonString(line.substring(start));
        if (end == -1) return null;
        return line.substring(start, start + end);
    }

    /**
     * Find the end of a JSON string value (the closing unescaped quote).
     * Handles escape sequences properly.
     */
    static int findEndOfJsonString(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2; // skip escape sequence
            } else if (c == '"') {
                return i;
            } else {
                i++;
            }
        }
        return -1;
    }

    static String getRelativePath(String fullPath) {
        fullPath = fullPath.replace("\\", "/").replace("%20", " ");
        // Look for helpdesk/helpdesk/src/ pattern
        int idx = fullPath.indexOf("helpdesk/helpdesk/src/");
        if (idx != -1) {
            return fullPath.substring(idx + 18); // after "helpdesk/helpdesk/"
        }
        // Look for helpdesk/helpdesk/pom.xml etc
        idx = fullPath.indexOf("helpdesk/helpdesk/");
        if (idx != -1) {
            String rel = fullPath.substring(idx + 18);
            if (rel.startsWith("src/") || rel.equals("pom.xml")) {
                return rel;
            }
        }
        return null;
    }

    static String unescapePath(String s) {
        return s.replace("\\\\", "\\").replace("%20", " ");
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
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
