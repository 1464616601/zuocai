/* 我的菜谱 · 网页预览版
   与 Android 版共用同一套数据模型与交互规则，数据存在浏览器 IndexedDB。 */

(() => {
'use strict';

const P = window.RecipeParser;

// ---------------------------------------------------------------- 图标

const I = {
  add: '<path d="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>',
  search: '<path d="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"/>',
  back: '<path d="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"/>',
  edit: '<path d="M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"/>',
  close: '<path d="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"/>',
  download: '<path d="M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z"/>',
  chevron: '<path d="M9,6l6,6 -6,6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>',
  upload: '<path d="M9,16h6v-6h4l-7,-7 -7,7h4zM5,18v2h14v-2H5z"/>',
  sort: '<path d="M3,18h6v-2H3V18zM3,6v2h18V6H3zM3,13h12v-2H3V13z"/>',
  check: '<path d="M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"/>',
  spark: '<path d="M12 2.6l1.9 5.1 5.1 1.9-5.1 1.9L12 16.6l-1.9-5.1L5 9.6l5.1-1.9L12 2.6zM18.6 15.2l.9 2.3 2.3.9-2.3.9-.9 2.3-.9-2.3-2.3-.9 2.3-.9.9-2.3z"/>',
  pin: '<path d="M17,3H7A2,2 0 0,0 5,5V21L12,18L19,21V5C19,3.89 18.1,3 17,3Z"/>',
  cart: '<path d="M7,18c-1.1,0 -1.99,0.9 -1.99,2S5.9,22 7,22s2,-0.9 2,-2 -0.9,-2 -2,-2zM1,2v2h2l3.6,7.59 -1.35,2.45c-0.16,0.28 -0.25,0.61 -0.25,0.96 0,1.1 0.9,2 2,2h12v-2L7.42,15c-0.14,0 -0.25,-0.11 -0.25,-0.25l0.03,-0.12 0.9,-1.63h7.45c0.75,0 1.41,-0.41 1.75,-1.03l3.58,-6.49c0.08,-0.14 0.12,-0.31 0.12,-0.48 0,-0.55 -0.45,-1 -1,-1L5.21,4l-0.94,-2L1,2zM17,18c-1.1,0 -1.99,0.9 -1.99,2s0.89,2 1.99,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"/>',
  setting: '<path d="M19.14 12.94a7.07 7.07 0 0 0 0-1.88l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7.03 7.03 0 0 0-1.62-.94l-.36-2.54a.5.5 0 0 0-.5-.42h-3.84a.5.5 0 0 0-.49.42l-.36 2.54c-.58.24-1.12.55-1.62.94l-2.39-.96a.5.5 0 0 0-.6.22L2.71 8.84a.5.5 0 0 0 .12.64l2.03 1.58a7.07 7.07 0 0 0 0 1.88l-2.03 1.58a.5.5 0 0 0-.12.64l1.92 3.32c.13.22.39.3.6.22l2.39-.96c.5.39 1.04.7 1.62.94l.36 2.54c.04.24.25.42.49.42h3.84c.25 0 .46-.18.5-.42l.36-2.54c.58-.24 1.12-.55 1.62-.94l2.39.96c.22.08.47 0 .6-.22l1.92-3.32a.5.5 0 0 0-.12-.64l-2.03-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z"/>',
  play: '<path d="M8 5.14v13.72L19.5 12z"/>',
};

const svg = (name, size = 22, opacity = 1) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="currentColor" style="opacity:${opacity}" aria-hidden="true">${I[name]}</svg>`;

const svg2 = (d, size = 24) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">${d}</svg>`;

const strokeIcon = (d, size = 24, sw = 1.7) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`;

const ICON_PHOTO = `<rect x="3" y="4.5" width="18" height="15" rx="3.4"/><circle cx="8.6" cy="9.9" r="1.7"/><path d="M3.6 16.8l4.9-4.7 3.6 3.5 3.2-3 5.1 4.5"/>`;
const ICON_BOWL = `<path d="M3.6 10.2h16.8c0 4.6-3.76 8.3-8.4 8.3s-8.4-3.7-8.4-8.3z"/><path d="M2.2 10.2h19.6"/>`;
const ICON_CLOCK = `<circle cx="12" cy="12" r="8.6"/><path d="M12 7.3V12l3.1 1.9"/>`;
const ICON_USER = `<circle cx="12" cy="8.2" r="3.5"/><path d="M5.6 19.4c0-3.2 2.9-5.3 6.4-5.3s6.4 2.1 6.4 5.3"/>`;
const ICON_FLAME = `<path d="M12 3.3c.5 2.6 2.2 3.6 2.2 6.1a2.15 2.15 0 0 1-4.3 0c0-.9.3-1.7.8-2.4-1.4 1.3-2.5 3-2.5 5.2a3.8 3.8 0 1 0 7.6 0c0-3.5-2.2-6.1-3.8-8.9z"/>`;

// ---------------------------------------------------------------- 常量

const CATEGORIES = [
  { key: 'home', label: '家常菜' },
  { key: 'soup', label: '汤羹' },
  { key: 'staple', label: '主食' },
  { key: 'cold', label: '凉菜' },
  { key: 'dessert', label: '甜点' },
  { key: 'other', label: '其他' },
];
const catLabel = (k) => (CATEGORIES.find((c) => c.key === k) || CATEGORIES[5]).label;
const DIFF = ['简单', '中等', '有点难'];

// 直连模式的出厂配置：和 App 端一致用智谱 glm-4-flash，带默认 Key，开箱即用。
// 用户在设置页填自己的 Key 后以用户的为准。
// 注意：Key 打包进前端文件就等于公开，公开发布前要换成服务端代理。
const DEFAULT_API = {
  baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
  model: 'glm-4-flash',
  key: '0214776f7c53423bb64eb4c20261dc0b.j8uG29SXkMoy0uNf',
};

const PLACEHOLDER = `把菜谱文本粘进来，自动识别菜名、食材和步骤。

# 花菜炒肉
### 食材
花菜半颗、瘦肉 200g、蒜 3 瓣、生抽 2 勺、盐
### 步骤
1. 花菜掰小朵，盐水泡 5 分钟，焯水 30 秒捞出。
2. 热锅放油，肉片大火炒至变色盛出。
3. 爆香蒜片，下花菜翻炒 2 分钟，倒回肉片调味出锅。
> 小贴士：焯水别太久，否则花菜软塌不清脆。`;

// ---------------------------------------------------------------- 数据层

const DB_NAME = 'zuocai-web';
const DB_VER = 1;
let _db = null;

function openDb() {
  if (_db) return Promise.resolve(_db);
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VER);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('recipes')) db.createObjectStore('recipes', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('images')) db.createObjectStore('images');
    };
    req.onsuccess = () => { _db = req.result; resolve(_db); };
    req.onerror = () => reject(req.error);
  });
}

function tx(store, mode) {
  return openDb().then((db) => db.transaction(store, mode).objectStore(store));
}

function reqToPromise(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

const dbAll = () => tx('recipes', 'readonly').then((s) => reqToPromise(s.getAll()));
const dbGet = (id) => tx('recipes', 'readonly').then((s) => reqToPromise(s.get(id)));
const dbPut = (r) => tx('recipes', 'readwrite').then((s) => reqToPromise(s.put(r)));
const dbDel = (id) => tx('recipes', 'readwrite').then((s) => reqToPromise(s.delete(id)));
const imgGet = (k) => tx('images', 'readonly').then((s) => reqToPromise(s.get(k)));
const imgPut = (k, b) => tx('images', 'readwrite').then((s) => reqToPromise(s.put(b, k)));
const imgDel = (k) => tx('images', 'readwrite').then((s) => reqToPromise(s.delete(k)));

// ---------------------------------------------------------------- 图片

const urlCache = new Map();

/**
 * 丢掉一张图的缓存。
 *
 * 必须连 Blob URL 一起 revoke：只 delete key 的话，那张图的数据会一直挂在内存里，
 * 换封面几十次之后页面就明显变卡了。
 */
function dropUrl(key) {
  if (!key) return;
  const url = urlCache.get(key);
  if (url) URL.revokeObjectURL(url);
  urlCache.delete(key);
}

async function imageUrl(key) {
  if (!key) return null;
  // 内置封面是静态文件，直接当相对路径用，不进 IndexedDB
  if (key.indexOf('covers/') === 0) return key;
  if (urlCache.has(key)) return urlCache.get(key);
  const blob = await imgGet(key);
  if (!blob) return null;
  const url = URL.createObjectURL(blob);
  urlCache.set(key, url);
  return url;
}

function compressImage(file, maxEdge = 1600, quality = 0.88) {
  return new Promise((resolve, reject) => {
    const src = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxEdge / Math.max(img.naturalWidth, img.naturalHeight));
      const w = Math.max(1, Math.round(img.naturalWidth * scale));
      const h = Math.max(1, Math.round(img.naturalHeight * scale));
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      canvas.getContext('2d').drawImage(img, 0, 0, w, h);
      canvas.toBlob((blob) => {
        URL.revokeObjectURL(src);
        blob ? resolve(blob) : reject(new Error('compress failed'));
      }, 'image/jpeg', quality);
    };
    img.onerror = () => { URL.revokeObjectURL(src); reject(new Error('decode failed')); };
    img.src = src;
  });
}

async function storeImage(file) {
  const blob = await compressImage(file);
  return storeBlob(blob);
}

async function storeBlob(blob) {
  const key = 'img_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
  await imgPut(key, blob);
  return key;
}

async function hydrateImages(scope) {
  const nodes = [...scope.querySelectorAll('[data-img]')];
  for (const el of nodes) {
    const key = el.getAttribute('data-img');
    if (!key) continue;
    const url = await imageUrl(key);
    if (url && el.getAttribute('data-img') === key) {
      el.src = url;
      el.style.display = 'block';
      const ph = el.parentElement.querySelector('.ph');
      if (ph) ph.style.display = 'none';
    }
  }
}

// ---------------------------------------------------------------- 状态

const state = {
  keyword: '',
  category: null,
  // 出厂默认「使用最多」；用户手动换过就记住他的选择
  sort: (() => {
    try { return localStorage.getItem('zuocai_sort') || 'usage'; } catch { return 'usage'; }
  })(),
  lastCountedKw: null,
  recipes: [],
  editId: null,
  editFrom: 'list',
  draft: null,
  dirty: false,
  userPickedCat: false,
};

const root = document.getElementById('screen');
const phone = document.querySelector('.phone');

// 本地服务代理：serve.py 会把请求转发给大模型，绕开浏览器跨域限制
const proxy = { on: false, configured: false, model: '', baseUrl: '' };

async function detectProxy() {
  if (!/^https?:$/.test(location.protocol)) return;
  try {
    const r = await fetch('/api/status', { cache: 'no-store' });
    if (!r.ok) return;
    const j = await r.json();
    if (j && j.proxy) {
      proxy.on = true;
      proxy.configured = !!j.configured;
      proxy.model = j.model || '';
      proxy.baseUrl = j.baseUrl || '';
    }
  } catch {
    /* 没有代理服务，走直连 */
  }
}

// ---------------------------------------------------------------- 工具

const esc = (s) =>
  String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
  );

const $ = (sel, scope = document) => scope.querySelector(sel);
const $$ = (sel, scope = document) => [...scope.querySelectorAll(sel)];

function toast(msg) {
  const old = $('.toast');
  if (old) old.remove();
  const el = document.createElement('div');
  el.className = 'toast';
  el.textContent = msg;
  phone.appendChild(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => {
    el.classList.remove('show');
    setTimeout(() => el.remove(), 240);
  }, 1800);
}

function dialog({ title, message, actions, custom }) {
  return new Promise((resolve) => {
    const mask = document.createElement('div');
    mask.className = 'mask';
    mask.innerHTML = `
      <div class="dialog">
        <h4>${esc(title)}</h4>
        ${custom || `<p>${esc(message || '')}</p>`}
        <div class="acts">
          ${actions.map((a, i) =>
            `<button data-i="${i}" class="${a.style || ''}">${esc(a.label)}</button>`
          ).join('')}
        </div>
      </div>`;
    phone.appendChild(mask);
    mask.addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-i]');
      if (btn) {
        const action = actions[Number(btn.dataset.i)];
        mask.remove();
        resolve(action.value);
      } else if (e.target === mask) {
        mask.remove();
        resolve(null);
      }
    });
  });
}

function newRecipe() {
  return {
    id: 0, name: '', category: 'home', cover: null, description: '',
    servings: 2, minutes: 0, difficulty: 1,
    ingredients: [], steps: [], createdAt: 0, updatedAt: 0,
    videoUrl: '', videoType: '',
  };
}

function nextId() {
  return state.recipes.reduce((m, r) => Math.max(m, r.id || 0), 0) + 1;
}

function clone(r) { return JSON.parse(JSON.stringify(r)); }
function debounce(fn, ms) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; }

// ---------------------------------------------------------------- 查询

function queryRecipes() {
  let list = state.recipes.slice();
  const words = searchWords();

  // 每个词都要在 菜名/描述/食材 之一出现（空格 = AND）
  if (words.length) {
    list = list.filter((r) => {
      const name = (r.name || '').toLowerCase();
      const desc = (r.description || '').toLowerCase();
      const ings = (r.ingredients || []).map((i) => i.n || '').join(' ').toLowerCase();
      return words.every((w) => name.includes(w) || desc.includes(w) || ings.includes(w));
    });

    // 相关度：菜名 2 分、描述 1 分，多词求和；命中食材不计分。
    // 不然搜「土豆」时腊肉焖饭（食材命中）可能排在土豆炒肉前面，看起来像搜错了。
    const rel = (r) => {
      const name = (r.name || '').toLowerCase();
      const desc = (r.description || '').toLowerCase();
      return words.reduce((s, w) =>
        s + (name.includes(w) ? 2 : desc.includes(w) ? 1 : 0), 0);
    };

    list.sort((a, b) => rel(b) - rel(a) || sortCmp(a, b));
  } else {
    list.sort(sortCmp);
  }
  return list;
}

/** 用户选的排序方式（相关度排在它前面，只在有搜索词时生效） */
function sortCmp(a, b) {
  // 常做的菜永远置顶，不受排序方式影响
  const pin = (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0);
  if (pin) return pin;
  if (state.sort === 'name') {
    return a.name.localeCompare(b.name, 'zh-Hans-CN');
  }
  if (state.sort === 'usage') {
    // 打开次数 + 搜索命中次数，热度相同的再按最近更新
    const heat = (r) => (r.openCount || 0) + (r.searchCount || 0);
    return heat(b) - heat(a) || (b.updatedAt || 0) - (a.updatedAt || 0) || b.id - a.id;
  }
  return (b.updatedAt || 0) - (a.updatedAt || 0) || b.id - a.id;
}

/** 使用量 +1：直接改内存对象，排序立刻生效，落库异步进行 */
function bumpUsage(recipe, field) {
  if (!recipe) return;
  recipe[field] = (recipe[field] || 0) + 1;
  dbPut(recipe).catch(() => {});
}

/** 搜索命中：菜名匹配的菜谱各 +1，同一个关键词只记一次 */
function countSearchHits(keyword) {
  const kw = (keyword || '').trim().toLowerCase();
  if (kw.length < 2) return;
  if (kw === state.lastCountedKw) return;
  state.lastCountedKw = kw;
  for (const r of state.recipes) {
    if (!(r.name || '').toLowerCase().includes(kw)) continue;
    r.searchCount = (r.searchCount || 0) + 1;
    dbPut(r).catch(() => {});
  }
}

// ---------------------------------------------------------------- 首页

function listView() {
  return `
  <div class="view enter-left">
    <div class="list-header">
      <div class="head-row">
        <div class="head-text">
          <h1>我的菜谱</h1>
          <div class="sub" id="listSub">记录每一次下厨的味道</div>
        </div>
        <div class="head-actions">
          <button class="round-btn" id="btnCart" aria-label="买菜清单" title="买菜清单">${svg('cart', 19)}</button>
          <button class="round-btn" id="btnImport" aria-label="导入" title="导入">${svg('download', 19)}</button>
          <button class="round-btn" id="btnExport" aria-label="导出" title="导出">${svg('upload', 19)}</button>
        </div>
      </div>
    </div>

    <div class="searchbar">
      ${svg('search', 19)}
      <input id="searchInput" type="text" placeholder="搜索菜名或食材，空格分隔多个词" autocomplete="off" value="${esc(state.keyword)}">
      <button class="clear ${state.keyword ? 'show' : ''}" id="searchClear" aria-label="清空">${svg('close', 20)}</button>
    </div>

    <div class="chipbar" id="chipBar"></div>
    <div class="scroll" id="listScroll"><div id="gridWrap"></div></div>

    <button class="fab" id="fabAdd" aria-label="新建菜谱">${svg('add', 26)}</button>
    <button class="fab-mini" id="fabSort" aria-label="排序">${svg('sort', 19)}</button>
  </div>`;
}

/** 搜索词数组，多处要用（过滤、相关度、高亮、命中原因） */
function searchWords() {
  return state.keyword.trim().toLowerCase().split(/\s+/).filter(Boolean);
}

/** 命中片段标成品牌色 */
function highlight(text, words) {
  if (!words.length || !text) return esc(text);
  let out = esc(text);
  for (const w of words) {
    const e = esc(w).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    out = out.replace(new RegExp(`(${e})`, 'gi'), '<mark>$1</mark>');
  }
  return out;
}

function cardHTML(r) {
  const words = searchWords();
  const name = r.name || '未命名';

  let meta = [
    DIFF[(r.difficulty || 1) - 1],
    r.minutes > 0 ? `${r.minutes}分钟` : null,
    r.servings > 0 ? `${r.servings}人份` : null,
  ].filter(Boolean).join(' · ');

  // 菜名没含搜索词但被搜出来，多半是食材命中——告诉用户原因，
  // 不然搜「土豆」冒出腊肉焖饭会像出了 bug
  const nameHit = !words.length || words.some((w) => name.toLowerCase().includes(w));
  if (!nameHit) {
    const ing = (r.ingredients || []).find((i) =>
      words.some((w) => ((i.n || '') + ' ' + (i.a || '')).toLowerCase().includes(w)));
    meta = `${ing ? `食材含「${ing.n}」` : '描述命中'} · ${meta}`;
  }

  return `
  <div class="card" data-id="${r.id}">
    <div class="cover">
      ${r.cover ? `<img data-img="${esc(r.cover)}" alt="" style="display:none">` : ''}
      <span class="ph">${strokeIcon(ICON_BOWL, 32, 1.5)}</span>
      <span class="badge">${esc(catLabel(r.category))}</span>
      ${r.pinned ? '<span class="badge pinb">常做</span>' : ''}
    </div>
    <div class="body">
      <div class="name">${highlight(name, words)}</div>
      <div class="meta">${esc(meta)}</div>
    </div>
  </div>`;
}

function renderChips() {
  const bar = $('#chipBar');
  if (!bar) return;
  const items = [{ key: null, label: '全部' }, ...CATEGORIES];
  bar.innerHTML = items.map((c) =>
    `<button class="chip ${state.category === c.key ? 'on' : ''}" data-cat="${c.key ?? ''}">${c.label}</button>`
  ).join('');

  bar.onclick = (e) => {
    const btn = e.target.closest('.chip');
    if (!btn) return;
    state.category = btn.dataset.cat || null;
    renderChips();
    renderGrid();
  };
}

function renderGrid() {
  const wrap = $('#gridWrap');
  if (!wrap) return;

  const list = queryRecipes();
  const sub = $('#listSub');
  if (sub) {
    sub.textContent = state.recipes.length ? `共 ${state.recipes.length} 道菜谱` : '记录每一次下厨的味道';
  }

  if (list.length === 0) {
    const filtering = !!state.keyword.trim() || !!state.category;
    wrap.innerHTML = `
      <div class="empty">
        <div class="box">${strokeIcon(ICON_BOWL, 44, 1.4)}</div>
        <h3>${filtering ? '没有找到菜谱' : '还没有菜谱'}</h3>
        <p>${filtering ? '换个关键词或分类试试' : '把拿手好菜记下来，下次照着做'}</p>
        ${filtering ? '' : '<button class="btn" id="emptyAdd">添加第一道菜</button>'}
      </div>`;
    const btn = $('#emptyAdd');
    if (btn) btn.onclick = () => openEditor(null, 'list');
    return;
  }

  wrap.innerHTML = `<div class="grid">${list.map(cardHTML).join('')}</div>`;
  hydrateImages(wrap);

  wrap.onclick = (e) => {
    const card = e.target.closest('.card');
    if (card) showDetail(Number(card.dataset.id));
  };
}

function showList(transition = 'left') {
  root.innerHTML = listView();
  const view = $('.view', root);
  view.classList.remove('enter-left');
  if (transition === 'right') view.classList.add('enter-right');

  renderChips();
  renderGrid();

  const input = $('#searchInput');
  const doSearch = debounce(() => {
    state.keyword = input.value;
    countSearchHits(state.keyword);
    renderGrid();
  }, 260);

  input.addEventListener('input', () => {
    $('#searchClear').classList.toggle('show', !!input.value);
    doSearch();
  });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') input.blur(); });

  $('#searchClear').onclick = () => {
    input.value = '';
    state.keyword = '';
    state.lastCountedKw = null;
    $('#searchClear').classList.remove('show');
    renderGrid();
    input.focus();
  };

  $('#fabAdd').onclick = () => openEditor(null, 'list');
  $('#btnCart').onclick = openShopping;
  $('#btnExport').onclick = doExport;
  $('#btnImport').onclick = () => pickImportFile();
  $('#fabSort').onclick = showSortMenu;
}

function showSortMenu() {
  const old = $('.menu');
  if (old) { old.remove(); return; }

  const menu = document.createElement('div');
  menu.className = 'menu';
  const opts = [
    { key: 'recent', label: '最近更新' },
    { key: 'name', label: '按菜名' },
    { key: 'usage', label: '使用最多' },
  ];
  menu.innerHTML = opts.map((o) =>
    `<button data-sort="${o.key}" class="${state.sort === o.key ? 'on' : ''}">
       <span>${o.label}</span>${state.sort === o.key ? svg('check', 16) : ''}
     </button>`).join('')
    + '<div class="menu-sep"></div>'
    + '<button data-act="games" class="menu-game"><span>🎮 放松一下</span></button>';

  $('.view', root).appendChild(menu);

  menu.onclick = (e) => {
    const game = e.target.closest('button[data-act="games"]');
    if (game) {
      menu.remove();
      showGames();
      return;
    }
    const btn = e.target.closest('button[data-sort]');
    if (!btn) return;
    state.sort = btn.dataset.sort;
    try { localStorage.setItem('zuocai_sort', state.sort); } catch { /* 忽略 */ }
    menu.remove();
    renderGrid();
  };

  setTimeout(() => {
    const close = (ev) => {
      if (!menu.contains(ev.target) && ev.target.id !== 'fabSort') {
        menu.remove();
        document.removeEventListener('click', close);
      }
    };
    document.addEventListener('click', close);
  }, 0);
}

// ---------------------------------------------------------------- 小游戏

const GAME_ICONS = {
  snake: '<path d="M5 6h8a3 3 0 0 1 0 6H9a3 3 0 0 0 0 6h9"/><circle cx="18.5" cy="6.5" r="1.6"/>',
  tetris: '<rect x="4" y="4" width="6" height="6" rx="1.2"/><rect x="10" y="10" width="6" height="6" rx="1.2"/><rect x="14" y="4" width="6" height="6" rx="1.2"/><rect x="8" y="16" width="6" height="4" rx="1.2"/>',
  box: '<rect x="4.5" y="7" width="15" height="12" rx="2"/><path d="M4.5 11h15M12 7v12"/>',
  plane: '<path d="M12 3.2c.9 0 1.6 1.3 1.6 3.2v2.3l5.5 3.2c.6.35.9.8.9 1.5v1.2l-6.4-1.9v3.6l1.8 1.4v1.2L12 18.1l-3.4 1.9v-1.2l1.8-1.4v-3.6l-6.4 1.9v-1.2c0-.7.3-1.15.9-1.5l5.5-3.2V6.4c0-1.9.7-3.2 1.6-3.2z"/>',
  tank: '<rect x="4" y="10" width="16" height="8" rx="2.5"/><path d="M11 10V6h5"/><circle cx="8" cy="19.5" r="1.4"/><circle cx="16" cy="19.5" r="1.4"/>',
  jump: '<rect x="3.5" y="17.5" width="6" height="3.5" rx="1.2"/><rect x="14.5" y="14.5" width="6" height="6.5" rx="1.2"/><path d="M6.5 15.5C8 10.5 14.5 10.5 16.5 13.5"/><circle cx="6.5" cy="12.2" r="1.7"/>',
};

function gameIcon(name, size = 26) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"
    aria-hidden="true">${GAME_ICONS[name] || GAME_ICONS.box}</svg>`;
}

const bestKey = (k) => 'zuocai_best_' + k;
const getBest = (k) => Number(localStorage.getItem(bestKey(k)) || 0);
const setBest = (k, v) => localStorage.setItem(bestKey(k), String(v));

function showGames() {
  // 清掉可能残留的结算遮罩（例如中途退出）
  phone.querySelectorAll('.mask').forEach((m) => m.remove());
  const meta = window.Games.META;
  root.innerHTML = `
  <div class="view enter-right">
    <div class="edit-top">
      <button class="icon-btn" id="gamesBack" aria-label="返回">${svg('back', 21)}</button>
      <div class="title">放松一下</div>
      <span style="width:40px"></span>
    </div>
    <div class="scroll">
      <div class="games-body">
        <div class="games-intro">写菜谱累了就玩两分钟。分数只存在本机。</div>
        ${meta.map((g) => {
          const best = getBest(g.key);
          return `
          <button class="game-card" data-key="${g.key}">
            <span class="game-ico">${gameIcon(g.icon)}</span>
            <span class="game-info">
              <span class="game-name">${g.name}</span>
              <span class="game-desc">${g.desc}</span>
            </span>
            <span class="game-best">${best > 0 ? '最高 ' + best : ''}</span>
            ${svg('chevron', 18)}
          </button>`;
        }).join('')}
      </div>
    </div>
  </div>`;

  $('#gamesBack').onclick = () => showList('left');
  root.querySelectorAll('.game-card').forEach((c) => {
    c.onclick = () => openGame(c.dataset.key);
  });
}

// ------------------------------------------------ 游戏反馈：音效 + 震动

// 音效用 WebAudio 现场合成，不加载任何音频文件；震动用 navigator.vibrate。
// 事件名与 Android 端 Game.drainEvents 的约定一致。
const GameAudio = (() => {
  let actx = null;
  function ctx() {
    if (!actx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (AC) actx = new AC();
    }
    if (actx && actx.state === 'suspended') actx.resume();
    return actx;
  }
  function tone(freq, dur, type, vol, slideTo) {
    const a = ctx();
    if (!a) return;
    const o = a.createOscillator();
    const g = a.createGain();
    o.type = type || 'triangle';
    o.frequency.setValueAtTime(freq, a.currentTime);
    if (slideTo) o.frequency.linearRampToValueAtTime(slideTo, a.currentTime + dur);
    g.gain.setValueAtTime(vol, a.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, a.currentTime + dur);
    o.connect(g).connect(a.destination);
    o.start();
    o.stop(a.currentTime + dur);
  }
  function noise(dur, vol) {
    const a = ctx();
    if (!a) return;
    const len = Math.floor(a.sampleRate * dur);
    const buf = a.createBuffer(1, len, a.sampleRate);
    const d = buf.getChannelData(0);
    let last = 0;
    for (let i = 0; i < len; i++) {
      last = last * 0.72 + (Math.random() * 2 - 1) * 0.28;
      d[i] = last;
    }
    const src = a.createBufferSource();
    const g = a.createGain();
    g.gain.setValueAtTime(vol, a.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, a.currentTime + dur);
    src.buffer = buf;
    src.connect(g).connect(a.destination);
    src.start();
  }
  const table = {
    shoot: () => tone(880, 0.07, 'square', 0.08, 380),
    hit: () => { noise(0.06, 0.12); tone(500, 0.06, 'triangle', 0.14, 700); },
    boom: () => { tone(160, 0.42, 'triangle', 0.3, 60); noise(0.4, 0.3); },
    hurt: () => { tone(320, 0.09, 'square', 0.2, 0); setTimeout(() => tone(220, 0.13, 'square', 0.2, 0), 90); },
    eat: () => { tone(660, 0.055, 'triangle', 0.2, 0); setTimeout(() => tone(990, 0.075, 'triangle', 0.2, 0), 55); },
    line: () => [523, 659, 784, 1047].forEach((f, i) => setTimeout(() => tone(f, 0.09, 'triangle', 0.22, 0), i * 60)),
    drop: () => { tone(200, 0.11, 'triangle', 0.25, 110); noise(0.09, 0.2); },
    push: () => { noise(0.07, 0.15); tone(240, 0.07, 'triangle', 0.18, 300); },
    win: () => [523, 659, 784, 1047].forEach((f, i) => setTimeout(() => tone(f, 0.09, 'triangle', 0.24, 0), i * 90)),
    over: () => [523, 392, 262].forEach((f, i) => setTimeout(() => tone(f, 0.16, 'triangle', 0.24, 0), i * 160)),
  };
  return { play(name) { try { (table[name] || (() => {}))(); } catch { /* 忽略 */ } } };
})();

function buzz(pattern) {
  try { if (navigator.vibrate) navigator.vibrate(pattern); } catch { /* 忽略 */ }
}

// 事件 → 音效 + 震动强度
const GAME_FX = {
  shoot: null, hit: null, eat: null, line: null, win: null,
  boom: 32, hurt: 48, drop: 20, push: 16, over: [40, 70, 40, 70, 90],
};

function playGameEvents(events) {
  if (!events || !events.length) return;
  for (const name of events) {
    GameAudio.play(name);
    const v = GAME_FX[name];
    if (v) buzz(v);
  }
}

let gameRaf = null;
let gameInst = null;
let gameKey = null;
/** 离开游戏页要摘掉的键盘/尺寸监听 */
let gameCleanup = null;
/** 重新拉起主循环（复活用） */
let gameStartLoop = null;

function stopGame() {
  if (gameRaf) cancelAnimationFrame(gameRaf);
  gameRaf = null;
  gameInst = null;
  gameStartLoop = null;
  // 以前只在「返回列表」那条路径摘监听，点「再来一局」会再挂一份，
  // 结果是第二局里每按一次键动作执行两次
  if (gameCleanup) {
    gameCleanup();
    gameCleanup = null;
  }
}

function openGame(key) {
  const meta = window.Games.META.find((g) => g.key === key);
  if (!meta) return;
  phone.querySelectorAll('.mask').forEach((m) => m.remove());
  stopGame();

  gameKey = key;
  root.innerHTML = `
  <div class="view enter-right game-view">
    <div class="game-top">
      <button class="icon-btn" id="gameBack" aria-label="返回">${svg('back', 21)}</button>
      <div class="game-head">
        <div class="game-title">${meta.name}</div>
        <div class="game-sub" id="gameLabel">${meta.hint}</div>
      </div>
      <div class="game-score pill"><span>本局</span><b id="gameScore">0</b></div>
      <div class="game-score pill ghost"><span>最高</span><b id="gameBest">0</b></div>
    </div>

    <div class="game-stage" id="gameStage">
      <canvas id="gameCanvas"></canvas>
    </div>

    <div class="game-pad${meta.pad === 'single' ? ' single' : ''}">
      ${meta.pad === 'single' ? '' : `
      <div class="dpad">
        <button class="pad-btn up" data-k="up" aria-label="上"><i class="pad-ic up">${svg('chevron', 24)}</i></button>
        <button class="pad-btn left" data-k="left" aria-label="左"><i class="pad-ic left">${svg('chevron', 24)}</i></button>
        <button class="pad-btn right" data-k="right" aria-label="右"><i class="pad-ic right">${svg('chevron', 24)}</i></button>
        <button class="pad-btn down" data-k="down" aria-label="下"><i class="pad-ic down">${svg('chevron', 24)}</i></button>
      </div>`}
      <div class="acts">
        <button class="act-btn${meta.pad === 'single' ? ' big' : ''}" data-k="a">${meta.action || 'A'}</button>
        ${meta.pad === 'single' ? '' : '<button class="act-btn alt" data-k="b">B</button>'}
      </div>
    </div>
  </div>`;

  $('#gameBack').onclick = () => { saveBestIfBetter(key); stopGame(); showGames(); };

  const canvas = $('#gameCanvas');
  const stage = $('#gameStage');
  const scoreEl = $('#gameScore');
  const labelEl = $('#gameLabel');

  gameInst = window.Games.create(key);
  if (!gameInst) { showGames(); return; }

  let ctx = null;
  let W = 0;
  let H = 0;

  function fit() {
    const rect = stage.getBoundingClientRect();
    const dpr = Math.min(3, window.devicePixelRatio || 1);
    W = Math.max(120, Math.floor(rect.width));
    H = Math.max(120, Math.floor(rect.height));
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    canvas.style.width = W + 'px';
    canvas.style.height = H + 'px';
    ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    gameInst.resize(W, H);
  }

  // 等一帧，等布局算完再量尺寸
  requestAnimationFrame(() => {
    fit();
    gameInst.reset();
    let last = performance.now();

    function tick(t) {
      const dt = Math.min(60, t - last);
      last = t;

      gameInst.update(dt);
      playGameEvents(gameInst.state.events);
      if (gameInst.state.events) gameInst.state.events.length = 0;
      gameInst.render(ctx, W, H);

      scoreEl.textContent = gameInst.state.score;
      labelEl.textContent = gameInst.state.label || meta.hint;

      if (gameInst.state.over) {
        const bestEl = $('#gameBest');
        if (bestEl) {
          bestEl.textContent = Math.max(Number(bestEl.textContent) || 0, gameInst.state.score || 0);
        }
        finishGame(key, meta.name, gameInst.state.score);
        return;
      }
      gameRaf = requestAnimationFrame(tick);
    }

    // 复活时用它把循环重新拉起来，不用重建整页 DOM
    gameStartLoop = () => {
      if (!gameInst) return;
      last = performance.now();
      if (gameRaf) cancelAnimationFrame(gameRaf);
      gameRaf = requestAnimationFrame(tick);
    };
    gameStartLoop();
  });

  // 虚拟按键：按住连续触发
  root.querySelectorAll('[data-k]').forEach((btn) => {
    const action = btn.dataset.k;
    let timer = null;
    let delay = null;

    const fire = (e) => {
      e.preventDefault();
      if (!gameInst) return;
      gameInst.input(action);
    };
    const start = (e) => {
      buzz(14);
      if (gameInst && gameInst.press) gameInst.press(action);
      fire(e);
      // 跳一跳靠「按住蓄力」，不能连发，否则一按就跳出去了
      if (!gameInst || gameInst.holdRepeat === false) return;
      delay = setTimeout(() => {
        timer = setInterval(() => { if (gameInst) gameInst.input(action); }, 95);
      }, 280);
    };
    const stop = () => {
      clearTimeout(delay);
      clearInterval(timer);
      timer = null;
      if (gameInst && gameInst.release) gameInst.release(action);
    };

    btn.addEventListener('pointerdown', start);
    btn.addEventListener('pointerup', stop);
    btn.addEventListener('pointercancel', stop);
    btn.addEventListener('pointerleave', stop);
    btn.addEventListener('contextmenu', (e) => e.preventDefault());
  });

  // 键盘
  const keyMap = {
    ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
    w: 'up', s: 'down', a: 'left', d: 'right',
    ' ': 'a', z: 'a', x: 'b', j: 'b', k: 'a',
  };
  const onKey = (e) => {
    const act = keyMap[e.key];
    if (!act || !gameInst) return;
    e.preventDefault();
    // 长按住时浏览器会连发 keydown，蓄力游戏只认第一次
    if (!e.repeat && gameInst.press) gameInst.press(act);
    gameInst.input(act);
  };
  const onKeyUp = (e) => {
    const act = keyMap[e.key];
    if (!act || !gameInst) return;
    if (gameInst.release) gameInst.release(act);
  };
  document.addEventListener('keydown', onKey);
  document.addEventListener('keyup', onKeyUp);

  // 窗口尺寸变化（旋转屏幕 / 拖窗口）
  const onResize = () => {
    if (!gameInst) return;
    const before = { score: gameInst.state.score };
    fit();
    gameInst.state.score = before.score;
  };
  window.addEventListener('resize', onResize);

  // 统一交给 stopGame() 摘，保证每条离开路径都会清理
  gameCleanup = () => {
    document.removeEventListener('keydown', onKey);
    document.removeEventListener('keyup', onKeyUp);
    window.removeEventListener('resize', onResize);
  };
}

/** 中途退出也要记住最高分，否则玩得再高也白费 */
function saveBestIfBetter(key) {
  if (!gameInst) return;
  const s = gameInst.state.score || 0;
  if (s > getBest(key)) setBest(key, s);
}

function finishGame(key, name, score) {
  const prev = getBest(key);
  const isNew = score > prev;
  if (isNew) setBest(key, score);

  // 只停循环，先别拆监听：跳一跳点「复活」还要接着玩
  if (gameRaf) {
    cancelAnimationFrame(gameRaf);
    gameRaf = null;
  }

  const inst = gameInst;
  const canRevive = !!(inst && typeof inst.canRevive === 'function' && inst.canRevive());

  const mask = document.createElement('div');
  mask.className = 'mask';
  mask.innerHTML = `
    <div class="dialog gameover">
      <h4>${isNew ? '新纪录！' : '本局结束'}</h4>
      <p>${name}　得分 <b>${score}</b>${isNew || prev <= 0 ? '' : `<br>最高分 ${prev}`}</p>
      <div class="acts">
        ${canRevive ? '<button class="primary" id="goRevive">复活继续（无限）</button>' : ''}
        <button${canRevive ? '' : ' class="primary"'} id="goAgain">再来一局</button>
        <button id="goBack">返回游戏列表</button>
      </div>
    </div>`;
  phone.appendChild(mask);

  if (canRevive) {
    mask.querySelector('#goRevive').onclick = () => {
      mask.remove();
      inst.revive();
      gameInst = inst;
      if (gameStartLoop) gameStartLoop();
    };
  }

  mask.querySelector('#goAgain').onclick = () => {
    mask.remove();
    stopGame();
    openGame(key);
  };
  mask.querySelector('#goBack').onclick = () => {
    mask.remove();
    stopGame();
    showGames();
  };
}

// ---------------------------------------------------------------- 做法管理

/** 长按变体标签：重命名或删除。只动变体，原做法不受影响 */
async function manageVariant(id, index) {
  const r = state.recipes.find((x) => x.id === id);
  const v = r && (r.variants || [])[index];
  if (!v) return;
  const label = v.label || '做法';

  const act = await dialog({
    title: '做法管理 · ' + label,
    actions: [
      { label: '重命名', value: 'rename' },
      { label: '删除', value: 'delete', style: 'danger' },
      { label: '取消', value: null },
    ],
  });

  if (act === 'rename') {
    const name = await askText({
      title: '做法名称', message: '', placeholder: label, okLabel: '确定',
    });
    if (!name) return;
    v.label = name;
    await dbPut(r);
    showDetail(id);
    toast('已重命名');
  } else if (act === 'delete') {
    r.variants.splice(index, 1);
    await dbPut(r);
    if (detailVariant >= (r.variants || []).length) detailVariant = -1;
    showDetail(id);
    toast('已删除');
  }
}

// ---------------------------------------------------------------- 买菜清单

const SHOP_KEY = 'zuocai_shopping';

function shopLoad() {
  try { return JSON.parse(localStorage.getItem(SHOP_KEY) || '[]'); } catch { return []; }
}
function shopSave(items) {
  try { localStorage.setItem(SHOP_KEY, JSON.stringify(items)); } catch { /* 忽略 */ }
}

/** 几道菜的食材并进清单。同名合并：用量一样只留一条，不一样就并排（没法做加法） */
function shopAdd(recipes) {
  const items = shopLoad();
  let changed = 0;
  for (const r of recipes) {
    for (const ing of (r.ingredients || [])) {
      const name = (ing.n || '').trim();
      if (!name) continue;
      const amount = (ing.a || '').trim();
      const exist = items.find((i) => i.name.toLowerCase() === name.toLowerCase());
      if (!exist) {
        items.push({ name, amount, checked: false });
        changed++;
      } else if (amount && amount !== exist.amount && !exist.amount.split(' / ').includes(amount)) {
        exist.amount = [exist.amount, amount].filter(Boolean).join(' / ');
        changed++;
      }
    }
  }
  shopSave(items);
  return changed;
}

async function shopDelete(idx, render) {
  const items = shopLoad();
  const it = items[idx];
  if (!it) return;
  const ok = await dialog({
    title: '删除',
    message: `删除「${it.name}」？`,
    actions: [
      { label: '取消', value: false },
      { label: '删除', value: true, style: 'danger' },
    ],
  });
  if (!ok) return;
  items.splice(idx, 1);
  shopSave(items);
  render();
  toast('已删除');
}

function openShopping() {
  root.innerHTML = `
  <div class="view enter-right">
    <div class="edit-top">
      <button class="icon-btn" id="shopBack" aria-label="返回">${svg('back', 21)}</button>
      <div class="title">买菜清单</div>
      <button class="btn-primary" id="shopAdd" style="min-width:auto;padding:0 14px;font-size:13px;height:36px">从菜谱添加</button>
    </div>
    <div class="scroll">
      <div class="shop-list" id="shopList"></div>
    </div>
    <div class="shop-foot">
      <button id="shopClearChecked">清空已勾选</button>
      <button id="shopClearAll" class="danger">清空全部</button>
    </div>
  </div>`;

  const render = () => {
    const items = shopLoad();
    const list = $('#shopList');
    if (!items.length) {
      list.innerHTML = `<div class="shop-empty">清单还是空的<br>点右上「从菜谱添加」，选几道菜，食材就汇总好了</div>`;
    } else {
      list.innerHTML = items.map((it, i) => `
        <div class="shop-item ${it.checked ? 'done' : ''}" data-i="${i}">
          <span class="shop-check">${it.checked ? svg('check', 13) : ''}</span>
          <div class="shop-name">${esc(it.name)}<em>${esc(it.amount || '')}</em></div>
        </div>`).join('');
    }
    $('#shopClearChecked').style.visibility = items.some((i) => i.checked) ? 'visible' : 'hidden';
    $('#shopClearAll').style.visibility = items.length ? 'visible' : 'hidden';
  };

  const listEl = $('#shopList');

  // 点行勾/取消；长按删除
  let lpTimer = null;
  let lpFired = false;
  listEl.addEventListener('pointerdown', (e) => {
    const row = e.target.closest('.shop-item');
    if (!row) return;
    lpFired = false;
    const idx = Number(row.dataset.i);
    lpTimer = setTimeout(() => { lpFired = true; lpTimer = null; shopDelete(idx, render); }, 500);
  });
  ['pointerup', 'pointerleave', 'pointercancel', 'pointermove'].forEach((ev) =>
    listEl.addEventListener(ev, () => { if (lpTimer) { clearTimeout(lpTimer); lpTimer = null; } }));
  listEl.addEventListener('contextmenu', (e) => {
    const row = e.target.closest('.shop-item');
    if (!row) return;
    e.preventDefault();
    shopDelete(Number(row.dataset.i), render);
  });
  listEl.addEventListener('click', (e) => {
    if (lpFired) { lpFired = false; return; }
    const row = e.target.closest('.shop-item');
    if (!row) return;
    const items = shopLoad();
    const it = items[Number(row.dataset.i)];
    if (!it) return;
    it.checked = !it.checked;
    shopSave(items);
    render();
  });

  $('#shopBack').onclick = () => showList('left');

  $('#shopClearChecked').onclick = () => {
    shopSave(shopLoad().filter((i) => !i.checked));
    render();
  };
  $('#shopClearAll').onclick = async () => {
    if (!shopLoad().length) return;
    const ok = await dialog({
      title: '清空',
      message: '清空全部清单？',
      actions: [
        { label: '取消', value: false },
        { label: '清空', value: true, style: 'danger' },
      ],
    });
    if (!ok) return;
    shopSave([]);
    render();
    toast('已清空');
  };

  $('#shopAdd').onclick = () => {
    const rs = state.recipes;
    if (!rs.length) { toast('还没有菜谱'); return; }
    const mask = document.createElement('div');
    mask.className = 'mask';
    mask.innerHTML = `
      <div class="dialog shop-picker">
        <h4>选择要买的菜（可多选）</h4>
        <div class="shop-pick-list">
          ${rs.map((r, i) => `
            <label class="shop-pick"><input type="checkbox" data-i="${i}"><span>${esc(r.name)}</span></label>`).join('')}
        </div>
        <div class="acts" style="margin-top:12px">
          <button data-v="0">取消</button>
          <button data-v="1" class="primary">加入清单</button>
        </div>
      </div>`;
    phone.appendChild(mask);
    mask.addEventListener('click', (e) => {
      const b = e.target.closest('button[data-v]');
      if (b) {
        const go = b.dataset.v === '1';
        const chosen = [...mask.querySelectorAll('input:checked')]
          .map((i) => rs[Number(i.dataset.i)]);
        mask.remove();
        if (go && chosen.length) {
          const n = shopAdd(chosen);
          toast(`已加入 ${n} 项食材`);
          render();
        }
      } else if (e.target === mask) {
        mask.remove();
      }
    });
  };

  render();
}

// ---------------------------------------------------------------- 视频

function videoCardHTML(r, previewing) {
  if (!r.videoUrl || previewing) return '';
  const type = r.videoType || P.detectVideoType(r.videoUrl);
  const site = P.videoSiteLabel(r.videoUrl);
  const inside = P.videoCanPlayInside(r.videoUrl, type);
  return `
    <div class="video-card" id="videoCard">
      <span class="vplay">${svg('play', 15)}</span>
      <span class="vtext">
        <b>${esc(site)} 视频</b>
        <i>${inside ? '点击播放' : '需跳转浏览器或原 App'}</i>
      </span>
      <span class="vgo">${inside ? '播放' : '打开'}</span>
    </div>
    <div class="video-stage" id="videoStage" hidden></div>`;
}

/**
 * 网页端能内嵌的只有直链和 B 站官方播放器。
 * 小红书这类平台会返回 X-Frame-Options，iframe 必然加载失败，只能开新标签。
 */
function playVideo(r) {
  const stage = $('#videoStage');
  if (!stage || !r.videoUrl) return;

  const type = r.videoType || P.detectVideoType(r.videoUrl);
  const embed = P.bilibiliEmbed(r.videoUrl);

  if (type === 'direct') {
    stage.innerHTML = `<video controls autoplay playsinline preload="metadata" src="${esc(r.videoUrl)}"></video>`;
  } else if (embed) {
    stage.innerHTML = `<iframe src="${esc(embed)}" frameborder="0" scrolling="no"
      allowfullscreen allow="autoplay; fullscreen"></iframe>`;
  } else {
    window.open(r.videoUrl, '_blank', 'noopener');
    toast('该平台不允许网页内嵌，已在新标签打开');
    return;
  }
  stage.hidden = false;
  stage.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}

// ---------------------------------------------------------------- 详情页

// 做法切换状态：detailVariant = -1 表示原做法
let detailId = null;
let detailVariant = -1;
let detailPending = null;   // 生成好、还没保存的做法

function showDetail(id, transition = 'right') {
  const r = state.recipes.find((x) => x.id === id);
  if (!r) { showList(); return; }

  if (detailId !== id) { detailVariant = -1; detailPending = null; }
  detailId = id;

  bumpUsage(r, 'openCount');

  const variants = r.variants || [];
  if (detailVariant >= variants.length) detailVariant = -1;

  const previewing = !!detailPending;
  const shown = detailPending || variants[detailVariant] || null;

  const meta = [];
  if (r.minutes > 0) meta.push(`<span class="meta-item">${strokeIcon(ICON_CLOCK, 14)}${r.minutes}分钟</span>`);
  if (r.servings > 0) meta.push(`<span class="meta-item">${strokeIcon(ICON_USER, 14)}${r.servings}人份</span>`);
  meta.push(`<span class="meta-item">${svg2(ICON_FLAME, 14)}${DIFF[(r.difficulty || 1) - 1]}</span>`);

  const ings = ((shown && shown.ingredients) || r.ingredients || []).filter((i) => i.n || i.a);
  const steps = ((shown && shown.steps) || r.steps || []).filter((s) => s && s.trim());
  const desc = ((shown ? shown.description : r.description) || '').trim();

  const labels = ['原做法'].concat(variants.map((v) => v.label || '做法'));
  const chipRow = (!previewing && variants.length)
    ? `<div class="chipbar wrap" id="variantRow">${labels.map((label, i) =>
        `<button class="chip ${i - 1 === detailVariant ? 'on' : ''}" data-v="${i - 1}">${esc(label)}</button>`
      ).join('')}</div>`
    : '';

  root.innerHTML = `
  <div class="view enter-right">
    <div class="scroll">
      <div style="padding-bottom:${previewing ? 132 : 32}px">
        <div class="detail-cover">
          ${r.cover ? `<img data-img="${esc(r.cover)}" alt="" style="display:none">` : ''}
          <span class="ph">${strokeIcon(ICON_BOWL, 58, 1.3)}</span>
        </div>

        <div class="sheet">
          <h2>${esc(r.name || '未命名')}</h2>
          <div class="info-row">
            <span class="pill">${esc(catLabel(r.category))}</span>
            ${meta.join('')}
          </div>
          ${chipRow}
          <div class="desc ${desc ? '' : 'muted'}">${esc(desc || '暂无描述')}</div>

          ${videoCardHTML(r, previewing)}

          ${ings.length ? `
            <hr class="sep">
            <h3 class="sec-title">食材清单</h3>
            <div>${ings.map((i) => `
              <div class="ing">
                <span class="dot"></span>
                <span class="n">${esc(i.n || '—')}</span>
                <span class="a">${esc(i.a || '')}</span>
              </div>`).join('')}</div>` : ''}

          ${steps.length ? `
            <hr class="sep">
            <h3 class="sec-title">做法步骤</h3>
            <div>${steps.map((s, idx) => `
              <div class="step">
                <span class="num">${idx + 1}</span>
                <span class="txt">${esc(s)}</span>
              </div>`).join('')}</div>` : ''}
        </div>
      </div>
    </div>

    <button class="circle-btn left" id="btnBack" aria-label="返回">${svg('back', 21)}</button>
    ${previewing ? '' : `<button class="circle-btn right" id="btnEdit" aria-label="编辑">${svg('edit', 19)}</button>`}
    ${previewing ? '' : `<button class="circle-btn right" id="btnAi" style="right:68px" aria-label="换个做法">${svg('spark', 19)}</button>`}
    ${previewing ? '' : `<button class="circle-btn right ${r.pinned ? 'pinned' : ''}" id="btnPin" style="right:112px" aria-label="常做" title="常做">${svg('pin', 19)}</button>`}

    ${previewing ? `
      <div class="preview-bar">
        <div class="preview-title">预览 · 还没保存</div>
        <div class="preview-acts">
          <button class="btn-ghost" id="pvDiscard">放弃</button>
          <button class="btn-primary" id="pvSave">保存为做法</button>
        </div>
      </div>` : ''}
  </div>`;

  hydrateImages(root);

  $('#btnBack').onclick = () => {
    if (previewing) { detailPending = null; showDetail(id); return; }
    detailId = null;
    showList('left');
  };
  if ($('#videoCard')) $('#videoCard').onclick = () => playVideo(r);
  if ($('#btnEdit')) $('#btnEdit').onclick = () => openEditor(id, 'detail');
  if ($('#btnAi')) $('#btnAi').onclick = () => askMissing(id);
  if ($('#btnPin')) {
    $('#btnPin').onclick = async () => {
      r.pinned = !r.pinned;
      await dbPut(r);
      showDetail(id);
      toast(r.pinned ? '已加入常做，会置顶显示' : '已取消常做');
    };
  }
  if ($('#variantRow')) {
    $('#variantRow').onclick = (e) => {
      const btn = e.target.closest('button[data-v]');
      if (!btn) return;
      detailVariant = Number(btn.dataset.v);
      showDetail(id);
    };
    // 长按（触屏）或右键（桌面）管理变体：重命名 / 删除
    let lpTimer = null;
    const cancelLP = () => { if (lpTimer) { clearTimeout(lpTimer); lpTimer = null; } };
    $('#variantRow').addEventListener('pointerdown', (e) => {
      const btn = e.target.closest('button[data-v]');
      if (!btn || btn.dataset.v === '-1') return;
      const vi = Number(btn.dataset.v);
      lpTimer = setTimeout(() => { lpTimer = null; manageVariant(id, vi); }, 500);
    });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) =>
      $('#variantRow').addEventListener(ev, cancelLP));
    $('#variantRow').addEventListener('contextmenu', (e) => {
      const btn = e.target.closest('button[data-v]');
      if (!btn || btn.dataset.v === '-1') return;
      e.preventDefault();
      manageVariant(id, Number(btn.dataset.v));
    });
  }
  if ($('#pvDiscard')) $('#pvDiscard').onclick = () => { detailPending = null; showDetail(id); };
  if ($('#pvSave')) $('#pvSave').onclick = () => saveVariant(id);
}

