<script setup lang="ts">
/**
 * 滑块拼图验证组件。
 *
 * <p>渲染策略（关键，直接决定体验是否"丝滑"）：
 * <ul>
 *     <li><b>背景与缺口只在重置时绘制一次</b>：拖动过程中绝不重绘背景。
 *         早期实现每次 pointermove 都重新生成随机背景，导致画面每帧换一张图（明显闪烁）；</li>
 *     <li><b>拼图块用 transform 位移</b>：块内容画在一个独立的 canvas 上，
 *         拖动时只改 translateX，由合成器处理，不触发 canvas 重绘；</li>
 *     <li><b>只在松手失败回弹时才启用过渡</b>：拖动过程不加 transition，否则会滞后于手指。</li>
 * </ul>
 *
 * <p>视觉（2026 改版）：早期版本的底图是随机色相的高饱和渐变（hsl(random,70%,62%) + 十几个霓虹圆），
 * 一出现就像在克制的登录页里炸开一朵彩虹，与全站"蓝 + 石板灰"的调性直接冲突。
 * 现改为<b>固定色相区间（蓝→靛）的程序化极光</b>：低饱和渐变 + 柔和光斑 + 等高线波纹 + 细网格 + 颗粒，
 * 每次重置仍会换一张，但始终长着同一张脸。
 *
 * <p>安全相关：
 * <ul>
 *     <li><b>缺口位置由服务端下发</b>（targetX）：前端只负责渲染，不参与"正确答案"的产生；</li>
 *     <li><b>前端不判定结果</b>：只采集"落点 + 轨迹 + 时长 + 停顿"并原样上报；</li>
 *     <li><b>轨迹不修匀、不补点</b>：修饰过的轨迹失去判定意义；</li>
 *     <li><b>指针事件统一</b>：pointer 事件同时覆盖鼠标与触屏，键盘亦可完成（辅助设备可达）。</li>
 * </ul>
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = withDefaults(
  defineProps<{
    /** 缺口目标 x（服务端下发） */
    targetX: number;
    /** 画布宽度（与后端滑块区间常量保持一致） */
    width?: number;
    /** 画布高度 */
    height?: number;
    /** 拼图块边长 */
    blockSize?: number;
    /** 服务端判定中：拖动结束后到结果返回前的等待态 */
    pending?: boolean;
  }>(),
  { width: 300, height: 160, blockSize: 48, pending: false },
);

const emit = defineEmits<{
  (e: 'done', payload: { slideX: number; track: number[]; durationMs: number; startDelayMs: number }): void;
}>();

/** 外层容器：用于等比缩放测量 */
const wrapRef = ref<HTMLDivElement | null>(null);
const bgRef = ref<HTMLCanvasElement | null>(null);
const blockRef = ref<HTMLCanvasElement | null>(null);
const sliderX = ref(0);
const dragging = ref(false);
/** 失败回弹中：仅在此时给位移加过渡动画 */
const resetting = ref(false);
/** 拼图块纵向位置（在可放置范围内随机） */
const blockY = ref(0);
/** 窄屏等比缩放比：1 表示按设计尺寸渲染 */
const scale = ref(1);

/** 背景缓存：拼图块从中取图，保证块与背景图案一致 */
let bgCache: HTMLCanvasElement | null = null;
/** 一次拖动的采样轨迹 */
let track: number[] = [];
let startClientX = 0;
let downAt = 0;
let firstMoveAt = 0;
let lastSampleAt = 0;
let observer: ResizeObserver | null = null;

/** 手柄宽度（逻辑像素）：与 maxX 配合，保证拖到尽头不越界 */
const HANDLE_W = 44;
/** 轨道高度（逻辑像素）：足够大，移动端也好按 */
const BAR_H = 42;

/** 凸/凹半径 */
const radius = computed(() => props.blockSize / 4);
/** 可滑动的最大距离（右边界留出拼图块宽度） */
const maxX = computed(() => props.width - props.blockSize);
/** 舞台显示尺寸（逻辑尺寸 × 缩放比） */
const stageW = computed(() => props.width * scale.value);
const stageH = computed(() => props.height * scale.value);
/** 拖动进度：用于轨道文案淡出 */
const progress = computed(() => Math.round((sliderX.value / Math.max(1, maxX.value)) * 100));

