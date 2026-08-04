"""
象棋谱网 (xqipu.com) 古谱爬虫

从 https://www.xqipu.com/canjugupu 下载残局、古谱棋书的走法数据。
输出格式兼容现有 export_app.py 管道。

用法：
    python xqipu_scraper.py                    # 下载所有古谱
    python xqipu_scraper.py --book 桔中秘      # 只下载指定古谱
    python xqipu_scraper.py --list             # 列出所有可用古谱
"""

import json
import logging
import subprocess
import time
import argparse
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import requests
import websocket
from bs4 import BeautifulSoup

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s',
    datefmt='%H:%M:%S',
)
logger = logging.getLogger(__name__)

BASE_URL = 'https://www.xqipu.com'
DATA_DIR = Path(__file__).parent.parent / 'data'
DATA_DIR.mkdir(parents=True, exist_ok=True)

CHROME_PATH = r'C:\Program Files\Google\Chrome\Application\chrome.exe'
DEBUG_PORT = 9222
CHROME_PROFILE_DIR = str(Path(__file__).parent.parent / 'chrome_profile')

# 请求间隔（秒）
REQUEST_DELAY = 2
# 页面加载等待（含人机验证倒计时 6s + 页面渲染）
PAGE_LOAD_WAIT = 14

# ICCS 列字母到数字的映射
ICCS_COL_MAP = {c: i for i, c in enumerate('abcdefghi')}

# 标准初始局面的 binit 编码
STANDARD_BINIT = (
    '09' '19' '29' '39' '49' '59' '69' '79' '89'
    '17' '77'
    '06' '26' '46' '66' '86'
    '00' '10' '20' '30' '40' '50' '60' '70' '80'
    '12' '72'
    '03' '23' '43' '63' '83'
)

# FEN 棋子字符到槽位的映射（用于 FEN → binit 转换）
FEN_PIECE_SLOTS = {
    'R': [0, 8],
    'N': [1, 7],
    'B': [2, 6],
    'A': [3, 5],
    'K': [4],
    'C': [9, 10],
    'P': [11, 12, 13, 14, 15],
    'r': [16, 24],
    'n': [17, 23],
    'b': [18, 22],
    'a': [19, 21],
    'k': [20],
    'c': [25, 26],
    'p': [27, 28, 29, 30, 31],
}


def fen_to_binit(fen: str) -> str:
    """
    将象棋 FEN 字符串转换为 DhtmlXQ binit 格式（64字符）。

    FEN 行顺序：从黑方底线(row 0)到红方底线(row 9)，用 / 分隔。
    binit: 32 个槽位，每个 2 字符 (col, row)，99 表示不在棋盘上。
    """
    if not fen or not fen.strip():
        return ''

    # 只取 FEN 的局面部分（可能带有 w/b 标记等附加信息）
    fen_board = fen.strip().split()[0]
    rows = fen_board.split('/')

    if len(rows) != 10:
        logger.warning(f'FEN 行数不为 10: {fen}')
        return ''

    # 解析棋盘上所有棋子的位置
    pieces_on_board: List[Tuple[str, int, int]] = []  # (piece_char, col, row)
    for row_idx, row_str in enumerate(rows):
        col = 0
        for ch in row_str:
            if ch.isdigit():
                col += int(ch)
            elif ch.isalpha():
                pieces_on_board.append((ch, col, row_idx))
                col += 1

    # 分配槽位
    slot_positions = ['99'] * 32
    slot_usage = {piece: 0 for piece in FEN_PIECE_SLOTS}

    for piece_char, col, row in pieces_on_board:
        if piece_char not in FEN_PIECE_SLOTS:
            continue
        slots = FEN_PIECE_SLOTS[piece_char]
        idx = slot_usage[piece_char]
        if idx >= len(slots):
            continue
        slot = slots[idx]
        slot_positions[slot] = f'{col}{row}'
        slot_usage[piece_char] = idx + 1

    return ''.join(slot_positions)


def iccs_to_moves_raw(iccs: str) -> str:
    """将 ICCS 着法字符串转换为 DhtmlXQ moves_raw 格式。

    ICCS: 每步 4 字符 (col_from_letter, row_from, col_to_letter, row_to)
    moves_raw: 每步 4 字符 (col_from_digit, row_from, col_to_digit, row_to)
    """
    result = []
    for i in range(0, len(iccs) - 3, 4):
        fc_char = iccs[i]
        fr_char = iccs[i + 1]
        tc_char = iccs[i + 2]
        tr_char = iccs[i + 3]

        fc = ICCS_COL_MAP.get(fc_char)
        tc = ICCS_COL_MAP.get(tc_char)
        if fc is None or tc is None:
            logger.warning(f'无法解析 ICCS 着法: {iccs[i:i+4]}')
            break

        result.append(f'{fc}{fr_char}{tc}{tr_char}')

    return ''.join(result)