/** 当前看到的那一版 → 文本，交给 AI 当底稿 */
function variantBaseText(r) {
  const shown = detailPending || (r.variants || [])[detailVariant] || null;
  if (!shown) return P.stringifyRecipe(r);
  return P.stringifyRecipe({
    ...r,
    description: shown.description || '',
    ingredients: shown.ingredients || [],
    steps: shown.steps || [],
  });
}

/** 点「换个做法」：问缺什么，交给 AI 出一版替代方案 */
async function askMissing(id) {
  const r = state.recipes.find((x) => x.id === id);
  if (!r) return;

  const cfg = getApi();
  if (proxy.on) {
    if (!proxy.configured) {
      await dialog({
        title: '服务端还没配置 Key',
        message: '请编辑 web/config.local.json 填写 api_key，然后重启本地服务。',
        actions: [{ label: '知道了', value: true, style: 'primary' }],
      });
      return;
    }
  } else if (!cfg.key) {
    const go = await dialog({
      title: '还没配置大模型',
      message: '需要填一个 API Key 才能生成替代方案。',
      actions: [
        { label: '暂不', value: false },
        { label: '去配置', value: true, style: 'primary' },
      ],
    });
    if (go) openSettings(() => showDetail(id));
    return;
  }

  const request = await askText({
    title: '缺什么食材？',
    message: '说清楚手头的情况，AI 会按现有做法给一版替代方案。',
    placeholder: '例：没有大蒜 / 不吃辣 / 只有一个空气炸锅',
    okLabel: '生成',
  });
  if (!request) return;

  const modelName = proxy.on ? proxy.model : cfg.model;
  const mask = document.createElement('div');
  mask.className = 'mask';
  mask.innerHTML = `
    <div class="dialog">
      <h4>正在生成替代方案</h4>
      <p>调用 ${esc(modelName)}，通常需要 3–15 秒。</p>
      <div class="loading"><span></span><span></span><span></span></div>
    </div>`;
  phone.appendChild(mask);

  try {
    const text = await callLLM(buildVariantPrompt(variantBaseText(r), request));
    mask.remove();
    const parsed = P.parseRecipe(text);
    if (!parsed.ok) throw new Error(parsed.hints[0] || '没识别出菜谱内容');
    detailVariant = -1;
    detailPending = {
      label: request.replace(/\s+/g, ' ').slice(0, 12),
      description: parsed.tips || '',
      ingredients: parsed.ingredients || [],
      steps: parsed.steps || [],
      createdAt: Date.now(),
    };
    showDetail(id);
  } catch (err) {
    mask.remove();
    await dialog({
      title: '生成失败',
      message: friendlyError(String(err && err.message ? err.message : err)),
      actions: [{ label: '知道了', value: true, style: 'primary' }],
    });
  }
}

