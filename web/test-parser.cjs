const { parseRecipe, stringifyRecipe } = require('./parser.js');

const sample = `# 花菜炒肉
### 食材
花菜半颗、瘦肉 200g、蒜 3 瓣、干辣椒可选、生抽 2 勺、蚝油 1 勺、盐、少许白糖、食用油
### 步骤
1. 花菜掰小朵，盐水浸泡 5 分钟，开水下锅焯**30 秒**捞出沥干（去生味，缩短炒制时间）；肉切片，用生抽 + 淀粉抓腌 10 分钟；蒜切片。
2. 热锅放油，肉片下锅大火炒至变色盛出。
3. 留底油，蒜片、干辣椒爆香，倒入花菜中火翻炒 2 分钟。
4. 倒回肉片，放生抽、蚝油、一点点白糖提鲜，加盐调味，大火翻炒均匀就可以出锅。
>
> 小贴士：焯水别太久，不然花菜软塌不清脆；喜欢干香口感可以不焯水，多炒一会儿。`;

const r = parseRecipe(sample);
console.log('=== 解析结果 ===');
console.log('菜名:', r.name);
console.log('ok:', r.ok, '| hints:', r.hints);
console.log('\n食材 (' + r.ingredients.length + '):');
r.ingredients.forEach((i, idx) => console.log(`  ${idx + 1}. [${i.n}] [${i.a}]`));
console.log('\n步骤 (' + r.steps.length + '):');
r.steps.forEach((s, idx) => console.log(`  ${idx + 1}. ${s}`));
console.log('\n贴士:', r.tips);

console.log('\n=== 反序列化 ===');
console.log(stringifyRecipe({
  name: '花菜炒肉',
  ingredients: r.ingredients,
  steps: r.steps,
  description: r.tips,
}));

console.log('\n=== 边界用例 ===');
const cases = [
  ['没标题纯文本', '青椒炒蛋\n鸡蛋2个、青椒1个、盐适量\n1、打散鸡蛋\n2、炒熟'],
  ['冒号分隔', '菜名：土豆丝\n食材：土豆 2个、醋 1勺\n步骤：\n1. 切丝\n2. 炒'],
  ['横线列表', '# 番茄炒蛋\n## 材料\n- 番茄 2个\n- 鸡蛋 3个\n## 做法\n- 番茄切块\n- 打蛋液\n- 一起炒'],
  ['紧凑单行食材', '# 蒜蓉西兰花\n食材\n西兰花一颗、蒜末适量、盐少许\n步骤\n1. 西兰花焯水\n2. 爆香蒜末翻炒'],
  ['含破折号与加粗', '**红烧肉**\n**用料**：五花肉500g、冰糖30g、老抽2勺\n**做法**：\n1. 五花肉切块焯水\n2. 炒糖色\n3. 加水炖40分钟'],
];

for (const [label, text] of cases) {
  const p = parseRecipe(text);
  console.log(`\n[${label}] name=${p.name} | ing=${p.ingredients.length} | step=${p.steps.length} | ok=${p.ok}`);
  console.log('  食材:', p.ingredients.map((i) => `${i.n}${i.a ? '/' + i.a : ''}`).join(', '));
  console.log('  步骤:', p.steps.map((s) => s.slice(0, 18)).join(' | '));
}