class ChromeBrowser:
    """通过 Chrome DevTools Protocol 控制无头浏览器"""

    def __init__(self):
        self.proc = None
        self._ws = None
        self._msg_id = 0

    def start(self):
        """启动 Chrome 无头调试模式"""
        cmd = [
            CHROME_PATH,
            f'--remote-debugging-port={DEBUG_PORT}',
            '--headless=new',
            '--no-sandbox',
            '--disable-gpu',
            '--ignore-certificate-errors',
            '--remote-allow-origins=*',
            f'--user-data-dir={CHROME_PROFILE_DIR}',
        ]
        self.proc = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )
        # 等待 Chrome 就绪（最多尝试 10 次）
        for attempt in range(10):
            time.sleep(1)
            try:
                resp = requests.get(f'http://127.0.0.1:{DEBUG_PORT}/json/version', timeout=3)
                if resp.status_code == 200:
                    logger.info('Chrome 浏览器已启动')
                    return
            except Exception:
                pass
        raise RuntimeError('Chrome 启动超时')

    def stop(self):
        if self._ws:
            try:
                self._ws.close()
            except Exception:
                pass
            self._ws = None
        if self.proc:
            try:
                self.proc.terminate()
                self.proc.wait(timeout=5)
            except Exception:
                self.proc.kill()
            self.proc = None

    def _connect_tab(self):
        """连接到浏览器标签页"""
        resp = requests.get(f'http://127.0.0.1:{DEBUG_PORT}/json', timeout=5)
        tabs = resp.json()
        ws_url = None
        for tab in tabs:
            if tab.get('type') == 'page':
                ws_url = tab.get('webSocketDebuggerUrl')
                break
        if not ws_url:
            raise RuntimeError('无法获取 Chrome 标签页')
        self._ws = websocket.create_connection(ws_url, timeout=30)

    def _send(self, method: str, params: dict = None):
        """发送 CDP 命令并等待响应"""
        self._msg_id += 1
        cmd = {'id': self._msg_id, 'method': method}
        if params:
            cmd['params'] = params
        self._ws.send(json.dumps(cmd))
        while True:
            raw = self._ws.recv()
            result = json.loads(raw)
            if result.get('id') == self._msg_id:
                return result

    def fetch_page(self, url: str, wait_seconds: int = PAGE_LOAD_WAIT) -> Optional[str]:
        """导航到 URL 并返回页面 HTML（会自动处理人机验证）"""
        if not self._ws:
            self._connect_tab()

        try:
            self._send('Page.navigate', {'url': url})
            time.sleep(wait_seconds)

            result = self._send('Runtime.evaluate', {
                'expression': 'document.documentElement.outerHTML'
            })
            html = result.get('result', {}).get('result', {}).get('value', '')
            return html if html else None
        except Exception as e:
            logger.warning(f'CDP 页面获取失败: {e}，尝试重启浏览器...')
            try:
                self._ws.close()
            except Exception:
                pass
            self._ws = None
            # 重启浏览器
            self.stop()
            time.sleep(2)
            self.start()
            return None


