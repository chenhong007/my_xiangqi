"""
古谱目录发现模块

支持三种发现策略（按优先级尝试）：
1. 共享目录页面解析（/hldcg/share/chess_象棋谱大全/古谱残局/橘中秘/）
2. 搜索接口翻页（/hldcg/search/?s=list.asp?owner=u&event=橘中秘）
3. 顺序 ID 扫描（已知 ID 范围内逐个验证）
"""

import re
import logging
from typing import List, Dict, Optional, Tuple

from downloader import fetch_share_page, fetch_game_page, fetch_url, BASE_URL
from parser import extract_game_ids_from_html, parse_fields, parse_dhtmlxq_block

logger = logging.getLogger(__name__)

# 目标古谱完整配置
# 正确的 URL 结构：chess_象棋谱大全/象棋谱大全-{分类}/{古谱名}/
# 分类可能包含：古谱残局、古谱全局
MANUALS: List[Dict] = [
    {
        'name': '橘中秘',
        'alt_names': ['桔中秘'],        # 网站有时用"桔"代替"橘"
        'type': '古谱残局',
        'class': '象棋谱大全-古谱残局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱残局/橘中秘',
            'chess_象棋谱大全/象棋谱大全-古谱残局/桔中秘',
        ],
    },
    {
        'name': '自出洞来无敌手',
        'alt_names': [],
        'type': '古谱全局',
        'class': '象棋谱大全-古谱全局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱全局/自出洞来无敌手',
        ],
    },
    {
        'name': '梅花谱',
        'alt_names': [],
        'type': '古谱全局',
        'class': '象棋谱大全-古谱全局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱全局/梅花谱',
            'chess_象棋谱大全/象棋谱大全-古谱全局/梅花变',
        ],
    },
    {
        'name': '反梅花谱',
        'alt_names': [],
        'type': '古谱全局',
        'class': '象棋谱大全-古谱全局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱全局/反梅花谱',
        ],
    },
    {
        'name': '适情雅趣',
        'alt_names': [],
        'type': '古谱残局',
        'class': '象棋谱大全-古谱残局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱残局/适情雅趣',
        ],
    },
    {
        'name': '渊深海阔',
        'alt_names': [],
        'type': '古谱残局',
        'class': '象棋谱大全-古谱残局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱残局/渊深海阔',
        ],
    },
    {
        'name': '心武残编',
        'alt_names': [],
        'type': '古谱残局',
        'class': '象棋谱大全-古谱残局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱残局/心武残编',
        ],
    },
    {
        'name': '梦入神机',
        'alt_names': [],
        'type': '古谱残局',
        'class': '象棋谱大全-古谱残局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱残局/梦入神机',
        ],
    },
    {
        'name': '竹香斋',
        'alt_names': [],
        'type': '古谱残局',
        'class': '象棋谱大全-古谱残局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱残局/竹香斋',
        ],
    },
    {
        'name': '韬略元机',
        'alt_names': [],
        'type': '古谱残局',
        'class': '象棋谱大全-古谱残局',
        'share_paths': [
            'chess_象棋谱大全/象棋谱大全-古谱残局/韬略元机',
        ],
    },
]


def _extract_subdir_links(html: str, base_path: str) -> List[str]:
    """
    从目录页面提取子目录链接（卷一、卷二...等分卷结构）。

    东萍网站古谱页面结构：
      手册目录页 → 各卷子目录 → 每卷内的具体棋谱列表
    """
    import re as _re
    from urllib.parse import unquote as _unq

    # 在 /hldcg/share/ 下的子路径链接
    pattern = _re.compile(r'href="(/hldcg/share/[^"]+/)"')
    subdirs = []
    base_prefix = '/hldcg/share/' + base_path.rstrip('/')

    for m in pattern.finditer(html):
        href = _unq(m.group(1))
        # 只保留比当前目录深一级的子目录
        if href.startswith(base_prefix) and href != base_prefix + '/':
            sub = href[len(base_prefix):].strip('/')
            # 排除棋谱列表路径（棋谱列表、按编号等）
            skip_keywords = ['棋谱列表', '按编号', '按价值', '按日期', '按人气', '按修改']
            if sub and not any(k in sub for k in skip_keywords):
                subdirs.append(href)

    return list(dict.fromkeys(subdirs))  # 去重保序