/**
 * 轨道引导文案：只在等待用户操作时出现。
 *
 * <p>判定中让位给 stage 上的等待层——两处同时说"正在验证"是重复噪音。
 */
const tipText = computed(() => (props.pending ? '' : '按住滑块，拖动完成拼图'));

/** 设备像素比上限取 2：再高对拼图底图没有可感知收益，只会拖慢绘制 */
function dpr(): number {
  return Math.min(window.devicePixelRatio || 1, 2);
}

/** 当前是否暗色主题：底图色板随之切换 */
function isDark(): boolean {
  return document.documentElement.classList.contains('dark');
}

/**
 * 绘制底图（离屏画布）。
 *
 * <p>只在重置时调用：拖动过程中不重新生成，避免画面"每帧换一张图"的闪烁。
 * 色相固定落在 202°–248°（蓝→靛），与品牌主色成套；光斑、波纹、网格随机，
 * 因此每次都不一样，但不会跳出调性。
 *
 * @param ctx 画布上下文
 * @param w   逻辑宽度
 * @param h   逻辑高度
 */
function drawBackground(ctx: CanvasRenderingContext2D, w: number, h: number): void {
  const dark = isDark();
  const hue = 202 + Math.random() * 46;
  const grad = ctx.createLinearGradient(0, 0, w, h);
  grad.addColorStop(0, `hsl(${hue}, ${dark ? 46 : 62}%, ${dark ? 34 : 76}%)`);
  grad.addColorStop(1, `hsl(${hue + 26}, ${dark ? 54 : 68}%, ${dark ? 19 : 56}%)`);
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, w, h);

  // 两团柔和极光：大半径、低透明，只为让画面有深浅
  for (let i = 0; i < 2; i++) {
    const cx = w * (0.18 + Math.random() * 0.64);
    const cy = h * (0.12 + Math.random() * 0.76);
    const r = 70 + Math.random() * 84;
    const glow = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
    glow.addColorStop(0, `hsla(${hue + 52 + i * 26}, 92%, ${dark ? 54 : 84}%, ${dark ? 0.2 : 0.4})`);
    glow.addColorStop(1, 'hsla(0, 0%, 100%, 0)');
    ctx.fillStyle = glow;
    ctx.fillRect(0, 0, w, h);
  }

  // 等高线波纹：细线、低对比，制造"可被拼合"的层次
  ctx.lineWidth = 1;
  for (let i = 0; i < 8; i++) {
    const amp = 5 + Math.random() * 15;
    const freq = 0.01 + Math.random() * 0.022;
    const phase = Math.random() * Math.PI * 2;
    const baseY = ((h + 44) * i) / 8 - 22;
    ctx.beginPath();
    for (let x = 0; x <= w; x += 3) {
      const y = baseY + Math.sin(x * freq + phase) * amp + Math.sin(x * freq * 2.3 + phase) * amp * 0.25;
      if (x === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    }
    ctx.strokeStyle = dark ? 'rgba(226, 232, 240, 0.13)' : 'rgba(255, 255, 255, 0.46)';
    ctx.stroke();
  }

  // 细网格：给"对齐"一个参照物，也让纯色区域不至于太空
  ctx.beginPath();
  for (let x = 24; x < w; x += 24) {
    ctx.moveTo(x + 0.5, 0);
    ctx.lineTo(x + 0.5, h);
  }
  for (let y = 24; y < h; y += 24) {
    ctx.moveTo(0, y + 0.5);
    ctx.lineTo(w, y + 0.5);
  }
  ctx.strokeStyle = dark ? 'rgba(148, 163, 184, 0.07)' : 'rgba(255, 255, 255, 0.26)';
  ctx.stroke();

  drawGrain(ctx, w, h, dark);
}

/**
 * 叠加一层极淡颗粒：消除大面积渐变的"塑料感"。
 *
 * @param ctx  画布上下文
 * @param w    逻辑宽度
 * @param h    逻辑高度
 * @param dark 是否暗色主题
 */