function buildVariantPrompt(recipeText, request) {
  return `你是一位专业中餐厨师。下面是同一道菜现有的做法，用户临时缺东西或条件变了，请给出一份仍然好吃的替代方案。

【现有做法】
${recipeText}

【用户的情况】
${request}

只输出改好后的菜谱正文，不要开场白、解释或结尾。严格照现有做法的格式：

# 菜名
### 食材
...
### 步骤
1. ...
> 小贴士：...

要求：
- 第一行沿用原菜名，不要加「改良版」「替代版」这类后缀
- 只改受影响的部分，其他食材和步骤尽量保持原样
- 缺的食材直接换成替代品，并在小贴士里说明替代后口感有什么变化
- 步骤 3–6 步，写清火候和时间`;
}

async function saveVariant(id) {
  const r = state.recipes.find((x) => x.id === id);
  const v = detailPending;
  if (!r || !v) return;
  r.variants = (r.variants || []).concat([v]);
  await dbPut(r);
  detailVariant = r.variants.length - 1;
  detailPending = null;
  showDetail(id);
  toast('已保存，可在顶部切换做法');
}

/** 单行文本输入弹窗 */
function askText({ title, message, placeholder, okLabel = '确定' }) {
  return new Promise((resolve) => {
    const mask = document.createElement('div');
    mask.className = 'mask';
    mask.innerHTML = `
      <div class="dialog">
        <h4>${esc(title)}</h4>
        <p>${esc(message || '')}</p>
        <input class="field" id="askTextInput" placeholder="${esc(placeholder || '')}" maxlength="40" autocomplete="off">
        <div class="acts" style="margin-top:14px">
          <button data-v="0">取消</button>
          <button data-v="1" class="primary">${esc(okLabel)}</button>
        </div>
      </div>`;
    phone.appendChild(mask);
    const input = $('#askTextInput', mask);
    setTimeout(() => input.focus(), 60);
    const done = (v) => { mask.remove(); resolve(v); };
    mask.addEventListener('click', (e) => {
      const b = e.target.closest('button[data-v]');
      if (b) done(b.dataset.v === '1' ? input.value.trim() : '');
      else if (e.target === mask) done('');
    });
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') done(input.value.trim());
    });
  });
}

