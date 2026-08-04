"""
快速批量抓取脚本

策略：
1. 用已知 ID 列表（从缓存目录页提取）直接下载各局棋谱
2. 对未知 ID 范围的古谱，先用高超时获取目录页
3. 输出每个古谱的 JSON 文件

在正式运行 scraper.py 之前先运行此脚本来收集数据。
"""

import re
import json
import time
import random
import logging
import hashlib
from pathlib import Path
from datetime import date
from typing import List, Dict, Optional, Set

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s %(message)s',
    datefmt='%H:%M:%S',
)
logger = logging.getLogger(__name__)

BASE_URL = 'http://www.dpxq.com'
RAW_DIR = Path(__file__).parent.parent / 'data' / 'raw'
DATA_DIR = Path(__file__).parent.parent / 'data'
RAW_DIR.mkdir(parents=True, exist_ok=True)

# ─────────────────── User-Agent 池 ───────────────────
# 模拟多种浏览器/平台，每次请求随机选取

_USER_AGENTS = [
    # Chrome on Windows
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    # Chrome on Mac
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    # Firefox on Windows
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0',
    # Firefox on Mac
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:128.0) Gecko/20100101 Firefox/128.0',
    # Edge on Windows
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0',
    # Safari on Mac
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15',
    # Chrome on Linux
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
]

_ACCEPT_LANGUAGES = [
    'zh-CN,zh;q=0.9',
    'zh-CN,zh;q=0.9,en;q=0.8',
    'zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
    'zh-CN,zh-TW;q=0.9,zh;q=0.8',
    'zh;q=0.9,zh-CN;q=0.8',
]


def _random_headers() -> Dict[str, str]:
    """每次请求生成略有差异的请求头，降低指纹识别概率。"""
    ua = random.choice(_USER_AGENTS)
    is_firefox = 'Firefox' in ua
    return {
        'User-Agent': ua,
        'Accept': (
            'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
            if is_firefox else
            'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8'
        ),
        'Accept-Language': random.choice(_ACCEPT_LANGUAGES),
        'Accept-Encoding': 'gzip, deflate',
        'Referer': random.choice([
            'http://www.dpxq.com/',
            'http://www.dpxq.com/hldcg/search/',
            'http://www.dpxq.com/hldcg/share/',
        ]),
        'Connection': 'keep-alive',
    }


# ─────────────────── 代理 IP 支持 ───────────────────

PROXY_FILE = Path(__file__).parent / 'proxies.txt'

def _load_proxies() -> List[str]:
    """
    从 proxies.txt 加载代理列表，每行一个，格式示例：
      http://127.0.0.1:7890
      http://user:pass@proxy.example.com:8080
      socks5://127.0.0.1:1080
    空行和 # 开头的注释行会被跳过。
    """
    if not PROXY_FILE.exists():
        return []
    proxies = []
    for line in PROXY_FILE.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#'):
            proxies.append(line)
    return proxies

_PROXY_LIST = _load_proxies()
if _PROXY_LIST:
    logger.info(f"已加载 {len(_PROXY_LIST)} 个代理")
else:
    logger.info("未配置代理，使用直连（如需代理请创建 scraper/proxies.txt）")


def _pick_proxy() -> Optional[Dict[str, str]]:
    """随机选取一个代理，返回 requests 格式的 proxies dict。"""
    if not _PROXY_LIST:
        return None
    proxy = random.choice(_PROXY_LIST)
    return {'http': proxy, 'https': proxy}


# ─────────────────── Session 池 ───────────────────

_SESSION_POOL_SIZE = 3
_SESSION_POOL: List[requests.Session] = []
_SESSION_REQ_COUNT: List[int] = []
_SESSION_MAX_REQS = 80  # 每个 Session 最多使用若干次后换新


def _make_session() -> requests.Session:
    s = requests.Session()
    s.headers.update(_random_headers())
    s.trust_env = False
    retry = Retry(total=2, backoff_factor=3, status_forcelist=[500, 502, 504])
    s.mount('http://', HTTPAdapter(max_retries=retry))
    return s


def _init_session_pool():
    global _SESSION_POOL, _SESSION_REQ_COUNT
    _SESSION_POOL = [_make_session() for _ in range(_SESSION_POOL_SIZE)]
    _SESSION_REQ_COUNT = [0] * _SESSION_POOL_SIZE


