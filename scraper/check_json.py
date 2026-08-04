import json
from pathlib import Path

data_dir = Path(r'd:\code\xiangqi\data')
total = 0
for f in sorted(data_dir.glob('*.json')):
    d = json.loads(f.read_text(encoding='utf-8'))
    n = d['total_parsed']
    total += n
    print(f"  {d['manual_name']}: {n} 局  ({f.stat().st_size//1024}KB)")
print(f"合计: {total} 局，{len(list(data_dir.glob('*.json')))} 个古谱")
