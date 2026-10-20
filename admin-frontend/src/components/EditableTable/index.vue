<script setup lang="ts" generic="T extends object">
/**
 * EditableTable：可直接在单元格里编辑的数据网格（订单明细、出货批量录入通用）。
 *
 * <p>为什么必须自研：这是本系统的核心交互。出货是高频录入，让用户"点新增 → 填弹窗 → 保存 → 再点新增"
 * 是不可接受的；真实的录入节奏是"像 Excel 一样连着敲"。Element Plus 没有可编辑表格组件，
 * 因此按需求 10.6 的"最小扩展"原则自研，但只做通用表格能力，
 * 输入控件一律用 el-input / el-select / el-date-picker 原生组件（不另造控件）。
 *
 * <p>键盘行为（对齐 Excel 习惯）：
 * <ul>
 *     <li>Tab / Shift+Tab：右移 / 左移，到行尾自动换行；</li>
 *     <li>Enter：下移，最后一行时自动追加新行；</li>
 *     <li>↑ / ↓：上移 / 下移；</li>
 *     <li>Ctrl+V：从当前单元格开始按块粘贴（Excel / WPS / 网页表格均可）。</li>
 * </ul>
 *
 * <p>取舍：不做虚拟滚动。单张订单的明细通常几十行，出货一次粘贴也是几十行，
 * 虚拟滚动带来的收益不足以抵消"粘贴定位、行号对齐"的实现复杂度；
 * 若将来出现上千行的场景，再引入 el-table-v2 替换。
 */
import { computed, nextTick, ref } from 'vue';
import { Delete, Plus } from '@element-plus/icons-vue';
import { looksLikeTable, parseClipboardTable } from '@/utils/paste';
import type { EditableColumn } from '@/types/editable';

