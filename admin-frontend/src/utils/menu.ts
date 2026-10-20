/**
 * 菜单类型常量与工具。
 *
 * <p>后端用整数编码（1 目录 / 2 菜单 / 3 按钮），前端统一从这里取，
 * 避免各处散落魔法数字。
 */
export const MENU_TYPE = {
  /** 目录 */
  DIR: 1,
  /** 菜单 */
  MENU: 2,
  /** 按钮 */
  BUTTON: 3,
} as const;

/** 类型 → 中文名 */
export const MENU_TYPE_LABEL: Record<number, string> = {
  [MENU_TYPE.DIR]: '目录',
  [MENU_TYPE.MENU]: '菜单',
  [MENU_TYPE.BUTTON]: '按钮',
};

/**
 * 是否为目录。
 *
 * @param type 菜单类型
 */
export function isDir(type?: number): boolean {
  return type === MENU_TYPE.DIR;
}

/**
 * 是否为按钮（按钮不生成路由，只作为权限标识）。
 *
 * @param type 菜单类型
 */
export function isButton(type?: number): boolean {
  return type === MENU_TYPE.BUTTON;
}
