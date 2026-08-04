import re

with open('d:/code/xiangqi/android/app/src/main/java/com/yigu/xiangqi/ui/home/HomeScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    cleaned = re.sub(r'".*?"', '', line)
    cleaned = cleaned.split('//')[0]
    
    count += cleaned.count('{')
    count -= cleaned.count('}')
    
    if count == 0 and cleaned.strip() == '}':
        print(f"Line {i+1} closes top level.")
    
    if count < 0:
        print(f"Negative count at line {i+1}")
        
print(f"Final count: {count}")
