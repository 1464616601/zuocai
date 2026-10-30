/* 放松小游戏 · 与菜谱 App 内置的五个小游戏
   纯 Canvas 实现，无外部依赖。接口约定：

     const game = Games.create(key, canvas)
     game.resize(w, h)       // 画布尺寸变化
     game.update(dt)         // dt 毫秒
     game.render(ctx)
     game.input(action)      // up / down / left / right / a / b
     game.state              // { score, over, paused, label }

   每个游戏自己管状态，宿主只负责循环、计分板和按键。
*/
(function (root) {
  'use strict';

  const C = {
    // 街机暗色主题：与 Android 端 GameStyle 一致
    bg: '#17130F',
    grid: '#352C23',
    ink: '#F3E9DC',
    sub: '#8A7A6C',
    brand: '#E2571E',
    brand2: '#F08A4B',
    soft: '#F6E0CE',
    good: '#3E9A5C',
    warn: '#F2A03D',
    white: '#FFFFFF',
    darkPanel: '#241E18',
    light: '#F3E9DC',
    red: '#E9536F',
  };

  const rnd = (n) => Math.floor(Math.random() * n);
  const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);

  // 圆角矩形（部分浏览器没有 roundRect）
  function rr(ctx, x, y, w, h, r) {
    const rad = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + rad, y);
    ctx.arcTo(x + w, y, x + w, y + h, rad);
    ctx.arcTo(x + w, y + h, x, y + h, rad);
    ctx.arcTo(x, y + h, x, y, rad);
    ctx.arcTo(x, y, x + w, y, rad);
    ctx.closePath();
  }

  function fillRR(ctx, x, y, w, h, r, color) {
    ctx.fillStyle = color;
    rr(ctx, x, y, w, h, r);
    ctx.fill();
  }

  function text(ctx, str, x, y, size, color, align, weight) {
    ctx.fillStyle = color || C.ink;
    ctx.font = `${weight || 600} ${size}px -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif`;
    ctx.textAlign = align || 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText(str, x, y);
  }

  /* ================================================================
     1. 贪吃蛇
     ================================================================ */
  function Snake() {
    const COLS = 15;
    const ROWS = 22;
    let cell = 16;
    const state = { score: 0, over: false, paused: false, label: '', events: [] };

    let snake, dir, nextDir, food, acc, stepMs, alive, started;

    function reset() {
      snake = [{ x: 7, y: 11 }, { x: 7, y: 12 }, { x: 7, y: 13 }];
      dir = { x: 0, y: -1 };
      nextDir = { x: 0, y: -1 };
      acc = 0;
      stepMs = 150;
      alive = true;
      started = false;
      state.score = 0;
      state.over = false;
      placeFood();
    }

    function placeFood() {
      let tries = 0;
      do {
        food = { x: rnd(COLS), y: rnd(ROWS) };
        tries++;
      } while (tries < 200 && snake.some((s) => s.x === food.x && s.y === food.y));
    }

    function step() {
      dir = nextDir;
      const head = { x: snake[0].x + dir.x, y: snake[0].y + dir.y };

      if (head.x < 0 || head.y < 0 || head.x >= COLS || head.y >= ROWS) {
        alive = false;
        state.over = true;
        state.events.push('over');
        return;
      }
      if (snake.some((s) => s.x === head.x && s.y === head.y)) {
        alive = false;
        state.over = true;
        state.events.push('over');
        return;
      }

      snake.unshift(head);
      if (head.x === food.x && head.y === food.y) {
        state.score += 10;
        placeFood();
        if (stepMs > 70) stepMs -= 3;
        state.events.push('eat');
      } else {
        snake.pop();
      }
    }

    reset();

    return {
      state,
      reset,
      resize(w, h) {
        cell = Math.floor(Math.min(w / COLS, (h - 8) / ROWS));
      },
      input(a) {
        const map = {
          up: { x: 0, y: -1 }, down: { x: 0, y: 1 },
          left: { x: -1, y: 0 }, right: { x: 1, y: 0 },
        };
        const d = map[a];
        if (!d) return;
        // 不能直接反向
        if (d.x === -dir.x && d.y === -dir.y) return;
        nextDir = d;
        started = true;
      },
      update(dt) {
        if (state.over || !alive) return;
        state.label = started ? '' : '按方向键开始';
        if (!started) return;
        acc += dt;
        while (acc >= stepMs) {
          acc -= stepMs;
          step();
          if (state.over) break;
        }
      },
      render(ctx, w, h) {
        const ox = Math.floor((w - COLS * cell) / 2);
        const oy = Math.floor((h - ROWS * cell) / 2);

        ctx.fillStyle = C.bg;
        ctx.fillRect(0, 0, w, h);

        // 棋盘底纹
        ctx.strokeStyle = C.grid;
        ctx.lineWidth = 1;
        for (let i = 0; i <= COLS; i++) {
          ctx.beginPath();
          ctx.moveTo(ox + i * cell, oy);
          ctx.lineTo(ox + i * cell, oy + ROWS * cell);
          ctx.stroke();
        }
        for (let j = 0; j <= ROWS; j++) {
          ctx.beginPath();
          ctx.moveTo(ox, oy + j * cell);
          ctx.lineTo(ox + COLS * cell, oy + j * cell);
          ctx.stroke();
        }

        // 食物：光晕 + 实心
        ctx.fillStyle = C.red;
        ctx.globalAlpha = 0.18;
        ctx.beginPath();
        ctx.arc(ox + food.x * cell + cell / 2, oy + food.y * cell + cell / 2, cell * 0.46, 0, 7);
        ctx.fill();
        ctx.globalAlpha = 1;
        ctx.beginPath();
        ctx.arc(ox + food.x * cell + cell / 2, oy + food.y * cell + cell / 2, cell * 0.3, 0, 7);
        ctx.fill();

        // 蛇（撞墙那一下头部会越界，跳过不画）
        snake.forEach((s, i) => {
          if (s.x < 0 || s.y < 0 || s.x >= COLS || s.y >= ROWS) return;
          const t = i / Math.max(1, snake.length - 1);
          ctx.fillStyle = i === 0 ? C.ink : `rgba(42,33,27,${0.85 - t * 0.45})`;
          fillRR(
            ctx,
            ox + s.x * cell + 1.5,
            oy + s.y * cell + 1.5,
            cell - 3,
            cell - 3,
            i === 0 ? cell * 0.32 : cell * 0.24,
            ctx.fillStyle
          );
        });
      },
    };
  }

  /* ================================================================
     2. 俄罗斯方块
     ================================================================ */
  function Tetris() {
    const COLS = 10;
    const ROWS = 18;
    let cell = 18;
    const state = { score: 0, over: false, paused: false, label: '', events: [] };

    const SHAPES = [
      [[1, 1, 1, 1]],                       // I
      [[1, 1], [1, 1]],                     // O
      [[0, 1, 0], [1, 1, 1]],               // T
      [[1, 0, 0], [1, 1, 1]],               // J
      [[0, 0, 1], [1, 1, 1]],               // L
      [[0, 1, 1], [1, 1, 0]],               // S
      [[1, 1, 0], [0, 1, 1]],               // Z
    ];
    const COLORS = [C.brand, C.warn, '#8B5CF6', '#3B82F6', C.brand2, C.good, '#E9536F'];

    let board, cur, nextIdx, dropAcc, dropMs, lines;

    function newPiece() {
      const idx = nextIdx;
      nextIdx = rnd(SHAPES.length);
      const shape = SHAPES[idx].map((r) => r.slice());
      return {
        cells: shape,
        color: COLORS[idx],
        x: Math.floor((COLS - shape[0].length) / 2),
        y: 0,
      };
    }

    function reset() {
      board = Array.from({ length: ROWS }, () => Array(COLS).fill(null));
      nextIdx = rnd(SHAPES.length);
      cur = newPiece();
      dropAcc = 0;
      dropMs = 620;
      lines = 0;
      state.score = 0;
      state.over = false;
    }

    function collide(cells, px, py) {
      for (let y = 0; y < cells.length; y++) {
        for (let x = 0; x < cells[y].length; x++) {
          if (!cells[y][x]) continue;
          const bx = px + x;
          const by = py + y;
          if (bx < 0 || bx >= COLS || by >= ROWS) return true;
          if (by >= 0 && board[by][bx]) return true;
        }
      }
      return false;
    }

    function rotate(cells) {
      const h = cells.length;
      const w = cells[0].length;
      const out = Array.from({ length: w }, () => Array(h).fill(0));
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) out[x][h - 1 - y] = cells[y][x];
      }
      return out;
    }

    function lock() {
      cur.cells.forEach((row, y) => row.forEach((v, x) => {
        if (v && cur.y + y >= 0) board[cur.y + y][cur.x + x] = cur.color;
      }));

      // 消行
      let cleared = 0;
      for (let y = ROWS - 1; y >= 0; y--) {
        if (board[y].every((c) => c)) {
          board.splice(y, 1);
          board.unshift(Array(COLS).fill(null));
          cleared++;
          y++;
        }
      }
      if (cleared) {
        lines += cleared;
        state.score += [0, 100, 300, 500, 800][cleared] || 800;
        dropMs = Math.max(150, 620 - lines * 22);
        state.events.push('line');
      }

      cur = newPiece();
      if (collide(cur.cells, cur.x, cur.y)) {
        state.over = true;
        state.events.push('over');
      }
    }

    function move(dx) {
      if (!collide(cur.cells, cur.x + dx, cur.y)) cur.x += dx;
    }

    function softDrop() {
      if (!collide(cur.cells, cur.x, cur.y + 1)) {
        cur.y++;
        state.score += 1;
      } else {
        lock();
      }
    }

    function hardDrop() {
      while (!collide(cur.cells, cur.x, cur.y + 1)) {
        cur.y++;
        state.score += 2;
      }
      state.events.push('drop');
      lock();
    }

    function doRotate() {
      const r = rotate(cur.cells);
      for (const dx of [0, -1, 1, -2, 2]) {
        if (!collide(r, cur.x + dx, cur.y)) {
          cur.cells = r;
          cur.x += dx;
          return;
        }
      }
    }

    reset();

    return {
      state,
      reset,
      resize(w, h) {
        cell = Math.floor(Math.min(w / COLS, (h - 10) / ROWS));
      },
      input(a) {
        if (state.over) return;
        if (a === 'left') move(-1);
        else if (a === 'right') move(1);
        else if (a === 'up' || a === 'a') doRotate();
        else if (a === 'down') softDrop();
        else if (a === 'b') hardDrop();
      },
      update(dt) {
        if (state.over) return;
        dropAcc += dt;
        while (dropAcc >= dropMs) {
          dropAcc -= dropMs;
          if (!collide(cur.cells, cur.x, cur.y + 1)) cur.y++;
          else lock();
          if (state.over) break;
        }
      },
      render(ctx, w, h) {
        const ox = Math.floor((w - COLS * cell) / 2);
        const oy = Math.floor((h - ROWS * cell) / 2);

        ctx.fillStyle = C.bg;
        ctx.fillRect(0, 0, w, h);

        // 边框
        ctx.strokeStyle = C.grid;
        ctx.lineWidth = 2;
        ctx.strokeRect(ox, oy, COLS * cell, ROWS * cell);

        const drawCell = (cx, cy, color) => {
          fillRR(ctx, ox + cx * cell + 1.5, oy + cy * cell + 1.5, cell - 3, cell - 3, 4, color);
        };

        // 已固定的方块
        board.forEach((row, y) => row.forEach((c, x) => {
          if (c) drawCell(x, y, c);
        }));

        // 落点投影
        let ghostY = cur.y;
        while (!collide(cur.cells, cur.x, ghostY + 1)) ghostY++;
        cur.cells.forEach((row, y) => row.forEach((v, x) => {
          if (!v) return;
          ctx.globalAlpha = 0.16;
          drawCell(cur.x + x, ghostY + y, cur.color);
          ctx.globalAlpha = 1;
        }));

        // 当前方块
        cur.cells.forEach((row, y) => row.forEach((v, x) => {
          if (v && cur.y + y >= 0) drawCell(cur.x + x, cur.y + y, cur.color);
        }));
      },
    };
  }

  /* ================================================================
     3. 推箱子
     ================================================================ */
  function Sokoban() {
    const LEVELS = [
      [
        '########',
        '#  .   #',
        '#  $   #',
        '#  @   #',
        '#      #',
        '########',
      ],
      [
        '#########',
        '#   #   #',
        '# $ # . #',
        '#  @    #',
        '# $   . #',
        '#   #   #',
        '#########',
      ],
      [
        '##########',
        '#  ..    #',
        '#  $$    #',
        '#   @    #',
        '#        #',
        '#   ##   #',
        '##########',
      ],
    ];

    let cell = 30;
    let map = [];
    let px = 0;
    let py = 0;
    let level = 0;
    let history = [];
    const state = { score: 0, over: false, paused: false, label: '', events: [] };

    function load(idx) {
      level = idx;
      const src = LEVELS[idx];
      map = src.map((row) => row.split(''));
      history = [];
      for (let y = 0; y < map.length; y++) {
        for (let x = 0; x < map[y].length; x++) {
          if (map[y][x] === '@' || map[y][x] === '+') {
            px = x; py = y;
            map[y][x] = map[y][x] === '+' ? '*' : ' ';
          }
        }
      }
      state.over = false;
      state.label = `第 ${idx + 1} 关 · 共 ${LEVELS.length} 关`;
    }

    function snapshot() {
      history.push({ map: map.map((r) => r.slice()), px, py });
      if (history.length > 60) history.shift();
    }

    function isDone() {
      return !map.some((row) => row.some((c) => c === '$'));
    }

    function walk(dx, dy) {
      if (state.over) return;
      const nx = px + dx;
      const ny = py + dy;
      const target = map[ny] && map[ny][nx];
      if (!target || target === '#') return;

      if (target === '$') {
        const bx = nx + dx;
        const by = ny + dy;
        const beyond = map[by] && map[by][bx];
        if (!beyond || beyond === '#' || beyond === '$' || beyond === '*') return;
        snapshot();
        map[by][bx] = beyond === '.' ? '*' : '$';
        map[ny][nx] = ' ';
        px = nx; py = ny;
        state.events.push('push');
      } else {
        if (target === '*') return; // 箱子落在点上，不能走
        snapshot();
        px = nx; py = ny;
      }

      if (isDone()) {
        state.score += 100;
        state.events.push('win');
        if (level + 1 < LEVELS.length) {
          load(level + 1);
        } else {
          state.over = true;
          state.label = '全部通关';
          state.events.push('over');
        }
      }
    }

    load(0);

    return {
      state,
      reset: () => load(level),
      resize(w, h) {
        const rows = map.length || 6;
        const cols = (map[0] && map[0].length) || 8;
        cell = Math.floor(Math.min((w - 16) / cols, (h - 16) / rows));
      },
      input(a) {
        if (a === 'up') walk(0, -1);
        else if (a === 'down') walk(0, 1);
        else if (a === 'left') walk(-1, 0);
        else if (a === 'right') walk(1, 0);
        else if (a === 'b') load(level);          // 重来本关
        else if (a === 'a' && history.length) {   // 撤销
          const s = history.pop();
          map = s.map;
          px = s.px;
          py = s.py;
        }
      },
      update() {},
      render(ctx, w, h) {
        const rows = map.length;
        const cols = map[0].length;
        const ox = Math.floor((w - cols * cell) / 2);
        const oy = Math.floor((h - rows * cell) / 2);

        ctx.fillStyle = C.bg;
        ctx.fillRect(0, 0, w, h);

        map.forEach((row, y) => row.forEach((c, x) => {
          const gx = ox + x * cell;
          const gy = oy + y * cell;

          if (c === '#') {
            fillRR(ctx, gx + 1, gy + 1, cell - 2, cell - 2, 5, '#6B5C4A');
            return;
          }
          fillRR(ctx, gx + 1, gy + 1, cell - 2, cell - 2, 4, C.darkPanel);

          if (c === '.' || c === '*') {
            ctx.fillStyle = C.brand;
            ctx.beginPath();
            ctx.arc(gx + cell / 2, gy + cell / 2, cell * 0.18, 0, 7);
            ctx.fill();
          }
          if (c === '$' || c === '*') {
            const onGoal = c === '*';
            fillRR(ctx, gx + cell * 0.15, gy + cell * 0.15,
              cell * 0.7, cell * 0.7, 6, onGoal ? C.good : '#C99A5B');
            // 木箱纹理
            ctx.strokeStyle = 'rgba(255,255,255,.5)';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.moveTo(gx + cell * 0.15, gy + cell * 0.15);
            ctx.lineTo(gx + cell * 0.85, gy + cell * 0.85);
            ctx.stroke();
          }
        }));

        // 小人：圆头 + 肩膀 + 两条腿
        const cx = ox + px * cell + cell / 2;
        const cy = oy + py * cell + cell / 2;
        const R = cell * 0.17;
        ctx.fillStyle = C.ink;
        ctx.beginPath();
        ctx.arc(cx, cy - cell * 0.2, R, 0, 7);
        ctx.fill();
        fillRR(ctx, cx - cell * 0.21, cy - cell * 0.02, cell * 0.42, cell * 0.26, cell * 0.1, C.ink);
        fillRR(ctx, cx - cell * 0.15, cy + cell * 0.2, cell * 0.11, cell * 0.14, 2, C.ink);
        fillRR(ctx, cx + cell * 0.04, cy + cell * 0.2, cell * 0.11, cell * 0.14, 2, C.ink);

      },
    };
  }

  /* ================================================================
     4. 飞机大战
     ================================================================ */
  function Shooter() {
    const state = { score: 0, over: false, paused: false, label: '', events: [] };
    let W = 360;
    let H = 560;

    let player, bullets, enemies, eBullets, parts, spawnAcc, shootAcc, hp;

    function reset() {
      player = { x: W / 2, y: H - 70, r: 15 };
      bullets = [];
      enemies = [];
      eBullets = [];
      parts = [];
      spawnAcc = 0;
      shootAcc = 0;
      hp = 3;
      state.score = 0;
      state.over = false;
    }

    function boom(x, y, color) {
      for (let i = 0; i < 10; i++) {
        const a = Math.random() * 7;
        const s = 40 + Math.random() * 120;
        parts.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, life: 420, color });
      }
    }

    reset();

    return {
      state,
      reset,
      resize(w, h) {
        W = w; H = h;
        if (player) {
          player.x = clamp(player.x, 20, W - 20);
          player.y = H - 70;
        }
      },
      input(a) {
        if (state.over) return;
        const sp = 26;
        if (a === 'left') player.x = clamp(player.x - sp, 18, W - 18);
        else if (a === 'right') player.x = clamp(player.x + sp, 18, W - 18);
        else if (a === 'up') player.y = clamp(player.y - sp, H * 0.45, H - 40);
        else if (a === 'down') player.y = clamp(player.y + sp, H * 0.45, H - 40);
      },
      update(dt) {
        if (state.over) return;
        const k = dt / 16.7;

        shootAcc += dt;
        if (shootAcc > 150) {
          shootAcc = 0;
          bullets.push({ x: player.x, y: player.y - 18 });
          state.events.push('shoot');
        }

        spawnAcc += dt;
        if (spawnAcc > Math.max(320, 900 - state.score * 0.4)) {
          spawnAcc = 0;
          enemies.push({
            x: 24 + Math.random() * (W - 48),
            y: -30,
            r: 16,
            vy: 1.4 + Math.random() * 1.3 + state.score * 0.0006,
            fireAcc: Math.random() * 900,
            hp: 1 + (Math.random() < 0.15 ? 1 : 0),
          });
        }

        bullets.forEach((b) => { b.y -= 7.5 * k; });
        bullets = bullets.filter((b) => b.y > -20);

        eBullets.forEach((b) => { b.y += 4.2 * k; });
        eBullets = eBullets.filter((b) => b.y < H + 20);

        enemies.forEach((e) => {
          e.y += e.vy * k;
          e.fireAcc += dt;
          if (e.fireAcc > 1400 && e.y > 0) {
            e.fireAcc = 0;
            eBullets.push({ x: e.x, y: e.y + 14 });
          }
        });

        // 子弹命中敌人
        for (let i = enemies.length - 1; i >= 0; i--) {
          const e = enemies[i];
          for (let j = bullets.length - 1; j >= 0; j--) {
            const b = bullets[j];
            if (Math.abs(b.x - e.x) < e.r && Math.abs(b.y - e.y) < e.r) {
              bullets.splice(j, 1);
              e.hp--;
              if (e.hp <= 0) {
                boom(e.x, e.y, C.brand);
                enemies.splice(i, 1);
                state.score += 10;
                state.events.push('boom');
              } else {
                state.events.push('hit');
              }
              break;
            }
          }
        }

        // 敌人撞玩家
        enemies.forEach((e) => {
          if (Math.abs(e.x - player.x) < e.r + 10 && Math.abs(e.y - player.y) < e.r + 12) {
            boom(e.x, e.y, C.warn);
            e.y = H + 100;
            hp--;
            state.events.push('hurt');
          }
        });
        enemies = enemies.filter((e) => e.y < H + 40);

        // 敌弹命中玩家
        for (let i = eBullets.length - 1; i >= 0; i--) {
          const b = eBullets[i];
          if (Math.abs(b.x - player.x) < 12 && Math.abs(b.y - player.y) < 14) {
            eBullets.splice(i, 1);
            boom(player.x, player.y, C.brand2);
            hp--;
            state.events.push('hurt');
          }
        }

        parts.forEach((p) => {
          p.x += p.vx * (dt / 1000);
          p.y += p.vy * (dt / 1000);
          p.life -= dt;
        });
        parts = parts.filter((p) => p.life > 0);

        if (hp <= 0) {
          state.over = true;
          state.events.push('over');
        }
        state.label = `生命 ${Math.max(0, hp)} / 3`;
      },
      render(ctx, w, h) {
        ctx.fillStyle = '#1E1A16';
        ctx.fillRect(0, 0, w, h);

        // 星空
        for (let i = 0; i < 26; i++) {
          const y = ((i * 97 + (performance.now() * 0.03)) % h);
          ctx.fillStyle = i % 3 ? 'rgba(255,255,255,.18)' : 'rgba(255,255,255,.4)';
          ctx.fillRect((i * 53) % w, y, 2, 2);
        }

        // 玩家飞机
        ctx.save();
        ctx.translate(player.x, player.y);
        // 机身
        ctx.fillStyle = C.brand;
        ctx.beginPath();
        ctx.moveTo(0, -20);
        ctx.quadraticCurveTo(5, -10, 5, 4);
        ctx.lineTo(5, 13);
        ctx.lineTo(-5, 13);
        ctx.lineTo(-5, 4);
        ctx.quadraticCurveTo(-5, -10, 0, -20);
        ctx.closePath();
        ctx.fill();
        // 后掠翼
        ctx.fillStyle = C.brand2;
        ctx.beginPath();
        ctx.moveTo(-4, -2);
        ctx.lineTo(-17, 11);
        ctx.lineTo(-16, 14);
        ctx.lineTo(-4, 10);
        ctx.closePath();
        ctx.fill();
        ctx.beginPath();
        ctx.moveTo(4, -2);
        ctx.lineTo(17, 11);
        ctx.lineTo(16, 14);
        ctx.lineTo(4, 10);
        ctx.closePath();
        ctx.fill();
        // 尾翼
        ctx.beginPath();
        ctx.moveTo(-3, 10);
        ctx.lineTo(-8, 15);
        ctx.lineTo(8, 15);
        ctx.lineTo(3, 10);
        ctx.closePath();
        ctx.fill();
        // 座舱
        ctx.fillStyle = 'rgba(255,255,255,.75)';
        ctx.beginPath();
        ctx.ellipse(0, -6, 2.6, 5, 0, 0, 7);
        ctx.fill();
        ctx.restore();

        // 我方子弹
        bullets.forEach((b) => {
          fillRR(ctx, b.x - 2, b.y - 8, 4, 12, 2, '#FFD9C2');
        });

        // 敌机
        enemies.forEach((e) => {
          ctx.fillStyle = e.hp > 1 ? '#8B5CF6' : '#7BC96F';
          ctx.beginPath();
          ctx.moveTo(e.x, e.y + 15);
          ctx.lineTo(e.x + 14, e.y - 10);
          ctx.lineTo(e.x, e.y - 4);
          ctx.lineTo(e.x - 14, e.y - 10);
          ctx.closePath();
          ctx.fill();
        });

        // 敌弹
        eBullets.forEach((b) => {
          ctx.fillStyle = '#FF6B6B';
          ctx.beginPath();
          ctx.arc(b.x, b.y, 3.4, 0, 7);
          ctx.fill();
        });

        // 爆炸粒子
        parts.forEach((p) => {
          ctx.globalAlpha = Math.max(0, p.life / 420);
          ctx.fillStyle = p.color;
          ctx.fillRect(p.x - 2, p.y - 2, 4, 4);
          ctx.globalAlpha = 1;
        });
      },
    };
  }

  /* ================================================================
     5. 坦克大战
     ================================================================ */
  function Tank() {
    const state = { score: 0, over: false, paused: false, label: '', events: [] };

    // 战场固定 13×20 逻辑格，格子按画布自适应、整体居中——
    // 手机上墙不会再缩在左上角（旧版固定 36px 且 resize 不重排）
    const COLS = 13;
    const ROWS = 20;

    let W = 360;
    let H = 560;
    let cell = 27;
    let ox = 0;
    let oy = 0;
    let T = 25;

    let player, enemies, bullets, walls, parts, spawnAcc, hp, baseAlive;

    const bfLeft = () => ox;
    const bfTop = () => oy;
    const bfRight = () => ox + COLS * cell;
    const bfBottom = () => oy + ROWS * cell;
    const bfCX = () => ox + (COLS * cell) / 2;
    const wallCX = (w) => ox + w.cx * cell + cell / 2;
    const wallCY = (w) => oy + w.cy * cell + cell / 2;

    function layout() {
      cell = Math.min(W / COLS, H / ROWS);
      ox = (W - COLS * cell) / 2;
      oy = (H - ROWS * cell) / 2;
      T = cell * 0.92;
    }

    function buildWalls() {
      walls = [];
      const put = (cx, cy, hp) => {
        if (cx < 1 || cx > COLS - 2 || cy < 1 || cy > ROWS - 3) return;
        walls.push({ cx, cy, hp: hp || 2 });
      };

      const left = 2;
      const right = COLS - 3;
      [3, 4, 8, 9].forEach((cy) => { put(left, cy); put(right, cy); });
      [4, 5, 9, 10].forEach((cy) => { put(left + 1, cy); put(right - 1, cy); });

      const mid = Math.floor(COLS / 2);
      [5, 6].forEach((cx) => { put(cx, 6); put(COLS - 1 - cx, 6); });
      put(mid, 7);
      put(mid, 11);
      put(mid - 1, 11);
      put(mid + 1, 11);

      const baseCol = Math.floor(COLS / 2);
      put(baseCol - 2, ROWS - 4, 3);
      put(baseCol + 2, ROWS - 4, 3);
      put(baseCol - 1, ROWS - 3, 3);
      put(baseCol + 1, ROWS - 3, 3);
    }

    function boom(x, y, color) {
      for (let i = 0; i < 8; i++) {
        const a = Math.random() * 6.28;
        const s = cell * (2 + Math.random() * 4);
        parts.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, life: 380, color });
      }
    }

    function reset() {
      layout();
      player = { x: bfCX(), y: bfBottom() - cell * 1.4, dir: 0, cool: 0 };
      enemies = [];
      bullets = [];
      parts = [];
      hp = 3;
      baseAlive = true;
      spawnAcc = 0;
      state.score = 0;
      state.over = false;
      buildWalls();
    }

    function shoot() {
      if (player.cool > 0) return;
      player.cool = 260;
      const d = DIRS[player.dir];
      bullets.push({
        x: player.x + d.x * T * 0.55,
        y: player.y + d.y * T * 0.55,
        vx: d.x, vy: d.y, mine: true,
      });
      state.events.push('shoot');
    }

    function enemyShoot(e) {
      const d = DIRS[e.dir];
      bullets.push({
        x: e.x + d.x * T * 0.55,
        y: e.y + d.y * T * 0.55,
        vx: d.x, vy: d.y, mine: false,
      });
    }

    function hitWall(x, y) {
      return walls.some((w) => Math.abs(wallCX(w) - x) < cell * 0.72 &&
        Math.abs(wallCY(w) - y) < cell * 0.72);
    }

    const DIRS = [
      { x: 0, y: -1 }, { x: 1, y: 0 }, { x: 0, y: 1 }, { x: -1, y: 0 },
    ];

    reset();

    return {
      state,
      reset,
      resize(w, h) {
        W = w; H = h;
        layout();
        const half = T / 2;
        player.x = clamp(player.x, bfLeft() + half, bfRight() - half);
        player.y = clamp(player.y, bfTop() + T, bfBottom() - half);
        enemies.forEach((e) => {
          e.x = clamp(e.x, bfLeft() + half, bfRight() - half);
          e.y = clamp(e.y, bfTop() + half, bfBottom() - T);
        });
        bullets = bullets.filter((b) =>
          b.x > bfLeft() && b.x < bfRight() && b.y > bfTop() && b.y < bfBottom());
      },
      input(a) {
        if (state.over) return;
        const step = cell * 0.62;
        if (a === 'up') { player.dir = 0; player.y = Math.max(bfTop() + T / 2, player.y - step); }
        else if (a === 'right') { player.dir = 1; player.x = Math.min(bfRight() - T / 2, player.x + step); }
        else if (a === 'down') { player.dir = 2; player.y = Math.min(bfBottom() - T / 2, player.y + step); }
        else if (a === 'left') { player.dir = 3; player.x = Math.max(bfLeft() + T / 2, player.x - step); }
        else if (a === 'a' || a === 'b') shoot();
      },
      update(dt) {
        if (state.over) return;
        const k = dt / 16.7;
        const espd = cell * 0.043 * k;
        const bspd = cell * 0.18 * k;
        player.cool = Math.max(0, player.cool - dt);

        spawnAcc += dt;
        if (spawnAcc > Math.max(900, 2000 - state.score * 3) && enemies.length < 5) {
          spawnAcc = 0;
          let ex = bfLeft() + cell * 1.5;
          for (let t = 0; t < 16; t++) {
            const cand = bfLeft() + cell + Math.random() * (COLS * cell - cell * 2);
            const tooClose = enemies.some((o) => Math.abs(o.x - cand) < T * 1.3);
            if (!hitWall(cand, bfTop() + cell * 0.7) && !tooClose) { ex = cand; break; }
          }
          enemies.push({
            x: ex,
            y: bfTop() + cell * 0.6,
            dir: 2,
            hp: Math.random() < 0.25 ? 2 : 1,
          });
        }

        enemies.forEach((e) => {
          if (Math.random() < dt / 700) e.dir = Math.floor(Math.random() * 4);
          const d = DIRS[e.dir];
          const nx = e.x + d.x * espd;
          const ny = e.y + d.y * espd;
          if (nx > bfLeft() + T / 2 && nx < bfRight() - T / 2 &&
            ny > bfTop() + T / 2 && ny < bfBottom() - T && !hitWall(nx, ny)) {
            e.x = nx;
            e.y = ny;
          } else {
            e.dir = Math.floor(Math.random() * 4);
          }

          if (Math.random() < dt / 1500) enemyShoot(e);
        });

        bullets.forEach((b) => {
          b.x += b.vx * bspd;
          b.y += b.vy * bspd;
        });

        // 子弹打墙
        bullets.forEach((b) => {
          for (let i = walls.length - 1; i >= 0; i--) {
            const w = walls[i];
            if (b.x > ox + w.cx * cell && b.x < ox + (w.cx + 1) * cell &&
              b.y > oy + w.cy * cell && b.y < oy + (w.cy + 1) * cell) {
              w.hp--;
              b.dead = true;
              boom(b.x, b.y, '#B08958');
              if (w.hp <= 0) walls.splice(i, 1);
              break;
            }
          }
        });

        // 我方子弹打敌人
        bullets.filter((b) => b.mine && !b.dead).forEach((b) => {
          for (let i = enemies.length - 1; i >= 0; i--) {
            const e = enemies[i];
            if (Math.abs(b.x - e.x) < T * 0.7 && Math.abs(b.y - e.y) < T * 0.7) {
              b.dead = true;
              e.hp--;
              if (e.hp <= 0) {
                enemies.splice(i, 1);
                state.score += 20;
                boom(e.x, e.y, C.brand);
                state.events.push('boom');
              } else {
                state.events.push('hit');
              }
              break;
            }
          }
        });

        // 敌弹打玩家
        bullets.filter((b) => !b.mine && !b.dead).forEach((b) => {
          if (Math.abs(b.x - player.x) < T * 0.6 && Math.abs(b.y - player.y) < T * 0.6) {
            b.dead = true;
            hp--;
            player.x = bfCX();
            player.y = bfBottom() - cell * 1.4;
            boom(player.x, player.y, C.brand2);
            state.events.push('hurt');
          }
        });

        // 敌人撞玩家
        enemies.forEach((e) => {
          if (Math.abs(e.x - player.x) < T && Math.abs(e.y - player.y) < T) {
            hp--;
            e.hp = 0;
            boom(e.x, e.y, C.warn);
            state.events.push('hurt');
          }
        });
        enemies = enemies.filter((e) => e.hp > 0);

        parts.forEach((p) => {
          p.x += p.vx * (dt / 1000);
          p.y += p.vy * (dt / 1000);
          p.life -= dt;
        });
        parts = parts.filter((p) => p.life > 0);

        bullets = bullets.filter((b) => !b.dead &&
          b.x > bfLeft() - 10 && b.x < bfRight() + 10 &&
          b.y > bfTop() - 10 && b.y < bfBottom() + 10);

        if (hp <= 0 || !baseAlive) {
          state.over = true;
          state.events.push('over');
        }
        state.label = `生命 ${Math.max(0, hp)} / 3`;
      },
      render(ctx, w, h) {
        ctx.fillStyle = C.bg;
        ctx.fillRect(0, 0, w, h);

        // 战场地面 + 边框
        fillRR(ctx, bfLeft(), bfTop(), COLS * cell, ROWS * cell, 8, C.darkPanel);
        ctx.strokeStyle = C.grid;
        ctx.lineWidth = 1;
        for (let i = 1; i < COLS; i++) {
          ctx.beginPath();
          ctx.moveTo(bfLeft() + i * cell, bfTop());
          ctx.lineTo(bfLeft() + i * cell, bfBottom());
          ctx.stroke();
        }
        for (let j = 1; j < ROWS; j++) {
          ctx.beginPath();
          ctx.moveTo(bfLeft(), bfTop() + j * cell);
          ctx.lineTo(bfRight(), bfTop() + j * cell);
          ctx.stroke();
        }

        // 砖墙
        walls.forEach((wl) => {
          const x = ox + wl.cx * cell;
          const y = oy + wl.cy * cell;
          fillRR(ctx, x + 1, y + 1, cell - 2, cell - 2, 3,
            wl.hp >= 3 ? '#8A6A45' : '#B08958');
          ctx.strokeStyle = 'rgba(0,0,0,.25)';
          ctx.beginPath();
          ctx.moveTo(x + 1, y + cell / 2);
          ctx.lineTo(x + cell - 1, y + cell / 2);
          ctx.stroke();
        });

        // 基地
        const bw = cell * 1.1;
        const bh = cell * 0.8;
        ctx.fillStyle = baseAlive ? '#8A6A2F' : '#4A4038';
        rr(ctx, bfCX() - bw / 2, bfBottom() - bh - 4, bw, bh, 5);
        ctx.fill();
        ctx.fillStyle = baseAlive ? C.warn : '#6B5F55';
        rr(ctx, bfCX() - bw / 2 + 4, bfBottom() - bh, bw - 8, bh - 8, 3);
        ctx.fill();

        const drawTank = (x, y, dir, color, size) => {
          ctx.save();
          ctx.translate(x, y);
          ctx.rotate((dir * Math.PI) / 2);
          ctx.fillStyle = 'rgba(0,0,0,.4)';
          rr(ctx, -size * 0.5, -size * 0.38, size * 0.14, size * 0.76, 2);
          ctx.fill();
          rr(ctx, size * 0.36, -size * 0.38, size * 0.14, size * 0.76, 2);
          ctx.fill();
          ctx.fillStyle = color;
          rr(ctx, -size * 0.42, -size * 0.42, size * 0.84, size * 0.84, 4);
          ctx.fill();
          ctx.fillStyle = 'rgba(0,0,0,.45)';
          rr(ctx, -size * 0.09, -size * 0.72, size * 0.18, size * 0.5, 2);
          ctx.fill();
          ctx.restore();
        };

        drawTank(player.x, player.y, player.dir, C.brand, T);
        enemies.forEach((e) => drawTank(e.x, e.y, e.dir, e.hp > 1 ? '#8B5CF6' : '#7BC96F', T));

        bullets.forEach((b) => {
          ctx.fillStyle = b.mine ? '#FFD9C2' : '#FF6B6B';
          ctx.beginPath();
          ctx.arc(b.x, b.y, cell * 0.1, 0, 7);
          ctx.fill();
        });

        parts.forEach((p) => {
          ctx.globalAlpha = Math.max(0, p.life / 380);
          ctx.fillStyle = p.color;
          ctx.beginPath();
          ctx.arc(p.x, p.y, cell * 0.09, 0, 7);
          ctx.fill();
          ctx.globalAlpha = 1;
        });
      },
    };
  }

  /* ================================================================
     对外接口
     ================================================================ */
  const META = [
    {
      key: 'snake', name: '贪吃蛇', desc: '吃到红色的果子，别撞墙也别咬到自己',
      icon: 'snake', hint: '方向键转向',
    },
    {
      key: 'tetris', name: '俄罗斯方块', desc: '转一转、堆一堆，消掉整行拿高分',
      icon: 'tetris', hint: '▲旋转 · ▼加速 · 长按▼直接落底',
    },
    {
      key: 'sokoban', name: '推箱子', desc: '把木箱推到圆点上，共三关',
      icon: 'box', hint: 'A 撤销 · B 重来本关',
    },
    {
      key: 'shooter', name: '飞机大战', desc: '自动开火，躲子弹，活下来',
      icon: 'plane', hint: '方向键移动',
    },
    {
      key: 'tank', name: '坦克大战', desc: '守住基地，打掉来袭的坦克',
      icon: 'tank', hint: '方向键移动 · A 开炮',
    },
  ];

  const FACTORY = {
    snake: Snake,
    tetris: Tetris,
    sokoban: Sokoban,
    shooter: Shooter,
    tank: Tank,
  };

  function create(key) {
    const f = FACTORY[key];
    return f ? f() : null;
  }

  root.Games = { META, create };
})(typeof self !== 'undefined' ? self : this);
