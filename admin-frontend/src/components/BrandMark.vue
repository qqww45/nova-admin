<script setup lang="ts">
/**
 * 品牌标记（Brand Mark）。
 *
 * <p>设计原则：
 * <ul>
 *     <li><b>几何 + 单一焦点</b>：32×32 视口只画一件事（简化的 N 形 + 一颗高光点），
 *         缩小到 16×16 仍然可辨；</li>
 *     <li><b>渐变自带明暗两套</b>：亮色主题深蓝→亮蓝，暗色主题亮蓝→青，
 *         与 token 体系同色相但亮度反转，避免"暗色下还是亮色感"的违和；</li>
 *     <li><b>N 上的光点是 nova（新星）的语义表达</b>：一个稳定字形 + 一颗跃动的点，
 *         让 logo 既有重量又不呆板；</li>
 *     <li><b>不继承 currentColor</b>：mark 自身用固定色，
 *         避免外部 hover 时字色变化把 logo 染脏。</li>
 * </ul>
 *
 * <p>使用：<code>&lt;BrandMark :size="32" /&gt;</code>；<code>size=0</code> 时占满父容器。
 */
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    /** 像素边长；0 表示自适应（CSS 中由 width/height 100% 撑满） */
    size?: number;
    /** 是否在 mark 右侧渲染 "NOVA" 文字 */
    withWordmark?: boolean;
  }>(),
  { size: 32, withWordmark: false },
);

/** mark 实际尺寸：size=0 时退回 100%，由外层容器决定 */
const sizeStyle = computed(() =>
  props.size > 0 ? { width: `${props.size}px`, height: `${props.size}px` } : { width: '100%', height: '100%' },
);
</script>

<template>
  <span class="brand-mark">
    <svg
      class="brand-mark__svg"
      :style="sizeStyle"
      viewBox="0 0 32 32"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label="NOVA Admin"
    >
      <defs>
        <!-- 主渐变：stop 色值由 CSS 驱动，明暗主题自动切换 -->
        <linearGradient id="nova-mark-bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" class="brand-mark__stop-0" />
          <stop offset="100%" class="brand-mark__stop-1" />
        </linearGradient>
      </defs>
      <!-- 圆角方底：8px 圆角，克制的企业调性 -->
      <rect x="0" y="0" width="32" height="32" rx="8" fill="url(#nova-mark-bg)" />
      <!-- N 形：左右竖 + 中间斜，斜边比"几何感"再收一点 -->
      <path
        d="M9 22 L9 10 L12 10 L20 19 L20 10 L23 10 L23 22 L20 22 L12 13 L12 22 Z"
        fill="#ffffff"
        fill-opacity="0.96"
      />
      <!-- nova 光点：右竖顶端外侧，"星"的语义化点睛 -->
      <circle cx="20" cy="6" r="1.6" fill="#ffffff" fill-opacity="0.85" />
    </svg>
    <!-- wordmark：仅在显式开启时渲染；字距拉满，与 logomark 形成节奏差 -->
    <span v-if="withWordmark" class="brand-mark__word">NOVA</span>
  </span>
</template>

<style scoped lang="scss">
/* mark 容器：inline-flex 让 mark 与文字基线对齐，且不被父容器高度撑开 */
.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  vertical-align: middle;
}

.brand-mark__svg {
  display: block;
  /* 给 mark 一圈极淡的投影：让它在任何底色上都能"浮"起来一点点 */
  filter: drop-shadow(0 2px 6px rgba(15, 23, 42, 0.18));
  transition: filter var(--admin-transition-duration) ease;
}

/* 暗色下投影染上主色微光，与整体的"星"意象呼应 */
:global(html.dark) .brand-mark__svg {
  filter: drop-shadow(0 2px 10px rgba(96, 165, 250, 0.32));
}

/* 渐变停色：亮色 blue-700 → sky-400，底色有重量、顶端有亮度 */
.brand-mark__stop-0 {
  stop-color: #1d4ed8;
}

.brand-mark__stop-1 {
  stop-color: #38bdf8;
}

/* 暗色反相：blue-500 → cyan-400，避免深底上的灰蓝感 */
:global(html.dark) .brand-mark__stop-0 {
  stop-color: #3b82f6;
}

:global(html.dark) .brand-mark__stop-1 {
  stop-color: #22d3ee;
}

.brand-mark__word {
  margin-right: -0.32em; /* 抵消末尾字距，保证视觉居中 */
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.32em; /* 拉满字距：让 "N O V A" 自带 logo 的停顿节奏 */
  color: var(--admin-text-primary);
}
</style>