function drawGrain(ctx: CanvasRenderingContext2D, w: number, h: number, dark: boolean): void {
  const count = Math.round(w * h * 0.02);
  for (let i = 0; i < count; i++) {
    const alpha = (Math.random() * 0.05).toFixed(3);
    ctx.fillStyle = dark ? `rgba(255, 255, 255, ${alpha})` : `rgba(15, 23, 42, ${alpha})`;
    ctx.fillRect(Math.random() * w, Math.random() * h, 1, 1);
  }
}

/**
 * 拼图形状路径：上边中部凸起 + 右边中部凸起（缺口为其反形）。
 *
 * @param ctx  画布上下文
 * @param x    左上角 x
 * @param y    左上角 y
 */
function puzzlePath(ctx: CanvasRenderingContext2D, x: number, y: number): void {
  const r = radius.value;
  const s = props.blockSize;
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.lineTo(x + s / 2 - r, y);
  ctx.arc(x + s / 2, y, r, Math.PI, Math.PI * 2);
  ctx.lineTo(x + s, y);
  ctx.lineTo(x + s, y + s / 2 - r);
  ctx.arc(x + s, y + s / 2, r, -Math.PI / 2, Math.PI / 2);
  ctx.lineTo(x + s, y + s);
  ctx.lineTo(x, y + s);
  ctx.closePath();
}

/** 绘制底图（背景 + 缺口）：仅在重置时执行一次 */
function renderStage(): void {
  const bg = bgRef.value;
  if (!bg) {
    return;
  }
  const ctx = bg.getContext('2d');
  if (!ctx) {
    return;
  }
  const { width, height, targetX } = props;
  const ratio = dpr();
  bg.width = Math.round(width * ratio);
  bg.height = Math.round(height * ratio);
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  ctx.clearRect(0, 0, width, height);

  // 背景生成一次并缓存：拼图块复用同一份图案，保证拖到缺口处严丝合缝
  bgCache = document.createElement('canvas');
  bgCache.width = bg.width;
  bgCache.height = bg.height;
  const cacheCtx = bgCache.getContext('2d');
  if (!cacheCtx) {
    return;
  }
  cacheCtx.setTransform(ratio, 0, 0, ratio, 0, 0);
  drawBackground(cacheCtx, width, height);
  ctx.drawImage(bgCache, 0, 0, width, height);

  // 挖出缺口（destination-out 挖成透明，露出画布底色）
  ctx.globalCompositeOperation = 'destination-out';
  puzzlePath(ctx, targetX, blockY.value);
  ctx.fill();
  ctx.globalCompositeOperation = 'source-over';

  // 缺口描边：一层暗边压住轮廓，一层浅边提亮内侧，形成"凹陷"的错觉
  ctx.save();
  puzzlePath(ctx, targetX, blockY.value);
  ctx.shadowColor = 'rgba(2, 6, 23, 0.45)';
  ctx.shadowBlur = 6;
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.55)';
  ctx.lineWidth = 1.5;
  ctx.stroke();
  ctx.restore();
}

/** 绘制拼图块内容：同样只执行一次，拖动时靠 transform 移动 */
function renderBlock(): void {
  const block = blockRef.value;
  if (!block || !bgCache) {
    return;
  }
  const ctx = block.getContext('2d');
  if (!ctx) {
    return;
  }
  const r = radius.value;
  const s = props.blockSize;
  const ratio = dpr();
  const w = s + r;
  const h = s + r;
  block.width = Math.round(w * ratio);
  block.height = Math.round(h * ratio);
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  ctx.clearRect(0, 0, w, h);
  ctx.save();
  // 块画布顶部预留 r 给"上凸"，所以路径起点是 (0, r)
  puzzlePath(ctx, 0, r);
  ctx.clip();
  // 源坐标为缓存画布的物理像素，故乘 ratio；目标为逻辑像素，由 transform 负责放大
  ctx.drawImage(
    bgCache,
    props.targetX * ratio,
    (blockY.value - r) * ratio,
    w * ratio,
    h * ratio,
    0,
    0,
    w,
    h,
  );
  ctx.restore();
  puzzlePath(ctx, 0, r);
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.85)';
  ctx.lineWidth = 1.5;
  ctx.stroke();
}

