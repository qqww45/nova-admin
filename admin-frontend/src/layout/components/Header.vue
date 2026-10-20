<script setup lang="ts">
/**
 * 顶栏：折叠按钮、面包屑、主题切换、用户下拉菜单。
 *
 * <p>主题切换提供"浅色 / 深色 / 跟随系统"三态，
 * 切换后立即写入 DOM 与 localStorage，并异步保存到后端。
 */
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { useAppStore, type ThemeMode } from '@/store/app';
import { useUserStore } from '@/store/user';

const appStore = useAppStore();
const userStore = useUserStore();
const router = useRouter();

/** 当前主题对应的图标 */
const themeIcon = computed(() => (appStore.isDark ? 'Moon' : 'Sunny'));

/** 切换主题（在浅色 / 深色之间切换，auto 由下拉菜单显式选择） */
function toggleTheme(): void {
  appStore.setTheme(appStore.isDark ? 'light' : 'dark');
}

/** 下拉选择主题模式 */
function selectTheme(mode: ThemeMode): void {
  appStore.setTheme(mode);
}

/** 退出登录 */
async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  await userStore.logout();
  void router.replace('/login');
}

/** 跳转个人中心 */
function goProfile(): void {
  void router.push('/profile');
}
</script>

<template>
  <div class="header">
    <div class="header__left">
      <el-tooltip content="折叠 / 展开菜单" placement="bottom">
        <el-icon class="header__icon" :size="18" @click="appStore.toggleSidebar()">
          <component :is="appStore.sidebarCollapsed ? 'Expand' : 'Fold'" />
        </el-icon>
      </el-tooltip>
      <el-breadcrumb separator="/" class="header__breadcrumb">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="header__right">
      <el-tooltip content="切换主题" placement="bottom">
        <el-icon class="header__icon" :size="18" @click="toggleTheme">
          <component :is="themeIcon" />
        </el-icon>
      </el-tooltip>

      <el-dropdown trigger="click" @command="selectTheme">
        <el-icon class="header__icon" :size="18"><Setting /></el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="light">浅色模式</el-dropdown-item>
            <el-dropdown-item command="dark">深色模式</el-dropdown-item>
            <el-dropdown-item command="auto">跟随系统</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-dropdown trigger="click">
        <div class="header__user">
          <el-avatar :size="26" :src="userStore.userInfo?.avatar || ''">
            {{ userStore.userInfo?.nickname?.charAt(0) ?? 'U' }}
          </el-avatar>
          <span class="header__username">{{ userStore.userInfo?.nickname ?? '未登录' }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="goProfile">个人中心</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped lang="scss">
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 100%;
  padding: 0 12px;

  &__left,
  &__right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* 图标统一做成 30x30 的按钮形态：
     有可 hover 的圆形热区，而不是"孤零零的图标"，
     与面包屑、头像在同一视觉轴线上。 */
  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    color: var(--admin-text-regular);
    cursor: pointer;
    border-radius: var(--admin-radius-base);
    transition:
      background-color var(--admin-transition-fast) ease,
      color var(--admin-transition-fast) ease;

    &:hover {
      color: var(--admin-color-primary);
      background-color: var(--admin-menu-hover-bg);
    }
  }

  &__breadcrumb {
    margin-left: 4px;
  }

  &__user {
    display: flex;
    gap: 6px;
    align-items: center;
    height: 30px;
    padding: 0 8px 0 4px;
    cursor: pointer;
    outline: none;
    border-radius: 999px;
    transition: background-color var(--admin-transition-fast) ease;

    &:hover {
      background-color: var(--admin-menu-hover-bg);
    }
  }

  &__username {
    font-size: 13px;
    color: var(--admin-text-regular);
  }
}
</style>
