from flask import Flask, abort, Response, send_from_directory
import os
import json
import requests
import threading
import time
import mimetypes
import re

app = Flask(__name__)

CONFIG_FILE = os.path.join(os.path.dirname(__file__), 'sources.json')
FILES_FOLDER = os.path.join(os.path.dirname(__file__), 'files')

# Store cached subtitles for each file
cached_content = {}

def load_config():
    """Load the sources configuration file."""
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}

def fetch_type_a(url):
    """
    Type A: TuneIn format
    Fetch subtitle from a JSON endpoint (Header.Subtitle).
    """
    try:
        response = requests.get(url, timeout=5)
        response.raise_for_status()
        data = response.json()
        # Extract subtitle from Header.Subtitle
        return data.get('Header', {}).get('Subtitle', '')
    except Exception as e:
        print(f"[Type A] Error fetching from {url}: {e}")
        return None

def fetch_type_b(url):
    """
    Type B: SSE format with JSON data field
    Fetches SSE stream and extracts the title from the JSON data field.
    Expected format:
        id: 1234567890
        data: {"title":"Artist - Song", ...}
    """
    try:
        # Use streaming to handle SSE format
        response = requests.get(url, timeout=10, stream=True, headers={
            'Accept': 'text/event-stream',
            'Cache-Control': 'no-cache'
        })
        response.raise_for_status()
        
        # Read the response content
        content = response.text
        
        # Parse SSE format - look for data: lines
        data_match = re.search(r'^data:\s*(.+)$', content, re.MULTILINE)
        if data_match:
            data_str = data_match.group(1).strip()
            try:
                data = json.loads(data_str)
                title = data.get('title', '')
                return title
            except json.JSONDecodeError as e:
                print(f"[Type B] Error parsing JSON data: {e}")
                return None
        else:
            print(f"[Type B] No data field found in SSE response from {url}")
            return None
    except Exception as e:
        print(f"[Type B] Error fetching from {url}: {e}")
        return None

def fetch_content(source_config):
    """
    Fetch content based on source type.
    source_config can be:
    - A string (URL) for backwards compatibility (defaults to Type A)
    - A dict with 'type' and 'url' keys
    """
    if isinstance(source_config, str):
        # Backwards compatibility: plain URL defaults to Type A
        return fetch_type_a(source_config)
    elif isinstance(source_config, dict):
        source_type = source_config.get('type', 'A').upper()
        url = source_config.get('url', '')
        
        if not url:
            print(f"No URL specified in source config")
            return None
        
        if source_type == 'A':
            return fetch_type_a(url)
        elif source_type == 'B':
            return fetch_type_b(url)
        else:
            print(f"Unknown source type: {source_type}")
            return None
    else:
        print(f"Invalid source config format")
        return None

def update_all_sources():
    """Fetch and update all configured sources."""
    config = load_config()
    for filename, source_config in config.items():
        content = fetch_content(source_config)
        if content is not None:
            cached_content[filename] = content
            # Also save to file
            filepath = os.path.join(FILES_FOLDER, filename)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"Updated {filename}: {content}")

def background_updater():
    """Background thread that updates sources every 10 seconds."""
    while True:
        update_all_sources()
        time.sleep(10)

@app.route('/<filename>')
def serve_file(filename):
    config = load_config()
    
    # Check if this file is configured for JSON fetching
    if filename in config:
        # This is a text file updated from JSON source
        if filename in cached_content:
            return Response(cached_content[filename], mimetype='text/plain')
        
        # Fallback to file on disk if cached content not available yet
        filepath = os.path.join(FILES_FOLDER, filename)
        if os.path.exists(filepath):
            with open(filepath, 'r', encoding='utf-8') as f:
                return Response(f.read(), mimetype='text/plain')
    else:
        # This is a static file (mp3, flac, zip, etc.) - serve directly
        filepath = os.path.join(FILES_FOLDER, filename)
        if os.path.exists(filepath):
            return send_from_directory(FILES_FOLDER, filename)
    
    abort(404)

if __name__ == '__main__':
    # Create files folder if it doesn't exist
    os.makedirs(FILES_FOLDER, exist_ok=True)
    
    # Create example sources.json if it doesn't exist
    if not os.path.exists(CONFIG_FILE):
        example_config = {
            "example.txt": "https://example.com/api/station.json"
        }
        with open(CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(example_config, f, indent=2)
        print(f"Created example config at {CONFIG_FILE}")
    
    # Start background updater thread
    updater_thread = threading.Thread(target=background_updater, daemon=True)
    updater_thread.start()
    print("Background updater started (fetches every 10 seconds)")
    
    # Initial fetch
    update_all_sources()
    
    app.run(host='0.0.0.0', port=5004, debug=True, use_reloader=False)