def _get_session() -> requests.Session:
    """从池中轮换取 Session，使用次数过多时自动替换。"""
    if not _SESSION_POOL:
        _init_session_pool()

    idx = random.randrange(len(_SESSION_POOL))
    _SESSION_REQ_COUNT[idx] += 1

    if _SESSION_REQ_COUNT[idx] > _SESSION_MAX_REQS:
        logger.debug(f"Session #{idx} 使用 {_SESSION_REQ_COUNT[idx]} 次，替换")
        _SESSION_POOL[idx].close()
        _SESSION_POOL[idx] = _make_session()
        _SESSION_REQ_COUNT[idx] = 1

    return _SESSION_POOL[idx]


# ─────────────────── 自适应限速 ───────────────────

_LAST_REQ = 0.0
_BASE_DELAY = 2.0       # 基础最小间隔
_DELAY_JITTER = 2.0     # 随机抖动范围
_CONSECUTIVE_503 = 0    # 连续 503 计数


def _throttle():
    """自适应限速：连续遇到 503 时自动加大间隔。"""
    global _LAST_REQ
    penalty = min(_CONSECUTIVE_503 * 5.0, 30.0)
    min_s = _BASE_DELAY + penalty
    max_s = min_s + _DELAY_JITTER

    elapsed = time.time() - _LAST_REQ
    wait = random.uniform(min_s, max_s)
    if elapsed < wait:
        time.sleep(wait - elapsed)
    _LAST_REQ = time.time()


def fetch_cached_or_live(url: str, cache_file: Path, timeout: int = 60,
                         encoding: str = 'gbk') -> Optional[str]:
    """读缓存或发起请求。被限流时自动换 Session/代理/请求头重试。"""
    global _CONSECUTIVE_503

    if cache_file.exists() and cache_file.stat().st_size > 200:
        return cache_file.read_text(encoding='utf-8', errors='replace')

    _throttle()
    for attempt in range(5):
        session = _get_session()
        headers = _random_headers()
        proxies = _pick_proxy()

        try:
            logger.info(f"  GET {url[:80]}"
                        f"{' [proxy]' if proxies else ''}"
                        f" (attempt {attempt+1})")
            resp = session.get(url, timeout=timeout, stream=False,
                               headers=headers, proxies=proxies)

            if resp.status_code == 503:
                _CONSECUTIVE_503 += 1
                wait = 30 * (2 ** attempt) + random.uniform(0, 10)
                logger.warning(f"  503 限流（连续第 {_CONSECUTIVE_503} 次），"
                               f"等待 {wait:.0f}s 后换身份重试")
                time.sleep(wait)
                continue

            if resp.status_code == 429:
                _CONSECUTIVE_503 += 1
                wait = 60 * (2 ** attempt) + random.uniform(0, 15)
                logger.warning(f"  429 请求过多，等待 {wait:.0f}s")
                time.sleep(wait)
                continue

            resp.raise_for_status()
            _CONSECUTIVE_503 = max(0, _CONSECUTIVE_503 - 1)
            resp.encoding = encoding
            html = resp.text
            cache_file.write_text(html, encoding='utf-8', errors='replace')
            return html

        except Exception as e:
            if attempt < 4:
                wait = 15 * (2 ** attempt) + random.uniform(0, 5)
                logger.warning(f"  失败: {e}，等待 {wait:.0f}s 重试")
                time.sleep(wait)
            else:
                logger.warning(f"  最终失败: {e}")
                return None
    return None


def fetch_game(gid: int) -> Optional[str]:
    url = f"{BASE_URL}/hldcg/search/view_u_{gid}.html"
    cache = RAW_DIR / f"view_u_{gid}.html"
    return fetch_cached_or_live(url, cache, timeout=45)


def fetch_list_page(path: str) -> Optional[str]:
    """获取目录列表页（含多卷子目录）。"""
    url = f"{BASE_URL}/hldcg/share/{path}/"
    digest = hashlib.md5(url.encode()).hexdigest()[:12]
    cache = RAW_DIR / f"list_{digest}.html"
    return fetch_cached_or_live(url, cache, timeout=90)