// ---------------------------------------------------------------- 编辑页

function editorView(d) {
  return `
  <div class="view enter-right">
    <div class="edit-top">
      <button class="icon-btn" id="btnClose" aria-label="关闭">${svg('close', 22)}</button>
      <div class="title" id="editTitle">${d.id ? '编辑菜谱' : '新建菜谱'}</div>
      <button class="btn-primary" id="btnSave">保存</button>
    </div>

    <div class="scroll">
      <div class="edit-body">

        <div class="write-card">
          <textarea id="recipeText" spellcheck="false" placeholder="${esc(PLACEHOLDER)}"></textarea>
        </div>

        <div class="parse-bar" id="parseBar">
          <span class="parse-dot" id="parseDot"></span>
          <span class="parse-msg" id="parseMsg">等待输入…</span>
          <button class="parse-toggle" id="parseToggle" hidden>预览</button>
        </div>

        <div class="parse-preview" id="parsePreview" hidden></div>

        <div class="ai-row">
          <button class="ai-btn" id="aiBtn">
            ${svg('spark', 17)}<span>用 AI 写一道菜</span>
          </button>
          <button class="icon-btn subtle" id="btnConfig" aria-label="设置">${svg('setting', 20)}</button>
        </div>

        <div class="opt-block">
          <div class="label">分类</div>
          <div class="chipbar wrap" id="catBar" style="padding:0"></div>
        </div>

        <div class="opt-block">
          <div class="label">封面（选填）</div>
          <div class="cover-strip">
            <div class="cover-thumb ${d.cover ? 'has-img' : ''}" id="coverPick">
              <img id="coverImg" alt="" style="display:none"${d.cover ? ` data-img="${esc(d.cover)}"` : ''}>
              <span class="ph"${d.cover ? ' style="display:none"' : ''}>${strokeIcon(ICON_PHOTO, 24, 1.6)}</span>
            </div>
            <button class="cover-clear" id="coverClear" ${d.cover ? '' : 'hidden'}>移除封面</button>
          </div>
        </div>

        <div class="opt-block">
          <div class="label">视频（选填）</div>
          <input class="field" id="fVideo" autocomplete="off"
                 placeholder="粘贴视频链接或整段分享文案，支持小红书 / B 站 / mp4 直链"
                 value="${esc(d.videoUrl || '')}">
          <div class="video-state" id="videoState" hidden></div>
        </div>

        <details class="more">
          <summary>更多选项（人份 / 时长 / 难度）</summary>
          <div class="row-3">
            <div>
              <div class="label">人份</div>
              <input class="field" id="fServ" type="number" inputmode="numeric" min="0" max="99" value="${d.servings || ''}">
            </div>
            <div>
              <div class="label">分钟</div>
              <input class="field" id="fMin" type="number" inputmode="numeric" min="0" max="9999" value="${d.minutes || ''}">
            </div>
            <div>
              <div class="label">难度</div>
              <div class="field pick" id="fDiff">${DIFF[(d.difficulty || 1) - 1]}</div>
            </div>
          </div>
        </details>

        ${d.id ? `<button class="danger-btn" id="btnDelete">删除这道菜谱</button>` : ''}
      </div>
    </div>

    <input type="file" id="fileInput" accept="image/*" hidden>
  </div>`;
}

