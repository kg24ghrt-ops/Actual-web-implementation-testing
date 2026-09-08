"""
Search Tool Module for Real-Time Web Search and Documentation Lookup

This module provides a simple interface to search the web using DuckDuckGo
for finding documentation, implementation assistance, and real-time data.
"""

from ddgs import DDGS
from typing import List, Dict, Optional


def search_web(query: str, max_results: int = 5) -> List[Dict[str, str]]:
    """
    Search the web for information related to the query.
    
    Args:
        query: The search query string
        max_results: Maximum number of results to return (default: 5)
    
    Returns:
        A list of dictionaries containing search results with keys:
        - 'title': Title of the result
        - 'href': URL of the result
        - 'body': Snippet/description of the result
    """
    try:
        ddgs = DDGS()
        results = list(ddgs.text(query, max_results=max_results))
        return results
    except Exception as e:
        print(f"Search error: {e}")
        return []


def find_documentation(topic: str, language: str = "python") -> List[Dict[str, str]]:
    """
    Search for official documentation on a specific topic.
    
    Args:
        topic: The topic to search documentation for
        language: Programming language (default: "python")
    
    Returns:
        List of search results related to documentation
    """
    query = f"{language} {topic} official documentation"
    return search_web(query, max_results=5)


def find_implementation_examples(task: str) -> List[Dict[str, str]]:
    """
    Search for code examples and implementation patterns.
    
    Args:
        task: Description of the task or problem to solve
    
    Returns:
        List of search results with implementation examples
    """
    query = f"{task} code example implementation"
    return search_web(query, max_results=5)


def get_latest_info(topic: str) -> List[Dict[str, str]]:
    """
    Get the latest information about a topic.
    
    Args:
        topic: The topic to get latest information about
    
    Returns:
        List of recent search results
    """
    query = f"{topic} 2025 2026 latest news updates"
    return search_web(query, max_results=5)


if __name__ == "__main__":
    # Example usage
    print("=== Testing Search Tool ===\n")
    
    # Test general search
    print("1. General Search Test:")
    results = search_web("Python async await best practices", max_results=3)
    for i, r in enumerate(results, 1):
        print(f"   {i}. {r['title']}")
        print(f"      {r['href']}\n")
    
    # Test documentation lookup
    print("2. Documentation Lookup Test:")
    results = find_documentation("decorators")
    for i, r in enumerate(results, 1):
        print(f"   {i}. {r['title']}")
        print(f"      {r['href']}\n")
    
    # Test implementation examples
    print("3. Implementation Examples Test:")
    results = find_implementation_examples("REST API authentication")
    for i, r in enumerate(results, 1):
        print(f"   {i}. {r['title']}")
        print(f"      {r['href']}\n")
    
    print("=== Search tool is ready to use! ===")
