<script setup lang="ts">
/**
 * 侧边栏菜单项（递归组件）。
 *
 * @prop menu 菜单节点（含 children）
 */
import { computed } from 'vue';
import type { MenuVo } from '@/types/api';
import { resolveIcon } from '@/utils/icon';

defineOptions({ name: 'SidebarItem' });

const props = defineProps<{ menu: MenuVo; basePath?: string }>();

/**
 * 图标组件：库里的 icon 可能带 ep: 前缀或短横线，统一交给 resolveIcon 收敛。
 * 解析不到时降级为占位，避免渲染出浏览器不认识的自定义元素（表现为一片空白）。
 */
const icon = computed(() => resolveIcon(props.menu.icon));

/** 拼接完整路径：子菜单 path 可能是不带斜杠的相对路径 */
function resolvePath(path?: string): string {
  if (!path) {
    return props.basePath ?? '';
  }
  if (path.startsWith('/')) {
    return path;
  }
  return `${props.basePath ?? ''}/${path}`.replace(/\/+/g, '/');
}
</script>

<template>
  <!-- 有子节点：渲染为可展开的子菜单 -->
  <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="resolvePath(menu.path)">
    <template #title>
      <el-icon v-if="icon"><component :is="icon" /></el-icon>
      <i v-else class="sidebar-item__icon-slot" aria-hidden="true" />
      <span>{{ menu.menuName }}</span>
    </template>
    <SidebarItem
      v-for="child in menu.children"
      :key="child.id"
      :menu="child"
      :base-path="resolvePath(menu.path)"
    />
  </el-sub-menu>

  <!-- 叶子节点：渲染为可跳转的菜单项 -->
  <el-menu-item v-else :index="resolvePath(menu.path)">
    <el-icon v-if="icon"><component :is="icon" /></el-icon>
    <i v-else class="sidebar-item__icon-slot" aria-hidden="true" />
    <template #title>{{ menu.menuName }}</template>
  </el-menu-item>
</template>

<style scoped lang="scss">
/*
 * 无图标时的占位：与 el-icon 同宽同距，保证"配了图标"和"没配图标"的
 * 菜单项文字左对齐——否则侧边栏会出现一列参差不齐的缩进。
 * 用 <i> 而非 <span>：折叠态 Element Plus 会隐藏菜单项内的 span，占位会被一并藏掉。
 */
.sidebar-item__icon-slot {
  display: inline-block;
  flex-shrink: 0;
  width: 24px;
  height: 18px;
  margin-right: 5px;
}
</style>
