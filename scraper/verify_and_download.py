import json
import logging
import time
from pathlib import Path

from xqipu_scraper import XqipuScraper, iccs_to_moves_raw, fen_to_binit, DATA_DIR

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s',
    datefmt='%H:%M:%S',
)
logger = logging.getLogger(__name__)

def get_manuals_from_gupu():
    manuals = []
    gupu_path = Path('../gupu.md')
    if not gupu_path.exists():
        gupu_path = Path('gupu.md')
    with open(gupu_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('http') and not line.startswith('下载'):
                manuals.append(line)
    return manuals

def main():
    manuals = get_manuals_from_gupu()
    logger.info(f"从 gupu.md 获取到 {len(manuals)} 个古谱。")

    scraper = XqipuScraper()
    books = scraper.get_book_list()
    book_dict = {b['name']: b for b in books}

    for manual_name in manuals:
        if manual_name == '天天象棋过关攻略':
            continue # 特殊处理，或者忽略

        filepath = DATA_DIR / f"{manual_name}.json"
        
        if manual_name not in book_dict:
            logger.warning(f"网站上未找到古谱: {manual_name}")
            continue
            
        book_info = book_dict[manual_name]
        
        if filepath.exists():
            with open(filepath, 'r', encoding='utf-8') as f:
                try:
                    data = json.load(f)
                    discovered = data.get('total_discovered', 0)
                    games = data.get('games', [])
                    parsed = len(games)
                    if discovered == parsed:
                        logger.info(f"✓ {manual_name} 已完整下载 ({parsed}/{discovered})")
                        continue
                    else:
                        logger.info(f"✗ {manual_name} 不完整 ({parsed}/{discovered})，准备补充下载...")
                except Exception as e:
                    logger.error(f"读取 {manual_name}.json 失败: {e}，准备重新下载...")
                    data = None
        else:
            logger.info(f"✗ {manual_name} 未下载，准备下载...")
            data = None
            games = []
            
        # 开始下载或补充下载
        book_url = book_info['url']
        try:
            game_list = scraper.get_book_games(book_url, manual_name)
        except Exception as e:
            logger.error(f"获取 {manual_name} 的棋谱列表失败: {e}")
            continue
        
        existing_uuids = {g['id'] for g in games} if data else set()
        missing_games = [g for g in game_list if g['uuid'] not in existing_uuids]
        
        if not missing_games:
            logger.info(f"  {manual_name} 没有缺失的棋谱。")
            if data and data.get('total_discovered') != len(game_list):
                data['total_discovered'] = len(game_list)
                data['total_parsed'] = len(games)
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
            continue
            
        logger.info(f"  需要下载 {len(missing_games)} 个棋谱...")
        
        scraper.browser.start()
        try:
            for i, game_meta in enumerate(missing_games):
                logger.info(f'  [{i+1}/{len(missing_games)}] {game_meta["title"]}')
                
                game_data = scraper.fetch_game_data(game_meta['uuid'])
                if not game_data or not game_data['iccs_moves']:
                    logger.warning(f'    跳过（无走法数据）')
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
                games.append(game_entry)
                
                # 每下载 5 个保存一次
                if (i + 1) % 5 == 0:
                    output = {
                        'manual_name': manual_name,
                        'manual_type': '残局古谱',
                        'source': book_info['url'],
                        'total_discovered': len(game_list),
                        'total_parsed': len(games),
                        'downloaded_at': time.strftime('%Y-%m-%d'),
                        'games': games,
                    }
                    with open(filepath, 'w', encoding='utf-8') as f:
                        json.dump(output, f, ensure_ascii=False, indent=2)
        finally:
            scraper.browser.stop()
            
        output = {
            'manual_name': manual_name,
            'manual_type': '残局古谱',
            'source': book_info['url'],
            'total_discovered': len(game_list),
            'total_parsed': len(games),
            'downloaded_at': time.strftime('%Y-%m-%d'),
            'games': games,
        }
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(output, f, ensure_ascii=False, indent=2)
        logger.info(f"✓ {manual_name} 下载完成，共 {len(games)} 局。")

if __name__ == '__main__':
    main()