def discover_via_share_pages(manual: Dict, max_pages: int = 20) -> List[int]:
    """
    策略1：通过共享目录页面发现所有棋谱 ID。

    东萍网站目录结构（两层）：
      /chess_象棋谱大全/象棋谱大全-古谱残局/适情雅趣/
        → 卷一/    卷二/    卷三/  ...
            → view_u_XXXXX.html 列表
    """
    all_ids: List[int] = []
    seen: set = set()

    for path in manual['share_paths']:
        logger.info(f"[发现] 尝试目录: {path}")

        # 第一层：获取手册主目录页（含各卷子目录）
        html = fetch_share_page(path, page=1)
        if not html:
            logger.warning(f"  主目录获取失败，跳过路径: {path}")
            continue

        # 提取子目录（卷一、卷二...）
        subdirs = _extract_subdir_links(html, path)
        logger.info(f"  发现 {len(subdirs)} 个子目录")

        if subdirs:
            # 第二层：逐个获取每个子目录（卷）的棋谱列表
            for subdir in subdirs:
                # subdir 格式：/hldcg/share/chess_xxx/xxx/手册名/卷一/
                sub_path = subdir.replace('/hldcg/share/', '').strip('/')
                logger.info(f"  扫描子目录: {sub_path}")

                for page in range(1, max_pages + 1):
                    sub_html = fetch_share_page(sub_path, page=page)
                    if not sub_html:
                        break

                    ids = extract_game_ids_from_html(sub_html)
                    new_ids = [i for i in ids if i not in seen]
                    if not new_ids:
                        break

                    logger.info(f"    第{page}页: {len(new_ids)} 个新 ID")
                    seen.update(new_ids)
                    all_ids.extend(new_ids)

                    if len(ids) < 5:  # 最后一页
                        break
        else:
            # 无子目录：直接从主目录页提取 ID（单层结构）
            ids = extract_game_ids_from_html(html)
            new_ids = [i for i in ids if i not in seen]
            if new_ids:
                logger.info(f"  直接发现 {len(new_ids)} 个 ID（单层目录）")
                seen.update(new_ids)
                all_ids.extend(new_ids)

            # 翻页
            for page in range(2, max_pages + 1):
                pg_html = fetch_share_page(path, page=page)
                if not pg_html:
                    break
                ids = extract_game_ids_from_html(pg_html)
                new_ids = [i for i in ids if i not in seen]
                if not new_ids:
                    break
                seen.update(new_ids)
                all_ids.extend(new_ids)

        if all_ids:
            break  # 第一个有效路径已找到足够数据

    return all_ids


def discover_via_search_api(manual: Dict, max_pages: int = 30) -> List[int]:
    """
    策略2：通过搜索接口发现棋谱 ID。
    使用 /hldcg/search/?s=list.asp?owner=u&event=XXX 分页接口。
    """
    from urllib.parse import quote

    event_name = manual['name']
    all_ids: List[int] = []
    seen: set = set()

    for page in range(1, max_pages + 1):
        if page == 1:
            url = (
                f"{BASE_URL}/hldcg/search/"
                f"?s=list.asp%3Fowner%3Du%26event%3D{quote(event_name)}"
            )
        else:
            url = (
                f"{BASE_URL}/hldcg/search/"
                f"?s=list.asp%3Fowner%3Du%26event%3D{quote(event_name)}"
                f"%26page%3D{page}"
            )

        html = fetch_url(url)
        if not html:
            logger.warning(f"[搜索API] 第{page}页失败")
            break

        ids = extract_game_ids_from_html(html)
        new_ids = [i for i in ids if i not in seen]

        if not new_ids:
            logger.info(f"[搜索API] 第{page}页无新 ID，完成")
            break

        logger.info(f"[搜索API] 第{page}页: {len(new_ids)} 个新 ID")
        seen.update(new_ids)
        all_ids.extend(new_ids)

    return all_ids