def extract_ids(html: str) -> List[int]:
    return list(dict.fromkeys(int(m) for m in re.findall(r'view_u_(\d+)', html)))


def extract_subdirs(html: str, base_path: str) -> List[str]:
    """提取同一古谱下的子目录（卷一、卷二...）。"""
    pattern = re.compile(r'href="(/hldcg/share/[^"]+/)"')
    base_prefix = '/hldcg/share/' + base_path.rstrip('/')
    subdirs = []
    for m in pattern.finditer(html):
        href = m.group(1)
        if href.startswith(base_prefix + '/') and href != base_prefix + '/':
            sub = href[len(base_prefix):].strip('/')
            skip = ['棋谱列表', '按编号', '按价值', '按日期', '按人气', '按修改']
            if sub and not any(k in sub for k in skip):
                subdirs.append(href.replace('/hldcg/share/', '').strip('/'))
    return list(dict.fromkeys(subdirs))


def discover_ids(manual_name: str, share_paths: List[str]) -> List[int]:
    """从目录页发现所有棋谱 ID（两层目录结构）。"""
    all_ids: Set[int] = set()

    for path in share_paths:
        logger.info(f"  尝试目录: {path}")
        html = fetch_list_page(path)
        if not html:
            continue

        # 先检查主目录是否直接有 ID
        direct_ids = extract_ids(html)
        if direct_ids:
            all_ids.update(direct_ids)
            logger.info(f"  直接发现 {len(direct_ids)} 个 ID")

        # 提取子目录（卷）
        subdirs = extract_subdirs(html, path)
        logger.info(f"  发现 {len(subdirs)} 个子目录")

        for subpath in subdirs:
            logger.info(f"    子目录: {subpath}")
            sub_html = fetch_list_page(subpath)
            if sub_html:
                ids = extract_ids(sub_html)
                all_ids.update(ids)
                logger.info(f"      → {len(ids)} 个 ID")

        if all_ids:
            break

    return sorted(all_ids)


_JS_MOVELIST_RE = re.compile(r"var\s+DhtmlXQ_movelist\s*=\s*'(.*?)'", re.DOTALL)
_BRANCH_RE = re.compile(r'\[(\d+_\d+_\d+)\]([\da-fA-F]*)\[/\1\]')
_SIMPLE_MOVELIST_RE = re.compile(
    r'\[DhtmlXQ_movelist\]([\da-fA-F]+)\[/DhtmlXQ_movelist\]'
)


def _extract_js_movelist(html: str) -> Dict:
    """
    从页面 JavaScript 中提取着法数据。

    返回格式:
      {
        'moves_raw': '主线着法编码',
        'variations': [
          {'branch': '0_20_1', 'moves_raw': '...'},  # 在主线第 20 步的变着
          ...
        ]
      }

    着法数据存储在 JS 变量 var DhtmlXQ_movelist 中，有两种子格式：
    - 分支型: [parent_step_branch]着法[/parent_step_branch]
    - 简单型: [DhtmlXQ_movelist]着法[/DhtmlXQ_movelist]
    """
    m = _JS_MOVELIST_RE.search(html)
    if not m:
        return {'moves_raw': '', 'variations': []}

    val = m.group(1)

    # 分支型：[parent_step_branch]moves[/parent_step_branch]
    branches = _BRANCH_RE.findall(val)
    if branches:
        main_moves = ''
        variations = []
        for branch_id, moves in branches:
            if branch_id == '0_1_0':
                main_moves = moves
            else:
                variations.append({'branch': branch_id, 'moves_raw': moves})
        return {'moves_raw': main_moves, 'variations': variations}

    # 简单型：[DhtmlXQ_movelist]moves[/DhtmlXQ_movelist]
    sm = _SIMPLE_MOVELIST_RE.search(val)
    if sm:
        return {'moves_raw': sm.group(1), 'variations': []}

    # 纯数字序列（无标签包裹）
    digits = re.sub(r'[^0-9a-fA-F]', '', val)
    if len(digits) >= 4:
        return {'moves_raw': digits, 'variations': []}

    return {'moves_raw': '', 'variations': []}


