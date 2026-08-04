import json
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open('gupu.md', 'r', encoding='utf-8') as f:
    lines = f.read().splitlines()

expected_names = []
for line in lines:
    line = line.strip()
    if line and not line.startswith('http') and not line.startswith('下载'):
        expected_names.append(line)

incomplete_files = []
error_files = []
total_games_all = 0

for name in expected_names:
    filepath = f'data/{name}.json'
    if not os.path.exists(filepath):
        error_files.append(f'{name} (File missing)')
        continue
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
            
            discovered = data.get('total_discovered', 0)
            parsed = data.get('total_parsed', 0)
            games = data.get('games', [])
            
            total_games_all += len(games)
            
            if discovered != parsed or len(games) != discovered or len(games) == 0:
                incomplete_files.append({
                    'name': name,
                    'discovered': discovered,
                    'parsed': parsed,
                    'actual_games': len(games)
                })
    except Exception as e:
        error_files.append(f'{name} (Error: {str(e)})')

print(f'Total manuals checked: {len(expected_names)}')
print(f'Total games across all manuals: {total_games_all}')

if error_files:
    print('\nErrors found:')
    for e in error_files:
        print(' -', e)
else:
    print('\nNo file loading errors.')

if incomplete_files:
    print('\nIncomplete manuals found:')
    for inc in incomplete_files:
        print(f" - {inc['name']}: Discovered {inc['discovered']}, Parsed {inc['parsed']}, Actual Games {inc['actual_games']}")
else:
    print('\nAll manuals are complete (discovered == parsed == actual_games > 0).')
