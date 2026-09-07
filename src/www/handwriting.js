/* handwriting.js - Handwritten Text on Notebook Paper
 * 
 * Uses Rough.js to render text with a handwritten appearance.
 * Features:
 *   - Click on any line to start typing
 *   - Text appears with handwritten/sketchy style
 *   - Supports multiple handwritten fonts
 *   - Text can be dragged to reposition
 *   - Text can be edited after placement
 */
(function() {
  'use strict';

  /* ========================================================================
   * Configuration
   * ======================================================================== */
  const CONFIG = {
    // Rough.js options
    roughness: 1.5,        // 0-5, higher = more rough/sketchy
    bowing: 0.8,          // 0-1, curve amount
    stroke: '#18181b',    // Text color
    strokeWidth: 1.5,     // Line thickness
    font: 'Caveat',       // Font family
    fontSize: 18,         // Base font size
    
    // Text positioning
    lineSpacing: 2,       // Extra spacing between lines of text
    marginLeft: 5,        // Extra margin from left edge
    
    // Interaction
    hitTestRadius: 15,    // Radius for clicking on text
    minTextWidth: 20,     // Minimum width to consider for hit test
    
    // Visual
    fillStyle: 'none',    // 'none' for outline, or a color for filled
    hachureGap: 3
  };

  /* ========================================================================
   * State
   * ======================================================================== */
  const state = {
    enabled: false,           // Handwriting mode enabled
    isTyping: false,         // Currently typing text
    activeText: null,        // Currently active text element
    textElements: [],        // All text elements on the paper
    canvas: null,            // Canvas element reference
    ctx: null,               // Canvas 2D context
    roughCanvas: null,       // Rough.js canvas
    currentX: 0,             // Current cursor X position
    currentY: 0,             // Current cursor Y position
    currentLineY: 0,         // Current line Y position (for snapping to lines)
    lineHeight: 25,          // Line height for text
    scale: 1,               // Current zoom scale
    offsetX: 0,             // Current pan offset X
    offsetY: 0              // Current pan offset Y
  };

  /* ========================================================================
   * DOM References
   * ======================================================================== */
  let paperEl, ruledLines, handwritingCanvas, paperContainer, viewport;

  /* ========================================================================
   * Text Element Class
   * ======================================================================== */
  class TextElement {
    constructor(text, x, y, options = {}) {
      this.text = text;
      this.x = x;
      this.y = y;
      this.options = { ...CONFIG, ...options };
      this.id = `text-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      this.width = 0;
      this.height = 0;
      this.roughNode = null;
      this.selected = false;
      this.editing = false;
      this.dragging = false;
      this.dragOffsetX = 0;
      this.dragOffsetY = 0;
    }

    render(rc) {
      if (!rc) return;
      
      // Remove existing node if present
      if (this.roughNode) {
        rc.remove(this.roughNode);
      }

      // Draw text with Rough.js
      this.roughNode = rc.text(
        this.x, 
        this.y,
        this.text,
        {
          font: this.options.font,
          fontSize: this.options.fontSize * (state.scale || 1),
          roughness: this.options.roughness,
          bowing: this.options.bowing,
          stroke: this.options.stroke,
          strokeWidth: this.options.strokeWidth * (state.scale || 1),
          fill: this.options.fillStyle,
          fillStyle: this.options.fillStyle,
          hachureGap: this.options.hachureGap,
          fillWeight: 0.5
        }
      );

      // Get dimensions (approximate)
      const testCtx = document.createElement('canvas').getContext('2d');
      testCtx.font = `${this.options.fontSize}px ${this.options.font}`;
      const metrics = testCtx.measureText(this.text);
      this.width = metrics.width + 10;
      this.height = this.options.fontSize + 10;
    }

    containsPoint(px, py) {
      const halfWidth = this.width / 2;
      const halfHeight = this.height / 2;
      const distX = Math.abs(px - this.x - halfWidth);
      const distY = Math.abs(py - this.y - halfHeight);
      return distX < halfWidth && distY < halfHeight;
    }

    toJSON() {
      return {
        text: this.text,
        x: this.x,
        y: this.y,
        options: this.options
      };
    }

    static fromJSON(data) {
      const te = new TextElement(data.text, data.x, data.y, data.options);
      return te;
    }
  }

  /* ========================================================================
   * Find Line Y Position
   * ======================================================================== */
  function findNearestLineY(y) {
    if (!paperEl || !window.Paper) return y;
    
    const format = window.Paper.getFormat();
    const dims = window.Paper.getDimensions(format);
    const lineGap = mmToPx(8); // 8mm standard
    const topM = mmToPx(30); // 30mm top margin
    const bottomM = mmToPx(16); // 16mm bottom margin
    
    // Calculate line positions
    const lines = [];
    let lineY = topM;
    while (lineY < dims.height - bottomM) {
      lines.push(lineY);
      lineY += lineGap;
    }
    
    // Find nearest line
    let nearestY = y;
    let minDist = Infinity;
    for (const line of lines) {
      const dist = Math.abs(y - line);
      if (dist < minDist) {
        minDist = dist;
        nearestY = line;
      }
    }
    
    return nearestY;
  }

  function mmToPx(mm) {
    return mm * (96 / 25.4);
  }

  /* ========================================================================
   * Initialize Canvas
   * ======================================================================== */
  function initCanvas() {
    if (!handwritingCanvas) return;
    
    // Set canvas size to match paper
    const paperRect = paperEl.getBoundingClientRect();
    handwritingCanvas.width = paperRect.width * window.devicePixelRatio;
    handwritingCanvas.height = paperRect.height * window.devicePixelRatio;
    handwritingCanvas.style.width = `${paperRect.width}px`;
    handwritingCanvas.style.height = `${paperRect.height}px`;
    
    state.ctx = handwritingCanvas.getContext('2d');
    state.ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    
    // Initialize Rough.js canvas
    state.roughCanvas = rough.canvas(handwritingCanvas);
    
    // Clear and re-render all text
    clearCanvas();
    renderAllText();
  }

  /* ========================================================================
   * Clear Canvas
   * ======================================================================== */
  function clearCanvas() {
    if (state.ctx && handwritingCanvas) {
      state.ctx.clearRect(0, 0, handwritingCanvas.width, handwritingCanvas.height);
    }
    if (state.roughCanvas) {
      state.roughCanvas.clear();
    }
  }

  /* ========================================================================
   * Render All Text
   * ======================================================================== */
  function renderAllText() {
    if (!state.roughCanvas) return;
    
    clearCanvas();
    for (const te of state.textElements) {
      te.render(state.roughCanvas);
    }
  }

  /* ========================================================================
   * Add New Text
   * ======================================================================== */
  function addText(text, x, y) {
    const te = new TextElement(text, x, y);
    state.textElements.push(te);
    te.render(state.roughCanvas);
    return te;
  }

  /* ========================================================================
   * Remove Text
   * ======================================================================== */
  function removeText(element) {
    const index = state.textElements.indexOf(element);
    if (index > -1) {
      state.textElements.splice(index, 1);
      if (element.roughNode) {
        state.roughCanvas.remove(element.roughNode);
      }
    }
  }

  /* ========================================================================
   * Handle Canvas Click
   * ======================================================================== */
  function onCanvasClick(e) {
    if (!state.enabled) return;
    
    // Get click position relative to paper
    const rect = handwritingCanvas.getBoundingClientRect();
    const x = (e.clientX - rect.left) / (state.scale || 1);
    const y = (e.clientY - rect.top) / (state.scale || 1);
    
    // Check if clicking on existing text
    const clickedElement = findTextAtPosition(x, y);
    
    if (clickedElement) {
      // Select existing text for editing
      selectText(clickedElement);
      startEditing(clickedElement, e);
    } else {
      // Find nearest line
      const lineY = findNearestLineY(y);
      
      // Start new text at this position
      startNewText(x, lineY);
    }
  }

  /* ========================================================================
   * Find Text at Position
   * ======================================================================== */
  function findTextAtPosition(x, y) {
    for (let i = state.textElements.length - 1; i >= 0; i--) {
      if (state.textElements[i].containsPoint(x, y)) {
        return state.textElements[i];
      }
    }
    return null;
  }

  /* ========================================================================
   * Select Text
   * ======================================================================== */
  function selectText(element) {
    // Deselect all
    for (const te of state.textElements) {
      te.selected = false;
    }
    
    element.selected = true;
    state.activeText = element;
  }

  /* ========================================================================
   * Start New Text
   * ======================================================================== */
  function startNewText(x, y) {
    state.isTyping = true;
    state.currentX = x;
    state.currentY = y;
    state.currentLineY = y;
    
    // Create a temporary input
    createTextInput(x, y);
  }

  /* ========================================================================
   * Start Editing Existing Text
   * ======================================================================== */
  function startEditing(element, e) {
    state.isTyping = true;
    state.activeText = element;
    element.editing = true;
    
    // Create input at element position
    createTextInput(element.x, element.y, element.text);
  }

  /* ========================================================================
   * Create Text Input
   * ======================================================================== */
  function createTextInput(x, y, initialText = '') {
    // Remove any existing input
    removeTextInput();
    
    const input = document.createElement('textarea');
    input.id = 'handwriting-input';
    input.value = initialText;
    input.style.position = 'absolute';
    input.style.left = `${x}px`;
    input.style.top = `${y}px`;
    input.style.fontFamily = CONFIG.font;
    input.style.fontSize = `${CONFIG.fontSize}px`;
    input.style.border = '2px solid #10b981';
    input.style.borderRadius = '4px';
    input.style.padding = '4px 8px';
    input.style.background = 'white';
    input.style.zIndex = '1000';
    input.style.minWidth = '100px';
    input.style.minHeight = '24px';
    input.style.resize = 'none';
    input.style.overflow = 'hidden';
    input.style.transform = `scale(${1 / (state.scale || 1)})`;
    input.style.transformOrigin = '0 0';
    
    // Position relative to paper
    const paperRect = paperEl.getBoundingClientRect();
    const viewportRect = viewport.getBoundingClientRect();
    input.style.left = `${x + paperRect.left - viewportRect.left}px`;
    input.style.top = `${y + paperRect.top - viewportRect.top}px`;
    
    document.body.appendChild(input);
    input.focus();
    input.select();
    
    // Handle input events
    input.addEventListener('input', function(e) {
      autoResizeInput(input);
    });
    
    input.addEventListener('keydown', function(e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        finishTextInput(input);
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        cancelTextInput(input);
      }
    });
    
    input.addEventListener('blur', function() {
      finishTextInput(input);
    });
  }

  /* ========================================================================
   * Auto Resize Input
   * ======================================================================== */
  function autoResizeInput(input) {
    input.style.height = 'auto';
    input.style.height = `${input.scrollHeight}px`;
    input.style.width = `${Math.max(input.scrollWidth, 100)}px`;
  }

  /* ========================================================================
   * Finish Text Input
   * ======================================================================== */
  function finishTextInput(input) {
    const text = input.value.trim();
    if (!text) {
      removeTextInput();
      state.isTyping = false;
      return;
    }
    
    const paperRect = paperEl.getBoundingClientRect();
    const viewportRect = viewport.getBoundingClientRect();
    const x = parseFloat(input.style.left) - (paperRect.left - viewportRect.left);
    const y = parseFloat(input.style.top) - (paperRect.top - viewportRect.top);
    
    if (state.activeText && state.activeText.editing) {
      // Update existing text
      state.activeText.text = text;
      state.activeText.x = x;
      state.activeText.y = y;
      state.activeText.editing = false;
    } else {
      // Create new text
      addText(text, x, y);
    }
    
    state.isTyping = false;
    state.activeText = null;
    removeTextInput();
    renderAllText();
  }

  /* ========================================================================
   * Cancel Text Input
   * ======================================================================== */
  function cancelTextInput(input) {
    if (state.activeText && state.activeText.editing) {
      state.activeText.editing = false;
    }
    state.isTyping = false;
    state.activeText = null;
    removeTextInput();
  }

  /* ========================================================================
   * Remove Text Input
   * ======================================================================== */
  function removeTextInput() {
    const input = document.getElementById('handwriting-input');
    if (input) {
      input.remove();
    }
  }

  /* ========================================================================
   * Toggle Handwriting Mode
   * ======================================================================== */
  function toggle() {
    state.enabled = !state.enabled;
    
    if (state.enabled) {
      // Enable handwriting mode
      handwritingCanvas.style.pointerEvents = 'auto';
      handwritingCanvas.style.cursor = 'text';
      document.body.style.cursor = 'text';
      
      // Re-initialize canvas
      initCanvas();
      
      showToast('Handwriting mode enabled. Click on lines to type.');
    } else {
      // Disable handwriting mode
      handwritingCanvas.style.pointerEvents = 'none';
      handwritingCanvas.style.cursor = '';
      document.body.style.cursor = '';
      
      // Remove any active input
      removeTextInput();
      state.isTyping = false;
      state.activeText = null;
      
      showToast('Handwriting mode disabled.');
    }
    
    return state.enabled;
  }

  /* ========================================================================
   * Enable Handwriting Mode
   * ======================================================================== */
  function enable() {
    if (!state.enabled) {
      toggle();
    }
  }

  /* ========================================================================
   * Disable Handwriting Mode
   * ======================================================================== */
  function disable() {
    if (state.enabled) {
      toggle();
    }
  }

  /* ========================================================================
   * Clear All Text
   * ======================================================================== */
  function clearAll() {
    state.textElements = [];
    clearCanvas();
  }

  /* ========================================================================
   * Show Toast
   * ======================================================================== */
  function showToast(message, duration = 3000) {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    
    const existing = container.querySelector('.toast');
    if (existing) existing.remove();
    
    const toast = document.createElement('div');
    toast.className = 'toast bg-zinc-900 text-white px-4 py-2 rounded shadow-lg animate-fade-in pointer-events-auto';
    toast.textContent = message;
    container.appendChild(toast);
    
    setTimeout(() => {
      toast.classList.add('animate-fade-out');
      setTimeout(() => toast.remove(), 300);
    }, duration);
  }

  /* ========================================================================
   * Update Scale and Offset
   * ======================================================================== */
  function updateTransform(scale, tx, ty) {
    state.scale = scale || 1;
    state.offsetX = tx || 0;
    state.offsetY = ty || 0;
    
    // Re-render text at new scale
    renderAllText();
  }

  /* ========================================================================
   * Get State
   * ======================================================================== */
  function getState() {
    return {
      enabled: state.enabled,
      isTyping: state.isTyping,
      textCount: state.textElements.length,
      scale: state.scale
    };
  }

  /* ========================================================================
   * Set Configuration
   * ======================================================================== */
  function setConfig(options) {
    Object.assign(CONFIG, options);
  }

  /* ========================================================================
   * Get Configuration
   * ======================================================================== */
  function getConfig() {
    return { ...CONFIG };
  }

  /* ========================================================================
   * Export Public API
   * ======================================================================== */
  window.Handwriting = {
    toggle,
    enable,
    disable,
    clearAll,
    addText,
    removeText,
    updateTransform,
    getState,
    setConfig,
    getConfig,
    findTextAtPosition,
    selectText
  };

  /* ========================================================================
   * Initialize
   * ======================================================================== */
  function init() {
    // Get DOM references
    paperEl = document.getElementById('paper');
    ruledLines = document.getElementById('ruledLines');
    handwritingCanvas = document.getElementById('handwritingCanvas');
    paperContainer = document.getElementById('paperContainer');
    viewport = document.getElementById('viewport');
    
    if (!paperEl || !handwritingCanvas) {
      console.warn('Handwriting.js: Required elements not found');
      return;
    }
    
    // Initialize canvas
    initCanvas();
    
    // Set up event listeners
    handwritingCanvas.addEventListener('click', onCanvasClick);
    
    // Listen for paper size changes
    const observer = new ResizeObserver(() => {
      initCanvas();
    });
    observer.observe(paperEl);
    
    // Mark as ready
    console.log('Handwriting.js initialized - Rough.js ready');
  }

  /* ========================================================================
   * Auto-initialize
   * ======================================================================== */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
