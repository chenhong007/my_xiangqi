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
        
    with open(final_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    existing_games = data.get('games', [])
    existing_uuids = {g['id'] for g in existing_games}
    
    scraper = XqipuScraper()
    
    # 获取所有的关卡链接
    logger.info("获取天天象棋攻略列表...")
    resp = scraper._get(f'{BASE_URL}/tiantiangonglue')
    soup = BeautifulSoup(resp.text, 'lxml')
    
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
            
    missing_games = [g for g in game_list if g['uuid'] not in existing_uuids]
    if not missing_games:
        logger.info("所有关卡都已下载，无需补充。")
        return
        
    logger.info(f"需要强力补充下载 {len(missing_games)} 个关卡...")

    # 启动浏览器获取走法数据
    scraper.browser.start()
    try:
        for i, game_meta in enumerate(missing_games):
            logger.info(f'  [{i+1}/{len(missing_games)}] 补漏: {game_meta["title"]} ({game_meta["uuid"]})')
            
            # 使用更长的等待时间以防验证超时
            html = scraper.browser.fetch_page(game_meta['url'], wait_seconds=15)
            
            # 如果遇到 robotverify，再等久一点并重试
            if html and 'robotverify' in html:
                logger.info('    遇到人机验证，等待 15 秒后重试...')
                time.sleep(15)
                html = scraper.browser.fetch_page(game_meta['url'], wait_seconds=15)
                
            if not html or 'qipu-moves-iccs' not in html:
                logger.warning(f'    仍然失败（无走法数据），直接尝试用 requests 无头抓取看看运气')
                # 尝试用 requests 直接抓，有时候不需要验证
                resp = scraper._get(game_meta['url'])
                if 'qipu-moves-iccs' in resp.text:
                    html = resp.text
                    logger.info('    requests 抓取成功！')
                else:
                    continue

            soup = BeautifulSoup(html, 'lxml')
            
            # 提取 ICCS 着法
            moves_el = soup.find(id='qipu-moves-iccs')
            iccs_moves = moves_el.get_text(strip=True) if moves_el else ''

            # 提取初始 FEN
            fen_el = soup.find(id='qipu-init-fen')
            init_fen = fen_el.get_text(strip=True) if fen_el else ''

            # 提取比赛结果
            result = ''
            for field in soup.select('.field--label-inline'):
                label = field.select_one('.field--label')
                value = field.select_one('.field--item')
                if label and value:
                    label_text = label.get_text(strip=True)
                    if '结果' in label_text:
                        result = value.get_text(strip=True)

            if not iccs_moves:
                logger.warning(f'    跳过（没有解析出 moves）')
                continue

            # 转换为 DhtmlXQ 兼容格式
            moves_raw = iccs_to_moves_raw(iccs_moves)
            binit = fen_to_binit(init_fen) if init_fen else ''

            game_entry = {
                'id': game_meta['uuid'],
                'url': game_meta['url'],
                'title': game_meta['title'],
                'event': '天天象棋过关攻略',
                'class': '',
                'round': '',
                'date': '',
                'result': result,
                'red_player': '',
                'black_player': '',
                'board_init_raw': binit,
                'moves_raw': moves_raw,
                'comments': {},
                'variations': [],
            }
            existing_games.append(game_entry)
            
            # 立即保存
            data['games'] = existing_games
            data['total_parsed'] = len(existing_games)
            with open(final_file, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            logger.info(f'    已暂存 (目前共 {len(existing_games)} 局)')

    finally:
        scraper.browser.stop()
        
    logger.info(f'完美补漏抓取完成！(总计 {len(existing_games)} 局)')

if __name__ == '__main__':
    main()