def discover_via_id_scan(
    manual: Dict,
    start_id: int,
    end_id: int,
    max_consecutive_misses: int = 20,
) -> List[int]:
    """
    策略3：顺序扫描 ID 范围，通过页面内容验证是否属于目标古谱。
    
    这是最慢但最可靠的方法，在前两种策略失败时使用。
    每次请求后会写入缓存，下次运行不会重复请求。
    """
    target_events = {manual['name']} | set(manual.get('alt_names', []))
    found_ids: List[int] = []
    consecutive_misses = 0
    in_range = False  # 是否已进入该古谱的 ID 范围

    logger.info(f"[ID扫描] 范围 {start_id}-{end_id}，目标: {target_events}")

    for gid in range(start_id, end_id + 1):
        html = fetch_game_page(gid)
        if not html:
            consecutive_misses += 1
            if consecutive_misses >= max_consecutive_misses and in_range:
                logger.info(f"  连续{max_consecutive_misses}次未命中，停止扫描")
                break
            continue

        # 检查是否属于目标古谱
        block = parse_dhtmlxq_block(html)
        if not block:
            consecutive_misses += 1
            continue

        fields = parse_fields(block)
        event = fields.get('event', '')
        owner = fields.get('owner', '')

        # 检查 event 名是否匹配
        is_match = any(t in event for t in target_events)
        if not is_match and owner:
            is_match = any(t in owner for t in target_events)

        if is_match:
            found_ids.append(gid)
            consecutive_misses = 0
            in_range = True
            logger.info(f"  ✓ {gid}: {fields.get('title', '?')} [{event}]")
        else:
            consecutive_misses += 1
            if in_range and consecutive_misses >= max_consecutive_misses:
                logger.info(f"  已离开范围（{max_consecutive_misses}次未命中），结束")
                break

    return found_ids


def discover_manual(manual: Dict, scan_range: Optional[Tuple[int, int]] = None) -> List[int]:
    """
    综合发现策略：依次尝试各种方法，返回第一个成功结果。

    参数：
        manual     - 古谱配置字典
        scan_range - ID 扫描范围 (start, end)，仅策略3使用
    """
    name = manual['name']
    logger.info(f"\n{'='*50}")
    logger.info(f"开始发现: 《{name}》")

    # 策略1：共享目录页面
    ids = discover_via_share_pages(manual)
    if ids:
        logger.info(f"[策略1成功] 发现 {len(ids)} 局")
        return sorted(set(ids))

    logger.info(f"策略1失败，尝试策略2...")

    # 策略2：搜索 API
    ids = discover_via_search_api(manual)
    if ids:
        logger.info(f"[策略2成功] 发现 {len(ids)} 局")
        return sorted(set(ids))

    logger.info(f"策略2失败，尝试策略3（ID扫描）...")

    # 策略3：顺序 ID 扫描（需要提供范围）
    if scan_range:
        ids = discover_via_id_scan(manual, scan_range[0], scan_range[1])
        if ids:
            logger.info(f"[策略3成功] 发现 {len(ids)} 局")
            return sorted(set(ids))

    logger.warning(f"《{name}》所有发现策略均失败")
    return []


# 已知 ID 范围（基于网站数据，可根据实际情况调整）
# 这些范围通过人工抽样确定，覆盖主要古谱
KNOWN_ID_RANGES = {
    '适情雅趣':   (42800, 43000),
    '橘中秘':     (43000, 44500),
    '竹香斋':     (44000, 46000),
    '渊深海阔':   (44000, 50000),
    '韬略元机':   (44000, 50000),
    '心武残编':   (44000, 50000),
    '梦入神机':   (44000, 50000),
    '自出洞来无敌手': (40000, 45000),
    '梅花谱':     (40000, 50000),
    '反梅花谱':   (40000, 50000),
}


if __name__ == '__main__':
    import sys
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s %(levelname)s %(message)s',
        datefmt='%H:%M:%S',
    )

    target = sys.argv[1] if len(sys.argv) > 1 else '适情雅趣'
    manual = next((m for m in MANUALS if m['name'] == target), None)
    if not manual:
        print(f"未找到古谱: {target}")
        sys.exit(1)

    scan_range = KNOWN_ID_RANGES.get(target)
    ids = discover_manual(manual, scan_range=scan_range)
    print(f"\n发现 {len(ids)} 局: {ids[:10]}{'...' if len(ids) > 10 else ''}")
