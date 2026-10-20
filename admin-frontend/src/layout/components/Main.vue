<script setup lang="ts">
/**
 * 内容区：路由出口 + 过渡动画。
 *
 * <p>过渡样式统一放在 styles/index.scss（.fade-slide-*），
 * 这里只负责挂载 transition，避免同一动画存在两份定义。
 *
 * <p>未启用 keep-alive：后台系统以列表页为主，缓存过多实例反而增加内存且易产生脏数据，
 * 需要缓存的页面可在自身内部自行处理。
 */
</script>

<template>
  <router-view v-slot="{ Component }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>