function openEditor(id, from) {
  const origin = id ? state.recipes.find((r) => r.id === id) : null;
  state.editId = origin ? origin.id : null;
  state.editFrom = from || 'list';
  state.draft = origin ? clone(origin) : newRecipe();
  state.dirty = false;
  state.userPickedCat = !!origin;
  state.videoCleared = false;

  renderEditor();
}

function renderEditor() {
  const d = state.draft;
  root.innerHTML = editorView(d);

  const ta = $('#recipeText');
  ta.value = d.id ? P.stringifyRecipe(d) : '';
  autoGrow(ta);

  renderCatBar();
  if (d.cover) hydrateImages(root);
  bindEditorEvents();
  runParse();
  updateCoverUI();
}

async function pickCoverSource(fileInput) {
  const has = !!state.draft.cover;
  const actions = [
    { label: '🔍 从网络找图', value: 'search' },
    { label: '从相册选择', value: 'upload' },
    { label: '✨ 用 AI 生成', value: 'ai' },
  ];
  if (has) actions.push({ label: '移除封面', value: 'remove', style: 'danger' });
  actions.push({ label: '取消', value: null });

  const v = await dialog({ title: '封面', message: has ? '当前已有封面' : '还没有封面', actions });
  if (v === 'search') openImageSearch();
  else if (v === 'upload') fileInput.click();
  else if (v === 'ai') generateCover();
  else if (v === 'remove') {
    const old = state.draft.cover;
    state.draft.cover = null;
    if (old) { await imgDel(old); dropUrl(old); }
    state.dirty = true;
    updateCoverUI();
  }
}

