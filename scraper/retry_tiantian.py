import json
import logging
import time
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
    final_file = DATA_DIR / '天天象棋过关攻略.json'
    if not final_file.exists():
        logger.error(f"未找到 {final_file}，无法进行重试补充。")
        return
        
    with open(final_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    existing_games = data.get('games', [])
    existing_uuids = {g['id'] for g in existing_games}
    
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
            
    logger.info(f"共找到 {len(game_list)} 个关卡，本地已有 {len(existing_uuids)} 个关卡")
    
    missing_games = [g for g in game_list if g['uuid'] not in existing_uuids]
    if not missing_games:
        logger.info("所有关卡都已下载，无需补充。")
        return
        
    logger.info(f"需要补充下载 {len(missing_games)} 个关卡...")

    # 启动浏览器获取走法数据
    scraper.browser.start()
    try:
        for i, game_meta in enumerate(missing_games):
            logger.info(f'  [{i+1}/{len(missing_games)}] 补漏: {game_meta["title"]}')
            
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
            existing_games.append(game_entry)
            
            # 每下载一定数量保存一下，避免又超时导致丢失
            if (i + 1) % 5 == 0:
                data['games'] = existing_games
                data['total_parsed'] = len(existing_games)
                with open(final_file, 'w', encoding='utf-8') as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
                logger.info(f'    已暂存 (目前共 {len(existing_games)} 局)')

    finally:
        scraper.browser.stop()
        
    data['games'] = existing_games
    data['total_parsed'] = len(existing_games)
    
    with open(final_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    logger.info(f'补漏抓取完成！已更新: {final_file} (总计 {len(existing_games)} 局)')

if __name__ == '__main__':
    main()