/** 重置：换一张背景、滑块归位（缺口位置由服务端决定，不在这里改） */
function reset(): void {
  const r = radius.value;
  blockY.value = Math.floor(r + Math.random() * (props.height - props.blockSize - r * 2));
  sliderX.value = 0;
  dragging.value = false;
  track = [];
  renderStage();
  renderBlock();
}

/** 开始一次拖动会话：记录首帧轨迹与起始时间（指针与键盘共用） */
function beginSession(): void {
  if (downAt) {
    return;
  }
  downAt = Date.now();
  firstMoveAt = 0;
  lastSampleAt = 0;
  track = [Math.round(sliderX.value)];
}

/**
 * 按采样节奏追加一个轨迹点。
 *
 * <p>约每帧采样一次：既保留真实节奏，也不至于产生过大的轨迹数组。
 */
function sample(): void {
  const now = Date.now();
  if (!firstMoveAt) {
    firstMoveAt = now;
  }
  if (now - lastSampleAt >= 16) {
    track.push(Math.round(sliderX.value));
    lastSampleAt = now;
  }
}

/** 移动到指定位置（已做边界收敛） */
function moveTo(x: number): void {
  beginSession();
  sliderX.value = Math.min(maxX.value, Math.max(0, x));
  sample();
}

/** 结束一次拖动：上报过程特征（前端不做任何判定） */
function finish(): void {
  if (!downAt) {
    return;
  }
  dragging.value = false;
  const now = Date.now();
  track.push(Math.round(sliderX.value));
  emit('done', {
    slideX: Math.round(sliderX.value),
    track,
    durationMs: now - downAt,
    startDelayMs: firstMoveAt ? firstMoveAt - downAt : 0,
  });
  downAt = 0;
  firstMoveAt = 0;
}

function onPointerDown(e: PointerEvent): void {
  if (props.pending) {
    return;
  }
  resetting.value = false;
  dragging.value = true;
  startClientX = e.clientX;
  beginSession();
  (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
}

function onPointerMove(e: PointerEvent): void {
  if (!dragging.value) {
    return;
  }
  // 屏幕位移换算回逻辑坐标：窄屏整体缩放后手感与视觉保持一致
  const delta = (e.clientX - startClientX) / scale.value;
  startClientX = e.clientX;
  // 只更新位移：背景与块都不重绘，画面因此不会闪
  sliderX.value = Math.min(maxX.value, Math.max(0, sliderX.value + delta));
  sample();
}

/**
 * 键盘操作：方向键微调、Home/End 直达两端、回车提交。
 *
 * <p>滑块是"辅助设备也能走通"的降级前提，只有指针事件等于把键盘用户排除在外。
 */
function onKeydown(e: KeyboardEvent): void {
  if (props.pending) {
    return;
  }
  const step = e.shiftKey ? 16 : 4;
  if (e.key === 'ArrowLeft') {
    moveTo(sliderX.value - step);
  } else if (e.key === 'ArrowRight') {
    moveTo(sliderX.value + step);
  } else if (e.key === 'Home') {
    moveTo(0);
  } else if (e.key === 'End') {
    moveTo(maxX.value);
  } else if (e.key === 'Enter' || e.key === ' ') {
    if (!downAt) {
      // 从未移动过就提交等于"没验证"，先给一帧会话避免误触
      beginSession();
    }
    finish();
  } else {
    return;
  }
  e.preventDefault();
}

/** 同步缩放比：容器变窄时整体等比缩小，避免画布溢出卡片 */
function syncScale(): void {
  const el = wrapRef.value;
  if (!el || !el.clientWidth) {
    return;
  }
  scale.value = Math.min(1, el.clientWidth / props.width);
}

// 新票据意味着新缺口：这里重绘，同时把滑块归位（组件在父级以 ticketId 为 key 重建，此分支为兜底）
watch(() => props.targetX, reset);

onMounted(() => {
  syncScale();
  reset();
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(syncScale);
    if (wrapRef.value) {
      observer.observe(wrapRef.value);
    }
  }
});

onBeforeUnmount(() => {
  dragging.value = false;
  observer?.disconnect();
  observer = null;
});