/* ---------------------------------------------------------------- 网络搜图 */

/**
 * 搜图面板：先按菜名搜一次，用户从候选缩略图里挑一张，
 * 点中的原图下载回来后居中裁成 4:3 存为封面。
 */
async function openImageSearch() {
  if (!proxy.on) {
    await dialog({
      title: '需要本地服务',
      message: '搜图要经过本地服务转发（Bing 搜索页没有跨域头，原图站点还有防盗链）。请用「启动预览.bat」打开，或访问 http://127.0.0.1:8765。',
      actions: [{ label: '知道了', value: true, style: 'primary' }],
    });
    return;
  }

  const parsed = P.parseRecipe($('#recipeText').value);
  const preset = (parsed.name || '').trim();

  const mask = document.createElement('div');
  mask.className = 'mask sheet';
  mask.innerHTML = `
    <div class="picker">
      <div class="picker-top">
        <div class="picker-title">从网络找图</div>
        <button class="icon-btn" id="pkClose" aria-label="关闭">${svg('close', 20)}</button>
      </div>
      <div class="picker-search">
        <input id="pkInput" type="text" placeholder="输入菜名，例如 宫保鸡丁"
               value="${esc(preset)}" autocomplete="off">
        <button id="pkGo">搜索</button>
      </div>
      <div class="picker-body" id="pkBody">
        <div class="picker-hint">输入菜名后点「搜索」。图来自公开网络，仅供参考。</div>
      </div>
    </div>`;
  phone.appendChild(mask);

  const input = $('#pkInput');
  const body = $('#pkBody');

  const close = () => mask.remove();
  $('#pkClose').onclick = close;

  let busy = false;
  let page = 0;
  let items = [];

  function renderGrid() {
    body.innerHTML = `<div class="picker-grid">${items.map((it, i) => `
      <button class="pick-cell" data-i="${i}">
        <img src="${it.thumb}" alt="">
      </button>`).join('')}</div>
      <button class="picker-more" id="pkMore">换一批</button>
      <div class="picker-foot">共 ${items.length} 张，点一张设为封面（自动裁成 4:3）</div>`;

    body.querySelectorAll('.pick-cell').forEach((cell) => {
      cell.onclick = () => applyPickedImage(items[Number(cell.dataset.i)], mask, body);
    });
    $('#pkMore').onclick = () => doSearch(false);
  }

  async function doSearch(reset = true) {
    const q = (input.value || '').trim();
    if (!q || busy) return;
    busy = true;

    if (reset) {
      page = 0;
      items = [];
      body.innerHTML = `<div class="picker-loading"><div class="loading"><span></span><span></span><span></span></div><div>正在搜索「${esc(q)}」…</div></div>`;
    } else {
      const more = $('#pkMore');
      if (more) { more.disabled = true; more.textContent = '正在加载…'; }
    }

    try {
      const res = await fetch('/api/search-image', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: q, count: 12, page }),
      });
      let j = {};
      try { j = await res.json(); } catch { /* ignore */ }
      if (!res.ok) throw new Error(j.error || ('HTTP ' + res.status));

      const got = j.items || [];
      if (!got.length) {
        if (reset) {
          body.innerHTML = `<div class="picker-hint">没有搜到「${esc(q)}」的图片。<br>换个更通用的菜名试试，比如去掉修饰词。</div>`;
        } else {
          const more = $('#pkMore');
          if (more) { more.disabled = true; more.textContent = '没有更多了'; }
        }
        return;
      }

      // 追加时按 murl 去重，避免翻页重叠
      const seen = new Set(items.map((x) => x.murl));
      items = items.concat(got.filter((x) => !seen.has(x.murl)));
      page += 1;
      renderGrid();
    } catch (err) {
      body.innerHTML = `<div class="picker-hint">搜索失败：${esc(String(err.message || err))}</div>`;
    } finally {
      busy = false;
    }
  }

  $('#pkGo').onclick = () => doSearch(true);
  input.onkeydown = (e) => { if (e.key === 'Enter') doSearch(true); };

  if (preset) doSearch(true);
  else setTimeout(() => input.focus(), 60);
}

/** 下载选中的原图 → 裁 4:3 → 存为封面 */
async function applyPickedImage(item, mask, body) {
  body.innerHTML = `<div class="picker-loading"><div class="loading"><span></span><span></span><span></span></div><div>正在下载图片…</div></div>`;

  try {
    const res = await fetch('/api/fetch-image', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: item.murl, referer: item.purl || 'https://cn.bing.com/' }),
    });
    let j = {};
    try { j = await res.json(); } catch { /* ignore */ }
    if (!res.ok) throw new Error(j.error || ('HTTP ' + res.status));

    const blob = await cropToRatio(j.dataUrl, 4 / 3, 1600, 0.88);
    const key = await storeBlob(blob);

    const old = state.draft.cover;
    state.draft.cover = key;
    if (old) { await imgDel(old); dropUrl(old); }

    state.dirty = true;
    mask.remove();
    updateCoverUI();
    hydrateImages($('#coverPick'));
    toast('封面已更新');
  } catch (err) {
    body.innerHTML = `<div class="picker-hint">图片下载失败：${esc(String(err.message || err))}<br><br>
      有些站点会拦截下载，换一张试试。</div>`;
  }
}

/** 居中裁切成固定比例，再压到指定长边 */
function cropToRatio(dataUrl, ratio, maxEdge, quality) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      let sw = img.naturalWidth;
      let sh = img.naturalHeight;
      let cw = sw;
      let ch = Math.round(sw / ratio);
      if (ch > sh) {
        ch = sh;
        cw = Math.round(sh * ratio);
      }
      const sx = Math.round((sw - cw) / 2);
      const sy = Math.round((sh - ch) / 2);

      let ow = cw;
      let oh = ch;
      if (Math.max(cw, ch) > maxEdge) {
        const k = maxEdge / Math.max(cw, ch);
        ow = Math.round(cw * k);
        oh = Math.round(ch * k);
      }

      const canvas = document.createElement('canvas');
      canvas.width = ow;
      canvas.height = oh;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, sx, sy, cw, ch, 0, 0, ow, oh);
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('图片处理失败'))), 'image/jpeg', quality);
    };
    img.onerror = () => reject(new Error('图片解码失败'));
    img.src = dataUrl;
  });
}

async function generateCover() {
  if (!proxy.on) {
    await dialog({
      title: '需要本地服务',
      message: 'AI 生成图片要经过本地服务转发，请用「启动预览.bat」打开，或直接访问 http://127.0.0.1:8765。',
      actions: [{ label: '知道了', value: true, style: 'primary' }],
    });
    return;
  }

  const parsed = P.parseRecipe($('#recipeText').value);
  if (!parsed.name) {
    toast('先写好菜名再生成图片');
    return;
  }

  const ings = parsed.ingredients.slice(0, 3).map((i) => i.n).filter(Boolean).join('、');
  const prompt = `一盘${parsed.name}${ings ? '，主要食材是' + ings : ''}，` +
    '白色瓷盘盛装，暖色调，俯拍摄影，高清美食摄影，自然光，浅景深，画面中不要出现文字';

  const mask = document.createElement('div');
  mask.className = 'mask';
  mask.innerHTML = `
    <div class="dialog">
      <h4>正在生成「${esc(parsed.name)}」的图片</h4>
      <p>菜品图通常需要 10–30 秒。</p>
      <div class="loading"><span></span><span></span><span></span></div>
    </div>`;
  phone.appendChild(mask);

  try {
    const res = await fetch('/api/image', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt }),
    });
    let j = {};
    try { j = await res.json(); } catch { /* ignore */ }
    if (!res.ok) throw new Error(j.error || ('HTTP ' + res.status));
    if (!j.dataUrl) throw new Error('没有拿到图片数据');

    const blob = await (await fetch(j.dataUrl)).blob();
    const key = await storeBlob(blob);

    const old = state.draft.cover;
    state.draft.cover = key;
    if (old) { await imgDel(old); dropUrl(old); }

    state.dirty = true;
    mask.remove();
    updateCoverUI();
    hydrateImages($('#coverPick'));
    toast(j.watermarked ? '已生成（AI 图片带水印标识）' : '已生成');
  } catch (err) {
    mask.remove();
    await dialog({
      title: '生成图片失败',
      message: String(err && err.message ? err.message : err),
      actions: [{ label: '知道了', value: true, style: 'primary' }],
    });
  }
}

function autoGrow(ta) {
  if (!ta) return;
  ta.style.height = 'auto';
  const limit = Math.round((phone.clientHeight || window.innerHeight) * 0.40);
  const want = Math.max(200, ta.scrollHeight + 4);
  ta.style.height = Math.min(want, Math.max(200, limit)) + 'px';
  ta.style.overflowY = want > limit ? 'auto' : 'hidden';
}

function bindEditorEvents() {
  const d = state.draft;
  const ta = $('#recipeText');

  ta.addEventListener('input', () => {
    autoGrow(ta);
    state.dirty = true;
    parseSoon();
    hidePreview();
  });

  $('#btnClose').onclick = tryCloseEditor;
  $('#btnSave').onclick = saveDraft;

  $('#parseToggle').onclick = () => {
    const box = $('#parsePreview');
    if (box.hidden) {
      renderPreview();
      box.hidden = false;
      $('#parseToggle').textContent = '收起';
    } else {
      hidePreview();
    }
  };

  $('#aiBtn').onclick = onAiClick;
  $('#btnConfig').onclick = () => openSettings(renderEditor);

  $('#fDiff').onclick = () => {
    d.difficulty = (d.difficulty % 3) + 1;
    $('#fDiff').textContent = DIFF[d.difficulty - 1];
    state.dirty = true;
  };

  const videoInput = $('#fVideo');
  if (videoInput) {
    videoInput.addEventListener('input', () => {
      state.dirty = true;
      // 用户手动清空后，别让正文里的 `@视频：` 又把它填回来
      state.videoCleared = !videoInput.value.trim();
      updateVideoState();
    });
    updateVideoState();
  }

  const fileInput = $('#fileInput');
  $('#coverPick').onclick = () => pickCoverSource(fileInput);
  fileInput.onchange = async () => {
    const file = fileInput.files && fileInput.files[0];
    fileInput.value = '';
    if (!file) return;
    try {
      const old = d.cover;
      d.cover = await storeImage(file);
      if (old) { await imgDel(old); dropUrl(old); }
      state.dirty = true;
      updateCoverUI();
      hydrateImages($('#coverPick'));
    } catch { toast('图片读取失败'); }
  };

  $('#coverClear').onclick = async (e) => {
    e.stopPropagation();
    const old = d.cover;
    d.cover = null;
    if (old) { await imgDel(old); dropUrl(old); }
    state.dirty = true;
    updateCoverUI();
  };

  const del = $('#btnDelete');
  if (del) {
    del.onclick = async () => {
      const ok = await dialog({
        title: '删除这道菜谱？',
        message: '删除后无法恢复。',
        actions: [
          { label: '取消', value: false },
          { label: '删除', value: true, style: 'danger' },
        ],
      });
      if (ok) await doDelete();
    };
  }

  $('.view', root).addEventListener('input', (e) => {
    if (e.target.matches('input.field')) state.dirty = true;
  });
}

function updateCoverUI() {
  const d = state.draft;
  const picker = $('#coverPick');
  const img = $('#coverImg');
  const ph = $('.ph', picker);
  const clear = $('#coverClear');

  if (!d.cover) {
    picker.classList.remove('has-img');
    img.style.display = 'none';
    img.removeAttribute('src');
    img.removeAttribute('data-img');
    if (ph) ph.style.display = '';
    if (clear) clear.hidden = true;
    return;
  }
  picker.classList.add('has-img');
  if (ph) ph.style.display = 'none';
  img.setAttribute('data-img', d.cover);
  img.style.display = 'none';
  if (clear) clear.hidden = false;
}

// ---------------------------------------------------------------- 解析

let lastParsed = null;

const parseSoon = debounce(() => runParse(), 300);

