import type { App } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';

/**
 * 全局注册 Element Plus 图标。
 *
 * <p>说明：图标总量约 300 个，全部注册会增加约 30KB（gzip 后很小），
 * 换来的是"任何地方直接用 <el-icon><Search /></el-icon>"的便利性，
 * 对于后台管理系统而言是划算的取舍。
 *
 * @param app Vue 应用实例
 */
export function setupIcons(app: App): void {
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component);
  }
}
