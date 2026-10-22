<script setup lang="ts">
/**
 * 后台主框架布局：侧边栏 + （顶栏 + 标签栏 + 内容区）。
 *
 * <p>布局尺寸全部来自 CSS 变量（--admin-sidebar-width 等），
 * 折叠时只需切换变量值，无需为每个子组件写两套样式。
 *
 * <p>视觉（2026 改版）：顶栏与标签栏合并进同一个 `.admin-layout__top` 区域，
 * 共用一块背景、只保留一条底部分割线——
 * 之前的"两块各带边框的深色横带"在暗色下会形成生硬的三段式条纹。
 */
import { computed } from 'vue';
import { useAppStore } from '@/store/app';
import Sidebar from './components/Sidebar/index.vue';
import HeaderBar from './components/Header.vue';
import TagsView from './components/TagsView.vue';
import MainContent from './components/Main.vue';

const appStore = useAppStore();
const sidebarWidth = computed(() =>
  appStore.sidebarCollapsed ? 'var(--admin-sidebar-collapsed-width)' : 'var(--admin-sidebar-width)',
);
</script>

<template>
  <div class="admin-layout">
    <el-container class="admin-layout__container">
      <el-aside class="admin-layout__aside" :style="{ width: sidebarWidth }">
        <Sidebar />
      </el-aside>
      <!-- direction="vertical" 必须显式声明：
           el-container 靠"直接子组件是否为 el-header/el-footer"推断方向，
           头部被包进 .admin-layout__top 后推断失效，会退化成水平排列（三块布局） -->
      <el-container class="admin-layout__body" direction="vertical">
        <!-- 顶栏 + 标签栏：一体化顶部区域，单条分割线 -->
        <div class="admin-layout__top">
          <el-header class="admin-layout__header" :style="{ height: 'var(--admin-header-height)' }">
            <HeaderBar />
          </el-header>
          <TagsView />
        </div>
        <el-main class="admin-layout__main">
          <MainContent />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  height: 100%;
  background-color: var(--admin-bg-color);

  &__container {
    height: 100%;
  }

  &__aside {
    overflow: hidden;
    background-color: var(--admin-sidebar-bg);
    border-right: 1px solid var(--admin-border-color);
    transition: width var(--admin-transition-duration) ease;
  }

  &__body {
    height: 100%;
    overflow: hidden;
    /* 双保险：确保"顶部区域在上、内容区在下"的纵向堆叠 */
    flex-direction: column;
  }

  /* 顶部区域：顶栏 + 标签栏共用背景，底部一条 hairline 与内容区分界 */
  &__top {
    flex: 0 0 auto;
    background-color: var(--admin-header-bg);
    border-bottom: 1px solid var(--admin-border-color);
  }

  &__header {
    display: flex;
    align-items: center;
    padding: 0;
    background-color: transparent;
  }

  &__main {
    padding: 0;
    /* 内容区纵向滚动：页面超高（如出货页"录入区 + 列表"）时，
       分页等底部内容必须能滚动到，不能被裁掉；
       横向滚动一律禁止，宽表格由自身处理。 */
    overflow-x: hidden;
    overflow-y: auto;
    background-color: var(--admin-bg-color);
  }
}
</style>
