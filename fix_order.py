import json
import os
import re

file_path = 'data/app/自出洞来无敌手.json'
with open(file_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

# Sort games based on the character and the number
char_order = ["自", "出", "洞", "来", "无", "敌", "手"]
num_order = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5}

def get_sort_key(game):
    title = game['title']
    # title looks like: “自”字 第一局
    char_match = re.search(r'“(.+)”', title)
    char = char_match.group(1) if char_match else ""
    
    num_match = re.search(r'第(.)局', title)
    num_str = num_match.group(1) if num_match else ""
    
    char_idx = char_order.index(char) if char in char_order else 99
    num_idx = num_order.get(num_str, 99)
    
    return (char_idx, num_idx)

data['games'] = sorted(data['games'], key=get_sort_key)

with open(file_path, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print("Sorted successfully!")