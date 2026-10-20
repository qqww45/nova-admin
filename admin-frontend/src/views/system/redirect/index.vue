<script setup lang="ts">
/**
 * 重定向容器（支撑"刷新当前页"）。
 *
 * <p>原理：路由 <code>/redirect/:path(.*)</code> 命中本组件后，
 * 立刻 <code>replace</code> 回 <code>:path</code> 指向的地址。
 * 中间经过一次"卸载 → 重新挂载"，目标页面组件被销毁重建，
 * 于是数据重新拉取、表单状态复位 —— 这才是"刷新当前页"要的效果。
 *
 * <p>为什么不能直接 <code>replace</code> 到当前地址：
 * 目标路由与当前路由相同时，Vue Router 只更新地址栏而不重建组件，
 * 页面状态不会有任何变化。
 */
import { onBeforeMount } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

/**
 * 把 params.path（可能是字符串或字符串数组）还原成绝对路径。
 */
function resolveTarget(): string {
  const raw = route.params.path;
  const segment = Array.isArray(raw) ? raw.join('/') : raw ?? '';
  return `/${String(segment).replace(/^\//, '')}`;
}

// 用 replace 而非 push：刷新不该在历史记录里留下痕迹，
// 否则浏览器"后退"会退到重定向页，再被自动弹回来，体验上是死循环。
onBeforeMount(() => {
  const { t: _t, ...restQuery } = route.query;
  void router.replace({ path: resolveTarget(), query: restQuery });
});
</script>

<template>
  <div />
</template>
