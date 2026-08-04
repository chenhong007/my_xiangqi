"""
HTTP 下载模块，支持：
- GBK 编码自动解码
- 请求限速（避免被封 IP）
- 文件缓存（断点续传）
- 自动重试（网络波动）
"""

import os
import time
import random
import logging
import hashlib
from pathlib import Path
from typing import Optional

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logger = logging.getLogger(__name__)

BASE_URL = 'http://www.dpxq.com'
CACHE_DIR = Path(__file__).parent.parent / 'data' / 'raw'

# 模拟浏览器 UA，减少被屏蔽概率
HEADERS = {
    'User-Agent': (
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
        'AppleWebKit/537.36 (KHTML, like Gecko) '
        'Chrome/120.0.0.0 Safari/537.36'
    ),
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Accept-Encoding': 'gzip, deflate',
    'Connection': 'keep-alive',
    'Referer': 'http://www.dpxq.com/',
}


def _make_session() -> requests.Session:
    """创建带重试策略的 HTTP Session。"""
    session = requests.Session()
    session.headers.update(HEADERS)

    retry = Retry(
        total=3,
        backoff_factor=2,       # 重试间隔：2, 4, 8 秒
        status_forcelist=[500, 502, 503, 504],
        allowed_methods=['GET'],
    )
    adapter = HTTPAdapter(max_retries=retry)
    session.mount('http://', adapter)
    session.mount('https://', adapter)
    return session


_SESSION: Optional[requests.Session] = None
_LAST_REQUEST_TIME: float = 0.0


def _get_session() -> requests.Session:
    global _SESSION
    if _SESSION is None:
        _SESSION = _make_session()
    return _SESSION


def _rate_limit(min_delay: float = 1.0, max_delay: float = 2.5) -> None:
    """限速：距上次请求不足 min_delay 秒则等待。"""
    global _LAST_REQUEST_TIME
    elapsed = time.time() - _LAST_REQUEST_TIME
    wait = random.uniform(min_delay, max_delay)
    if elapsed < wait:
        time.sleep(wait - elapsed)
    _LAST_REQUEST_TIME = time.time()


def _cache_path(url: str) -> Path:
    """根据 URL 生成缓存文件路径。"""
    # 提取 view_u_XXXXX 的 ID
    import re
    m = re.search(r'view_u_(\d+)', url)
    if m:
        return CACHE_DIR / f"view_u_{m.group(1)}.html"
    # 其他 URL 用 MD5 哈希
    digest = hashlib.md5(url.encode()).hexdigest()[:12]
    return CACHE_DIR / f"page_{digest}.html"


def fetch_url(
    url: str,
    encoding: str = 'gbk',
    use_cache: bool = True,
    timeout: int = 30,
    min_delay: float = 1.0,
    max_delay: float = 2.5,
) -> Optional[str]:
    """
    获取 URL 内容，优先读缓存，否则发起 HTTP 请求。

    参数：
        url       - 目标 URL
        encoding  - 响应编码（东萍网站使用 GBK）
        use_cache - 是否使用文件缓存
        timeout   - 请求超时秒数
        min_delay - 最小请求间隔（秒）
        max_delay - 最大请求间隔（秒）

    返回：
        页面 HTML 文本，失败时返回 None
    """
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cache_file = _cache_path(url)

    # 读取缓存
    if use_cache and cache_file.exists() and cache_file.stat().st_size > 100:
        logger.debug(f"[缓存] {cache_file.name}")
        return cache_file.read_text(encoding='utf-8', errors='replace')

    # 发起请求
    _rate_limit(min_delay, max_delay)
    session = _get_session()

    try:
        logger.info(f"[请求] {url}")
        resp = session.get(url, timeout=timeout)
        resp.raise_for_status()

        # 解码（网站使用 GBK，但 Content-Type 可能声明错误）
        resp.encoding = encoding
        html = resp.text

        # 写入缓存
        if use_cache:
            cache_file.write_text(html, encoding='utf-8', errors='replace')

        return html

    except requests.Timeout:
        logger.warning(f"[超时] {url}")
        return None
    except requests.HTTPError as e:
        logger.warning(f"[HTTP错误] {url}: {e}")
        return None
    except requests.RequestException as e:
        logger.warning(f"[请求失败] {url}: {e}")
        return None


def fetch_game_page(game_id: int, use_cache: bool = True) -> Optional[str]:
    """获取单局棋谱页面。"""
    url = f"{BASE_URL}/hldcg/search/view_u_{game_id}.html"
    return fetch_url(url, use_cache=use_cache)


def fetch_share_page(path: str, page: int = 1, use_cache: bool = True) -> Optional[str]:
    """
    获取共享目录列表页。

    参数：
        path - 目录路径（不含域名），如 "chess_象棋谱大全/古谱残局/橘中秘"
        page - 页码（从1开始）
    """
    if page == 1:
        url = f"{BASE_URL}/hldcg/share/{path}/"
    else:
        url = f"{BASE_URL}/hldcg/share/{path}/第{page}页/"

    # 列表页缓存键包含页码
    cache_file = CACHE_DIR / f"list_{hashlib.md5(url.encode()).hexdigest()[:12]}.html"

    if use_cache and cache_file.exists() and cache_file.stat().st_size > 100:
        logger.debug(f"[缓存列表] {url}")
        return cache_file.read_text(encoding='utf-8', errors='replace')

    _rate_limit()
    session = _get_session()

    try:
        logger.info(f"[列表页] {url}")
        resp = session.get(url, timeout=45)
        resp.raise_for_status()
        resp.encoding = 'gbk'
        html = resp.text

        if use_cache:
            cache_file.write_text(html, encoding='utf-8', errors='replace')

        return html

    except requests.Timeout:
        logger.warning(f"[超时-列表] {url}")
        return None
    except requests.RequestException as e:
        logger.warning(f"[失败-列表] {url}: {e}")
        return None


def fetch_search_page(
    event: str,
    class_name: str = '',
    page: int = 1,
    use_cache: bool = True,
) -> Optional[str]:
    """
    通过搜索 API 获取棋谱列表。

    URL 格式：/hldcg/share/chess_CLASSNAME/EVENT/
    或使用列表接口：/hldcg/search/?s=list.asp?owner=u&event=EVENT
    """
    from urllib.parse import quote

    # 尝试直接目录 URL
    if class_name:
        # 从 "象棋谱大全-古谱残局" 提取分类名 "古谱残局"
        subclass = class_name.split('-')[-1] if '-' in class_name else class_name
        path = f"chess_象棋谱大全/{subclass}/{event}"
    else:
        path = f"chess_象棋谱大全/{event}"

    return fetch_share_page(path, page=page, use_cache=use_cache)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
    html = fetch_game_page(42809)
    if html:
        print(f"成功获取页面，长度 {len(html)} 字符")
        print(html[:200])
    else:
        print("获取失败")
