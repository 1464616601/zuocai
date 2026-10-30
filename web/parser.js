/**
 * 菜谱文本解析器
 * 把用户粘贴的自由格式文本（Markdown / 纯文本）解析成结构化菜谱。
 * 同时提供反向序列化，用于"编辑已有菜谱"时回填文本框。
 *
 * 浏览器：window.RecipeParser
 * Node：  module.exports
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.RecipeParser = api;
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // 计量单位（长单位在前，避免 "小勺" 被 "勺" 抢先匹配）
  const UNIT = [
    '千克', '公斤', '毫升', '汤匙', '茶匙', '小勺', '大勺', '小碗', '大碗',
    'kg', 'ml', 'mL', 'KG', 'ML', '克', '升', '斤', '两', 'g', 'L',
    '勺', '个', '颗', '瓣', '片', '根', '只', '条', '块', '把', '碗', '杯',
    '段', '张', '粒', '朵', '撮', '滴', '寸', 'oz', 'lb',
  ];

  const AMOUNT_WORDS = ['适量', '少许', '少量', '若干', '随意', '可选', '按需', '一点点', '一点'];

  const CN_NUM = '０-９0-9一二三四五六七八九十百半两几';

  const RE_TRAILING_QTY = new RegExp(
    '^(.+?)[\\s:：]*(' +
      `[${CN_NUM}]+(?:\\.\\d+)?\\s*(?:${UNIT.join('|')})` +
      '|' + AMOUNT_WORDS.join('|') +
    ')$'
  );
  const RE_LEADING_QTY = new RegExp('^(' + AMOUNT_WORDS.join('|') + ')(.+)$');
  const RE_NUM_START = /^[0-9０-９][0-9０-９.]*\s*.*$/;

  const ING_WORDS = '食材|用料|材料|原料|主料|配料|辅料|调料|所需材料|准备材料|食材清单|用料清单';
  const STEP_WORDS = '步骤|做法|烹饪步骤|制作步骤|制作方法|烹饪方法|操作步骤|流程|方法|详细步骤';
  const TIP_WORDS = '小贴士|贴士|提示|注意|注意点|tips?|备注|心得';
  const NAME_WORDS = '菜名|名称|菜品|标题';

  const RE_ING_HEAD = new RegExp(`^(${ING_WORDS})[：:]?$`);
  const RE_STEP_HEAD = new RegExp(`^(${STEP_WORDS})[：:]?$`);
  const RE_TIP_HEAD = new RegExp(`^(${TIP_WORDS})[：:]?`, 'i');
  const RE_ING_INLINE = new RegExp(`^(${ING_WORDS})[：:]\\s*(.+)$`);
  const RE_STEP_INLINE = new RegExp(`^(${STEP_WORDS})[：:]\\s*(.+)$`);
  const RE_NAME_INLINE = new RegExp(`^(${NAME_WORDS})[：:]\\s*(.+)$`);
  // 模型有时把模板里的「# 菜名」原样输出，这种标题不是真菜名
  const RE_PLACEHOLDER_NAME = /^(菜名|名称|菜品|菜品名|菜谱名|食谱名|标题)$/;

  // 明确是编号列表（数字 / 圆圈数字），任何语境下都视为步骤
  const STEP_NUM_STRICT = /^\s*(?:\(?\d{1,2}\)?[.、)．]|[①-⑩]|\d{1,2}\s*[.、])\s*/;
  // 项目符号，仅在步骤语境下视为步骤标记
  const STEP_BULLET = /^\s*[-*•·]\s+/;

  const ING_SPLIT = /[、，,；;]+/;

  /** 去掉 Markdown 标记，保留文字 */
  // 导出 TXT 用的中文名（和 Kotlin 端 RecipeCategory.textOf / Difficulty.textOf 对齐）
  const CAT_TEXT = {
    home: '家常菜', soup: '汤羹', staple: '主食',
    cold: '凉菜', dessert: '甜点', other: '其他',
  };
  const CAT_BY_TEXT = {
    '家常菜': 'home', '汤羹': 'soup', '汤': 'soup', '主食': 'staple',
    '凉菜': 'cold', '甜点': 'dessert', '甜品': 'dessert', '其他': 'other',
  };
  const DIFF_TEXT = ['简单', '中等', '有点难'];

  function stripMd(s) {
    return String(s || '')
      .replace(/\*\*(.+?)\*\*/g, '$1')
      .replace(/__(.+?)__/g, '$1')
      .replace(/(^|[^*])\*(?!\s)(.+?)(?<!\s)\*/g, '$1$2')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/~~(.+?)~~/g, '$1')
      .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
      .replace(/\s+/g, ' ')
      .trim();
  }

  /** 单个食材 → {n, a} */
  function splitIngredient(raw) {
    let s = stripMd(raw).replace(/^[-*•·\s]+/, '').trim();
    s = s.replace(/^[：:]\s*/, '');
    if (!s) return null;

    // 量词在前：少许白糖
    const lead = s.match(RE_LEADING_QTY);
    if (lead && lead[2].trim()) {
      return { n: lead[2].trim(), a: lead[1] };
    }

    // 名称 + 末尾用量
    const tail = s.match(RE_TRAILING_QTY);
    if (tail && tail[1].trim()) {
      return { n: tail[1].trim(), a: tail[2].trim() };
    }

    // 空格分隔、后半以数字开头：鸡蛋 2个
    const parts = s.split(/\s+/);
    if (parts.length >= 2) {
      const last = parts[parts.length - 1];
      if (RE_NUM_START.test(last)) {
        return { n: parts.slice(0, -1).join(' '), a: last };
      }
    }

    return { n: s, a: '' };
  }

  /** 一行食材文本 → 数组 */
  function parseIngredientLine(line) {
    const out = [];
    stripMd(line)
      .replace(/^[-*•·]\s+/, '')
      .split(ING_SPLIT)
      .forEach((seg) => {
        const item = splitIngredient(seg);
        if (item && (item.n || item.a)) out.push(item);
      });
    return out;
  }

  /** 根据菜名与食材猜分类 */
  function guessCategory(name, ingredients) {
    const t = (String(name || '') + ' ' + (ingredients || []).map((i) => i.n).join(' ')).toLowerCase();
    if (/(汤|羹|煲|锅仔|炖盅)/.test(t)) return 'soup';
    if (/(凉拌|拌菜|沙拉|冷盘|口水)/.test(t)) return 'cold';
    if (/(蛋糕|布丁|甜品|糖水|饼干|派|酥|奶冻|慕斯|双皮奶)/.test(t)) return 'dessert';
    if (/(炒饭|拌饭|焖饭|盖饭|面条|拉面|炒面|汤面|米粉|河粉|粥|饺子|包子|馒头|花卷|烙饼|煎饼|年糕|意面|披萨)/.test(t)) return 'staple';
    return 'home';
  }

  /**
   * 主解析入口
   * @returns {{name, ingredients, steps, tips, category, ok, hints}}
   */
  function parseRecipe(text) {
    const result = {
      name: '', ingredients: [], steps: [], tips: '',
      category: 'home', ok: false, hints: [],
    };
    const rawText = String(text || '').replace(/\r\n?/g, '\n');
    if (!rawText.trim()) return result;

    const lines = rawText.split('\n');
    let mode = 'name'; // name | ing | step | tip | awaitName
    let nameFound = false;
    const tipLines = [];
    const stepBuffer = [];
    const meta = {};

    for (let i = 0; i < lines.length; i++) {
      const rawLine = lines[i];
      if (!rawLine.trim()) continue;

      const isQuote = /^\s*>/.test(rawLine);
      const plain = stripMd(rawLine.replace(/^\s*>\s?/, ''));
      if (!plain) continue;

      // ---- 0. 导出文件带的元信息：@分类：家常菜 / @时长：45分钟
      if (plain.startsWith('@')) {
        readMetaLine(plain, meta);
        continue;
      }

      // ---- 1. 贴士（优先级最高，"小贴士："开头的行不进正文）
      if (RE_TIP_HEAD.test(plain)) {
        const body = plain.replace(new RegExp(`^(${TIP_WORDS})[：:]?`, 'i'), '').trim();
        if (body) tipLines.push(body);
        mode = 'tip';
        continue;
      }

      // ---- 2. Markdown 标题
      const h = rawLine.match(/^\s*(#{1,6})\s+(.*)$/);
      if (h) {
        const title = stripMd(h[2]);
        if (RE_ING_HEAD.test(title)) { mode = 'ing'; continue; }
        if (RE_STEP_HEAD.test(title)) { mode = 'step'; continue; }
        if (!nameFound) {
          nameFound = true;
          if (RE_PLACEHOLDER_NAME.test(title.trim())) {
            // 等下一行的真实菜名
            mode = 'awaitName';
          } else {
            result.name = title;
            mode = 'ing';
          }
        } else {
          mode = 'ing';
        }
        continue;
      }

      // ---- 3. 前缀式：菜名：xxx
      const mName = plain.match(RE_NAME_INLINE);
      if (mName) {
        result.name = mName[2].trim();
        nameFound = true;
        mode = 'ing';
        continue;
      }

      // ---- 4. 前缀式：食材：xxx
      const mIng = plain.match(RE_ING_INLINE);
      if (mIng) {
        mode = 'ing';
        parseIngredientLine(mIng[2]).forEach((it) => result.ingredients.push(it));
        continue;
      }

      // ---- 5. 前缀式：步骤：xxx
      const mStep = plain.match(RE_STEP_INLINE);
      if (mStep) {
        mode = 'step';
        stepBuffer.push(mStep[2]);
        continue;
      }

      // ---- 6. 纯文本段落标题（食材 / 步骤 / 小贴士 独占一行）
      if (RE_ING_HEAD.test(plain)) { mode = 'ing'; continue; }
      if (RE_STEP_HEAD.test(plain)) { mode = 'step'; continue; }

      // ---- 7. 引用行
      if (isQuote) {
        if (plain) tipLines.push(plain);
        continue;
      }

      // ---- 8. 首个非空行兜底当菜名
      if (!nameFound && mode === 'name') {
        const first = plain.replace(/^[#\s]+/, '').trim();
        nameFound = true;
        if (RE_PLACEHOLDER_NAME.test(first)) {
          mode = 'awaitName';
        } else {
          result.name = first;
          mode = 'ing';
        }
        continue;
      }

      // 7.5 上一行是「# 菜名」这类占位标题，用这一行的内容当真实菜名
      if (mode === 'awaitName') {
        const real = plain.replace(/^[#\s]+/, '').trim();
        if (real && !RE_ING_HEAD.test(real) && !RE_STEP_HEAD.test(real)) {
          result.name = real;
          mode = 'ing';
          continue;
        }
      }

      // ---- 9. 编号行 → 切到步骤（食材区里出现 1. 2. 说明步骤开始了）
      if (mode !== 'step' && STEP_NUM_STRICT.test(rawLine)) {
        mode = 'step';
        stepBuffer.push(rawLine);
        continue;
      }

      // ---- 10. 按当前模式归集
      if (mode === 'ing') {
        parseIngredientLine(rawLine).forEach((it) => result.ingredients.push(it));
      } else if (mode === 'step') {
        stepBuffer.push(rawLine);
      } else if (mode === 'tip') {
        tipLines.push(plain);
      } else {
        parseIngredientLine(rawLine).forEach((it) => result.ingredients.push(it));
      }
    }

    // 合并步骤：编号行开新步骤，未编号行并入上一步
    let current = '';
    const flush = () => {
      const t = stripMd(current).trim();
      if (t) result.steps.push(t);
      current = '';
    };
    for (const line of stepBuffer) {
      const numbered = STEP_NUM_STRICT.test(line) || STEP_BULLET.test(line);
      const cleaned = line
        .replace(STEP_NUM_STRICT, '')
        .replace(STEP_BULLET, '')
        .trim();
      if (!cleaned) continue;
      if (numbered) {
        flush();
        current = cleaned;
      } else if (!current) {
        current = cleaned;
      } else {
        current += ' ' + cleaned;
      }
    }
    flush();

    result.tips = tipLines.filter(Boolean).join(' ');

    // 归一化
    if (result.name) {
      result.name = result.name.replace(/^[《【\[]+|[》】\]]+$/g, '').trim();
    }
    result.ingredients = dedupeIngredients(result.ingredients);

    // 导出文件里带了元信息就用它，否则按菜名猜分类
    result.category = CAT_BY_TEXT[(meta['分类'] || '').trim()] ||
      guessCategory(result.name, result.ingredients);
    result.minutes = firstInt(meta['时长']) ?? 0;
    result.servings = firstInt(meta['份量']) ?? 2;
    result.difficulty = parseDifficulty(meta['难度']);

    if (!result.name) result.hints.push('没识别到菜名，可在第一行写 # 菜名');
    if (!result.ingredients.length) result.hints.push('没识别到食材，检查是否有「### 食材」小标题');
    if (!result.steps.length) result.hints.push('没识别到步骤，检查是否有「### 步骤」小标题');

    result.ok = !!result.name && (result.ingredients.length > 0 || result.steps.length > 0);
    return result;
  }

  /** 读取一行 `@键：值` */
  function readMetaLine(line, out) {
    const body = line.replace(/^@/, '').trim();
    const m = body.match(/^([^：:]+)[：:]\s*(.+)$/);
    if (m && m[1].trim() && m[2].trim()) out[m[1].trim()] = m[2].trim();
  }

  function firstInt(text) {
    const m = String(text || '').match(/\d+/);
    return m ? Number(m[0]) : null;
  }

  function parseDifficulty(text) {
    const t = String(text || '').trim();
    if (t.includes('难')) return 3;
    if (t.includes('中')) return 2;
    return 1;
  }

  /** 去重（同名食材只留第一次出现的用量） */
  function dedupeIngredients(list) {
    const seen = new Set();
    const out = [];
    for (const it of list) {
      const key = (it.n || '').trim();
      if (!key) {
        if (it.a) out.push(it);
        continue;
      }
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(it);
    }
    return out;
  }

  /** 菜谱 → 文本（编辑时回填） */
  function stringifyRecipe(recipe) {
    const ings = (recipe.ingredients || [])
      .filter((i) => i.n || i.a)
      .map((i) => (i.a ? `${i.n} ${i.a}` : i.n))
      .join('、');

    const steps = (recipe.steps || [])
      .filter((s) => s && s.trim())
      .map((s, i) => `${i + 1}. ${s}`)
      .join('\n');

    const parts = [`# ${recipe.name || '未命名菜谱'}`, ''];

    // 元信息：导出 TXT 时带上，导入到另一台手机才能还原分类与份量
    parts.push(`@分类：${CAT_TEXT[recipe.category] || '其他'}`);
    if (Number(recipe.minutes) > 0) parts.push(`@时长：${Number(recipe.minutes)}分钟`);
    if (Number(recipe.servings) > 0) parts.push(`@份量：${Number(recipe.servings)}人份`);
    parts.push(`@难度：${DIFF_TEXT[(Number(recipe.difficulty) || 1) - 1] || '简单'}`);

    parts.push('', '### 食材', ings, '', '### 步骤', steps);

    if (recipe.description && recipe.description.trim()) {
      parts.push('', `> 小贴士：${recipe.description.trim()}`);
    }
    return parts.join('\n');
  }

  /** 分隔行：4 个以上等号独占一行，菜谱正文里基本不会出现 */
  const RE_SPLIT = /^={4,}.*$/m;

  /** 多道菜谱 → 一个 TXT */
  function exportAll(recipes) {
    if (!recipes || !recipes.length) return '';
    return recipes
      .map((r, i) => `==========  菜谱 ${i + 1}  ==========\n\n${stringifyRecipe(r)}`)
      .join('\n\n');
  }

  /** TXT → 若干段落 */
  function splitBlocks(text) {
    return String(text || '')
      .replace(/\r\n?/g, '\n')
      .split(RE_SPLIT)
      .map((x) => x.trim())
      .filter(Boolean);
  }

  /** 建议的导出文件名 */
  function suggestFileName(d = new Date()) {
    const p = (n) => String(n).padStart(2, '0');
    const stamp = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-` +
      `${p(d.getHours())}${p(d.getMinutes())}`;
    return `我的菜谱-${stamp}.txt`;
  }

  return {
    parseRecipe, stringifyRecipe, splitIngredient, stripMd, guessCategory,
    exportAll, splitBlocks, suggestFileName,
  };
});