/**
 * 失败回弹：带过渡地滑回起点，并换一张新底图。
 *
 * @returns 动画结束的 Promise，便于调用方串行"回弹 → 换新票"
 */
function resetWithAnimation(): Promise<void> {
  return new Promise((resolve) => {
    resetting.value = true;
    sliderX.value = 0;
    window.setTimeout(() => {
      reset();
      window.setTimeout(() => {
        resetting.value = false;
        resolve();
      }, 20);
    }, 300);
  });
}

defineExpose({ reset: resetWithAnimation });
</script>

<template>
  <div ref="wrapRef" class="slide">
    <div class="slide__stage" :class="{ 'is-dragging': dragging }" :style="{ width: stageW + 'px', height: stageH + 'px' }">
      <canvas
        ref="bgRef"
        class="slide__bg"
        :style="{ width: stageW + 'px', height: stageH + 'px' }"
      />
      <!-- 拖动时的竖向"扫描光"：告诉用户当前对齐到哪了 -->
      <span
        v-if="dragging"
        class="slide__scan"
        aria-hidden="true"
        :style="{ transform: `translateX(${(sliderX + props.blockSize / 2) * scale}px)` }"
      />
      <canvas
        ref="blockRef"
        class="slide__block"
        :class="{ 'is-dragging': dragging, 'is-resetting': resetting }"
        :style="{
          top: (blockY - radius) * scale + 'px',
          width: (props.blockSize + radius) * scale + 'px',
          height: (props.blockSize + radius) * scale + 'px',
          transform: `translateX(${sliderX * scale}px)`,
        }"
      />
      <!-- 判定中：遮一层等待态，避免用户不知道"松手之后发生了什么" -->
      <div v-if="pending" class="slide__pending">
        <span class="slide__spinner" aria-hidden="true" />
        <span>正在验证…</span>
      </div>
    </div>

    <div
      class="slide__bar"
      :class="{ 'is-active': dragging || sliderX > 0 }"
      :style="{ width: stageW + 'px', height: BAR_H * scale + 'px' }"
    >
      <div
        class="slide__fill"
        :class="{ 'is-resetting': resetting }"
        :style="{ width: (sliderX + HANDLE_W) * scale + 'px' }"
      />
      <span class="slide__tip">{{ tipText }}</span>
      <div
        class="slide__handle"
        :class="{ 'is-dragging': dragging, 'is-resetting': resetting }"
        :style="{ left: sliderX * scale + 'px', width: HANDLE_W * scale + 'px', height: BAR_H * scale + 'px' }"
        role="slider"
        tabindex="0"
        aria-label="拖动滑块拼合图片"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="progress"
        :aria-valuetext="`已拖动 ${progress}%`"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="finish"
        @pointercancel="finish"
        @keydown="onKeydown"
      >
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
          <path
            d="M9 5l7 7-7 7"
            fill="none"
            stroke="currentColor"
            stroke-width="2.2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.slide {
  /* 组件级设计令牌：默认亮色，暗色在下方整体覆盖 */
  --slide-rail: #eef1f7;
  --slide-rail-line: var(--admin-border-color, #e4e7ed);
  --slide-handle: #ffffff;
  --slide-ink: #98a2b3;
  --slide-accent: #2563eb;
  --slide-accent-soft: rgba(37, 99, 235, 0.16);
  --slide-accent-fade: rgba(37, 99, 235, 0.03);
  --slide-hole: #dbe3ef;
  --slide-shadow: 0 2px 8px rgba(15, 23, 42, 0.18);

  display: block;
  width: 100%;
  max-width: 300px;
  user-select: none;

  &__stage {
    position: relative;
    overflow: hidden;
    border-radius: 8px;
    box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.06);
  }

  &__bg {
    display: block;
    /* 挖空处透出的底色：让缺口看得清，又不像一块黑洞 */
    background-color: var(--slide-hole);
  }

  /* 拼图块独立成层：拖动只改 transform，不重绘画布 */
  &__block {
    position: absolute;
    left: 0;
    pointer-events: none;
    will-change: transform;
    filter: drop-shadow(0 2px 4px rgba(2, 6, 23, 0.28));
    transition: filter 0.2s ease;

    &.is-dragging {
      filter: drop-shadow(0 6px 12px rgba(2, 6, 23, 0.34));
    }
  }

  /* 扫描光：跟随滑块的一道竖向微光，仅在拖动时存在 */
  &__scan {
    position: absolute;
    top: 0;
    left: 0;
    width: 2px;
    height: 100%;
    pointer-events: none;
    background: linear-gradient(180deg, transparent, rgba(255, 255, 255, 0.9), transparent);
    filter: blur(1.5px);
    opacity: 0.75;
  }

  /* 判定中遮罩：轻量等待态，不打断用户的空间感 */
  &__pending {
    position: absolute;
    inset: 0;
    display: flex;
    gap: 8px;
    font-size: 12.5px;
    letter-spacing: 0.08em;
    color: #fff;
    background-color: rgba(15, 23, 42, 0.45);
    backdrop-filter: blur(2px);
    align-items: center;
    justify-content: center;
  }

  &__spinner {
    width: 13px;
    height: 13px;
    border: 2px solid rgba(255, 255, 255, 0.35);
    border-top-color: #fff;
    border-radius: 50%;
    animation: slide-spin 0.7s linear infinite;
  }

  &__bar {
    position: relative;
    margin-top: 10px;
    border: 1px solid var(--slide-rail-line);
    /* 胶囊圆角与高度无关：窄屏等比缩放后依然是正圆头 */
    border-radius: 999px;
    background-color: var(--slide-rail);
    box-shadow: inset 0 1px 3px rgba(15, 23, 42, 0.06);
  }

  /* 填充：主色由浓到淡的横向渐变，与菜单 hover 同一套语言 */
  &__fill {
    height: 100%;
    border-radius: 999px;
    background-image: linear-gradient(90deg, var(--slide-accent-soft), var(--slide-accent-fade));
  }

  /* 引导文案：拖起来就淡出，别挡着反馈 */
  &__tip {
    position: absolute;
    inset: 0;
    display: flex;
    font-size: 12.5px;
    letter-spacing: 0.1em;
    color: var(--slide-ink);
    pointer-events: none;
    transition: opacity 0.2s ease;
    align-items: center;
    justify-content: center;
  }

  &__bar.is-active &__tip {
    opacity: 0;
  }

  &__handle {
    position: absolute;
    top: -1px;
    display: flex;
    color: var(--slide-ink);
    cursor: grab;
    touch-action: none;
    border: 1px solid var(--slide-rail-line);
    border-radius: 999px;
    background-color: var(--slide-handle);
    box-shadow: var(--slide-shadow);
    transition:
      background-color 0.18s ease,
      border-color 0.18s ease,
      color 0.18s ease;
    align-items: center;
    justify-content: center;

    &:hover {
      color: var(--slide-accent);
      border-color: var(--slide-accent);
    }

    &:focus-visible {
      outline: 2px solid var(--slide-accent);
      outline-offset: 2px;
    }

    &.is-dragging {
      color: #fff;
      cursor: grabbing;
      border-color: var(--slide-accent);
      background-color: var(--slide-accent);
    }
  }

  /* 只有回弹时才加过渡：拖动过程加过渡会滞后于手指 */
  &__block.is-resetting,
  &__fill.is-resetting,
  &__handle.is-resetting {
    transition:
      transform 0.3s cubic-bezier(0.22, 1, 0.36, 1),
      width 0.3s cubic-bezier(0.22, 1, 0.36, 1),
      left 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  }
}

/* 暗色：整体降饱和、降亮度，底图融入深蓝黑背景 */
:global(html.dark) .slide {
  --slide-rail: #16202f;
  --slide-rail-line: var(--admin-border-strong, #2f3b52);
  --slide-handle: #1e293b;
  --slide-ink: #64748b;
  --slide-accent: #60a5fa;
  --slide-accent-soft: rgba(96, 165, 250, 0.22);
  --slide-accent-fade: rgba(96, 165, 250, 0.04);
  --slide-hole: #0b1220;
  --slide-shadow: 0 2px 10px rgba(2, 6, 23, 0.6);
}

@keyframes slide-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .slide__spinner {
    animation-duration: 2s;
  }
}
</style>
