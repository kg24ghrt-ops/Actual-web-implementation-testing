#!/usr/bin/env python3
"""
Lightweight Documentation & Web Search Tool
Allows fetching documentation, web content, images, and searching via various methods.
Stores everything locally for quick access.
"""

import os
import sys
import json
import hashlib
import subprocess
import re
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse, quote_plus
import requests
from bs4 import BeautifulSoup

# Configuration
BASE_DIR = Path(__file__).parent / ".doc_tool_storage"
CACHE_DIR = BASE_DIR / "cache"
DOCS_DIR = BASE_DIR / "docs"
IMAGES_DIR = BASE_DIR / "images"
SEARCH_HISTORY_FILE = BASE_DIR / "search_history.json"
INDEX_FILE = BASE_DIR / "content_index.json"

# Ensure directories exist
for d in [CACHE_DIR, DOCS_DIR, IMAGES_DIR]:
    d.mkdir(parents=True, exist_ok=True)

def log_action(action: str, details: str):
    """Log actions to a file for tracking."""
    log_file = BASE_DIR / "activity.log"
    timestamp = datetime.now().isoformat()
    with open(log_file, "a") as f:
        f.write(f"[{timestamp}] {action}: {details}\n")

def generate_hash(content: str) -> str:
    """Generate a unique hash for content."""
    return hashlib.md5(content.encode()).hexdigest()

def save_content(url: str, content: str, content_type: str = "text") -> str:
    """Save content locally and return the file path."""
    url_hash = generate_hash(url)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    
    if content_type == "image":
        ext = "jpg"  # Default, could be improved by checking content-type
        filename = f"{timestamp}_{url_hash}.{ext}"
        filepath = IMAGES_DIR / filename
        with open(filepath, "wb") as f:
            f.write(content)
    else:
        filename = f"{timestamp}_{url_hash}.txt"
        filepath = DOCS_DIR / filename
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
    
    # Update index
    update_index(url, str(filepath), content_type)
    return str(filepath)

def update_index(url: str, filepath: str, content_type: str):
    """Update the content index."""
    index = {}
    if INDEX_FILE.exists():
        with open(INDEX_FILE, "r") as f:
            index = json.load(f)
    
    index[url] = {
        "filepath": filepath,
        "type": content_type,
        "added": datetime.now().isoformat()
    }
    
    with open(INDEX_FILE, "w") as f:
        json.dump(index, f, indent=2)

def fetch_url(url: str, save: bool = True) -> dict:
    """Fetch content from a URL."""
    try:
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }
        response = requests.get(url, headers=headers, timeout=30)
        response.raise_for_status()
        
        content_type = response.headers.get("Content-Type", "")
        
        result = {
            "url": url,
            "status": response.status_code,
            "content_type": content_type,
            "length": len(response.content)
        }
        
        if "image" in content_type:
            result["local_path"] = save_content(url, response.content, "image") if save else None
            result["message"] = f"Image saved to {result['local_path']}"
        else:
            text_content = response.text
            result["content"] = text_content[:10000]  # Limit content preview
            result["full_length"] = len(text_content)
            if save:
                result["local_path"] = save_content(url, text_content, "text")
                result["message"] = f"Content saved to {result['local_path']}"
            else:
                result["message"] = "Content fetched (not saved)"
        
        log_action("fetch_url", f"URL: {url}, Status: {response.status_code}")
        return result
        
    except Exception as e:
        error_msg = str(e)
        log_action("fetch_url_error", f"URL: {url}, Error: {error_msg}")
        return {"url": url, "error": error_msg}

def search_google(query: str, num_results: int = 5) -> list:
    """Search Google using curl/wget and parse results."""
    # Using DuckDuckGo HTML interface as it's more accessible without API keys
    search_url = f"https://html.duckduckgo.com/html/?q={quote_plus(query)}"
    
    try:
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }
        response = requests.get(search_url, headers=headers, timeout=30)
        response.raise_for_status()
        
        soup = BeautifulSoup(response.text, 'lxml')
        results = []
        
        for result in soup.select('.result')[:num_results]:
            title_elem = result.select_one('.result__title')
            snippet_elem = result.select_one('.result__snippet')
            url_elem = result.select_one('.result__url')
            
            if title_elem:
                title = title_elem.get_text(strip=True)
                snippet = snippet_elem.get_text(strip=True) if snippet_elem else ""
                url = url_elem.get_text(strip=True) if url_elem else ""
                
                results.append({
                    "title": title,
                    "snippet": snippet,
                    "url": url
                })
        
        log_action("search_google", f"Query: {query}, Results: {len(results)}")
        return results
        
    except Exception as e:
        log_action("search_google_error", f"Query: {query}, Error: {str(e)}")
        return [{"error": str(e)}]

def search_local(query: str) -> list:
    """Search through locally stored documentation."""
    results = []
    query_lower = query.lower()
    
    if not INDEX_FILE.exists():
        return results
    
    with open(INDEX_FILE, "r") as f:
        index = json.load(f)
    
    for url, info in index.items():
        filepath = info.get("filepath", "")
        if Path(filepath).exists() and info.get("type") == "text":
            try:
                with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                
                if query_lower in content.lower():
                    # Find context around matches
                    lines = content.split('\n')
                    matching_lines = []
                    for i, line in enumerate(lines):
                        if query_lower in line.lower():
                            start = max(0, i - 2)
                            end = min(len(lines), i + 3)
                            matching_lines.extend(lines[start:end])
                    
                    results.append({
                        "url": url,
                        "filepath": filepath,
                        "matches": len(matching_lines),
                        "preview": '\n'.join(matching_lines[:5])
                    })
            except Exception:
                continue
    
    log_action("search_local", f"Query: {query}, Results: {len(results)}")
    return results

