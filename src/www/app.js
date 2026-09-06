/* app.js — Application Bootstrap and State Management
 * 
 * This module provides:
 *   - Application initialization
 *   - State persistence
 *   - Theme management
 *   - Keyboard shortcuts
 *   - Cross-cutting application logic
 */
(function() {
  'use strict';

  /* ========================================================================
   * Application Configuration
   * ======================================================================== */
  const CONFIG = {
    appName: 'No Home Work',
    version: '2.1.0',
    defaultFormat: 'A4',
    defaultZoom: 1,
    storageKey: 'noHomeWorkSettings',
    themeKey: 'noHomeWorkTheme'
  };

  /* ========================================================================
   * State Management
   * ======================================================================== */
  const AppState = {
    // Get stored state from localStorage
    get: function() {
      try {
        const stored = localStorage.getItem(CONFIG.storageKey);
        return stored ? JSON.parse(stored) : {};
      } catch (e) {
        console.warn('Failed to read state:', e);
        return {};
      }
    },

    // Save state to localStorage
    set: function(state) {
      try {
        localStorage.setItem(CONFIG.storageKey, JSON.stringify(state));
      } catch (e) {
        console.warn('Failed to save state:', e);
      }
    },

    // Get theme preference
    getTheme: function() {
      try {
        return localStorage.getItem(CONFIG.themeKey) || 'system';
      } catch (e) {
        return 'system';
      }
    },

    // Set theme preference
    setTheme: function(theme) {
      try {
        localStorage.setItem(CONFIG.themeKey, theme);
      } catch (e) {
        console.warn('Failed to save theme:', e);
      }
    }
  };

  /* ========================================================================
   * Theme Management
   * ======================================================================== */
  const ThemeManager = {
    // Available themes
    themes: {
      light: {
        name: 'Light',
        bg: 'bg-zinc-100',
        paper: 'bg-white',
        toolbar: 'bg-zinc-900',
        text: 'text-zinc-900',
        secondary: 'text-zinc-600'
      },
      dark: {
        name: 'Dark',
        bg: 'bg-zinc-900',
        paper: 'bg-zinc-100',
        toolbar: 'bg-zinc-800',
        text: 'text-zinc-100',
        secondary: 'text-zinc-400'
      },
      sepia: {
        name: 'Sepia',
        bg: 'bg-amber-50',
        paper: 'bg-amber-100',
        toolbar: 'bg-amber-900',
        text: 'text-amber-900',
        secondary: 'text-amber-700'
      }
    },

    // Apply theme to document
    apply: function(themeName) {
      const theme = this.themes[themeName] || this.themes.light;
      const html = document.documentElement;
      
      // Remove all theme classes
      Object.values(this.themes).forEach(t => {
        ['bg', 'paper', 'toolbar', 'text', 'secondary'].forEach(key => {
          if (t[key]) {
            html.classList.remove(t[key]);
          }
        });
      });

      // Apply new theme classes
      html.classList.add(theme.bg, theme.text);
      
      // Update paper background
      const paper = document.getElementById('paper');
      if (paper) {
        paper.className = paper.className.replace(/bg-\w+-\d+/g, '').trim();
        paper.classList.add(theme.paper);
      }

      // Update toolbar
      const toolbar = document.querySelector('[class*="top-0"]');
      if (toolbar) {
        toolbar.className = toolbar.className.replace(/bg-\w+-\d+/g, '').trim();
        toolbar.classList.add(theme.toolbar);
      }

      // Store theme preference
      AppState.setTheme(themeName);
    },

    // Get current theme
    getCurrent: function() {
      const stored = AppState.getTheme();
      if (stored === 'system') {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      }
      return stored;
    },

    // Toggle between themes
    toggle: function() {
      const current = this.getCurrent();
      const themes = Object.keys(this.themes);
      const currentIndex = themes.indexOf(current);
      const nextIndex = (currentIndex + 1) % themes.length;
      this.apply(themes[nextIndex]);
      return themes[nextIndex];
    },

    // Set specific theme
    set: function(themeName) {
      if (this.themes[themeName]) {
        this.apply(themeName);
        return true;
      }
      return false;
    }
  };

  /* ========================================================================
   * Keyboard Shortcuts
   * ======================================================================== */
  const Keyboard = {
    bindings: {
      '+': () => { if (window.Paper) window.Paper.zoomIn(true); },
      '-': () => { if (window.Paper) window.Paper.zoomOut(true); },
      '=': () => { if (window.Paper) window.Paper.zoomIn(true); },
      '_': () => { if (window.Paper) window.Paper.zoomOut(true); },
      '0': () => { if (window.Paper) window.Paper.reset(true); },
      '1': () => { if (window.Paper) window.Paper.setFormat('A4'); },
      '2': () => { if (window.Paper) window.Paper.setFormat('A5'); },
      '3': () => { if (window.Paper) window.Paper.setFormat('A3'); },
      '4': () => { if (window.Paper) window.Paper.setFormat('Letter'); },
      'r': () => { if (window.Paper) window.Paper.reset(true); },
      't': () => { ThemeManager.toggle(); },
      'f': () => { this.toggleFullscreen(); }
    },

    // Handle keydown events
    handleKey: function(e) {
      // Ignore if typing in an input
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) {
        return;
      }

      const key = e.key.toLowerCase();
      const binding = this.bindings[key];
      
      if (binding) {
        e.preventDefault();
        binding();
      }
    },

    // Toggle fullscreen
    toggleFullscreen: function() {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(e => {
          console.warn('Fullscreen error:', e);
        });
      } else {
        document.exitFullscreen();
      }
    },

    // Initialize keyboard listeners
    init: function() {
      document.addEventListener('keydown', (e) => this.handleKey(e));
    }
  };

  /* ========================================================================
   * UI Enhancements
   * ======================================================================== */
  const UI = {
    // Initialize tooltip system
    initTooltips: function() {
      const elements = document.querySelectorAll('[title]');
      elements.forEach(el => {
        el.addEventListener('mouseenter', () => {
          // Could implement custom tooltip here
        });
      });
    },

    // Add loading indicator
    showLoading: function() {
      const loading = document.createElement('div');
      loading.id = 'loadingIndicator';
      loading.className = 'fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50';
      loading.innerHTML = '<div class="animate-spin rounded-full h-12 w-12 border-b-2 border-white"></div>';
      document.body.appendChild(loading);
    },

    hideLoading: function() {
      const loading = document.getElementById('loadingIndicator');
      if (loading) loading.remove();
    },

    // Show toast notification
    showToast: function(message, duration = 3000) {
      const existing = document.getElementById('toastNotification');
      if (existing) existing.remove();

      const toast = document.createElement('div');
      toast.id = 'toastNotification';
      toast.className = 'fixed bottom-4 left-1/2 transform -translate-x-1/2 bg-zinc-900 text-white px-4 py-2 rounded shadow-lg z-50 animate-fade-in';
      toast.textContent = message;
      document.body.appendChild(toast);

      setTimeout(() => {
        toast.classList.add('animate-fade-out');
        setTimeout(() => toast.remove(), 300);
      }, duration);
    },

    // Initialize all UI components
    init: function() {
      this.initTooltips();
      
      // Add CSS animations if not present
      const style = document.createElement('style');
      style.textContent = `
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        @keyframes fadeOut {
          from { opacity: 1; transform: translateY(0); }
          to { opacity: 0; transform: translateY(10px); }
        }
        .animate-fade-in { animation: fadeIn 0.3s ease-out; }
        .animate-fade-out { animation: fadeOut 0.3s ease-out; }
        .animate-spin {
          animation: spin 1s linear infinite;
        }
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `;
      document.head.appendChild(style);
    }
  };

  /* ========================================================================
   * Auto-save State
   * ======================================================================== */
  function saveState() {
    if (window.Paper) {
      const state = window.Paper.getState();
      AppState.set({
        format: state.format,
        scale: state.scale,
        tx: state.tx,
        ty: state.ty,
        lastUpdated: Date.now()
      });
    }
  }

  function loadState() {
    const saved = AppState.get();
    if (saved && saved.format && window.Paper) {
      // Apply saved format after a short delay to allow Paper.js to initialize
      setTimeout(() => {
        window.Paper.setFormat(saved.format);
        if (saved.scale) {
          window.Paper.zoomTo(saved.scale);
        }
      }, 100);
    }
  }

  /* ========================================================================
   * Health Check and Error Reporting
   * ======================================================================== */
  function checkWebGL() {
    try {
      const canvas = document.createElement('canvas');
      const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
      return !!gl;
    } catch (e) {
      return false;
    }
  }

  function checkTouch() {
    return 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  }

  function logEnvironment() {
    console.log('=== No Home Work - Environment ===');
    console.log('Version:', CONFIG.version);
    console.log('User Agent:', navigator.userAgent);
    console.log('Screen:', `${window.screen.width}x${window.screen.height}`);
    console.log('WebGL Support:', checkWebGL() ? 'Yes' : 'No');
    console.log('Touch Support:', checkTouch() ? 'Yes' : 'No');
    console.log('===================================');
  }

  /* ========================================================================
   * Initialization
   * ======================================================================== */
  function init() {
    // Mark initialization start
    const startTime = Date.now();
    
    // Log environment info
    logEnvironment();
    
    // Initialize UI
    UI.init();
    
    // Initialize keyboard shortcuts
    Keyboard.init();
    
    // Apply theme
    ThemeManager.apply(ThemeManager.getCurrent());
    
    // Load saved state
    loadState();
    
    // Set up auto-save on window close
    window.addEventListener('beforeunload', saveState);
    
    // Set up periodic auto-save
    setInterval(saveState, 30000);
    
    // Hide loading indicator after a delay
    setTimeout(() => UI.hideLoading(), 500);
    
    // Mark as fully initialized
    document.documentElement.dataset.ready = 'true';
    document.documentElement.dataset.initialized = 'true';
    
    console.log(`App initialized in ${Date.now() - startTime}ms`);
  }

  /* ========================================================================
   * Auto-initialize
   * ======================================================================== */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  // Export for debugging
  window.NoHomeWorkApp = {
    state: AppState,
    theme: ThemeManager,
    keyboard: Keyboard,
    ui: UI,
    config: CONFIG,
    saveState,
    loadState
  };
})();
