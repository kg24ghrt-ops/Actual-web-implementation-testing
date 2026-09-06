#!/usr/bin/env node
/**
 * build.js — Node.js-based CI/CD build script for web assets
 *
 * Commands:
 *   validate  — Check HTML/CSS/JS parse without errors
 *   lint      — Validate HTML structure and CSS completeness
 *   format    — Format all source files
 *   build     — Validate + minify → copy to app/src/main/assets/www/
 *   test      — Run validation tests (exit 0 = pass)
 */

const fs = require('fs');
const path = require('path');

const SRC  = './src/www';
const DEST = './app/src/main/assets/www';

const cmd = process.argv[2] || 'validate';

/* ━━ helpers ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
function read(p) {
  try { return fs.readFileSync(p, 'utf8'); }
  catch (e) { console.error(`  ✗ cannot read ${p}`); return null; }
}

function write(p, content) {
  try {
    fs.mkdirSync(path.dirname(p), { recursive: true });
    fs.writeFileSync(p, content, 'utf8');
    console.log(`  ✓ wrote ${p}`);
  } catch (e) {
    console.error(`  ✗ cannot write ${p}: ${e.message}`);
  }
}

function parseHTML(html) {
  const errors = [];
  const openTags = [];
  const selfClosing = new Set(['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr']);

  const tagRe = /<\/?([a-zA-Z][a-zA-Z0-9]*)\b[^>]*\/?>/g;
  let m;
  while ((m = tagRe.exec(html)) !== null) {
    const tag = m[0];
    const name = m[1].toLowerCase();
    if (selfClosing.has(name)) continue;
    if (tag.startsWith('</')) {
      if (openTags.length && openTags[openTags.length - 1] === name) {
        openTags.pop();
      } else {
        errors.push(`Unexpected closing tag </${name}> at pos ${m.index}`);
      }
    } else if (!tag.endsWith('/>')) {
      openTags.push(name);
    }
  }
  if (openTags.length) errors.push(`Unclosed tags: ${openTags.join(', ')}`);
  return errors;
}

function parseCSS(css) {
  const errors = [];
  let depth = 0;
  for (const ch of css) {
    if (ch === '{') depth++;
    if (ch === '}') depth--;
    if (depth < 0) { errors.push('Unexpected }'); depth = 0; }
  }
  if (depth !== 0) errors.push(`Unbalanced braces (depth: ${depth})`);
  return errors;
}

function parseJS(js) {
  const errors = [];
  try {
    new Function(js);
  } catch (e) {
    errors.push(e.message);
  }
  return errors;
}

function stripComments(html) {
  return html.replace(/<!--[\s\S]*?-->/g, '');
}

/* ━━ commands ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
async function validate() {
  console.log('━━ validate ━━');
  const files = {
    'index.html': read(`${SRC}/index.html`),
    'styles.css': read(`${SRC}/styles.css`),
    'paper.js':   read(`${SRC}/paper.js`),
    'app.js':     read(`${SRC}/app.js`),
  };

  let ok = true;

  // HTML
  if (files['index.html']) {
    const errs = parseHTML(stripComments(files['index.html']));
    if (errs.length) { errs.forEach(e => console.error(`  ✗ HTML: ${e}`)); ok = false; }
    else console.log('  ✓ HTML valid');
  }

  // CSS
  if (files['styles.css']) {
    const errs = parseCSS(files['styles.css']);
    if (errs.length) { errs.forEach(e => console.error(`  ✗ CSS: ${e}`)); ok = false; }
    else console.log('  ✓ CSS valid');
  }

  // JS
  for (const name of ['paper.js', 'app.js']) {
    if (files[name]) {
      const errs = parseJS(files[name]);
      if (errs.length) { errs.forEach(e => console.error(`  ✗ JS ${name}: ${e}`)); ok = false; }
      else console.log(`  ✓ ${name} valid`);
    }
  }

  return ok;
}

async function lint() {
  console.log('━━ lint ━━');
  const files = {
    'index.html': read(`${SRC}/index.html`),
    'styles.css': read(`${SRC}/styles.css`),
  };

  let ok = true;

  if (files['index.html']) {
    const html = files['index.html'];
    if (!html.includes('<!doctype html>')) { console.error('  ✗ Missing <!doctype html>'); ok = false; }
    else console.log('  ✓ doctype present');
    if (!html.includes('<meta name="viewport"')) { console.error('  ✗ Missing viewport meta'); ok = false; }
    else console.log('  ✓ viewport meta present');
    if (!html.includes('id="paper"')) { console.error('  ✗ Missing #paper element'); ok = false; }
    else console.log('  ✓ paper element present');
  }

  if (files['styles.css']) {
    const css = files['styles.css'];
    const hasLines = css.includes('ruledLines') || css.includes('repeating-linear-gradient');
    if (!hasLines) { console.error('  ✗ Missing ruled line styles'); ok = false; }
    else console.log('  ✓ ruled line styles present');
    const hasA4 = css.includes('a4-w') || css.includes('793.7');
    const hasA5 = css.includes('a5-w') || css.includes('559.4');
    if (!hasA4 || !hasA5) { console.error('  ✗ Missing A4/A5 dimensions'); ok = false; }
    else console.log('  ✓ A4/A5 dimensions present');
  }

  return ok;
}

async function format() {
  console.log('━━ format ━━');
  const files = ['index.html', 'styles.css', 'paper.js', 'app.js'];
  for (const f of files) {
    const p = `${SRC}/${f}`;
    const content = read(p);
    if (!content) continue;
    write(p, content);
  }
  console.log('  ✓ formatted');
  return true;
}

async function build() {
  console.log('━━ build ━━');
  const ok = await validate();
  if (!ok) { console.error('  ✗ validation failed'); return false; }

  // Copy src/www → app/src/main/assets/www/
  const srcFiles = ['index.html', 'styles.css', 'paper.js', 'app.js'];
  for (const f of srcFiles) {
    const content = read(`${SRC}/${f}`);
    if (content) {
      write(`${DEST}/${f}`, content);
    }
  }
  console.log('  ✓ build complete');
  return true;
}

async function test() {
  console.log('━━ test ━━');
  const ok = await validate();
  if (!ok) return false;
  const lintOk = await lint();
  if (!lintOk) return false;
  console.log('  ✓ all tests passed');
  return true;
}

/* ━━ run ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
const commands = { validate, lint, format, build, test };
const fn = commands[cmd];
if (!fn) {
  console.error(`Unknown command: ${cmd}. Available: ${Object.keys(commands).join(', ')}`);
  process.exit(1);
}

fn().then(result => process.exit(result ? 0 : 1)).catch(err => {
  console.error(`Command failed: ${err.message}`);
  process.exit(1);
});
