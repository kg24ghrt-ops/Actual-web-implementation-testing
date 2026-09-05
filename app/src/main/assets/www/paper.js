/* paper.js — Zoom/pan controller for DIN 476 lined notebook paper */
(function(){
  'use strict';

  /* --- ISO 216 / DIN 476 paper definitions (mm) --- */
  var PAPERS = {
    A4: { mmW: 210, mmH: 297, label: 'A4' },
    A5: { mmW: 148, mmH: 210, label: 'A5' }
  };

  var DPI   = 96;
  var MM2PX = DPI / 25.4;
  var LINE_GAP_MM  = 8;    /* German school standard */
  var TOP_MARGIN_MM  = 30;
  var LEFT_MARGIN_MM = 25;
  var BOTTOM_MARGIN_MM = 16;

  function mmToPx(mm) { return mm * MM2PX; }

  /* --- State --- */
  var state = {
    scale: 1,
    tx: 0,
    ty: 0,
    minScale: 0.15,
    maxScale: 5,
    format: 'A4',
    pinchDist0: null,
    pinchScale0: 1,
    pointerDown: false,
    lastX: 0,
    lastY: 0
  };

  /* --- DOM refs --- */
  var viewport, container, paperEl, paperContent, ruledLines;
  var formatSelect, formatLabel, zoomLabel, zoomInBtn, zoomOutBtn, resetBtn;

  function init() {
    viewport     = document.getElementById('viewport');
    container    = document.getElementById('paperContainer');
    paperEl      = document.getElementById('paper');
    paperContent = document.getElementById('paperContent');
    ruledLines   = document.getElementById('ruledLines');
    formatSelect = document.getElementById('formatSelect');
    formatLabel  = document.getElementById('formatLabel');
    zoomLabel    = document.getElementById('zoomLabel');
    zoomInBtn    = document.getElementById('zoomIn');
    zoomOutBtn   = document.getElementById('zoomOut');
    resetBtn     = document.getElementById('resetView');

    applyFormat(state.format);
    centerPaper();
    bindEvents();
  }

  /* --- Generate ruled-line SVG --- */
  function renderLines(fmt) {
    var p = PAPERS[fmt];
    if (!p) return;
    var wPx = mmToPx(p.mmW);
    var hPx = mmToPx(p.mmH);
    var lineGap  = mmToPx(LINE_GAP_MM);
    var topM     = mmToPx(TOP_MARGIN_MM);
    var leftM    = mmToPx(LEFT_MARGIN_MM);
    var bottomM  = mmToPx(BOTTOM_MARGIN_MM);

    var lines = '';
    var y = topM;
    while (y < hPx - bottomM) {
      lines += '<line x1="0" y1="' + y.toFixed(1) + '" x2="' + wPx.toFixed(1) + '" y2="' + y.toFixed(1) + '" stroke="#c5d5e8" stroke-width="0.8"/>';
      y += lineGap;
    }
    /* Top horizontal margin line */
    lines += '<line x1="0" y1="' + topM.toFixed(1) + '" x2="' + wPx.toFixed(1) + '" y2="' + topM.toFixed(1) + '" stroke="#c5d5e8" stroke-width="0.8"/>';
    /* Red vertical margin line */
    lines += '<line x1="' + leftM.toFixed(1) + '" y1="0" x2="' + leftM.toFixed(1) + '" y2="' + hPx.toFixed(1) + '" stroke="#e8b4b8" stroke-width="1.0"/>';

    var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + wPx + '" height="' + hPx + '">' + lines + '</svg>';
    ruledLines.innerHTML = svg;
  }

  /* --- Apply format (A4 / A5) --- */
  function applyFormat(fmt) {
    var p = PAPERS[fmt];
    if (!p) return;
    state.format = fmt;
    var w = mmToPx(p.mmW);
    var h = mmToPx(p.mmH);
    paperEl.style.width  = w + 'px';
    paperEl.style.height = h + 'px';
    paperEl.className = 'paper paper-' + fmt.toLowerCase();
    if (formatLabel) formatLabel.textContent = p.label;
    renderLines(fmt);
    applyTransform();
  }

  /* --- Center paper in viewport --- */
  function centerPaper() {
    var vw = viewport.clientWidth;
    var vh = viewport.clientHeight;
    var pw = paperEl.offsetWidth;
    var ph = paperEl.offsetHeight;
    var fitScale = Math.min((vw - 60) / pw, (vh - 40) / ph, 1);
    state.scale = fitScale;
    state.tx = (vw - pw * fitScale) / 2;
    state.ty = (vh - ph * fitScale) / 2;
    applyTransform();
  }

  /* --- Apply CSS transform (GPU composited) --- */
  function applyTransform() {
    container.style.transform =
      'translate(' + state.tx + 'px,' + state.ty + 'px) scale(' + state.scale + ')';
    zoomLabel.textContent = Math.round(state.scale * 100) + '%';
  }

  /* --- Zoom (centered on point) --- */
  function zoomAt(cx, cy, factor) {
    var ns = state.scale * factor;
    ns = Math.max(state.minScale, Math.min(state.maxScale, ns));
    var ratio = ns / state.scale;
    state.tx = cx - ratio * (cx - state.tx);
    state.ty = cy - ratio * (cy - state.ty);
    state.scale = ns;
    applyTransform();
  }

  /* --- Pointer helpers --- */
  function dist(a, b) {
    var dx = a.clientX - b.clientX;
    var dy = a.clientY - b.clientY;
    return Math.sqrt(dx * dx + dy * dy);
  }

  /* --- Touch events (pinch + pan) --- */
  function onTouchStart(e) {
    if (e.touches.length === 2) {
      e.preventDefault();
      state.pinchDist0  = dist(e.touches[0], e.touches[1]);
      state.pinchScale0 = state.scale;
      viewport.classList.add('cursor-grabbing');
    } else if (e.touches.length === 1) {
      state.pointerDown = true;
      state.lastX = e.touches[0].clientX;
      state.lastY = e.touches[0].clientY;
      viewport.classList.add('cursor-grabbing');
    }
  }

  function onTouchMove(e) {
    if (e.touches.length === 2 && state.pinchDist0 !== null) {
      e.preventDefault();
      var d = dist(e.touches[0], e.touches[1]);
      var newScale = state.pinchScale0 * (d / state.pinchDist0);
      newScale = Math.max(state.minScale, Math.min(state.maxScale, newScale));
      var cx = (e.touches[0].clientX + e.touches[1].clientX) / 2;
      var cy = (e.touches[0].clientY + e.touches[1].clientY) / 2;
      var ratio = newScale / state.scale;
      state.tx = cx - ratio * (cx - state.tx);
      state.ty = cy - ratio * (cy - state.ty);
      state.scale = newScale;
      applyTransform();
    } else if (e.touches.length === 1 && state.pointerDown) {
      var dx = e.touches[0].clientX - state.lastX;
      var dy = e.touches[0].clientY - state.lastY;
      state.tx += dx;
      state.ty += dy;
      state.lastX = e.touches[0].clientX;
      state.lastY = e.touches[0].clientY;
      applyTransform();
    }
  }

  function onTouchEnd(e) {
    if (e.touches.length < 2) state.pinchDist0 = null;
    if (e.touches.length === 0) {
      state.pointerDown = false;
      viewport.classList.remove('cursor-grabbing');
    }
  }

  /* --- Mouse wheel zoom --- */
  function onWheel(e) {
    e.preventDefault();
    var factor = e.deltaY < 0 ? 1.08 : 1 / 1.08;
    zoomAt(e.clientX, e.clientY, factor);
  }

  /* --- Mouse drag (desktop pan) --- */
  var mouseDown = false;
  function onMouseDown(e) {
    mouseDown = true;
    state.lastX = e.clientX;
    state.lastY = e.clientY;
    viewport.classList.add('cursor-grabbing');
  }
  function onMouseMove(e) {
    if (!mouseDown) return;
    state.tx += e.clientX - state.lastX;
    state.ty += e.clientY - state.lastY;
    state.lastX = e.clientX;
    state.lastY = e.clientY;
    applyTransform();
  }
  function onMouseUp() {
    mouseDown = false;
    viewport.classList.remove('cursor-grabbing');
  }

  /* --- Bind all events --- */
  function bindEvents() {
    viewport.addEventListener('touchstart',  onTouchStart, {passive: false});
    viewport.addEventListener('touchmove',   onTouchMove,  {passive: false});
    viewport.addEventListener('touchend',    onTouchEnd,   {passive: true});
    viewport.addEventListener('touchcancel', onTouchEnd,   {passive: true});
    viewport.addEventListener('wheel', onWheel, {passive: false});
    viewport.addEventListener('mousedown', onMouseDown);
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup',   onMouseUp);

    zoomInBtn.addEventListener('click', function() {
      zoomAt(viewport.clientWidth / 2, viewport.clientHeight / 2, 1.25);
    });
    zoomOutBtn.addEventListener('click', function() {
      zoomAt(viewport.clientWidth / 2, viewport.clientHeight / 2, 1 / 1.25);
    });
    resetBtn.addEventListener('click', centerPaper);
    formatSelect.addEventListener('change', function() {
      applyFormat(this.value);
      centerPaper();
    });
  }

  /* --- Public API --- */
  window.Paper = {
    PAPERS: PAPERS,
    mmToPx: mmToPx,
    getState: function() { return {scale: state.scale, tx: state.tx, ty: state.ty}; },
    zoomTo: function(s) { state.scale = s; applyTransform(); },
    setContent: function(html) { paperContent.innerHTML = html; },
    getFormat: function() { return state.format; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
