<script setup lang="ts">
/**
 * 侧边栏：数据源为后端下发的菜单树（store/permission）。
 *
 * <p>只渲染 menuType != 'F' 且 visible=0 的节点；
 * 按钮级权限不在此展示，由页面内 v-permission 指令控制。
 */
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useAppStore } from '@/store/app';
import { usePermissionStore } from '@/store/permission';
import type { MenuVo } from '@/types/api';
import { MENU_TYPE } from '@/utils/menu';
import SidebarItem from './SidebarItem.vue';
import BrandMark from '@/components/BrandMark.vue';
import { BRAND_NAME } from '@/config/brand';

const appStore = useAppStore();
const permissionStore = usePermissionStore();
const route = useRoute();

/**
 * 过滤出可见菜单（按钮与隐藏节点不展示）。
 *
 * <p>后端语义：visible 1=显示 0=隐藏，status 0=正常 1=停用（与领域模型 Menu 一致）。
 *
 * @param list 菜单节点集合
 */
function filterVisible(list: MenuVo[]): MenuVo[] {
  return list
    .filter((item) => item.menuType !== MENU_TYPE.BUTTON && item.visible === 1 && item.status === 0)
    .map((item) => ({ ...item, children: item.children ? filterVisible(item.children) : [] }));
}

/** 可见菜单 */
const menus = computed(() => filterVisible(permissionStore.menus));

const activeMenu = computed(() => route.path);
</script>

<template>
  <div class="sidebar">
    <div class="sidebar__logo">
      <!-- 品牌区：mark + wordmark 成套呈现。
           折叠态只留 mark —— 64px 宽容不下文字，且留白会让 icon 显得更"贵"。 -->
      <div class="sidebar__brand" :title="BRAND_NAME">
        <BrandMark :size="30" />
        <span v-if="!appStore.sidebarCollapsed" class="sidebar__word">
          <span class="sidebar__word-main">NOVA</span>
          <span class="sidebar__word-sub">ADMIN</span>
        </span>
      </div>
    </div>
    <el-scrollbar class="sidebar__scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        unique-opened
        router
        background-color="var(--admin-sidebar-bg)"
        text-color="var(--admin-sidebar-text)"
        active-text-color="var(--admin-color-primary)"
      >
        <SidebarItem v-for="menu in menus" :key="menu.id" :menu="menu" />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<style scoped lang="scss">
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__logo {
    display: flex;
    align-items: center;
    justify-content: center;
    height: var(--admin-header-height);
    color: var(--admin-color-primary);
    border-bottom: 1px solid var(--admin-border-color);
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    white-space: nowrap;
  }

  /* 品牌区：mark 与文字横向排布；折叠态由 v-if 收掉文字，容器自动居中 */
  &__brand {
    display: flex;
    align-items: center;
    gap: 10px;
    /* 左侧 20px 呼吸位：让 logo 不贴着窗口边缘 */
    padding-left: 20px;
    /* 折叠时宽度收窄，保证 mark 落在 64px 栏的正中 */
    transition: padding var(--admin-transition-duration) ease;
  }

  /*
   * 双行 wordmark：NOVA 在上（重），ADMIN 在下（轻）。
   * 两行不等重 + 字距差，比单行 "NOVA ADMIN" 更有 logo 的编排感，
   * 也不会在 220px 侧栏里占太多横向空间。
   */
  &__word {
    display: flex;
    flex-direction: column;
    gap: 1px;
    line-height: 1;
  }

  &__word-main {
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.18em;
    color: var(--admin-text-primary);
  }

  &__word-sub {
    font-size: 9.5px;
    font-weight: 500;
    letter-spacing: 0.3em;
    color: var(--admin-text-regular);
    /* 抵消末尾字距带来的偏移，保证 ADMIN 与 NOVA 视觉左对齐 */
    margin-right: -0.3em;
  }

  &__scroll {
    flex: 1;
    overflow: hidden;
  }
}

/* 菜单项配色、hover 渐变、选中高亮条统一由 styles/element/global.scss 提供，
   这里只声明与侧边栏布局相关的部分，避免样式分散在两处。 */
:deep(.el-menu) {
  border-right: none;
  padding: 4px 0;
}
</style>
