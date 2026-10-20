<script setup lang="ts">
/**
 * ProTable：列表页通用表格组件。
 *
 * <p>解决的痛点：每个列表页都要重复写"搜索区 + 工具栏 + 表格 + 分页"，
 * 这里把结构固定下来，页面只关心三件事：查询条件（#search）、操作按钮（#toolbar）、列渲染（#col-xxx）。
 *
 * <p>体验规范（与 styles/element 保持一致）：
 * <ul>
 *     <li>搜索区：项间距 16px、标签间距 8px，超过 searchMaxVisible 个条件自动收起，
 *         可一键展开，已输入的查询值不丢失；</li>
 *     <li>表格：卡片式（无外框/竖线、斑马纹），行高与表头样式全站统一；</li>
 *     <li>列拖拽：column-drag 开启后表头可拖拽换列，顺序按 column-drag-key 本地持久化；</li>
 *     <li>分页：与表格底部 16px 衔接，配色统一。</li>
 * </ul>
 *
 * <p>设计取舍：分页与数据请求交由页面通过 useTable 管理（而非组件内部发起请求），
 * 这样组件保持"哑组件"，既能接 REST 分页，也能接本地数据。
 */
import { computed, onMounted, onUpdated, ref } from 'vue';
import type { TableColumnCtx } from 'element-plus';
import { ArrowDown } from '@element-plus/icons-vue';
import { useColumnDrag, type ColumnLike } from '@/composables/useColumnDrag';

/** 列定义：对齐 el-table-column 常用属性，额外支持 slot 名 */
export interface ProTableColumn {
  prop: string;
  label: string;
  width?: number | string;
  minWidth?: number | string;
  fixed?: 'left' | 'right' | boolean;
  align?: 'left' | 'center' | 'right';
  /** 是否使用 #col-[prop] 插槽渲染 */
  slot?: boolean;
  /** 简单格式化函数（与 slot 二选一） */
  formatter?: (
    row: Record<string, unknown>,
    column: TableColumnCtx<Record<string, unknown>>,
    value: unknown,
  ) => string;
  /**
   * 单元格内容超出列宽时是否显示 tooltip（透传 el-table-column 的同名 prop）。
   *
   * <p>放在 ProTableColumn 里：配置里写"这一列要 tooltip"比在模板里
   * 每个 el-table-column 重复 `:show-overflow-tooltip="true"` 更紧凑。
   */
  showOverflowTooltip?: boolean;
}

const props = withDefaults(
  defineProps<{
    columns: ProTableColumn[];
    data: unknown[];
    total?: number;
    loading?: boolean;
    pageNum?: number;
    pageSize?: number;
    selectable?: boolean;
    /** 行数据的唯一键，树形表格必填 */
    rowKey?: string;
    /** 是否为树形表格（菜单、部门用） */
    tree?: boolean;
    /** 是否开启表头列拖拽排序 */
    columnDrag?: boolean;
    /** 列顺序持久化键（建议"模块:页面"，如 system:user） */
    columnDragKey?: string;
    /** 搜索区是否支持收起/展开 */
    searchCollapsible?: boolean;
    /** 搜索区收起时最多展示的条件数 */
    searchMaxVisible?: number;
  }>(),
  {
    total: 0,
    loading: false,
    pageNum: 1,
    pageSize: 10,
    selectable: false,
    rowKey: 'id',
    tree: false,
    columnDrag: false,
    columnDragKey: undefined,
    searchCollapsible: true,
    searchMaxVisible: 3,
  },
);

const emit = defineEmits<{
  (e: 'page-change', page: number): void;
  (e: 'size-change', size: number): void;
  (e: 'selection-change', rows: unknown[]): void;
}>();

/**
 * 显式声明插槽类型：列插槽 #col-[prop] 的 row 就是行数据类型，
 * 页面里可直接 row.xxx，避免到处写断言。
 */
defineSlots<{
  search?: () => unknown;
  'search-buttons'?: () => unknown;
  toolbar?: () => unknown;
  /** 工具栏右侧元信息（如"共 N 条"）；与「恢复列序」同区，自动避让 */
  'toolbar-meta'?: () => unknown;
  columns?: () => unknown;
  [key: `col-${string}`]: (props: { row: unknown }) => unknown;
}>();

/* ---------------- 列拖拽排序 ---------------- */

/** 是否启用拖拽（树形表格列固定语义强，不参与拖拽） */
const dragEnabled = computed(() => props.columnDrag && !props.tree);

const { tableRef, orderedColumns, resetOrder } = useColumnDrag(
  () => props.columns as ColumnLike[],
  {
    storageKey: props.columnDragKey,
    disabled: !dragEnabled.value,
  },
);

/** 实际渲染的列：开启拖拽时按用户自定义顺序，否则按定义顺序 */
const renderColumns = computed(() =>
  dragEnabled.value ? (orderedColumns.value as ProTableColumn[]) : props.columns,
);

/* ---------------- 搜索区收起 / 展开 ---------------- */

/** 搜索字段容器，用于统计条件数量 */
const searchFieldsRef = ref<HTMLElement>();

