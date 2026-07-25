import json
import os
import re

transcripts = [
    r"C:\Users\LENOVO\.gemini\antigravity-ide\brain\38583f59-4332-4d8b-b9ea-b09ce37171c8\.system_generated\logs\transcript.jsonl",
    r"C:\Users\LENOVO\.gemini\antigravity-ide\brain\c98c96d2-b02f-44b1-8550-c7b5f19ff053\.system_generated\logs\transcript.jsonl",
    r"C:\Users\LENOVO\.gemini\antigravity-ide\brain\c8686d29-6ac6-4cee-aac5-1657e8957596\.system_generated\logs\transcript.jsonl"
]

files_content = {}
# First, populate from backup_temp as the baseline
base_dir = r"d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\backup_temp"
for root, dirs, files in os.walk(base_dir):
    for f in files:
        if f.endswith('.java') or f.endswith('.html') or f.endswith('.properties'):
            path = os.path.join(root, f)
            rel_path = os.path.relpath(path, base_dir).replace('\\', '/')
            try:
                with open(path, 'r', encoding='utf-8') as f_in:
                    files_content[rel_path] = f_in.read()
            except Exception:
                pass

def apply_replace(content, start_line, end_line, target, replacement):
    # This is a naive replace. 
    # For accuracy, find target in content.
    if target in content:
        return content.replace(target, replacement)
    return content

for t_path in transcripts:
    if not os.path.exists(t_path):
        continue
    with open(t_path, 'r', encoding='utf-8') as f:
        for line in f:
            if 'Expand-Archive' in line and 'c8686d29' in t_path:
                break # Stop processing once the destructive command was run
            
            try:
                data = json.loads(line)
            except:
                continue
                
            if data.get('type') == 'VIEW_FILE':
                # Sometimes viewing a file gets its latest state
                content_raw = data.get('content', '')
                if 'File Path: ' in content_raw:
                    match = re.search(r'File Path: file:///(.*?)\n.*?\nShowing lines.*?\n.*?\n(.*)The above content', content_raw, re.DOTALL)
                    if match:
                        fpath = match.group(1).replace('%20', ' ')
                        content = match.group(2)
                        # Remove line numbers
                        clean_lines = []
                        for c_line in content.split('\n'):
                            m = re.match(r'^\d+: (.*)$', c_line)
                            if m:
                                clean_lines.append(m.group(1))
                        
                        rel_path = fpath.split('helpdesk/helpdesk/')[-1].replace('\\', '/')
                        if len(clean_lines) > 0:
                            files_content[rel_path] = '\n'.join(clean_lines)
            
            if 'tool_calls' in data:
                for tc in data['tool_calls']:
                    name = tc.get('name')
                    args = tc.get('args', {})
                    
                    if name == 'write_to_file':
                        target = args.get('TargetFile', '')
                        if 'helpdesk' in target:
                            rel_path = target.replace('\\', '/').split('helpdesk/helpdesk/')[-1]
                            files_content[rel_path] = args.get('CodeContent', '')
                            
                    elif name == 'replace_file_content':
                        target = args.get('TargetFile', '')
                        if 'helpdesk' in target:
                            rel_path = target.replace('\\', '/').split('helpdesk/helpdesk/')[-1]
                            if rel_path in files_content:
                                files_content[rel_path] = apply_replace(
                                    files_content[rel_path],
                                    args.get('StartLine'),
                                    args.get('EndLine'),
                                    args.get('TargetContent', ''),
                                    args.get('ReplacementContent', '')
                                )
                                
                    elif name == 'multi_replace_file_content':
                        target = args.get('TargetFile', '')
                        if 'helpdesk' in target:
                            rel_path = target.replace('\\', '/').split('helpdesk/helpdesk/')[-1]
                            if rel_path in files_content:
                                chunks = args.get('ReplacementChunks', [])
                                for chunk in chunks:
                                    files_content[rel_path] = apply_replace(
                                        files_content[rel_path],
                                        chunk.get('StartLine'),
                                        chunk.get('EndLine'),
                                        chunk.get('TargetContent', ''),
                                        chunk.get('ReplacementContent', '')
                                    )

# Write out the restored files
out_dir = r"d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\restored_latest"
os.makedirs(out_dir, exist_ok=True)
for rel_path, content in files_content.items():
    if not rel_path: continue
    full_path = os.path.join(out_dir, rel_path.replace('/', '\\'))
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, 'w', encoding='utf-8') as f:
        f.write(content)

print(f"Restored {len(files_content)} files to {out_dir}")
