/* paper.js — Advanced Zoom/Pan Controller for DIN 476 Lined Notebook Paper
 * 
 * Features:
 *   - Smooth GPU-accelerated zoom/pan with CSS transforms
 *   - Pinch-to-zoom with touch gestures
 *   - Mouse wheel zoom with configurable sensitivity
 *   - Drag-to-pan with momentum scrolling
 *   - Double-tap to zoom toggle
 *   - Edge resistance and boundary constraints
 *   - Format switching (A4/A5) with proper dimensions
 *   - SVG-based ruled lines for crisp rendering at any scale
 *   - Touch action optimization for mobile
 */
(function() {
  'use strict';

  /* ========================================================================
   * ISO 216 / DIN 476 Paper Definitions (mm)
   * ======================================================================== */
  const PAPERS = {
    A4: { mmW: 210, mmH: 297, label: 'A4', description: '210 × 297 mm' },
    A5: { mmW: 148, mmH: 210, label: 'A5', description: '148 × 210 mm' },
    A3: { mmW: 297, mmH: 420, label: 'A3', description: '297 × 420 mm' },
    Letter: { mmW: 215.9, mmH: 279.4, label: 'Letter', description: '8.5 × 11 in' }
  };

  const DPI = 96;
  const MM2PX = DPI / 25.4;
  
  // Notebook specifications (German school standard)
  const LINE_GAP_MM = 8;      // Standard ruled line spacing
  const TOP_MARGIN_MM = 30;   // Top margin for heading
  const LEFT_MARGIN_MM = 25;  // Left margin (red vertical line)
  const BOTTOM_MARGIN_MM = 16;

  /* ========================================================================
   * Configuration
   * ======================================================================== */
  const CONFIG = {
    minScale: 0.1,           // Minimum zoom level
    maxScale: 8,             // Maximum zoom level
    zoomSensitivity: 1.08,   // Mouse wheel zoom factor
    touchZoomSensitivity: 1.05,
    panMomentum: 0.92,       // Momentum decay factor
    panFriction: 0.98,      // Friction for slowing down
    edgeResistance: 0.3,    // Resistance when dragging beyond edges
    doubleTapScale: 2,       // Double-tap zoom factor
    doubleTapDuration: 300, // Max time between taps (ms)
    animationDuration: 200, // Duration for smooth animations (ms)
    renderThrottle: 16       // Max FPS for rendering (ms)
  };

  /* ========================================================================
   * State Management
   * ======================================================================== */
  const state = {
    scale: 1,
    targetScale: 1,
    tx: 0,
    ty: 0,
    targetTx: 0,
    targetTy: 0,
    vx: 0,                   // Velocity X for momentum
    vy: 0,                   // Velocity Y for momentum
    format: 'A4',
    isPanning: false,
    isPinching: false,
    isAnimating: false,
    pinchStartDist: 0,
    pinchStartScale: 1,
    touchStartTime: 0,
    lastTouchCount: 0,
    lastTapTime: 0,
    lastTapX: 0,
    lastTapY: 0,
    pointerId: -1,
    startX: 0,
    startY: 0,
    lastX: 0,
    lastY: 0,
    animationFrame: null,
    renderScheduled: false
  };

  /* ========================================================================
   * DOM References
   * ======================================================================== */
  let viewport, container, paperEl, paperContent, ruledLines;
  let formatSelect, formatLabel, zoomLabel, zoomInBtn, zoomOutBtn, resetBtn;
  let toolbar;

  /* ========================================================================
   * Utility Functions
   * ======================================================================== */
  function mmToPx(mm) { return mm * MM2PX; }
  function pxToMm(px) { return px / MM2PX; }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function lerp(start, end, t) {
    return start + (end - start) * t;
  }

  function distance(a, b) {
    const dx = a.clientX - b.clientX;
    const dy = a.clientY - b.clientY;
    return Math.sqrt(dx * dx + dy * dy);
  }

  function midpoint(a, b) {
    return {
      x: (a.clientX + b.clientX) / 2,
      y: (a.clientY + b.clientY) / 2
    };
  }

  function getTimestamp() {
    return Date.now();
  }

  /* ========================================================================
   * Paper Dimensions & Layout
   * ======================================================================== */
  function getPaperDimensions(format) {
    const p = PAPERS[format] || PAPERS.A4;
    return {
      width: mmToPx(p.mmW),
      height: mmToPx(p.mmH),
      label: p.label,
      description: p.description
    };
  }

  function getViewportDimensions() {
    return {
      width: viewport ? viewport.clientWidth : window.innerWidth,
      height: viewport ? viewport.clientHeight : window.innerHeight
    };
  }

  function getPaperBounds() {
    const dims = getPaperDimensions(state.format);
    return {
      left: state.tx,
      top: state.ty,
      right: state.tx + dims.width * state.scale,
      bottom: state.ty + dims.height * state.scale
    };
  }

  /* ========================================================================
   * SVG Ruled Lines Generation
   * ======================================================================== */
  function renderRuledLines(format) {
    const p = PAPERS[format];
    if (!p) return;
    
    const wPx = mmToPx(p.mmW);
    const hPx = mmToPx(p.mmH);
    const lineGap = mmToPx(LINE_GAP_MM);
    const topM = mmToPx(TOP_MARGIN_MM);
    const leftM = mmToPx(LEFT_MARGIN_MM);
    const bottomM = mmToPx(BOTTOM_MARGIN_MM);

    const lines = [];
    
    // Generate horizontal ruled lines
    let y = topM;
    while (y < hPx - bottomM) {
      lines.push(
        `<line x1="0" y1="${y.toFixed(1)}" x2="${wPx.toFixed(1)}" y2="${y.toFixed(1)}" ` +
        `stroke="#c5d5e8" stroke-width="0.8"/>`
      );
      y += lineGap;
    }
    
    // Top horizontal margin line
    lines.push(
      `<line x1="0" y1="${topM.toFixed(1)}" x2="${wPx.toFixed(1)}" y2="${topM.toFixed(1)}" ` +
      `stroke="#c5d5e8" stroke-width="1.2"/>`
    );
    
    // Red vertical margin line
    lines.push(
      `<line x1="${leftM.toFixed(1)}" y1="0" x2="${leftM.toFixed(1)}" y2="${hPx.toFixed(1)}" ` +
      `stroke="#e8b4b8" stroke-width="1.0"/>`
    );

    // Create SVG with optimized attributes
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${wPx}" height="${hPx}" shape-rendering="crispEdges">${lines.join('')}</svg>`;
    
    if (ruledLines) {
      ruledLines.innerHTML = svg;
    }
  }

  /* ========================================================================
   * Apply Paper Format
   * ======================================================================== */
  function applyFormat(fmt) {
    const p = PAPERS[fmt];
    if (!p) return;
    
    state.format = fmt;
    const dims = getPaperDimensions(fmt);
    
    // Update paper element dimensions
    paperEl.style.width = `${dims.width}px`;
    paperEl.style.height = `${dims.height}px`;
    
    // Update CSS classes
    paperEl.className = `paper paper-${fmt.toLowerCase()}`;
    
    // Update format label
    if (formatLabel) {
      formatLabel.textContent = p.label;
    }
    if (formatSelect) {
      formatSelect.value = fmt;
    }
    
    // Regenerate ruled lines
    renderRuledLines(fmt);
    
    // Recenter paper after format change
    centerPaper();
  }

  /* ========================================================================
   * Center Paper in Viewport
   * ======================================================================== */
  function centerPaper(animate = false) {
    const vw = viewport ? viewport.clientWidth : window.innerWidth;
    const vh = viewport ? viewport.clientHeight : window.innerHeight;
    const dims = getPaperDimensions(state.format);
    
    // Calculate fit scale to show entire paper
    const fitScale = Math.min(
      (vw - 40) / dims.width,
      (vh - 60) / dims.height,
      1
    );
    
    const targetScale = clamp(fitScale, CONFIG.minScale, CONFIG.maxScale);
    const scaledW = dims.width * targetScale;
    const scaledH = dims.height * targetScale;
    
    const targetTx = (vw - scaledW) / 2;
    const targetTy = (vh - scaledH) / 2;
    
    if (animate) {
      state.targetScale = targetScale;
      state.targetTx = targetTx;
      state.targetTy = targetTy;
      state.isAnimating = true;
      scheduleRender();
    } else {
      state.scale = targetScale;
      state.tx = targetTx;
      state.ty = targetTy;
      state.vx = 0;
      state.vy = 0;
      applyTransform();
    }
  }

  /* ========================================================================
   * Apply CSS Transform (GPU Composited)
   * ======================================================================== */
  function applyTransform() {
    if (!container) return;
    
    const transform = `translate(${state.tx.toFixed(2)}px, ${state.ty.toFixed(2)}px) scale(${state.scale.toFixed(4)})`;
    container.style.transform = transform;
    
    // Update zoom label
    if (zoomLabel) {
      zoomLabel.textContent = `${Math.round(state.scale * 100)}%`;
    }
    
    // Update cursor based on state
    updateCursor();
  }

  function updateCursor() {
    if (!viewport) return;
    
    if (state.isPanning || state.isPinching) {
      viewport.classList.add('cursor-grabbing');
      viewport.classList.remove('cursor-grab');
    } else {
      viewport.classList.remove('cursor-grabbing');
      viewport.classList.add('cursor-grab');
    }
  }

  /* ========================================================================
   * Zoom Functions
   * ======================================================================== */
  function zoomAt(cx, cy, factor, animate = false) {
    const newScale = clamp(state.scale * factor, CONFIG.minScale, CONFIG.maxScale);
    const ratio = newScale / state.scale;
    
    // Zoom centered on point
    const newTx = cx - ratio * (cx - state.tx);
    const newTy = cy - ratio * (cy - state.ty);
    
    if (animate) {
      state.targetScale = newScale;
      state.targetTx = newTx;
      state.targetTy = newTy;
      state.isAnimating = true;
      scheduleRender();
    } else {
      state.scale = newScale;
      state.tx = newTx;
      state.ty = newTy;
      applyTransform();
    }
  }

  function zoomIn(animate = false) {
    const vw = viewport ? viewport.clientWidth : window.innerWidth;
    const vh = viewport ? viewport.clientHeight : window.innerHeight;
    zoomAt(vw / 2, vh / 2, CONFIG.zoomSensitivity, animate);
  }

  function zoomOut(animate = false) {
    const vw = viewport ? viewport.clientWidth : window.innerWidth;
    const vh = viewport ? viewport.clientHeight : window.innerHeight;
    zoomAt(vw / 2, vh / 2, 1 / CONFIG.zoomSensitivity, animate);
  }

  function setZoom(level, animate = false) {
    const currentScale = state.scale;
    const target = clamp(level, CONFIG.minScale, CONFIG.maxScale);
    const factor = target / currentScale;
    
    const vw = viewport ? viewport.clientWidth : window.innerWidth;
    const vh = viewport ? viewport.clientHeight : window.innerHeight;
    zoomAt(vw / 2, vh / 2, factor, animate);
  }

  /* ========================================================================
   * Pan Functions with Edge Resistance
   * ======================================================================== */
  function applyPanConstraints() {
    const dims = getPaperDimensions(state.format);
    const vw = viewport ? viewport.clientWidth : window.innerWidth;
    const vh = viewport ? viewport.clientHeight : window.innerHeight;
    
    const scaledW = dims.width * state.scale;
    const scaledH = dims.height * state.scale;
    
    // Calculate visible bounds
    const minX = Math.min(0, vw - scaledW);
    const maxX = Math.max(0, vw - scaledW);
    const minY = Math.min(0, vh - scaledH);
    const maxY = Math.max(0, vh - scaledH);
    
    // Apply edge resistance
    const edgeX = clamp(state.tx, minX * (1 - CONFIG.edgeResistance), maxX * (1 + CONFIG.edgeResistance));
    const edgeY = clamp(state.ty, minY * (1 - CONFIG.edgeResistance), maxY * (1 + CONFIG.edgeResistance));
    
    // If we're at the edges, apply resistance
    if (state.tx < minX || state.tx > maxX) {
      state.tx = lerp(state.tx, edgeX, 0.3);
    }
    if (state.ty < minY || state.ty > maxY) {
      state.ty = lerp(state.ty, edgeY, 0.3);
    }
  }

  function panBy(dx, dy) {
    state.tx += dx;
    state.ty += dy;
    applyPanConstraints();
    applyTransform();
  }

  /* ========================================================================
   * Double-Tap Detection
   * ======================================================================== */
  function handleTap(x, y) {
    const now = getTimestamp();
    const tapWindow = now - state.lastTapTime;
    
    if (tapWindow < CONFIG.doubleTapDuration) {
      // Double tap detected
      const distance = Math.sqrt(
        Math.pow(x - state.lastTapX, 2) + 
        Math.pow(y - state.lastTapY, 2)
      );
      
      // Only trigger if taps are close together
      if (distance < 50) {
        handleDoubleTap(x, y);
        state.lastTapTime = 0; // Reset to prevent triple tap
        return true;
      }
    }
    
    // Single tap
    state.lastTapTime = now;
    state.lastTapX = x;
    state.lastTapY = y;
    return false;
  }

  function handleDoubleTap(x, y) {
    // Toggle between current scale and double-tap scale
    const currentScale = state.scale;
    const targetScale = currentScale > 1 ? 1 : CONFIG.doubleTapScale;
    
    zoomAt(x, y, targetScale / currentScale, true);
  }

  /* ========================================================================
   * Animation Loop
   * ======================================================================== */
  function scheduleRender() {
    if (state.renderScheduled) return;
    state.renderScheduled = true;
    state.animationFrame = requestAnimationFrame(animate);
  }

  function animate(timestamp) {
    state.renderScheduled = false;
    
    if (state.isAnimating) {
      const elapsed = timestamp - (state.animationStartTime || timestamp);
      const progress = Math.min(elapsed / CONFIG.animationDuration, 1);
      
      // Ease-out animation
      const ease = 1 - Math.pow(1 - progress, 3);
      
      state.scale = lerp(state.scale, state.targetScale, ease);
      state.tx = lerp(state.tx, state.targetTx, ease);
      state.ty = lerp(state.ty, state.targetTy, ease);
      
      applyTransform();
      
      if (progress < 1) {
        state.renderScheduled = true;
        state.animationFrame = requestAnimationFrame(animate);
        return;
      }
      
      state.isAnimating = false;
      state.scale = state.targetScale;
      state.tx = state.targetTx;
      state.ty = state.targetTy;
    }
    
    // Apply momentum
    if (Math.abs(state.vx) > 0.01 || Math.abs(state.vy) > 0.01) {
      state.tx += state.vx;
      state.ty += state.vy;
      
      // Apply friction
      state.vx *= CONFIG.panFriction;
      state.vy *= CONFIG.panFriction;
      
      applyPanConstraints();
      applyTransform();
      
      state.renderScheduled = true;
      state.animationFrame = requestAnimationFrame(animate);
      return;
    }
    
    // Check if we need to continue
    if (state.isPanning || state.isPinching || state.isAnimating) {
      state.renderScheduled = true;
      state.animationFrame = requestAnimationFrame(animate);
    }
  }

  /* ========================================================================
   * Event Handlers - Touch
   * ======================================================================== */
  function onTouchStart(e) {
    if (e.touches.length === 2) {
      e.preventDefault();
      state.isPinching = true;
      state.isPanning = false;
      state.pinchStartDist = distance(e.touches[0], e.touches[1]);
      state.pinchStartScale = state.scale;
      state.lastTouchCount = 2;
      updateCursor();
    } else if (e.touches.length === 1) {
      state.isPanning = true;
      state.isPinching = false;
      state.startX = e.touches[0].clientX;
      state.startY = e.touches[0].clientY;
      state.lastX = e.touches[0].clientX;
      state.lastY = e.touches[0].clientY;
      state.vx = 0;
      state.vy = 0;
      state.lastTouchCount = 1;
      updateCursor();
    }
  }

  function onTouchMove(e) {
    if (state.isPinching && e.touches.length === 2) {
      e.preventDefault();
      const currentDist = distance(e.touches[0], e.touches[1]);
      const center = midpoint(e.touches[0], e.touches[1]);
      
      const scaleFactor = currentDist / state.pinchStartDist;
      const newScale = clamp(state.pinchStartScale * scaleFactor, CONFIG.minScale, CONFIG.maxScale);
      const ratio = newScale / state.scale;
      
      // Zoom centered on pinch midpoint
      state.scale = newScale;
      state.tx = center.x - ratio * (center.x - state.tx);
      state.ty = center.y - ratio * (center.y - state.ty);
      
      applyPanConstraints();
      applyTransform();
      
    } else if (state.isPanning && e.touches.length === 1) {
      e.preventDefault();
      const dx = e.touches[0].clientX - state.lastX;
      const dy = e.touches[0].clientY - state.lastY;
      
      state.vx = dx;
      state.vy = dy;
      
      panBy(dx, dy);
      
      state.lastX = e.touches[0].clientX;
      state.lastY = e.touches[0].clientY;
    }
  }

  function onTouchEnd(e) {
    if (e.touches.length === 0) {
      // All fingers lifted
      if (state.isPinching) {
        state.isPinching = false;
        updateCursor();
      }
      
      if (state.isPanning) {
        state.isPanning = false;
        updateCursor();
        
        // Start momentum if velocity is significant
        if (Math.abs(state.vx) > 0.5 || Math.abs(state.vy) > 0.5) {
          scheduleRender();
        }
      }
      
      // Check for tap
      if (state.lastTouchCount === 1 && e.changedTouches.length === 1) {
        const touch = e.changedTouches[0];
        handleTap(touch.clientX, touch.clientY);
      }
      
      state.lastTouchCount = 0;
    } else if (e.touches.length === 1 && state.lastTouchCount === 2) {
      // One finger lifted, one remains - prepare for pan
      state.lastTouchCount = 1;
    }
  }

  function onTouchCancel(e) {
    state.isPanning = false;
    state.isPinching = false;
    state.vx = 0;
    state.vy = 0;
    state.lastTouchCount = 0;
    updateCursor();
  }

  /* ========================================================================
   * Event Handlers - Mouse
   * ======================================================================== */
  let mouseDown = false;
  let mouseStartX = 0;
  let mouseStartY = 0;

  function onMouseDown(e) {
    if (e.button !== 0) return; // Only left button
    
    mouseDown = true;
    mouseStartX = e.clientX;
    mouseStartY = e.clientY;
    state.lastX = e.clientX;
    state.lastY = e.clientY;
    state.vx = 0;
    state.vy = 0;
    state.isPanning = true;
    updateCursor();
    
    // Prevent text selection during pan
    e.preventDefault();
  }

  function onMouseMove(e) {
    if (!mouseDown) return;
    
    const dx = e.clientX - state.lastX;
    const dy = e.clientY - state.lastY;
    
    state.vx = dx;
    state.vy = dy;
    
    panBy(dx, dy);
    
    state.lastX = e.clientX;
    state.lastY = e.clientY;
  }

  function onMouseUp(e) {
    if (e.button !== 0) return;
    
    mouseDown = false;
    state.isPanning = false;
    updateCursor();
    
    // Start momentum if velocity is significant
    if (Math.abs(state.vx) > 0.5 || Math.abs(state.vy) > 0.5) {
      scheduleRender();
    }
  }

  function onMouseLeave() {
    mouseDown = false;
    state.isPanning = false;
    updateCursor();
  }

  function onWheel(e) {
    e.preventDefault();
    
    const factor = e.deltaY < 0 ? CONFIG.zoomSensitivity : 1 / CONFIG.zoomSensitivity;
    zoomAt(e.clientX, e.clientY, factor);
  }

  /* ========================================================================
   * Event Handlers - Pointer Events (for hybrid touch/mouse support)
   * ======================================================================== */
  function onPointerDown(e) {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    
    state.pointerId = e.pointerId;
    state.startX = e.clientX;
    state.startY = e.clientY;
    state.lastX = e.clientX;
    state.lastY = e.clientY;
    state.vx = 0;
    state.vy = 0;
    state.isPanning = true;
    updateCursor();
    
    e.preventDefault();
  }

  function onPointerMove(e) {
    if (state.pointerId !== e.pointerId) return;
    
    if (state.isPanning) {
      const dx = e.clientX - state.lastX;
      const dy = e.clientY - state.lastY;
      
      state.vx = dx;
      state.vy = dy;
      
      panBy(dx, dy);
      
      state.lastX = e.clientX;
      state.lastY = e.clientY;
      e.preventDefault();
    }
  }

  function onPointerUp(e) {
    if (state.pointerId !== e.pointerId) return;
    
    state.pointerId = -1;
    state.isPanning = false;
    updateCursor();
    
    // Start momentum if velocity is significant
    if (Math.abs(state.vx) > 0.5 || Math.abs(state.vy) > 0.5) {
      scheduleRender();
    }
  }

  /* ========================================================================
   * Window Resize Handler
   * ======================================================================== */
  function onResize() {
    // Re-center paper when window resizes
    centerPaper();
  }

  /* ========================================================================
   * Bind All Events
   * ======================================================================== */
  function bindEvents() {
    if (!viewport) return;

    // Touch events
    viewport.addEventListener('touchstart', onTouchStart, { passive: false });
    viewport.addEventListener('touchmove', onTouchMove, { passive: false });
    viewport.addEventListener('touchend', onTouchEnd, { passive: true });
    viewport.addEventListener('touchcancel', onTouchCancel, { passive: true });
    
    // Mouse events
    viewport.addEventListener('mousedown', onMouseDown, { passive: false });
    viewport.addEventListener('mousemove', onMouseMove, { passive: false });
    viewport.addEventListener('mouseup', onMouseUp, { passive: true });
    viewport.addEventListener('mouseleave', onMouseLeave, { passive: true });
    
    // Pointer events (for hybrid devices)
    viewport.addEventListener('pointerdown', onPointerDown, { passive: false });
    viewport.addEventListener('pointermove', onPointerMove, { passive: false });
    viewport.addEventListener('pointerup', onPointerUp, { passive: true });
    viewport.addEventListener('pointercancel', onPointerCancel, { passive: true });
    
    // Wheel event
    viewport.addEventListener('wheel', onWheel, { passive: false });
    
    // Button events
    if (zoomInBtn) {
      zoomInBtn.addEventListener('click', () => zoomIn(true));
    }
    if (zoomOutBtn) {
      zoomOutBtn.addEventListener('click', () => zoomOut(true));
    }
    if (resetBtn) {
      resetBtn.addEventListener('click', () => centerPaper(true));
    }
    if (formatSelect) {
      formatSelect.addEventListener('change', () => {
        applyFormat(formatSelect.value);
      });
    }
    
    // Window resize
    window.addEventListener('resize', debounce(onResize, 100));
    
    // Prevent context menu on long press
    viewport.addEventListener('contextmenu', (e) => {
      if (state.isPanning) {
        e.preventDefault();
      }
    });
  }

  function onPointerCancel(e) {
    if (state.pointerId === e.pointerId) {
      state.pointerId = -1;
      state.isPanning = false;
      updateCursor();
    }
  }

  /* ========================================================================
   * Utility: Debounce
   * ======================================================================== */
  function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  /* ========================================================================
   * Public API
   * ======================================================================== */
  window.Paper = {
    PAPERS: PAPERS,
    mmToPx: mmToPx,
    pxToMm: pxToMm,
    
    getState: function() {
      return {
        scale: state.scale,
        tx: state.tx,
        ty: state.ty,
        format: state.format,
        isPanning: state.isPanning,
        isPinching: state.isPinching,
        isAnimating: state.isAnimating
      };
    },
    
    getFormat: function() {
      return state.format;
    },
    
    setFormat: function(fmt) {
      applyFormat(fmt);
    },
    
    zoomTo: function(scale, animate = false) {
      setZoom(scale, animate);
    },
    
    zoomIn: function(animate = false) {
      zoomIn(animate);
    },
    
    zoomOut: function(animate = false) {
      zoomOut(animate);
    },
    
    center: function(animate = false) {
      centerPaper(animate);
    },
    
    reset: function(animate = false) {
      centerPaper(animate);
    },
    
    setContent: function(html) {
      if (paperContent) {
        paperContent.innerHTML = html;
      }
    },
    
    getContent: function() {
      return paperContent ? paperContent.innerHTML : '';
    },
    
    setRulings: function(html) {
      if (ruledLines) {
        ruledLines.innerHTML = html;
      }
    },
    
    getDimensions: function(format) {
      return getPaperDimensions(format || state.format);
    },
    
    getViewport: function() {
      return getViewportDimensions();
    },
    
    // Configuration getters/setters
    getConfig: function() {
      return { ...CONFIG };
    },
    
    setConfig: function(options) {
      Object.assign(CONFIG, options);
    }
  };

  /* ========================================================================
   * Initialization
   * ======================================================================== */
  function init() {
    // Get DOM references
    viewport = document.getElementById('viewport');
    container = document.getElementById('paperContainer');
    paperEl = document.getElementById('paper');
    paperContent = document.getElementById('paperContent');
    ruledLines = document.getElementById('ruledLines');
    formatSelect = document.getElementById('formatSelect');
    formatLabel = document.getElementById('formatLabel');
    zoomLabel = document.getElementById('zoomLabel');
    zoomInBtn = document.getElementById('zoomIn');
    zoomOutBtn = document.getElementById('zoomOut');
    resetBtn = document.getElementById('resetView');
    toolbar = document.querySelector('.toolbar') || document.querySelector('[class*="top-0"]');
    
    // Initialize
    applyFormat(state.format);
    centerPaper();
    bindEvents();
    
    // Set initial cursor
    updateCursor();
    
    // Mark as ready
    document.documentElement.dataset.ready = 'true';
    
    console.log('Paper.js initialized - Advanced rendering engine ready');
  }

  /* ========================================================================
   * Auto-initialize when DOM is ready
   * ======================================================================== */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
