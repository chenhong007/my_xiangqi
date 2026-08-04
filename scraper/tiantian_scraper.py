import json
import logging
import time
import os
from pathlib import Path
from bs4 import BeautifulSoup

from xqipu_scraper import XqipuScraper, BASE_URL, iccs_to_moves_raw, fen_to_binit, DATA_DIR

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s',
    datefmt='%H:%M:%S',
)
logger = logging.getLogger(__name__)

def main():
    scraper = XqipuScraper()
    
    logger.info("获取天天象棋攻略列表...")
    resp = scraper._get(f'{BASE_URL}/tiantiangonglue')
    soup = BeautifulSoup(resp.text, 'lxml')
    
    # 提取所有的关卡链接
    game_list = []
    for a in soup.find_all('a'):
        href = a.get('href', '')
        if href.startswith('/qipu/'):
            uuid = href.split('/qipu/')[-1]
            title = a.get_text(strip=True)
            game_list.append({
                'uuid': uuid,
                'title': title,
                'url': f'{BASE_URL}{href}',
            })
            
    logger.info(f"共找到 {len(game_list)} 个关卡")
    
    # 支持断点续传
    temp_file = DATA_DIR / '天天象棋过关攻略_temp.json'
    final_file = DATA_DIR / '天天象棋过关攻略.json'
    
    games = []
    existing_uuids = set()
    
    if final_file.exists():
        logger.info("发现已完成的文件，直接退出...")
        return
    elif temp_file.exists():
        try:
            with open(temp_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
                games = data.get('games', [])
                existing_uuids = {g['id'] for g in games}
                logger.info(f"从临时文件恢复 {len(games)} 局")
        except Exception as e:
            logger.warning(f"读取临时文件失败: {e}")

    # 启动浏览器获取走法数据
    scraper.browser.start()
    try:
        for i, game_meta in enumerate(game_list):
            if game_meta['uuid'] in existing_uuids:
                logger.info(f'  [{i+1}/{len(game_list)}] {game_meta["title"]} 已存在，跳过')
                continue

            logger.info(f'  [{i+1}/{len(game_list)}] {game_meta["title"]}')
            
            game_data = scraper.fetch_game_data(game_meta['uuid'])
            if not game_data or not game_data['iccs_moves']:
                logger.warning(f'    跳过（无走法数据）')
                continue

            # 转换为 DhtmlXQ 兼容格式
            moves_raw = iccs_to_moves_raw(game_data['iccs_moves'])
            init_fen = game_data['init_fen']
            binit = fen_to_binit(init_fen) if init_fen else ''

            game_entry = {
                'id': game_meta['uuid'],
                'url': game_meta['url'],
                'title': game_meta['title'],
                'event': '天天象棋过关攻略',
                'class': '',
                'round': '',
                'date': '',
                'result': game_data.get('result', ''),
                'red_player': '',
                'black_player': '',
                'board_init_raw': binit,
                'moves_raw': moves_raw,
                'comments': {},
                'variations': [],
            }
            games.append(game_entry)
            
            # 每下载5个保存一次
            if len(games) % 5 == 0:
                _save_temp(games)
                
    finally:
        scraper.browser.stop()
        
    output = {
        'manual_name': '天天象棋过关攻略',
        'manual_type': '残局闯关',
        'source': f'{BASE_URL}/tiantiangonglue',
        'total_discovered': len(game_list),
        'total_parsed': len(games),
        'downloaded_at': time.strftime('%Y-%m-%d'),
        'games': games,
    }
    
    with open(final_file, 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    logger.info(f'全部抓取完成！已保存: {final_file} ({len(games)} 局)')
    
    # 成功后删除临时文件
    if temp_file.exists():
        os.remove(temp_file)

def _save_temp(games):
    filepath = DATA_DIR / '天天象棋过关攻略_temp.json'
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump({'games': games}, f, ensure_ascii=False, indent=2)

if __name__ == '__main__':
    main()
