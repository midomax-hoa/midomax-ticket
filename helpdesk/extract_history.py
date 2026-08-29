import json
import os

transcript_path = r'C:\Users\LENOVO\.gemini\antigravity-ide\brain\38583f59-4332-4d8b-b9ea-b09ce37171c8\.system_generated\logs\transcript.jsonl'
output_dir = r'C:\Users\LENOVO\.gemini\antigravity-ide\brain\38583f59-4332-4d8b-b9ea-b09ce37171c8\scratch\restore'

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

files_to_track = ['schedule.html', 'dashboard.html', 'admin-home.html', 'user-home.html', 'ticket-management.html', 'ticket-modal-fragment.html', 'HomeController.java', 'TicketController.java', 'sidebar.html']
file_contents = {}

with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            entry = json.loads(line.strip())
            created_at = entry.get('created_at', '')
            
            # Stop if we are past 20:00 UTC+7 (which is 13:00:00Z)
            if created_at > "2026-06-28T13:30:00Z":
                break
                
            # Check tool_calls for write_to_file
            if 'tool_calls' in entry:
                for call in entry['tool_calls']:
                    if call['name'] == 'write_to_file':
                        target = call['args'].get('TargetFile', '')
                        content = call['args'].get('CodeContent', '')
                        for fname in files_to_track:
                            if fname in target:
                                file_contents[fname] = content
                                
            # Check RUN_COMMAND and view_file outputs
            if entry.get('type') in ['TOOL_RESPONSE', 'RUN_COMMAND']:
                content = entry.get('content', '')
                if '<!DOCTYPE html>' in content or 'package vn.midomax.helpdesk;' in content:
                    # heuristic to find which file it is
                    for fname in files_to_track:
                        if fname in content:
                            # Too risky to guess, but if it has a filename in the path before the content it might be safe
                            pass
        except Exception as e:
            pass

for fname, content in file_contents.items():
    with open(os.path.join(output_dir, fname), 'w', encoding='utf-8') as out:
        out.write(content)
    print(f'Restored {fname}')

print('Extraction complete!')