/** 当前是否收起（默认收起，避免页面顶部留白过多） */
const searchCollapsed = ref(true);

/** 搜索条件数量（DOM 统计，兼容页面在插槽里写 v-if 的动态条件） */
const fieldCount = ref(0);

/** 是否展示"展开/收起"开关：条件数超过阈值才出现 */
const showCollapseToggle = computed(
  () => props.searchCollapsible && fieldCount.value > props.searchMaxVisible,
);

/**
 * 统计条件数量并应用收起状态。
 *
 * <p>用 display:none 而不是 v-if 隐藏超量条件：
 * 输入框依然挂在 DOM 上，v-model 的值不会丢失，展开后立即恢复原状。
 */
function applyCollapse(): void {
  const items = searchFieldsRef.value?.querySelectorAll<HTMLElement>('.el-form-item');
  fieldCount.value = items?.length ?? 0;
  items?.forEach((item, index) => {
    item.style.display =
      searchCollapsed.value && showCollapseToggle.value && index >= props.searchMaxVisible ? 'none' : '';
  });
}

onMounted(applyCollapse);
onUpdated(applyCollapse);

/** 表格数据传给 el-table（框架内部行类型为 DefaultRow，这里做一次收口） */
function toRows(rows: unknown[]): never {
  return rows as never;
}

/** 分页变化 */
function onCurrentChange(page: number): void {
  emit('page-change', page);
}

/** 每页条数变化 */
function onSizeChange(size: number): void {
  emit('size-change', size);
}
</script>

<template>
  <div class="pro-table">
    <!-- 搜索区：紧凑间距 + 超量自动收起 -->
    <el-card v-if="$slots.search" shadow="never" class="pro-table__search">
      <el-form :inline="true" class="pro-search" @submit.prevent>
        <div ref="searchFieldsRef" class="pro-search__fields">
          <slot name="search" />
        </div>
        <div class="pro-search__actions">
          <slot name="search-buttons" />
          <el-button
            v-if="showCollapseToggle"
            link
            type="primary"
            @click="searchCollapsed = !searchCollapsed"
          >
            {{ searchCollapsed ? '展开' : '收起' }}
            <el-icon class="pro-search__arrow" :class="{ 'is-expanded': !searchCollapsed }">
              <ArrowDown />
            </el-icon>
          </el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="pro-table__body">
      <!-- 工具栏：左侧为主操作（新增 / 删除 / 导出），右侧为元信息与"恢复列序"。
           左右分区让"面向数据的动作"与"面向视图的动作"物理隔离，避免误点。 -->
      <div v-if="$slots.toolbar || dragEnabled || $slots['toolbar-meta']" class="pro-table__toolbar">
        <div class="pro-table__toolbar-actions">
          <slot name="toolbar" />
        </div>
        <div class="pro-table__toolbar-meta">
          <slot name="toolbar-meta" />
          <el-button v-if="dragEnabled" link size="small" @click="resetOrder">恢复列序</el-button>
        </div>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        class="pro-table__table"
        :class="{ 'pro-table__table--draggable': dragEnabled }"
        :data="toRows(data)"
        :row-key="rowKey"
        :tree-props="tree ? { children: 'children' } : undefined"
        size="default"
        @selection-change="(rows: unknown[]) => emit('selection-change', rows)"
      >
        <el-table-column v-if="selectable" type="selection" width="46" align="center" />
        <el-table-column
          v-for="column in renderColumns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :fixed="column.fixed"
          :align="column.align ?? 'center'"
          :formatter="column.formatter"
          :show-overflow-tooltip="column.showOverflowTooltip"
        >
          <!-- 自定义列：插槽名 col-[prop] -->
          <template v-if="column.slot" #default="{ row }">
            <slot :name="`col-${column.prop}`" :row="row" />
          </template>
        </el-table-column>
        <!-- 操作列固定由页面提供 -->
        <slot name="columns" />
      </el-table>

      <!-- 分页：树形表格不需要分页 -->
      <div v-if="!tree && total > 0" class="pro-table__pagination">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="onCurrentChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.pro-table {
  /* 搜索卡片与表格卡片之间保留 16px，形成清晰的区域分隔 */
  &__search {
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding-bottom: 0;
    }
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  &__toolbar-actions {
    display: flex;
    gap: 8px;
  }

  /* 右侧元信息区：统计文案 + 恢复列序，用 gap 而不是 margin 拉开间距 */
  &__toolbar-meta {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-left: auto;
    font-size: 13px;
    color: var(--admin-text-regular);
  }

  /* 可拖拽换列时，表头给出 move 光标提示 */
  &__table--draggable :deep(thead th.el-table__cell) {
    cursor: move;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}

/* ---------------- 搜索区：紧凑 + 收起 ---------------- */
.pro-search {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;

  &__fields {
    display: flex;
    flex-wrap: wrap;
  }

  /* 按钮组右对齐，与最后一个搜索条件自然成行 */
  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
    margin-bottom: var(--admin-form-item-gap);
  }

  &__arrow {
    margin-left: 4px;
    transition: transform var(--admin-transition-fast) ease;

    &.is-expanded {
      transform: rotate(180deg);
    }
  }
}
</style>
