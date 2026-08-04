import re

with open('d:/code/xiangqi/android/app/src/main/java/com/yigu/xiangqi/ui/home/HomeScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    cleaned = re.sub(r'".*?"', '', line)
    cleaned = cleaned.split('//')[0]
    
    count += cleaned.count('{')
    count -= cleaned.count('}')
    if 290 <= i+1 <= 300:
        print(f"Line {i+1}: {count} -> {line.strip()}")
