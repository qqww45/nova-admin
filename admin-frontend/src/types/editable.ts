/**
 * 可编辑表格的列定义（单独成文件的原因见 EditableTable 组件注释）。
 *
 * <p>放在 types 下而不是组件内导出：组件的 script setup 一旦带 export，
 * 该声明会被提升到泛型作用域之外，泛型参数 T 就失效了。
 */
export interface EditableColumn {
  /** 字段名 */
  prop: string;
  /** 列头 */
  label: string;
  width?: number | string;
  minWidth?: number | string;
  align?: 'left' | 'center' | 'right';
  /** 编辑控件类型 */
  type?: 'text' | 'number' | 'select' | 'date';
  /** select 的选项 */
  options?: { label: string; value: string | number }[];
  /** 是否服务端计算（只读，灰色底） */
  computed?: boolean;
  /** 必填（为空时该行标红） */
  required?: boolean;
  /** 千分位/小数位展示（number 列的只读态） */
  precision?: number;
  /** 占位提示 */
  placeholder?: string;
  /**
   * 自定义校验：返回错误文案，null 表示通过。
   *
   * @param row 行数据
   */
  validate?: (row: Record<string, unknown>) => string | null;
}