function runParse() {
  const text = $('#recipeText') ? $('#recipeText').value : '';
  const parsed = P.parseRecipe(text);
  lastParsed = parsed;

  const dot = $('#parseDot');
  const msg = $('#parseMsg');
  const toggle = $('#parseToggle');

  if (!text.trim()) {
    dot.className = 'parse-dot idle';
    msg.textContent = '等待输入…';
    toggle.hidden = true;
    return;
  }

  dot.className = 'parse-dot ' + (parsed.ok ? 'ok' : 'warn');

  if (parsed.ok) {
    const bits = [];
    if (parsed.ingredients.length) bits.push(`${parsed.ingredients.length} 食材`);
    if (parsed.steps.length) bits.push(`${parsed.steps.length} 步骤`);
    msg.textContent = `${parsed.name} · ${bits.join(' · ')}`;
    toggle.hidden = false;
  } else {
    msg.textContent = parsed.hints[0] || '没识别出内容，参考输入框里的示例格式';
    toggle.hidden = true;
  }

  if (!state.userPickedCat) {
    state.draft.category = parsed.category;
    renderCatBar();
  }

  // 正文里带 `@视频：` 时（导入的 TXT 就有）回填到视频框；
  // 已经有内容就以视频框为准，别拿旧文本覆盖用户刚填的链接
  const vInput = $('#fVideo');
  if (vInput && parsed.videoUrl && !state.videoCleared && !vInput.value.trim()) {
    vInput.value = parsed.videoUrl;
    updateVideoState();
  }
}

/** 视频输入框下方的识别提示 */
function updateVideoState() {
  const input = $('#fVideo');
  const box = $('#videoState');
  if (!input || !box) return;

  const url = P.normalizeVideoUrl(input.value);
  if (!url) {
    if (!input.value.trim()) {
      box.hidden = true;
      box.textContent = '';
    } else {
      // 填了东西却抠不出链接，说清楚，别让用户以为已经存上了
      box.hidden = false;
      box.classList.add('bad');
      box.textContent = '没识别出链接，请确认是 http(s):// 开头的地址';
    }
    return;
  }
  box.classList.remove('bad');
  const type = P.detectVideoType(url);
  box.hidden = false;
  box.textContent = `已识别：${P.videoSiteLabel(url)} · ` +
    (P.videoCanPlayInside(url, type) ? '可在 App 内播放' : '需跳转浏览器或原 App');
}

function renderPreview() {
  const p = lastParsed;
  const box = $('#parsePreview');
  if (!p || !p.ok) { box.innerHTML = ''; return; }

  box.innerHTML = `
    <div class="pv-title">${esc(p.name)}</div>
    <div class="pv-sec">食材 ${p.ingredients.length}</div>
    <div class="pv-ings">
      ${p.ingredients.map((i) => `
        <span class="pv-ing">${esc(i.n || '—')}${i.a ? `<i>${esc(i.a)}</i>` : ''}</span>`).join('')}
    </div>
    <div class="pv-sec">步骤 ${p.steps.length}</div>
    <ol class="pv-steps">${p.steps.map((s) => `<li>${esc(s)}</li>`).join('')}</ol>
    ${p.tips ? `<div class="pv-tip">${esc(p.tips)}</div>` : ''}`;
}

function hidePreview() {
  const box = $('#parsePreview');
  if (!box) return;
  box.hidden = true;
  const t = $('#parseToggle');
  if (t) t.textContent = '预览';
}

// ---------------------------------------------------------------- 分类条

function renderCatBar() {
  const bar = $('#catBar');
  if (!bar) return;
  const d = state.draft;
  bar.innerHTML = CATEGORIES.map((c) =>
    `<button class="chip ${d.category === c.key ? 'on' : ''}" data-cat="${c.key}">${c.label}</button>`
  ).join('');
  bar.onclick = (e) => {
    const btn = e.target.closest('.chip');
    if (!btn) return;
    d.category = btn.dataset.cat;
    state.userPickedCat = true;
    state.dirty = true;
    renderCatBar();
  };
}

// ---------------------------------------------------------------- 保存

async function tryCloseEditor() {
  if (!state.dirty) { leaveEditor(); return; }
  const discard = await dialog({
    title: '放弃修改？',
    message: '当前内容尚未保存。',
    actions: [
      { label: '继续编辑', value: false },
      { label: '放弃', value: true, style: 'danger' },
    ],
  });
  if (discard) {
    const origin = state.recipes.find((r) => r.id === state.editId);
    if (state.draft.cover && state.draft.cover !== (origin && origin.cover)) {
      await imgDel(state.draft.cover);
      dropUrl(state.draft.cover);
    }
    leaveEditor();
  }
}

function leaveEditor() {
  state.dirty = false;
  if (state.editFrom === 'detail' && state.editId) showDetail(state.editId, 'left');
  else showList('left');
}

async function saveDraft() {
  const d = state.draft;
  const parsed = P.parseRecipe($('#recipeText').value);

  if (!parsed.ok) {
    toast(parsed.hints[0] || '没识别出菜谱内容');
    $('#recipeText').focus();
    return;
  }
  if (!parsed.name) {
    toast('请先写菜名');
    return;
  }

  d.name = parsed.name;
  d.ingredients = parsed.ingredients.map((i) => ({ n: i.n, a: i.a }));
  d.steps = parsed.steps.slice();
  d.description = parsed.tips || '';

  const serv = parseInt($('#fServ').value, 10);
  const minu = parseInt($('#fMin').value, 10);
  d.servings = Number.isNaN(serv) ? 0 : Math.min(99, Math.max(0, serv));
  d.minutes = Number.isNaN(minu) ? 0 : Math.min(9999, Math.max(0, minu));

  // 视频以输入框为准：没有 `@视频：` 行的菜谱，正文解析不出链接
  const vUrl = P.normalizeVideoUrl($('#fVideo') ? $('#fVideo').value : '');
  d.videoUrl = vUrl;
  d.videoType = vUrl ? P.detectVideoType(vUrl) : '';

  const now = Date.now();
  if (!d.id) { d.id = nextId(); d.createdAt = now; }
  d.updatedAt = now;

  try {
    await dbPut(clone(d));
    state.recipes = await dbAll();
    state.dirty = false;
    toast('已保存');
    if (state.editFrom === 'detail') showDetail(d.id, 'left');
    else showList('left');
  } catch {
    toast('保存失败，请重试');
  }
}

async function doDelete() {
  const id = state.draft.id;
  const origin = state.recipes.find((r) => r.id === id);
  try {
    await dbDel(id);
    if (origin && origin.cover) { await imgDel(origin.cover); dropUrl(origin.cover); }
    if (state.draft.cover && state.draft.cover !== (origin && origin.cover)) {
      await imgDel(state.draft.cover);
      dropUrl(state.draft.cover);
    }
    state.recipes = await dbAll();
    state.dirty = false;
    toast('已删除');
    showList('left');
  } catch { toast('删除失败'); }
}

// ---------------------------------------------------------------- 导入导出

/** 全部菜谱 → TXT 并触发下载 */
function doExport() {
  if (!state.recipes.length) {
    toast('还没有菜谱可以导出');
    return;
  }
  const text = P.exportAll(state.recipes);
  // 开头加 BOM，Windows 记事本才能正确识别 UTF-8，否则中文会变乱码
  const blob = new Blob(['\uFEFF' + text], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);

  const a = document.createElement('a');
  a.href = url;
  a.download = P.suggestFileName();
  document.body.appendChild(a);
  a.click();
  a.remove();

  setTimeout(() => URL.revokeObjectURL(url), 2000);
  toast(`已导出 ${state.recipes.length} 道菜谱`);
}

/** 选择 TXT 文件 */
function pickImportFile() {
  // 挂到 DOM 上再触发：部分浏览器对未挂载的 input 不弹选择框
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.txt,text/plain';
  input.id = 'importFileInput';
  input.style.position = 'fixed';
  input.style.left = '-9999px';
  input.style.opacity = '0';
  document.body.appendChild(input);

  input.onchange = () => {
    const file = input.files && input.files[0];
    input.remove();
    if (file) doImportFile(file);
  };
  input.click();
}

/** 文件字节 → 文本。Windows 记事本存的可能是 GBK，按 UTF-8 硬解会乱码 */
function decodeBytes(buf) {
  try {
    // 严格模式：有非法字节就抛，说明不是 UTF-8
    return new TextDecoder('utf-8', { fatal: true }).decode(buf);
  } catch {
    try {
      return new TextDecoder('gbk').decode(buf);
    } catch {
      return new TextDecoder('utf-8').decode(buf);
    }
  }
}

async function doImportFile(file) {
  let text = '';
  try {
    text = decodeBytes(await file.arrayBuffer());
  } catch {
    toast('文件读取失败');
    return;
  }

  const blocks = P.splitBlocks(text);
  if (!blocks.length) {
    await dialog({
      title: '导入失败',
      message: '文件里没识别到菜谱。请确认是用本 App 导出的 TXT，或符合「# 菜名 / ### 食材 / ### 步骤」的格式。',
      actions: [{ label: '知道了', value: true, style: 'primary' }],
    });
    return;
  }

  const names = new Set(state.recipes.map((r) => (r.name || '').trim()));
  let added = 0;
  let skipped = 0;
  let failed = 0;
  let next = nextId();
  const now = Date.now();

  for (const block of blocks) {
    const parsed = P.parseRecipe(block);
    if (!parsed.ok || !parsed.name) {
      failed++;
      continue;
    }
    const key = parsed.name.trim();
    if (names.has(key)) {
      skipped++;
      continue;
    }
    names.add(key);

    try {
      await dbPut(clone({
        id: next++,
        name: parsed.name,
        category: parsed.category,
        cover: null,
        description: parsed.tips || '',
        servings: parsed.servings || 2,
        minutes: parsed.minutes || 0,
        difficulty: parsed.difficulty || 1,
        ingredients: parsed.ingredients.map((i) => ({ n: i.n, a: i.a })),
        steps: parsed.steps.slice(),
        createdAt: now,
        updatedAt: now,
        videoUrl: parsed.videoUrl || '',
        videoType: parsed.videoType || '',
      }));
      added++;
    } catch {
      failed++;
    }
  }

  state.recipes = await dbAll();
  state.keyword = '';
  state.category = null;
  showList('left');

  let msg = `新增 ${added} 道菜谱。`;
  if (skipped) msg += `\n跳过 ${skipped} 道同名菜谱。`;
  if (failed) msg += `\n${failed} 段没能识别。`;
  if (!added && !skipped) {
    msg = '文件里没识别到菜谱。请确认是用本 App 导出的 TXT。';
  }
  if (skipped && !added) msg += '\n\n（同名判断按菜名精确匹配）';

  await dialog({
    title: '导入完成',
    message: msg,
    actions: [{ label: '好', value: true, style: 'primary' }],
  });
}

// ---------------------------------------------------------------- AI

function getApi() {
  try {
    const raw = localStorage.getItem('zuocai_api');
    if (raw) return { ...DEFAULT_API, ...JSON.parse(raw) };
  } catch { /* ignore */ }
  return { ...DEFAULT_API };
}

function saveApi(cfg) {
  localStorage.setItem('zuocai_api', JSON.stringify(cfg));
}

function buildPrompt(dish) {
  return `你是一位专业中餐厨师，请为「${dish}」写一份可以直接下厨的菜谱。

只输出菜谱正文，不要开场白、解释或结尾。严格照下面的示例格式，把内容换成这道菜的：

# 番茄炒蛋
### 食材
番茄 2 个、鸡蛋 3 个、小葱 1 根、盐 适量、食用油 2 勺
### 步骤
1. 番茄切块，鸡蛋加少许盐打散。
2. 热锅倒油，蛋液下锅凝固后盛出。
3. 底油下番茄炒出汁，倒回鸡蛋翻炒均匀，加盐调味出锅。
> 小贴士：蛋液里加一勺清水，炒出来更嫩。

要求：
- 第一行必须是「# ${dish}」，不要把「菜名」两个字照抄成标题
- 食材 5–9 项，用量写具体（200g / 2 勺 / 半个），没有固定用量写「适量」
- 步骤 3–6 步，每步 20–60 字，写清火候和时间
- 小贴士一句话，点明容易失败的地方`;
}

async function callLLM(prompt) {
  // 有本地服务时走代理，浏览器不直接接触 API Key
  if (proxy.on) {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt, max_tokens: 3000 }),
    });
    let j = {};
    try { j = await res.json(); } catch { /* ignore */ }
    if (!res.ok) throw new Error(j.error || ('HTTP ' + res.status));
    if (!j.content) throw new Error('返回内容为空');
    return j.content;
  }

  const cfg = getApi();
  const url = cfg.baseUrl.replace(/\/+$/, '') + '/chat/completions';

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + cfg.key,
    },
    body: JSON.stringify({
      model: cfg.model,
      messages: [{ role: 'user', content: prompt }],
      temperature: 0.7,
      max_tokens: 3000,
      stream: false,
    }),
  });

  if (!res.ok) {
    const t = await res.text().catch(() => '');
    throw new Error(`HTTP ${res.status} ${t.slice(0, 160)}`);
  }
  const data = await res.json();
  const content = data && data.choices && data.choices[0] && data.choices[0].message
    ? data.choices[0].message.content : '';
  if (!content) throw new Error('返回内容为空');
  return content.trim();
}

