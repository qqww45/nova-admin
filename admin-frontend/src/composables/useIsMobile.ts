import { onMounted, onUnmounted, ref } from 'vue';

/**
 * 是否窄屏（移动端视图判定）。
 *
 * <p>用 matchMedia 而不是监听 resize：断点变化才触发，不会拖动窗口时连续重渲。
 * 业务页面据此切换"桌面表格 / 移动卡片"两套展示（如 KD 对账单的轻报数视图）。
 *
 * @param breakpoint 断点（px），默认 768
 */
export function useIsMobile(breakpoint = 768) {
  const isMobile = ref(false);
  let media: MediaQueryList | null = null;

  const update = () => {
    isMobile.value = media ? media.matches : typeof window !== 'undefined' && window.innerWidth <= breakpoint;
  };

  onMounted(() => {
    media = window.matchMedia(`(max-width: ${breakpoint}px)`);
    media.addEventListener('change', update);
    update();
  });

  onUnmounted(() => {
    media?.removeEventListener('change', update);
  });

  return { isMobile };
}
