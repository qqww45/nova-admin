import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue';
import Sortable from 'sortablejs';

/**
 * 表格列拖拽排序通用 Hook（基于 SortableJS）。
 *
 * <p>解决的痛点：业务表格常常需要"把常用列拖到前面"，
 * 如果每个页面各写一遍 Sortable 初始化 + 顺序持久化，维护成本极高。
 * 这里把三件事收敛到一处：
 * <ol>
 *     <li>在表头行上挂载 Sortable，拖动即换列；</li>
 *     <li>顺序提升为响应式数据，列的渲染顺序始终由 order 决定；</li>
 *     <li>顺序写入 localStorage，刷新后保持，且能容错"后端新增/删除了列"。</li>
 * </ol>
 *
 * <p>使用方式：
 * <pre>{@code
 * const { tableRef, orderedColumns, resetOrder } = useColumnDrag(columns, { storageKey: 'system:user' });
 * }</pre>
 *
 * @param columns 原始列定义（响应式）。每项需具备唯一 prop
 * @param options 配置项：storageKey 开启本地持久化，disabled 关闭拖拽
 */
export interface ColumnLike {
  /** 列唯一标识，也是持久化顺序的键 */
  prop: string;
  /** 固定列（左右固定）默认不参与拖拽排序 */
  fixed?: 'left' | 'right' | boolean;
}

/** 拖拽配置项 */
export interface UseColumnDragOptions {
  /** 持久化键（建议用"模块:页面"，如 system:user）；不传则每次刷新恢复默认顺序 */
  storageKey?: string;
  /** 是否禁用拖拽（例如移动端） */
  disabled?: boolean;
  /** 拖拽手柄选择器，默认整个表头单元格可拖 */
  handle?: string;
}

const STORAGE_PREFIX = 'admin-table-columns:';

/**
 * 读取已保存的列顺序。
 *
 * @param key 持久化键
 */
function readOrder(key: string): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + key);
    const parsed: unknown = raw ? JSON.parse(raw) : null;
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    // 存储被禁用或数据损坏时退回默认顺序，不影响页面可用性
    return [];
  }
}

/**
 * 写入列顺序。
 *
 * @param key 持久化键
 * @param order 列 prop 顺序
 */
function writeOrder(key: string, order: string[]): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(order));
  } catch {
    // 隐私模式下 localStorage 可能不可写，静默降级为"本次会话有效"
  }
}

/**
 * 表格列拖拽排序。
 *
 * @param columns 原始列定义
 * @param options 配置项
 */
export function useColumnDrag(
  columns: Ref<ColumnLike[]> | (() => ColumnLike[]),
  options: UseColumnDragOptions = {},
) {
  /** 绑定到 el-table 组件实例，用于定位表头行 DOM */
  const tableRef = ref<{ $el: HTMLElement }>();

  /** 统一成 computed，兼容 ref 与 getter 两种入参 */
  const source = computed(() => (typeof columns === 'function' ? columns() : columns.value));

  /** 当前列顺序（prop 数组） */
  const order = ref<string[]>([]);

  /** Sortable 实例 */
  let sortable: Sortable | null = null;

  /**
   * 校正顺序：已保存的 ∩ 当前列，再补上新增列。
   * 这样"后端加了一列 / 删了一列"都不会导致列丢失或顺序错乱。
   */
  function syncOrder(): void {
    const props = source.value.map((column) => column.prop);
    // 固定列不参与排序，始终保持在原位置
    const movable = source.value.filter((column) => !column.fixed).map((column) => column.prop);
    const saved = (options.storageKey ? readOrder(options.storageKey) : []).filter((prop) =>
      movable.includes(prop),
    );
    const merged = [...saved, ...movable.filter((prop) => !saved.includes(prop))];

    // 按原始定义顺序铺开，固定列插回原位
    let cursor = 0;
    order.value = props.map((prop) => {
      const column = source.value.find((item) => item.prop === prop);
      if (column?.fixed) {
        return prop;
      }
      return merged[cursor++] ?? prop;
    });
  }

  watch(source, syncOrder, { immediate: true });

  /** 按当前顺序排序后的列定义，交给模板 v-for 渲染 */
  const orderedColumns = computed<ColumnLike[]>(() => {
    const map = new Map(source.value.map((column) => [column.prop, column]));
    const result: ColumnLike[] = [];
    for (const prop of order.value) {
      const column = map.get(prop);
      if (column) {
        result.push(column);
      }
    }
    // 兜底：order 尚未初始化或列动态新增时，保证一列都不丢
    for (const column of source.value) {
      if (!result.includes(column)) {
        result.push(column);
      }
    }
    return result;
  });

  /** 销毁 Sortable 实例（表头 DOM 会被 el-table 重建，切换页面前必须清理） */
  function destroySortable(): void {
    sortable?.destroy();
    sortable = null;
  }

  /**
   * 判断某列是否为固定列。
   *
   * @param prop 列标识
   */
  function isFixed(prop: string): boolean {
    return Boolean(source.value.find((column) => column.prop === prop)?.fixed);
  }

  /** 在表头行上挂载拖拽 */
  function initSortable(): void {
    destroySortable();
    if (options.disabled) {
      return;
    }
    const root = tableRef.value?.$el as HTMLElement | undefined;
    const header = root?.querySelector('.el-table__header-wrapper thead tr') as HTMLElement | null;
    if (!header) {
      return;
    }

    sortable = Sortable.create(header, {
      animation: 180,
      easing: 'cubic-bezier(0.22, 0.61, 0.36, 1)',
      draggable: 'th.el-table__cell',
      handle: options.handle ?? 'th.el-table__cell',
      ghostClass: 'is-column-drag-ghost',
      chosenClass: 'is-column-drag-chosen',
      filter: '.el-table-column--fixed',
      onEnd(event: { oldIndex?: number; newIndex?: number }): void {
        const { oldIndex, newIndex } = event;
        if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) {
          return;
        }
        const next = [...order.value];
        const [moved] = next.splice(oldIndex, 1);
        next.splice(newIndex, 0, moved);
        order.value = next;
        if (options.storageKey) {
          writeOrder(options.storageKey, next.filter((prop) => !isFixed(prop)));
        }
        // 让 Vue 按新顺序重排真实 DOM，避免与 Sortable 的临时移动结果不一致
        void nextTick();
      },
    });
  }

  /** 恢复默认列顺序（供"重置列顺序"按钮使用） */
  function resetOrder(): void {
    order.value = source.value.map((column) => column.prop);
    if (options.storageKey) {
      try {
        localStorage.removeItem(STORAGE_PREFIX + options.storageKey);
      } catch {
        // 忽略存储异常
      }
    }
    void nextTick(initSortable);
  }

  onMounted(() => {
    void nextTick(initSortable);
  });

  // 列数量变化说明 el-table 重建了表头 DOM，需要重新挂载
  watch(
    () => source.value.length,
    () => {
      void nextTick(initSortable);
    },
  );

  onBeforeUnmount(destroySortable);

  return { tableRef, orderedColumns, order, resetOrder, initSortable };
}
