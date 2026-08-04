import json
import glob
from pathlib import Path

def is_legal(grid, fc, fr, tc, tr, piece):
    if fc == tc and fr == tr:
        return False, "Target is same as start"
    
    target = grid[tr][tc]
    
    # Determine piece type and side
    if piece in ['车', '马', '相', '仕', '帅', '炮', '兵']:
        is_red = True
    elif piece in ['車', '馬', '象', '士', '将', '砲', '卒']:
        is_red = False
    else:
        return False, f"Unknown piece {piece}"

    # Cannot capture own piece
    if target:
        target_is_red = target in ['车', '马', '相', '仕', '帅', '炮', '兵']
        if is_red == target_is_red:
            return False, "Cannot capture own piece"

    ptype = piece.replace('車','车').replace('馬','马').replace('象','相').replace('士','仕').replace('将','帅').replace('砲','炮').replace('卒','兵')

    dx = tc - fc
    dy = tr - fr
    adx = abs(dx)
    ady = abs(dy)

    if ptype == '车':
        if fc != tc and fr != tr: return False, "Rook must move straight"
        if fc == tc:
            step = 1 if dy > 0 else -1
            for r in range(fr + step, tr, step):
                if grid[r][fc]: return False, "Rook path blocked"
        else:
            step = 1 if dx > 0 else -1
            for c in range(fc + step, tc, step):
                if grid[fr][c]: return False, "Rook path blocked"
        return True, ""

    elif ptype == '马':
        if not ((adx == 1 and ady == 2) or (adx == 2 and ady == 1)):
            return False, "Knight must move in L-shape"
        if adx == 2:
            if grid[fr][fc + (1 if dx > 0 else -1)]: return False, "Knight leg blocked"
        else:
            if grid[fr + (1 if dy > 0 else -1)][fc]: return False, "Knight leg blocked"
        return True, ""

    elif ptype == '相':
        if adx != 2 or ady != 2: return False, "Elephant must move diagonally 2 steps"
        if is_red and tr < 5: return False, "Red elephant cannot cross river"
        if not is_red and tr > 4: return False, "Black elephant cannot cross river"
        if grid[fr + (1 if dy > 0 else -1)][fc + (1 if dx > 0 else -1)]:
            return False, "Elephant eye blocked"
        return True, ""

    elif ptype == '仕':
        if adx != 1 or ady != 1: return False, "Advisor must move diagonally 1 step"
        if tc < 3 or tc > 5: return False, "Advisor must stay in palace"
        if is_red and tr < 7: return False, "Advisor must stay in palace"
        if not is_red and tr > 2: return False, "Advisor must stay in palace"
        return True, ""

    elif ptype == '帅':
        if target and target.replace('将','帅') == '帅' and fc == tc:
            step = 1 if dy > 0 else -1
            clear = True
            for r in range(fr + step, tr, step):
                if grid[r][fc]:
                    clear = False
                    break
            if clear:
                return True, ""
        
        if adx + ady != 1: return False, "General must move 1 step straight"
        if tc < 3 or tc > 5: return False, "General must stay in palace"
        if is_red and tr < 7: return False, "General must stay in palace"
        if not is_red and tr > 2: return False, "General must stay in palace"
        return True, ""

    elif ptype == '炮':
        if fc != tc and fr != tr: return False, "Cannon must move straight"
        count = 0
        if fc == tc:
            step = 1 if dy > 0 else -1
            for r in range(fr + step, tr, step):
                if grid[r][fc]: count += 1
        else:
            step = 1 if dx > 0 else -1
            for c in range(fc + step, tc, step):
                if grid[fr][c]: count += 1
        
        if target:
            if count != 1: return False, f"Cannon capturing needs 1 screen, found {count}"
        else:
            if count != 0: return False, f"Cannon moving needs 0 screens, found {count}"
        return True, ""

    elif ptype == '兵':
        if adx + ady != 1: return False, "Pawn must move 1 step"
        if is_red:
            if dy > 0: return False, "Red pawn cannot move backward"
            if fr >= 5 and adx > 0: return False, "Red pawn cannot move horizontally before crossing river"
        else:
            if dy < 0: return False, "Black pawn cannot move backward"
            if fr <= 4 and adx > 0: return False, "Black pawn cannot move horizontally before crossing river"
        return True, ""

    return False, "Unknown rules"

def verify_all():
    files = glob.glob('../data/app/*.json')
    total_games = 0
    valid_games = 0
    invalid_games = 0
    errors = []

    for f in files:
        with open(f, encoding='utf-8') as file:
            data = json.load(file)
        
        for g in data.get('games', []):
            total_games += 1
            grid = [[None]*9 for _ in range(10)]
            
            init_board = g.get('init_board', [])
            for p in init_board:
                c, r = p['col'], p['row']
                if 0 <= c <= 8 and 0 <= r <= 9:
                    grid[r][c] = p['piece']
            
            game_valid = True
            for idx, m in enumerate(g.get('moves', [])):
                fc, fr = m['from']
                tc, tr = m['to']
                
                if not (0 <= fc <= 8 and 0 <= fr <= 9 and 0 <= tc <= 8 and 0 <= tr <= 9):
                    errors.append(f"File {Path(f).name} - Game {g.get('title')} step {idx+1}: Coordinates out of bounds ({fc},{fr})->({tc},{tr})")
                    game_valid = False
                    break

                piece = grid[fr][fc]
                if not piece:
                    errors.append(f"File {Path(f).name} - Game {g.get('title')} step {idx+1}: No piece at ({fc},{fr}) to move")
                    game_valid = False
                    break
                
                ok, msg = is_legal(grid, fc, fr, tc, tr, piece)
                if not ok:
                    errors.append(f"File {Path(f).name} - Game {g.get('title')} step {idx+1}: {piece} ({fc},{fr})->({tc},{tr}) Illegal: {msg}")
                    game_valid = False
                    break
                
                grid[tr][tc] = piece
                grid[fr][fc] = None
            
            if game_valid:
                valid_games += 1
            else:
                invalid_games += 1

    print("="*50)
    print(f"Total Games Checked: {total_games}")
    print(f"Valid Games: {valid_games}")
    print(f"Invalid Games: {invalid_games}")
    print("="*50)
    
    if errors:
        print("Sample errors (first 30):")
        for e in errors[:30]:
            print("  " + e)

if __name__ == '__main__':
    verify_all()