async function onAiClick() {
  const cfg = getApi();

  if (proxy.on) {
    // 本地服务模式：Key 在服务端，浏览器无需配置
    if (!proxy.configured) {
      await dialog({
        title: '服务端还没配置 Key',
        message: '请编辑 web/config.local.json 填写 api_key，然后重启本地服务。',
        actions: [{ label: '知道了', value: true, style: 'primary' }],
      });
      return;
    }
  } else if (!cfg.key) {
    const go = await dialog({
      title: '还没配置大模型',
      message: '需要填一个 API Key 才能生成菜谱。硅基流动有免费模型，注册即可用。',
      actions: [
        { label: '暂不', value: false },
        { label: '去配置', value: true, style: 'primary' },
      ],
    });
    if (go) openSettings(renderEditor);
    return;
  }

  const dish = await askDish();
  if (!dish) return;

  const modelName = proxy.on ? proxy.model : cfg.model;

  const mask = document.createElement('div');
  mask.className = 'mask';
  mask.innerHTML = `
    <div class="dialog">
      <h4>正在生成「${esc(dish)}」</h4>
      <p>调用 ${esc(modelName)}，通常需要 3–15 秒。</p>
      <div class="loading"><span></span><span></span><span></span></div>
    </div>`;
  phone.appendChild(mask);

  try {
    const text = await callLLM(buildPrompt(dish));
    mask.remove();

    const ta = $('#recipeText');
    const existing = ta.value.trim();
    if (existing) {
      const mode = await dialog({
        title: '已有内容',
        message: '文本框里已经有内容，要用 AI 结果替换吗？',
        actions: [
          { label: '取消', value: null },
          { label: '追加到末尾', value: 'append' },
          { label: '替换', value: 'replace', style: 'danger' },
        ],
      });
      if (!mode) return;
      ta.value = mode === 'append' ? existing + '\n\n' + text : text;
    } else {
      ta.value = text;
    }

    autoGrow(ta);
    state.dirty = true;
    state.userPickedCat = false;
    runParse();
    toast('已生成，记得检查一下');
  } catch (err) {
    mask.remove();
    await dialog({
      title: '生成失败',
      message: friendlyError(String(err && err.message ? err.message : err)),
      actions: [{ label: '知道了', value: true, style: 'primary' }],
    });
  }
}

function friendlyError(raw) {
  const msg = String(raw || '');
  if (/Failed to fetch|NetworkError|Load failed/i.test(msg)) {
    return '网络请求被拦截。可能是这个接口不允许浏览器直接调用（跨域限制），或网络不通。建议换硅基流动这类支持跨域的接口，或在手机 App 里使用。';
  }
  if (/HTTP 401|HTTP 403|invalid.*token|unauthorized|api.?key/i.test(msg)) {
    return 'API Key 无效或没有该模型的权限。请到设置里检查 Key 是否填写完整、是否已过期。';
  }
  if (/HTTP 404/.test(msg)) {
    return '接口地址或模型名不对。检查一下 API 地址是否以 /v1 结尾，模型名是否与平台一致。';
  }
  if (/HTTP 429/.test(msg)) {
    return '请求太频繁，触发了限流。等几十秒再试。';
  }
  if (/HTTP 5\d\d/.test(msg)) {
    return '服务端暂时不可用，过一会儿再试。';
  }
  if (/返回内容为空/.test(msg)) {
    return '接口返回了空内容，可能是模型名不对或额度用尽。';
  }
  return msg;
}

function askDish() {
  return new Promise((resolve) => {
    const mask = document.createElement('div');
    mask.className = 'mask';
    mask.innerHTML = `
      <div class="dialog">
        <h4>想做什么菜？</h4>
        <p>写菜名就行，比如「洋葱炒肉」。</p>
        <input class="field" id="aiDish" placeholder="洋葱炒肉" maxlength="30" autocomplete="off">
        <div class="acts" style="margin-top:14px">
          <button data-v="0">取消</button>
          <button data-v="1" class="primary">生成</button>
        </div>
      </div>`;
    phone.appendChild(mask);

    const input = $('#aiDish', mask);
    setTimeout(() => input.focus(), 60);

    const done = (v) => { mask.remove(); resolve(v); };
    mask.addEventListener('click', (e) => {
      const b = e.target.closest('button[data-v]');
      if (b) done(b.dataset.v === '1' ? input.value.trim() : '');
      else if (e.target === mask) done('');
    });
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') done(input.value.trim());
    });
  });
}

function openSettings(afterClose) {
  const backFn = () => { if (afterClose) afterClose(); else showList('left'); };

  if (proxy.on) {
    root.innerHTML = `
    <div class="view enter-right">
      <div class="edit-top">
        <button class="icon-btn" id="setBack" aria-label="返回">${svg('back', 21)}</button>
        <div class="title">大模型设置</div>
        <span style="width:40px"></span>
      </div>
      <div class="scroll">
        <div class="edit-body">
          <div class="group">
            <div style="display:flex;align-items:center;gap:8px">
              <span style="width:8px;height:8px;border-radius:50%;background:#3E9A5C"></span>
              <b style="font-size:14px">已通过本地服务连接</b>
            </div>
            <div style="margin-top:14px;font-size:12.5px;color:var(--text-2);line-height:2">
              模型：<code>${esc(proxy.model)}</code><br>
              地址：<code>${esc(proxy.baseUrl)}</code>
            </div>
          </div>
          <div class="note">
            当前由 <code>web/serve.py</code> 在服务端转发请求，API Key 存在本机
            <code>config.local.json</code>，不会进入浏览器，也不受跨域限制。<br><br>
            要换模型或 Key：编辑那个文件后重启服务即可，这里不需要填写。
          </div>
          <div class="note warn">
            直接用浏览器打开 <code>preview.html</code> 时没有本地服务，AI 功能会回退到浏览器直连模式，届时需要在设置里手动填 Key。
          </div>
        </div>
      </div>
    </div>`;
    $('#setBack').onclick = backFn;
    return;
  }

  const cfg = getApi();
  root.innerHTML = `
  <div class="view enter-right">
    <div class="edit-top">
      <button class="icon-btn" id="setBack" aria-label="返回">${svg('back', 21)}</button>
      <div class="title">大模型设置</div>
      <button class="btn-primary" id="setSave">保存</button>
    </div>
    <div class="scroll">
      <div class="edit-body">
        <div class="group">
          <div class="label">API 地址</div>
          <input class="field" id="cfgUrl" value="${esc(cfg.baseUrl)}" placeholder="https://api.siliconflow.cn/v1">
          <div class="label" style="margin-top:14px">模型名</div>
          <input class="field" id="cfgModel" value="${esc(cfg.model)}" placeholder="Qwen/Qwen2.5-7B-Instruct">
          <div class="label" style="margin-top:14px">API Key</div>
          <input class="field" id="cfgKey" type="password" value="${esc(cfg.key)}" placeholder="sk-...">
        </div>

        <div class="note">
          <b>网页版推荐：硅基流动</b><br>
          注册送 2000 万 Token，9B 以下模型永久免费，且允许浏览器直接调用。<br>
          <code>https://api.siliconflow.cn/v1</code> · <code>Qwen/Qwen2.5-7B-Instruct</code>
          <br><br>
          <b>智谱在网页版调不通</b><br>
          它的接口不返回跨域头，浏览器直连会被拦。<br>
          要用智谱请装 APK，填 <code>glm-4-flash</code>（实测免费可用、响应快）；<br>
          <code>glm-4.7-flash</code> 限流严重易 429，<code>glm-4.5-flash</code> 默认开深度思考会吃掉输出额度。
        </div>

        <div class="note warn">Key 只保存在本机浏览器，不会上传到任何服务器。</div>
      </div>
    </div>
  </div>`;

  $('#setBack').onclick = backFn;
  $('#setSave').onclick = () => {
    saveApi({
      baseUrl: $('#cfgUrl').value.trim() || DEFAULT_API.baseUrl,
      model: $('#cfgModel').value.trim() || DEFAULT_API.model,
      key: $('#cfgKey').value.trim(),
    });
    toast('已保存');
    backFn();
  };
}

// ---------------------------------------------------------------- 内置默认菜谱

// 默认菜谱在 default-recipes.js（与 app/src/main/assets/default_recipes.txt 同源），
// 这里只负责按菜名去重后补进 IndexedDB。版本号变了才会再跑一次。
const SEED_VER = '4';

// 旧版本自动写入的 3 道演示菜，用户不需要，升级时顺手清掉
const LEGACY_SAMPLES = ['西红柿炒鸡蛋', '可乐鸡翅', '番茄蛋花汤'];

// 内置封面是随包发布的静态文件（covers/cNN.jpg），与用户上传的 IndexedDB blob 用前缀区分
const SEED_COVER_DIR = 'covers/';

async function seedIfNeeded() {
  if (localStorage.getItem('zuocai_seeded') === SEED_VER) return;

  const all = await dbAll();
  const known = new Set(all.map((r) => (r.name || '').trim()));
  let cursor = all.reduce((m, r) => Math.max(m, r.id || 0), 0);
  const now = Date.now();
  const text = window.DEFAULT_RECIPES_TEXT || '';

  // 先解析一遍：菜名 → 封面相对路径
  const seeds = [];
  for (const block of P.splitBlocks(text)) {
    const parsed = P.parseRecipe(block);
    if (!parsed || !parsed.ok || !parsed.name) continue;
    const m = block.match(/@封面[：:]\s*([A-Za-z0-9_-]+)/);
    seeds.push({
      parsed,
      name: parsed.name.trim(),
      cover: m ? SEED_COVER_DIR + m[1] + '.jpg' : null,
    });
  }
  const covers = new Map(seeds.filter((s) => s.cover).map((s) => [s.name, s.cover]));

  // 清掉旧演示菜，并给已经存在的内置菜谱补封面
  for (const r of all) {
    const name = (r.name || '').trim();
    if (LEGACY_SAMPLES.includes(name)) {
      await dbDel(r.id);
      known.delete(name);
      continue;
    }
    const want = covers.get(name);
    if (want && r.cover !== want) {
      r.cover = want;
      await dbPut(r);
    }
  }

  for (const seed of seeds) {
    if (known.has(seed.name)) continue;
    const parsed = seed.parsed;
    known.add(seed.name);
    cursor += 1;
    await dbPut({
      id: cursor,
      name: seed.name,
      category: parsed.category || 'home',
      cover: seed.cover,
      description: parsed.tips || '',
      servings: parsed.servings || 2,
      minutes: parsed.minutes || 0,
      difficulty: parsed.difficulty || 1,
      ingredients: parsed.ingredients || [],
      steps: parsed.steps || [],
      createdAt: now - cursor * 1000,
      updatedAt: now - cursor * 1000,
    });
  }

  localStorage.setItem('zuocai_seeded', SEED_VER);
}


// ---------------------------------------------------------------- 启动

async function boot() {
  await detectProxy();
  try {
    await seedIfNeeded();
    state.recipes = await dbAll();
  } catch (err) {
    // 读不出数据就彻底别进主界面：以前是置空继续跑，之后新建菜谱会从 id=1 开始，
    // 直接覆盖库里已有的 1、2、3 号菜谱
    console.error(err);
    state.storageBroken = true;
    showStorageError();
    return;
  }
  showList();
}

/** 本地存储不可用时的兜底页，明确只读、不给编辑入口 */
function showStorageError() {
  root.innerHTML = `
  <div class="view">
    <div class="edit-top">
      <div class="title">无法读取本地数据</div>
      <span style="width:40px"></span>
    </div>
    <div class="scroll">
      <div class="games-body">
        <div class="games-intro">
          浏览器没能打开本地数据库，常见原因是无痕模式、站点数据被禁用，或存储空间已满。<br><br>
          为避免覆盖已有菜谱，这里不提供编辑入口。<br><br>
          换一个普通窗口打开，或清理浏览器存储后刷新即可恢复。
        </div>
      </div>
    </div>
  </div>`;
}

function tickClock() {
  const el = document.getElementById('clock');
  if (!el) return;
  const d = new Date();
  el.textContent = `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
}
tickClock();
setInterval(tickClock, 30000);

document.addEventListener('keydown', (e) => {
  if (e.key !== 'Escape') return;
  if ($('.mask')) return;
  if ($('#btnClose')) { tryCloseEditor(); return; }
  if ($('#setBack')) { $('#setBack').click(); return; }
  if ($('#btnBack')) { showList('left'); }
});

boot();

})();