def parse_game_from_html(html: str, gid: int) -> Optional[Dict]:
    """从 HTML 解析游戏数据（元信息来自 DhtmlXQ 块，着法来自 JS 变量）。"""
    block_m = re.search(r'\[DhtmlXQ\](.*?)\[/DhtmlXQ\]', html, re.DOTALL)
    if not block_m:
        return None
    block = block_m.group(1)

    def get_field(name):
        m = re.search(rf'\[DhtmlXQ_{name}\](.*?)\[/DhtmlXQ_{name}\]', block, re.DOTALL)
        return m.group(1).strip() if m else ''

    comments = {}
    for cm in re.finditer(r'\[DhtmlXQ_comment(\d+)\](.*?)\[/DhtmlXQ_comment\1\]',
                          block, re.DOTALL):
        val = cm.group(2).strip()
        if val:
            comments[cm.group(1)] = val

    movelist_data = _extract_js_movelist(html)

    result = {
        'id': gid,
        'url': f"{BASE_URL}/hldcg/search/view_u_{gid}.html",
        'title': get_field('title'),
        'event': get_field('event'),
        'class': get_field('class'),
        'round': get_field('round'),
        'date': get_field('date'),
        'result': get_field('result'),
        'red_player': get_field('red') or get_field('redname'),
        'black_player': get_field('black') or get_field('blackname'),
        'board_init_raw': get_field('binit'),
        'moves_raw': movelist_data['moves_raw'],
        'comments': comments,
        'hits': int(get_field('hits') or 0),
        'add_date': get_field('adddate'),
        'owner': get_field('owner'),
        'sort_id': get_field('sortid'),
    }
    if movelist_data['variations']:
        result['variations'] = movelist_data['variations']
    return result


def download_games(
    game_ids: List[int],
    manual_name: str,
    manual_type: str,
    share_paths: List[str],
) -> Path:
    """下载并解析所有游戏，保存到 JSON。"""
    games = []
    total = len(game_ids)
    target_names = {manual_name} | {p.split('/')[-1] for p in share_paths}

    logger.info(f"\n开始下载 《{manual_name}》 共 {total} 局...")

    for idx, gid in enumerate(game_ids, 1):
        if idx % 50 == 0:
            logger.info(f"  进度: {idx}/{total}")

        html = fetch_game(gid)
        if not html:
            continue

        game = parse_game_from_html(html, gid)
        if not game:
            continue

        # 过滤非目标古谱
        event = game.get('event', '')
        owner = game.get('owner', '')
        if not any(t in event or t in owner for t in target_names):
            continue

        games.append(game)

    # 按卷/局号排序
    games.sort(key=lambda g: (g.get('round', ''), g.get('title', '')))

    out = {
        'manual_name': manual_name,
        'manual_type': manual_type,
        'source': BASE_URL,
        'total_discovered': total,
        'total_parsed': len(games),
        'downloaded_at': str(date.today()),
        'games': games,
    }
    out_path = DATA_DIR / f"{manual_name}.json"
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    logger.info(f"已保存: {out_path} ({len(games)} 局)")
    return out_path


# ─────────────────── 目标古谱列表 ───────────────────

TARGETS = [
    # ── 残局谱 ──
    {'name': '适情雅趣',   'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/适情雅趣']},
    {'name': '梦入神机',   'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/梦入神机']},
    {'name': '渊深海阔',   'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/渊深海阔']},
    {'name': '竹香斋',     'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/竹香斋']},
    {'name': '心武残编',   'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/心武残编']},
    {'name': '韬略元机',   'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/韬略元机']},
    {'name': '百局象棋谱', 'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/百局象棋谱']},
    {'name': '烂柯神机',   'type': '古谱残局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱残局/烂柯神机']},
    # ── 全局谱 ──
    {'name': '橘中秘',     'type': '古谱全局', 'share_paths': [
        'chess_象棋谱大全/象棋谱大全-古谱残局/橘中秘',
        'chess_象棋谱大全/象棋谱大全-古谱残局/桔中秘',
        'chess_象棋谱大全/象棋谱大全-古谱全局/橘中秘',
        'chess_象棋谱大全/象棋谱大全-古谱全局/桔中秘',
    ]},
    {'name': '金鹏十八变', 'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/金鹏十八变']},
    {'name': '梅花谱',     'type': '古谱全局', 'share_paths': [
        'chess_象棋谱大全/象棋谱大全-古谱全局/梅花谱',
        'chess_象棋谱大全/象棋谱大全-古谱全局/梅花变',
    ]},
    {'name': '自出洞来无敌手', 'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/自出洞来无敌手']},
    {'name': '反梅花谱',   'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/反梅花谱']},
    {'name': '奕乘',       'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/奕乘']},
    {'name': '梅花泉',     'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/梅花泉']},
    {'name': '无双品',     'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/无双品']},
    {'name': '吴兆龙象棋谱', 'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/吴兆龙象棋谱']},
    {'name': '崇本堂梅花秘谱', 'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/崇本堂梅花秘谱']},
    {'name': '绿榕桥',     'type': '古谱全局', 'share_paths': ['chess_象棋谱大全/象棋谱大全-古谱全局/绿榕桥']},
]


