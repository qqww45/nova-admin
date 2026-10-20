/**
 * 菜单图标解析。
 *
 * <p>库里的 icon 存的是图标名（VARCHAR），渲染处用 <component :is="..." /> 按名解析。
 * 历史数据混着三种写法，直接拿去解析会命中不到组件、渲染成空白：
 * <ul>
 *     <li>PascalCase：Odometer（现行规范，与图标组件名一致）；</li>
 *     <li>短横线：odometer / office-building（早期按 CSS 类名写）；</li>
 *     <li>带前缀：ep:odometer / element-plus:odometer（早期为区分图标库加的）。</li>
 * </ul>
 * 这里统一收敛成组件对象；匹配不到时返回 undefined，
 * 由调用方降级为占位，而不是渲染一个浏览器不认识的自定义元素。
 */
import type { Component } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';

/** 全量图标：组件名 → 组件（与 plugins/icons.ts 的全局注册同源） */
export const ICON_MAP = ElementPlusIconsVue as unknown as Record<string, Component>;

/** 组件名列表：图标选择器的数据源 */
export const ICON_NAMES = Object.keys(ICON_MAP);

/**
 * 归一化图标名：去前缀、短横线转 PascalCase。
 *
 * <p>只做字符串变换，不校验是否存在（校验交给 resolveIcon）。
 *
 * @param raw 原始图标值
 * @returns PascalCase 图标名；空值返回空串
 */
export function normalizeIconName(raw?: string): string {
  const value = (raw ?? '').trim();
  if (!value) {
    return '';
  }
  // 去掉形如 "ep:" / "element-plus:" 的图标库前缀
  const name = value.includes(':') ? value.slice(value.lastIndexOf(':') + 1) : value;
  return name
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('');
}

/**
 * 原始图标值 → 可渲染组件。
 *
 * <p>先按原值精确匹配（PascalCase 场景零成本），
 * 再退一步按归一化后的名字匹配（兼容 ep: / 短横线写法）。
 *
 * @param raw 原始图标值
 * @returns 图标组件；无对应图标返回 undefined
 */
export function resolveIcon(raw?: string): Component | undefined {
  const value = (raw ?? '').trim();
  if (!value) {
    return undefined;
  }
  return ICON_MAP[value] ?? ICON_MAP[normalizeIconName(value)];
}