def fetch_documentation(project: str, version: str = "latest") -> dict:
    """Fetch documentation for popular projects."""
    doc_urls = {
        "python": f"https://docs.python.org/{version}/contents.html",
        "requests": f"https://requests.readthedocs.io/en/latest/",
        "flask": f"https://flask.palletsprojects.com/en/{version}/",
        "django": f"https://docs.djangoproject.com/en/{version}/",
        "numpy": f"https://numpy.org/doc/{version}/",
        "pandas": f"https://pandas.pydata.org/docs/",
    }
    
    url = doc_urls.get(project.lower())
    if not url:
        return {"error": f"Documentation URL not found for project: {project}"}
    
    return fetch_url(url)

def execute_command(cmd: str) -> dict:
    """Execute a shell command safely."""
    allowed_commands = ["curl", "wget", "cat", "ls", "grep", "find", "head", "tail"]
    
    parts = cmd.split()
    if not parts or parts[0] not in allowed_commands:
        return {"error": f"Command not allowed. Allowed: {', '.join(allowed_commands)}"}
    
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            timeout=60,
            cwd=str(BASE_DIR)
        )
        
        output = {
            "command": cmd,
            "returncode": result.returncode,
            "stdout": result.stdout[:5000],  # Limit output
            "stderr": result.stderr[:1000]
        }
        
        log_action("execute_command", f"Cmd: {cmd}, Return: {result.returncode}")
        return output
        
    except subprocess.TimeoutExpired:
        return {"error": "Command timed out"}
    except Exception as e:
        return {"error": str(e)}

def list_stored_content() -> dict:
    """List all stored content."""
    index = {}
    if INDEX_FILE.exists():
        with open(INDEX_FILE, "r") as f:
            index = json.load(f)
    
    stats = {
        "total_items": len(index),
        "text_files": sum(1 for v in index.values() if v.get("type") == "text"),
        "images": sum(1 for v in index.values() if v.get("type") == "image"),
        "storage_dir": str(BASE_DIR),
        "recent_items": list(index.items())[-10:]  # Last 10 items
    }
    
    return stats

def main():
    """Main CLI interface."""
    if len(sys.argv) < 2:
        print("""
DocTool - Lightweight Documentation & Search Tool

Usage:
  python doctool.py fetch <url>           - Fetch and save content from URL
  python doctool.py search <query>        - Search Google/DuckDuckGo
  python doctool.py local <query>         - Search local documentation
  python doctool.py docs <project> [ver]  - Fetch project documentation
  python doctool.py cmd <command>         - Execute allowed shell command
  python doctool.py list                  - List stored content
  python doctool.py help                  - Show this help

Examples:
  python doctool.py fetch https://example.com
  python doctool.py search "python async await"
  python doctool.py local "async"
  python doctool.py docs python 3.11
  python doctool.py cmd "curl -I https://example.com"
  python doctool.py list
""")
        return
    
    action = sys.argv[1].lower()
    
    if action == "fetch" and len(sys.argv) > 2:
        url = sys.argv[2]
        result = fetch_url(url)
        print(json.dumps(result, indent=2))
    
    elif action == "search" and len(sys.argv) > 2:
        query = " ".join(sys.argv[2:])
        results = search_google(query)
        print(json.dumps(results, indent=2))
    
    elif action == "local" and len(sys.argv) > 2:
        query = " ".join(sys.argv[2:])
        results = search_local(query)
        print(json.dumps(results, indent=2))
    
    elif action == "docs" and len(sys.argv) > 2:
        project = sys.argv[2]
        version = sys.argv[3] if len(sys.argv) > 3 else "latest"
        result = fetch_documentation(project, version)
        print(json.dumps(result, indent=2))
    
    elif action == "cmd" and len(sys.argv) > 2:
        cmd = " ".join(sys.argv[2:])
        result = execute_command(cmd)
        print(json.dumps(result, indent=2))
    
    elif action == "list":
        result = list_stored_content()
        print(json.dumps(result, indent=2))
    
    elif action in ["help", "-h", "--help"]:
        print("""
DocTool - Lightweight Documentation & Search Tool

Usage:
  python doctool.py fetch <url>           - Fetch and save content from URL
  python doctool.py search <query>        - Search Google/DuckDuckGo
  python doctool.py local <query>         - Search local documentation
  python doctool.py docs <project> [ver]  - Fetch project documentation
  python doctool.py cmd <command>         - Execute allowed shell command
  python doctool.py list                  - List stored content
  python doctool.py help                  - Show this help

Examples:
  python doctool.py fetch https://example.com
  python doctool.py search "python async await"
  python doctool.py local "async"
  python doctool.py docs python 3.11
  python doctool.py cmd "curl -I https://example.com"
  python doctool.py list
""")
    
    else:
        print(f"Unknown action or missing arguments: {action}")
        print("Use 'python doctool.py help' for usage information.")

if __name__ == "__main__":
    main()