def reparse_from_cache(
    manual_name: str,
    manual_type: str,
    share_paths: List[str],
) -> Optional[Path]:
    """从 data/raw/ 缓存重新解析生成 JSON（不发起任何网络请求）。"""
    target_names = {manual_name} | {p.split('/')[-1] for p in share_paths}

    # 扫描所有缓存的棋谱 HTML
    cached_files = sorted(RAW_DIR.glob('view_u_*.html'))
    logger.info(f"扫描 {len(cached_files)} 个缓存文件...")

    games = []
    for cf in cached_files:
        gid_m = re.search(r'view_u_(\d+)', cf.name)
        if not gid_m:
            continue
        gid = int(gid_m.group(1))

        html = cf.read_text(encoding='utf-8', errors='replace')
        game = parse_game_from_html(html, gid)
        if not game:
            continue

        event = game.get('event', '')
        owner = game.get('owner', '')
        if not any(t in event or t in owner for t in target_names):
            continue

        games.append(game)

    if not games:
        logger.warning(f"《{manual_name}》缓存中未找到匹配的棋谱")
        return None

    games.sort(key=lambda g: (g.get('round', ''), g.get('title', '')))

    with_moves = sum(1 for g in games if g.get('moves_raw'))
    with_vars = sum(1 for g in games if g.get('variations'))

    out = {
        'manual_name': manual_name,
        'manual_type': manual_type,
        'source': BASE_URL,
        'total_discovered': len(games),
        'total_parsed': len(games),
        'downloaded_at': str(date.today()),
        'games': games,
    }
    out_path = DATA_DIR / f"{manual_name}.json"
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    logger.info(f"已保存: {out_path} ({len(games)} 局, "
                f"有着法 {with_moves}, 有变着 {with_vars})")
    return out_path


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--manual', '-m', help='只处理指定古谱')
    ap.add_argument('--discover-only', action='store_true', help='只发现 ID，不下载')
    ap.add_argument('--reparse', action='store_true',
                    help='从缓存 HTML 重新解析生成 JSON（不发起网络请求）')
    args = ap.parse_args()

    targets = TARGETS
    if args.manual:
        targets = [t for t in TARGETS if t['name'] == args.manual]
        if not targets:
            print(f"未找到: {args.manual}")
            return

    summary = {}
    for target in targets:
        name = target['name']
        logger.info(f"\n{'='*60}")
        logger.info(f"处理 《{name}》")

        if args.reparse:
            out = reparse_from_cache(name, target['type'], target['share_paths'])
            if out:
                with open(out, encoding='utf-8') as f:
                    data = json.load(f)
                with_moves = sum(1 for g in data['games'] if g.get('moves_raw'))
                summary[name] = f"{data['total_parsed']} 局 (着法 {with_moves})"
            else:
                summary[name] = '无缓存数据'
            continue

        # 发现 ID
        ids = discover_ids(name, target['share_paths'])
        logger.info(f"发现 {len(ids)} 个 ID")

        if args.discover_only:
            summary[name] = len(ids)
            continue

        if not ids:
            logger.warning(f"《{name}》无 ID，跳过")
            summary[name] = 0
            continue

        # 下载并解析
        out = download_games(ids, name, target['type'], target['share_paths'])
        with open(out, encoding='utf-8') as f:
            data = json.load(f)
        summary[name] = data['total_parsed']

    # 汇总
    print(f"\n{'='*60}")
    print("汇总:")
    for name, count in summary.items():
        print(f"  {name}: {count}")


if __name__ == '__main__':
    main()