const props = withDefaults(
  defineProps<{
    columns: EditableColumn[];
    /** 行数据（v-model） */
    modelValue: T[];
    /** 新行工厂：追加行时调用 */
    rowFactory: () => T;
    /** 最少保留的行数（不足时自动补齐空行） */
    minRows?: number;
    /** 是否允许粘贴 */
    paste?: boolean;
    /** 是否显示行号列 */
    showIndex?: boolean;
    /** 是否显示删除按钮列 */
    removable?: boolean;
    /** 空行占位提示 */
    emptyText?: string;
  }>(),
  {
    minRows: 1,
    paste: true,
    showIndex: true,
    removable: true,
    emptyText: '点击单元格开始录入，或直接从 Excel 粘贴',
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', rows: T[]): void;
  (e: 'change'): void;
}>();

/** 当前编辑中的单元格 */
const editing = ref<{ row: number; col: string } | null>(null);

/** 单元格输入控件引用表：key = "行号:列名" */
const cellRefs = new Map<string, { focus: () => void } | null>();

/** 可编辑列（服务端计算列不参与编辑与 Tab 循环） */
const editableColumns = computed(() => props.columns.filter((column) => !column.computed));

/** 当前行集合（始终返回副本，避免直接改 props） */
const rows = computed({
  get: () => props.modelValue,
  set: (value: T[]) => emit('update:modelValue', value),
});

/**
 * 行级错误：返回「行号 → 第一个错误文案」。
 * 用于把有问题的行整行标红，用户一眼能定位。
 */
const rowErrors = computed<Record<number, string>>(() => {
  const errors: Record<number, string> = {};
  rows.value.forEach((row, index) => {
    for (const column of props.columns) {
      if (column.required && isBlank((row as Record<string, unknown>)[column.prop])) {
        errors[index] = `${column.label}不能为空`;
        return;
      }
      const message = column.validate?.(row as unknown as Record<string, unknown>);
      if (message) {
        errors[index] = message;
        return;
      }
    }
  });
  return errors;
});

/** 是否有任一行存在错误（提交前前端先拦一道，减少一次往返） */
const hasError = computed(() => Object.keys(rowErrors.value).length > 0);

function isBlank(value: unknown): boolean {
  return value === undefined || value === null || String(value).trim() === '';
}

/* ---------------- 行操作 ---------------- */

/** 追加一行并聚焦到该行第一个可编辑单元格 */
function addRow(focus = true): void {
  const next = [...rows.value, props.rowFactory()];
  emit('update:modelValue', next);
  emit('change');
  if (focus) {
    void nextTick(() => focusCell(next.length - 1, editableColumns.value[0]?.prop ?? ''));
  }
}

/** 删除一行 */
function removeRow(index: number): void {
  const next = rows.value.filter((_, i) => i !== index);
  // 保持最少行数，删完自动补空行，避免表格"塌缩"成 0 行后无法录入
  while (next.length < props.minRows) {
    next.push(props.rowFactory());
  }
  emit('update:modelValue', next);
  emit('change');
  if (editing.value?.row === index) {
    editing.value = null;
  }
}

/* ---------------- 单元格导航 ---------------- */

/** 记录单元格控件引用 */
function setCellRef(row: number, col: string, el: unknown): void {
  if (el) {
    cellRefs.set(`${row}:${col}`, el as { focus: () => void });
  } else {
    cellRefs.delete(`${row}:${col}`);
  }
}

/** 聚焦指定单元格（自动补齐行数） */
async function focusCell(row: number, col: string): Promise<void> {
  const column = editableColumns.value.find((item) => item.prop === col);
  if (!column) {
    return;
  }
  if (row >= rows.value.length) {
    const next = [...rows.value];
    while (next.length <= row) {
      next.push(props.rowFactory());
    }
    emit('update:modelValue', next);
  }
  editing.value = { row, col };
  await nextTick();
  cellRefs.get(`${row}:${col}`)?.focus?.();
}

/** 进入编辑态（单元格点击） */
function enterCell(row: number, col: string): void {
  void focusCell(row, col);
}

/**
 * el-table 单元格点击回调。
 *
 * <p>类型注解不能写在模板表达式里（Vue 模板编译器不支持），故收口成具名函数。
 *
 * @param row    行数据
 * @param column 列信息
 */
function onCellClick(row: T, column: { property: string }): void {
  void enterCell(rows.value.indexOf(row), column.property);
}

/** 退出编辑态 */
function leaveCell(): void {
  editing.value = null;
}

/** 列索引 */
function columnIndex(col: string): number {
  return editableColumns.value.findIndex((item) => item.prop === col);
}

/**
 * 键盘导航：Tab / Enter / 方向键。
 *
 * @param event 键盘事件
 * @param row   当前行号
 * @param col   当前列名
 */
function onKeydown(event: Event, row: number, col: string): void {
  // 模板里 $event 的静态类型是 Event，这里收窄一次（监听器只挂在输入控件上，必然是键盘事件）
  const keyboard = event as KeyboardEvent;
  const index = columnIndex(col);
  const total = editableColumns.value.length;

  if (keyboard.key === 'Tab') {
    event.preventDefault();
    const delta = keyboard.shiftKey ? -1 : 1;
    let nextIndex = index + delta;
    let nextRow = row;
    if (nextIndex < 0) {
      nextRow = row - 1;
      nextIndex = total - 1;
    } else if (nextIndex >= total) {
      nextRow = row + 1;
      nextIndex = 0;
    }
    if (nextRow < 0) {
      return;
    }
    void focusCell(nextRow, editableColumns.value[nextIndex].prop);
    return;
  }

  if (keyboard.key === 'Enter') {
    event.preventDefault();
    // 最后一行回车 → 追加新行，符合"连着敲"的录入节奏
    if (row === rows.value.length - 1) {
      addRow(true);
      return;
    }
    void focusCell(row + 1, col);
    return;
  }

  if (keyboard.key === 'ArrowUp' || keyboard.key === 'ArrowDown') {
    const nextRow = row + (keyboard.key === 'ArrowUp' ? -1 : 1);
    if (nextRow < 0 || nextRow >= rows.value.length) {
      return;
    }
    event.preventDefault();
    void focusCell(nextRow, col);
  }
}

/* ---------------- 粘贴 ---------------- */

/**
 * 处理 Ctrl+V：从当前单元格（或首行首列）开始按块填充。
 *
 * <p>粘贴超出表格范围时自动补行，不会静默丢弃用户复制的数据。
 *
 * @param event 粘贴事件
 */
function onPaste(event: ClipboardEvent): void {
  if (!props.paste) {
    return;
  }
  const text = event.clipboardData?.getData('text/plain') ?? '';
  if (!looksLikeTable(text)) {
    // 单值粘贴：交给输入框默认行为
    return;
  }
  event.preventDefault();
  const table = parseClipboardTable(text);
  if (table.length === 0) {
    return;
  }

  const startRow = editing.value?.row ?? 0;
  const startColIndex = editing.value ? columnIndex(editing.value.col) : 0;
  const next = [...rows.value];
  // 行数不足时补齐
  while (next.length < startRow + table.length) {
    next.push(props.rowFactory());
  }
  table.forEach((sourceRow, r) => {
    const target = { ...next[startRow + r] };
    sourceRow.forEach((cell, c) => {
      const column = editableColumns.value[startColIndex + c];
      if (!column) {
        return;
      }
      (target as Record<string, unknown>)[column.prop] = cell.trim();
    });
    next[startRow + r] = target;
  });
  emit('update:modelValue', next);
  emit('change');
}

/** 单元格展示文本（只读态） */
function display(row: T, column: EditableColumn): string {
  const value = (row as Record<string, unknown>)[column.prop];
  if (isBlank(value)) {
    return '';
  }
  if (column.type === 'number' && column.precision !== undefined) {
    const num = Number(value);
    return Number.isNaN(num) ? String(value) : num.toFixed(column.precision);
  }
  if (column.type === 'select') {
    return column.options?.find((option) => String(option.value) === String(value))?.label ?? String(value);
  }
  return String(value);
}

/** 行类名：有错的行标红 */
function rowClassName({ rowIndex }: { rowIndex: number }): string {
  return rowErrors.value[rowIndex] ? 'is-error-row' : '';
}

defineExpose({ addRow, hasError, rowErrors });
</script>

<template>
  <div class="editable-table" @paste="onPaste">
    <el-table
      :data="rows"
      border
      size="small"
      class="editable-table__grid"
      :row-class-name="rowClassName"
      empty-text="无数据"
      @cell-click="onCellClick"
    >
      <el-table-column v-if="showIndex" type="index" label="#" width="50" align="center" />

      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :width="column.width"
        :min-width="column.minWidth ?? 120"
        :align="column.align ?? 'center'"
        :class-name="column.computed ? 'is-computed-col' : ''"
      >
        <template #default="{ row, $index }">
          <!-- 只读（服务端计算）列：直接展示 -->
          <span v-if="column.computed" class="editable-table__computed">
            {{ display(row as T, column) || '-' }}
          </span>

          <!-- 编辑态 -->
          <el-select
            v-else-if="editing?.row === $index && editing.col === column.prop && column.type === 'select'"
            :ref="(el: unknown) => setCellRef($index, column.prop, el)"
            :model-value="row[column.prop]"
            size="small"
            filterable
            :placeholder="column.placeholder ?? column.label"
            @update:model-value="(value: unknown) => { (row as Record<string, unknown>)[column.prop] = value; emit('change'); }"
            @blur="leaveCell"
            @keydown="onKeydown($event, $index, column.prop)"
          >
            <el-option
              v-for="option in column.options ?? []"
              :key="String(option.value)"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <el-date-picker
            v-else-if="editing?.row === $index && editing.col === column.prop && column.type === 'date'"
            :ref="(el: unknown) => setCellRef($index, column.prop, el)"
            :model-value="row[column.prop]"
            type="date"
            size="small"
            value-format="YYYY-MM-DD"
            :placeholder="column.placeholder ?? '选择日期'"
            @update:model-value="(value: unknown) => { (row as Record<string, unknown>)[column.prop] = value; emit('change'); }"
            @blur="leaveCell"
            @keydown="onKeydown($event, $index, column.prop)"
          />

          <el-input
            v-else-if="editing?.row === $index && editing.col === column.prop"
            :ref="(el: unknown) => setCellRef($index, column.prop, el)"
            :model-value="row[column.prop]"
            size="small"
            :type="column.type === 'number' ? 'number' : 'text'"
            :placeholder="column.placeholder ?? column.label"
            @update:model-value="(value: unknown) => { (row as Record<string, unknown>)[column.prop] = value; emit('change'); }"
            @blur="leaveCell"
            @keydown="onKeydown($event, $index, column.prop)"
          />

          <!-- 展示态 -->
          <span v-else class="editable-table__text">{{ display(row as T, column) }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="removable" label="操作" width="70" align="center" fixed="right">
        <template #default="{ $index }">
          <el-button link type="danger" size="small" :icon="Delete" @click="removeRow($index)" />
        </template>
      </el-table-column>
    </el-table>

    <div class="editable-table__footer">
      <el-button link type="primary" size="small" :icon="Plus" @click="addRow(true)">新增一行</el-button>
      <span class="editable-table__tip">
        {{ rows.length ? 'Tab 右移 · Enter 下移 · 末行 Enter 自动加行 · Ctrl+V 批量粘贴' : emptyText }}
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.editable-table {
  width: 100%;

  /* 服务端计算列用浅灰底区分，明确"这列不能改" */
  :deep(.is-computed-col) {
    background-color: var(--el-fill-color-lighter);
  }

  /* 错误行整行淡红，比只标一个单元格更容易被注意到 */
  :deep(.el-table__row.is-error-row) {
    background-color: var(--el-color-danger-light-9);
  }

  &__computed {
    color: var(--admin-text-regular);
  }

  /* 展示态撑满单元格，保证点击热区覆盖整格（不只是文字那几个像素） */
  &__text {
    display: block;
    min-height: 24px;
    line-height: 24px;
    padding: 0 4px;
    cursor: text;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 8px;
  }

  &__tip {
    font-size: 12px;
    color: var(--admin-text-secondary);
  }
}
</style>
