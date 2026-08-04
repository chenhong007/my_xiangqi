import json
import sys
from pathlib import Path

sys.path.append('scraper')
from xqipu_scraper import XqipuScraper

sys.stdout.reconfigure(encoding='utf-8')

def main():
    with open('data/残局攻杀谱.json', 'r', encoding='utf-8') as f:
        data = json.load(f)
        book_url = data['source']
        local_games = {g['id']: g for g in data.get('games', [])}

    print(f'Book URL: {book_url}')
    scraper = XqipuScraper()
    game_list = scraper.get_book_games(book_url, '残局攻杀谱')

    missing = [g for g in game_list if g['uuid'] not in local_games]
    print(f'Missing games count: {len(missing)}')
    for m in missing:
        print(f"Missing: {m['title']} - {m['url']}")
        
        # Try to fetch it right now
        print(f"Attempting to fetch {m['title']}...")
        scraper.browser.start()
        try:
            game_data = scraper.fetch_game_data(m['uuid'])
            if game_data and game_data.get('iccs_moves'):
                print(f"SUCCESS: Fetched moves for {m['title']}")
            else:
                print(f"FAILED: No moves found for {m['title']}")
        except Exception as e:
            print(f"ERROR: {e}")
        finally:
            scraper.browser.stop()

if __name__ == '__main__':
    main()
