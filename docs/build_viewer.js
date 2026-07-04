#!/usr/bin/env node
/**
 * docs/build_viewer.js
 *
 * docs/ 아래의 모든 .md 파일을 읽어 _viewer_template.html에 인라인 주입한 뒤
 * docs/index.html을 생성한다. 결과 파일은 서버 없이 브라우저에서 바로 열린다.
 * 사용법: node docs/build_viewer.js
 */
const fs = require('fs');
const path = require('path');

const docsDir = path.resolve(__dirname);
const outFile = path.join(docsDir, 'index.html');

const mdFiles = {};
function walk(dir, prefix) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    if (entry.isDirectory()) {
      walk(path.join(dir, entry.name), prefix ? prefix + '/' + entry.name : entry.name);
    } else if (entry.name.endsWith('.md')) {
      const relPath = prefix ? prefix + '/' + entry.name : entry.name;
      mdFiles[relPath] = fs.readFileSync(path.join(dir, entry.name), 'utf8');
    }
  }
}
walk(docsDir, '');

const fileCount = Object.keys(mdFiles).length;
const jsonData = JSON.stringify(mdFiles);

function prettyName(file) {
  return file.replace(/\.md$/, '').replace(/_/g, ' ');
}

const MODULE_TITLES = { ui: 'UI', lsp: 'LSP' };
const MODULE_ORDER = ['app', 'core', 'editor', 'ui', 'lsp', 'language', 'runtime', 'workspace', 'atlas', 'git', 'echo', 'pair', 'perf'];

function capitalize(s) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function groupMeta(dir) {
  if (dir === '') return { title: 'Introduction', section: null, rank: 0, sub: 0 };
  if (dir === 'guides') return { title: 'Guides', section: null, rank: 1, sub: 0 };
  if (dir.indexOf('modules/') === 0) {
    const mod = dir.substring('modules/'.length);
    const idx = MODULE_ORDER.indexOf(mod);
    return { title: MODULE_TITLES[mod] || capitalize(mod), section: 'Modules', rank: 2, sub: idx === -1 ? 99 : idx };
  }
  return { title: dir, section: null, rank: 3, sub: 0 };
}

function stemOf(p) {
  const b = p.slice(0, -3);
  if (b.endsWith('_en')) return b.slice(0, -3);
  if (b.endsWith('_kr')) return b.slice(0, -3);
  return b;
}

function buildNav(files) {
  const has = function (p) { return Object.prototype.hasOwnProperty.call(files, p); };
  const stems = [];
  const seen = {};
  Object.keys(files).filter(function (p) { return p.endsWith('.md'); }).sort().forEach(function (p) {
    const s = stemOf(p);
    if (!seen[s]) { seen[s] = true; stems.push(s); }
  });
  const groups = {};
  const order = [];
  stems.forEach(function (stem) {
    const enPath = stem + '_en.md';
    const krPath = stem + '_kr.md';
    const basePath = stem + '.md';
    const en = has(enPath) ? enPath : null;
    const kr = has(krPath) ? krPath : null;
    const base = has(basePath) ? basePath : null;
    const pathKo = kr || base || en;
    const pathEn = en || base || kr;
    const slash = stem.lastIndexOf('/');
    const dir = slash === -1 ? '' : stem.substring(0, slash);
    const file = slash === -1 ? stem : stem.substring(slash + 1);
    const item = { nameKo: prettyName(file), nameEn: prettyName(file), pathKo: pathKo };
    if (pathEn && pathEn !== pathKo) item.pathEn = pathEn;
    if (!groups[dir]) { groups[dir] = []; order.push(dir); }
    groups[dir].push(item);
  });
  order.sort(function (a, b) {
    const ma = groupMeta(a);
    const mb = groupMeta(b);
    return ma.rank - mb.rank || ma.sub - mb.sub || a.localeCompare(b);
  });
  return order.map(function (dir) {
    const m = groupMeta(dir);
    const g = { titleKo: m.title, titleEn: m.title, items: groups[dir] };
    if (m.section) g.section = m.section;
    return g;
  });
}

const navData = JSON.stringify(buildNav(mdFiles));

const snapshotFile = path.join(docsDir, 'atlas-snapshot.json');
const atlasData = fs.existsSync(snapshotFile) ? fs.readFileSync(snapshotFile, 'utf8').trim() : 'null';

const templateFile = path.join(docsDir, '_viewer_template.html');
const template = fs.readFileSync(templateFile, 'utf8');
const html = template
  .replace('/*__MD_DATA__*/', 'const MD_DATA = ' + jsonData + ';')
  .replace('/*__NAV_DATA__*/[]', navData)
  .replace('/*__ATLAS_DATA__*/null', atlasData);

fs.writeFileSync(outFile, html, 'utf8');
const sizeKB = (Buffer.byteLength(html) / 1024).toFixed(1);
console.log('Built ' + outFile + ' (' + fileCount + ' docs, ' + sizeKB + ' KB)');
