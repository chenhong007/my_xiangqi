import json
import logging
import time
from pathlib import Path
import sys

sys.path.append(str(Path(__file__).parent))
from xqipu_scraper import XqipuScraper, iccs_to_moves_raw, fen_to_binit

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s',
    datefmt='%H:%M:%S',
)
logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).parent.parent / 'data'

TARGET_MANUALS = [
    '残局攻杀谱',
    '布局定式与战理',
    '微信天天象棋隋唐演义中集攻略',
    '弈林新编',
    '中局大全',
    '中国象棋高级教程',
    '桔中秘残局谱',
    '桔中秘',
    '梦入神机'
]

def main():
    scraper = XqipuScraper()
    books = scraper.get_book_list()
    book_dict = {b['name']: b for b in books}
    
    scraper.browser.start()
    try:
        for manual_name in TARGET_MANUALS:
            filepath = DATA_DIR / f"{manual_name}.json"
            if not filepath.exists():
                logger.warning(f"文件不存在: {filepath}")
                continue
                
            if manual_name not in book_dict:
                logger.warning(f"网站上未找到古谱: {manual_name}")
                continue
                
            book_info = book_dict[manual_name]
            book_url = book_info['url']
            
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
                
            games = data.get('games', [])
            existing_game_dict = {g['id']: g for g in games}
            
            # 1. 获取网站上最新的棋谱列表
            try:
                game_list = scraper.get_book_games(book_url, manual_name)
            except Exception as e:
                logger.error(f"获取 {manual_name} 列表失败: {e}")
                continue
                
            # 2. 找出缺失的棋谱 (在 game_list 中但不在 existing_game_dict 中)
            missing_games_meta = [g for g in game_list if g['uuid'] not in existing_game_dict]
            
            # 3. 找出走法为空的棋谱 (在 existing_game_dict 中且 moves_raw 为空)
            empty_games_meta = []
            for g in game_list:
                uuid = g['uuid']
                if uuid in existing_game_dict:
                    if not existing_game_dict[uuid].get('moves_raw'):
                        empty_games_meta.append(g)
            
            to_fetch = missing_games_meta + empty_games_meta
            
            if not to_fetch:
                logger.info(f"✓ {manual_name} 无需补充下载。")
                # 更新一下 total_discovered 和 total_parsed
                data['total_discovered'] = len(game_list)
                data['total_parsed'] = len(games)
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
                continue
                
            logger.info(f"✗ {manual_name} 需要补充下载 {len(missing_games_meta)} 局缺失，重新下载 {len(empty_games_meta)} 局空走法。")
            
            updated = False
            for i, game_meta in enumerate(to_fetch):
                logger.info(f"  [{i+1}/{len(to_fetch)}] 抓取: {game_meta['title']}")
                game_data = scraper.fetch_game_data(game_meta['uuid'])
                if not game_data or not game_data['iccs_moves']:
                    logger.warning(f"    跳过（仍无走法数据）")
                    continue
                    
                moves_raw = iccs_to_moves_raw(game_data['iccs_moves'])
                init_fen = game_data['init_fen']
                binit = fen_to_binit(init_fen) if init_fen else ''
                
                game_entry = {
                    'id': game_meta['uuid'],
                    'url': game_meta['url'],
                    'title': game_meta['title'],
                    'event': manual_name,
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
                
                existing_game_dict[game_meta['uuid']] = game_entry
                updated = True
                
            if updated:
                # 重新按照 game_list 的顺序组装 games
                new_games = []
                for g in game_list:
                    if g['uuid'] in existing_game_dict:
                        new_games.append(existing_game_dict[g['uuid']])
                        
                data['games'] = new_games
                data['total_discovered'] = len(game_list)
                data['total_parsed'] = len(new_games)
                
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
                logger.info(f"✓ {manual_name} 补充完成并保存。")
                
    finally:
        scraper.browser.stop()

if __name__ == '__main__':
    main()