class XqipuScraper:
    """象棋谱网爬虫"""

    def __init__(self):
        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
                          'AppleWebKit/537.36 (KHTML, like Gecko) '
                          'Chrome/120.0.0.0 Safari/537.36',
            'Accept-Language': 'zh-CN,zh;q=0.9',
        })
        self.browser = ChromeBrowser()
        self._last_request_time = 0
        import urllib3
        urllib3.disable_warnings()

    def _throttle(self):
        elapsed = time.time() - self._last_request_time
        if elapsed < REQUEST_DELAY:
            time.sleep(REQUEST_DELAY - elapsed)
        self._last_request_time = time.time()

    def _get(self, url: str, allow_redirects=True) -> requests.Response:
        """发送 GET 请求（用于不需要验证的页面），强制 UTF-8 解码"""
        self._throttle()
        resp = self.session.get(url, allow_redirects=allow_redirects, timeout=30)
        resp.encoding = 'utf-8'
        return resp

    def get_book_list(self) -> List[Dict[str, str]]:
        """获取所有古谱列表，返回 [{name, id, url}, ...]"""
        logger.info('获取古谱列表...')
        resp = self._get(f'{BASE_URL}/canjugupu')
        soup = BeautifulSoup(resp.text, 'lxml')

        books = []
        for link in soup.select('.views-field-name .field-content a'):
            href = link.get('href', '')
            name = link.get_text(strip=True)
            if href.startswith('/canjugupu/'):
                book_id = href.split('/')[-1]
                books.append({
                    'name': name,
                    'id': book_id,
                    'url': f'{BASE_URL}{href}',
                })

        logger.info(f'发现 {len(books)} 部古谱')
        return books

    def get_book_games(self, book_url: str, book_name: str) -> List[Dict]:
        """获取一部古谱的所有游戏列表（分页遍历）"""
        games = []
        page = 0

        while True:
            url = f'{book_url}?page={page}' if page > 0 else book_url
            logger.info(f'  获取 [{book_name}] 第 {page + 1} 页...')
            resp = self._get(url)
            soup = BeautifulSoup(resp.text, 'lxml')

            page_games = []
            for item in soup.select('.views-field-nothing-1 .field-content a'):
                href = item.get('href', '')
                title = item.get('title', '') or item.get_text(strip=True)
                if href.startswith('/qipu/'):
                    uuid = href.split('/qipu/')[-1]
                    page_games.append({
                        'uuid': uuid,
                        'title': title,
                        'url': f'{BASE_URL}{href}',
                    })

            # 也提取 FEN 数据（来自 data-fen 属性）
            fen_imgs = soup.select('.views-field-nothing .field-content img[data-fen]')
            for i, img in enumerate(fen_imgs):
                if i < len(page_games):
                    page_games[i]['fen_preview'] = img.get('data-fen', '')

            games.extend(page_games)

            # 检查是否有下一页
            next_link = soup.select_one('.pager-next a, .next a')
            if next_link:
                page += 1
            else:
                break

        logger.info(f'  [{book_name}] 共 {len(games)} 局')
        return games

    def fetch_game_data(self, uuid: str) -> Optional[Dict]:
        """通过 Chrome CDP 获取单局棋谱的完整走法数据"""
        url = f'{BASE_URL}/qipu/{uuid}'
        html = self.browser.fetch_page(url)

        if not html or 'qipu-moves-iccs' not in html:
            # 可能还在验证页面，再等一次
            if html and 'robotverify' in html:
                logger.info('    等待验证重试...')
                time.sleep(8)
                html = self.browser.fetch_page(url, wait_seconds=8)

            if not html or 'qipu-moves-iccs' not in html:
                logger.warning(f'  无法获取棋谱 {uuid}')
                return None

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

        return {
            'iccs_moves': iccs_moves,
            'init_fen': init_fen,
            'result': result,
        }

    def scrape_book(self, book_info: Dict) -> Dict:
        """抓取一部完整古谱的数据"""
        book_name = book_info['name']
        book_url = book_info['url']
        book_id = book_info['id']

        logger.info(f'开始抓取: {book_name} (ID: {book_id})')

        # 获取所有游戏列表（HTTP 请求即可，无需验证）
        game_list = self.get_book_games(book_url, book_name)

        # 启动浏览器获取走法数据
        self.browser.start()
        try:
            games = []
            for i, game_meta in enumerate(game_list):
                logger.info(f'  [{i+1}/{len(game_list)}] {game_meta["title"]}')

                game_data = self.fetch_game_data(game_meta['uuid'])
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
                    'event': book_name,
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
        finally:
            self.browser.stop()

        # 构建输出
        output = {
            'manual_name': book_name,
            'manual_type': '残局古谱',
            'source': f'{BASE_URL}/canjugupu/{book_id}',
            'total_discovered': len(game_list),
            'total_parsed': len(games),
            'downloaded_at': time.strftime('%Y-%m-%d'),
            'games': games,
        }

        return output

    def save_book(self, data: Dict):
        """保存古谱数据到 JSON 文件"""
        filename = f'{data["manual_name"]}.json'
        filepath = DATA_DIR / filename
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        logger.info(f'已保存: {filepath} ({data["total_parsed"]} 局)')


def load_existing_games() -> set:
    """加载已有数据中的游戏标题，用于去重"""
    existing = set()
    for f in DATA_DIR.glob('*.json'):
        try:
            data = json.loads(f.read_text(encoding='utf-8'))
            for game in data.get('games', []):
                title = game.get('title', '').strip()
                if title:
                    existing.add(title)
        except (json.JSONDecodeError, KeyError):
            pass
    return existing


def main():
    parser = argparse.ArgumentParser(description='象棋谱网古谱爬虫')
    parser.add_argument('--book', type=str, help='只下载指定名称的古谱')
    parser.add_argument('--list', action='store_true', help='列出所有可用古谱')
    parser.add_argument('--no-dedup', action='store_true', help='不进行去重')
    args = parser.parse_args()

    scraper = XqipuScraper()
    books = scraper.get_book_list()

    if args.list:
        print(f'\n共 {len(books)} 部古谱：\n')
        for b in books:
            print(f'  {b["name"]:20s}  (ID: {b["id"]})')
        return

    # 加载已有数据用于去重
    existing_titles = set() if args.no_dedup else load_existing_games()
    if existing_titles:
        logger.info(f'已加载 {len(existing_titles)} 条已有棋谱标题用于去重')

    # 筛选要下载的古谱
    if args.book:
        books = [b for b in books if args.book in b['name']]
        if not books:
            logger.error(f'未找到匹配的古谱: {args.book}')
            return

    for book in books:
        output_file = DATA_DIR / f'{book["name"]}.json'
        if output_file.exists():
            logger.info(f'跳过已存在: {book["name"]}')
            continue

        data = scraper.scrape_book(book)

        # 去重
        if existing_titles and not args.no_dedup:
            before = len(data['games'])
            data['games'] = [
                g for g in data['games']
                if g['title'].strip() not in existing_titles
            ]
            removed = before - len(data['games'])
            if removed:
                logger.info(f'  去重移除 {removed} 局')
            data['total_parsed'] = len(data['games'])

        if data['games']:
            scraper.save_book(data)
            # 将新标题加入已有集合
            for g in data['games']:
                existing_titles.add(g['title'].strip())
        else:
            logger.info(f'  {book["name"]}: 无新棋谱，跳过保存')


if __name__ == '__main__':
    main()
