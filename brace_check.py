import re

with open('d:/code/xiangqi/android/app/src/main/java/com/yigu/xiangqi/ui/home/HomeScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    # Remove strings
    cleaned = re.sub(r'".*?"', '', line)
    # Remove comments
    cleaned = cleaned.split('//')[0]
    
    count += cleaned.count('{')
    count -= cleaned.count('}')
    
    # Print lines that look like a top-level close brace or when indent seems wrong
    if cleaned.strip() == '}':
        print(f"Line {i+1}: brace depth = {count}")
    if cleaned.strip() != '' and cleaned.strip()[0] == '}' and len(cleaned.strip()) > 1:
        print(f"Line {i+1}: brace depth = {count}")

print(f"Final count: {count}")
