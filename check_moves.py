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

for name in expected_names:
    filepath = f'data/{name}.json'
    if not os.path.exists(filepath):
        continue
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
        games = data.get('games', [])
        
        for game in games:
            moves = game.get('moves_raw', '')
            if not moves:
                print(f"Empty moves in {name}: {game.get('title')}")
