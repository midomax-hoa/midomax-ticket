import json
import re

transcript_path = r'C:\Users\LENOVO\.gemini\antigravity-ide\brain\38583f59-4332-4d8b-b9ea-b09ce37171c8\.system_generated\logs\transcript.jsonl'
with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            entry = json.loads(line.strip())
            if 'tool_calls' in entry:
                for call in entry['tool_calls']:
                    if call['name'] == 'replace_file_content':
                        args = call.get('args', {})
                        if 'schedule.html' in args.get('TargetFile', ''):
                            print("-----------------------------------------------------")
                            print("REPLACEMENT FOR SCHEDULE.HTML:")
                            print("ReplacementContent:")
                            print(args.get('ReplacementContent', ''))
                            print("TargetContent:")
                            print(args.get('TargetContent', ''))
                            print("-----------------------------------------------------")
        except:
            pass