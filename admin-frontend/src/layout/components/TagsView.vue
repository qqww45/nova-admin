<script setup lang="ts">
/**
 * 标签页导航（多页签）。
 *
 * <p>交互：点击切换、右键刷新/关闭其他/关闭全部、当前页不可关闭（首页常驻）。
 * 数据存在组件内，刷新页面后重新累积（后台系统无需持久化页签）。
 *
 * <p>视觉（2026 改版）：弃用 el-tag——它的 info/success 主题色在暗色下
 * 会产生大片发白发亮的高对比色块；改为自绘"胶囊 chip"：
 * <ul>
 *     <li>未选中：低对比灰胶囊（--admin-tag-bg），hover 微亮；</li>
 *     <li>选中：主色浅透明胶囊 + 主色描边 + 前置主色圆点，
 *         与侧边栏菜单的选中语义保持一致；</li>
 *     <li>关闭按钮为圆形 hover 态，暗色下同样克制。</li>
 * </ul>
 * 全部取值来自 theme.css 的 --admin-tag-* 令牌，明暗主题自动适配。
 */
import { ref, watch } from 'vue';
import { useRoute, useRouter, type RouteLocationNormalized } from 'vue-router';
import { Close } from '@element-plus/icons-vue';

interface TagView {
  fullPath: string;
  title: string;
  name?: string;
  closable: boolean;
}

const route = useRoute();
const router = useRouter();

/** 已打开页签（首页常驻且不可关闭） */
const visitedViews = ref<TagView[]>([
  { fullPath: '/dashboard', title: '仪表盘', name: 'Dashboard', closable: false },
]);

/** 记录当前路由到页签 */
function addView(current: RouteLocationNormalized): void {
  if (!current.name || current.meta.hidden) {
    return;
  }
  const exists = visitedViews.value.some((item) => item.fullPath === current.fullPath);
  if (!exists) {
    visitedViews.value.push({
      fullPath: current.fullPath,
      title: (current.meta.title as string) ?? '未命名',
      name: current.name as string,
      closable: current.fullPath !== '/dashboard',
    });
  }
}

watch(
  () => route.fullPath,
  () => addView(route),
  { immediate: true },
);

/** 判断是否为当前激活页签 */
function isActive(tag: TagView): boolean {
  return tag.fullPath === route.fullPath;
}

/** 跳转 */
function go(tag: TagView): void {
  void router.push(tag.fullPath);
}

/** 关闭页签 */
function close(tag: TagView): void {
  const index = visitedViews.value.findIndex((item) => item.fullPath === tag.fullPath);
  if (index < 0) {
    return;
  }
  visitedViews.value.splice(index, 1);
  if (isActive(tag)) {
    const next = visitedViews.value[index] ?? visitedViews.value[index - 1];
    void router.push(next ? next.fullPath : '/dashboard');
  }
}

/** 关闭其他 */
function closeOthers(): void {
  visitedViews.value = visitedViews.value.filter((item) => item.fullPath === route.fullPath || !item.closable);
}

/** 关闭全部（保留首页） */
function closeAll(): void {
  visitedViews.value = visitedViews.value.filter((item) => !item.closable);
  void router.push('/dashboard');
}

/**
 * 刷新当前页。
 *
 * <p>做法：先 replace 到 /redirect/<当前路径>，由 Redirect 组件弹回原地址，
 * 借助"卸载 → 重新挂载"重建页面组件（数据重拉、状态复位）。
 * <b>必须带上当前路径</b>：只跳 /redirect 的话，中转页不知道要弹回哪里。
 *
 * <p>兜底：若中转失败（理论上不会），退回整页 reload，保证功能可用。
 */
function refresh(): void {
  const target = route.path.replace(/^\//, '');
  void router
    .replace({
      path: `/redirect/${target}`,
      query: { ...route.query, t: String(Date.now()) },
    })
    .catch(() => {
      window.location.reload();
    });
}
</script>

<template>
  <div class="tags-view">
    <el-scrollbar class="tags-view__scroll">
      <div class="tags-view__list">
        <div
          v-for="tag in visitedViews"
          :key="tag.fullPath"
          class="tags-view__item"
          :class="{ 'tags-view__item--active': isActive(tag) }"
          @click="go(tag)"
        >
          <span class="tags-view__dot" aria-hidden="true" />
          <span class="tags-view__title">{{ tag.title }}</span>
          <el-icon v-if="tag.closable" class="tags-view__close" :size="12" @click.stop="close(tag)">
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>

    <el-dropdown class="tags-view__more" trigger="click">
      <el-icon :size="16"><ArrowDown /></el-icon>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="refresh">刷新当前页</el-dropdown-item>
          <el-dropdown-item @click="closeOthers">关闭其他</el-dropdown-item>
          <el-dropdown-item @click="closeAll">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped lang="scss">
.tags-view {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--admin-tagsview-height);
  padding: 0 12px;
  /* 背景 / 底边框由 AdminLayout 的 .admin-layout__top 统一提供，
     这里保持透明，避免出现第二条分割线形成"横带" */
  background-color: transparent;

  &__scroll {
    flex: 1;
    overflow: hidden;

    /* 滚动条只保留细横线，避免抢占视觉 */
    :deep(.el-scrollbar__bar.is-horizontal) {
      height: 4px;
    }
  }

  &__list {
    display: flex;
    gap: 6px;
    align-items: center;
    height: 100%;
    padding: 4px 0;
    white-space: nowrap;
  }

  &__item {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    height: 24px;
    padding: 0 8px 0 7px;
    font-size: 12px;
    color: var(--admin-text-regular);
    cursor: pointer;
    user-select: none;
    background-color: var(--admin-tag-bg);
    border: 1px solid transparent;
    border-radius: 999px;
    transition:
      background-color var(--admin-transition-fast) ease,
      border-color var(--admin-transition-fast) ease,
      color var(--admin-transition-fast) ease;

    &:hover {
      color: var(--admin-text-primary);
      background-color: var(--admin-tag-bg-hover);
    }

    &--active,
    &--active:hover {
      font-weight: 500;
      color: var(--admin-tag-active-text);
      background-color: var(--admin-tag-active-bg);
      border-color: var(--admin-tag-active-border);
    }
  }

  /* 前置圆点：选中态点亮，未选中占位保持文字对齐一致 */
  &__dot {
    flex: 0 0 5px;
    width: 5px;
    height: 5px;
    background-color: var(--admin-tag-active-text);
    border-radius: 50%;
    opacity: 0;
    transform: scale(0.4);
    transition:
      opacity var(--admin-transition-fast) ease,
      transform var(--admin-transition-fast) ease;
  }

  &__item--active &__dot {
    opacity: 1;
    transform: none;
  }

  /* 圆形关闭按钮：hover 才浮出背景，避免一排"叉"太吵 */
  &__close {
    flex: 0 0 auto;
    padding: 2px;
    color: currentcolor;
    border-radius: 50%;
    transition:
      background-color var(--admin-transition-fast) ease,
      color var(--admin-transition-fast) ease;

    &:hover {
      color: var(--admin-tag-active-text);
      background-color: var(--admin-tag-active-bg);
    }
  }

  &__more {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    padding-left: 0;
    color: var(--admin-text-regular);
    cursor: pointer;
    outline: none;
    border-radius: var(--admin-radius-base);
    transition:
      background-color var(--admin-transition-fast) ease,
      color var(--admin-transition-fast) ease;

    &:hover {
      color: var(--admin-color-primary);
      background-color: var(--admin-menu-hover-bg);
    }
  }
}
</style>
