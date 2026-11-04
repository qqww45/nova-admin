<script setup lang="ts">
/**
 * 表格操作栏：按数量智能布局，杜绝小屏无规则换行。
 *
 * <p>规则：
 * <ul>
 *     <li>按钮 ≤ max（默认 3）个：全部横向并排；</li>
 *     <li>按钮 > max 个：前 max 个并排，其余收进「更多」下拉菜单；</li>
 *     <li>单个按钮最小宽度 64px、统一 small 尺寸与 4px 间距，保证点击热区。</li>
 * </ul>
 *
 * <p>用法：
 * <pre>{@code
 * <TableActionBar :actions="[
 *   { label: '编辑', type: 'primary', icon: 'Edit', onClick: () => openEdit(row) },
 *   { label: '删除', type: 'danger', onClick: () => remove(row) },
 * ]" />
 * }</pre>
 */
import { computed } from 'vue';
import { ArrowDown } from '@element-plus/icons-vue';

/** 操作项定义 */
export interface TableActionItem {
  /** 文案 */
  label: string;
  /** 语义色，决定文字/图标颜色 */
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default';
  /** 图标名（已全局注册的 Element Plus 图标，如 'Edit'） */
  icon?: string;
  /** 禁用 */
  disabled?: boolean;
  /** 动态隐藏（用于按行数据判断是否展示，如"仅正常状态可停用"） */
  hidden?: boolean;
  /** 下拉菜单中标记为危险操作（红色） */
  danger?: boolean;
  /** 下拉菜单中在该项上方插入分隔线 */
  divided?: boolean;
  /** 点击回调 */
  onClick?: () => void;
}

const props = withDefaults(
  defineProps<{
    /** 操作项列表 */
    actions: TableActionItem[];
    /** 最多并排展示的按钮数量，超出部分收进「更多」 */
    max?: number;
    /** 按钮尺寸，全站统一 small */
    size?: 'large' | 'default' | 'small';
    /**
     * 紧凑模式：缩短按钮最小宽度，给「同步 / 指令 / 轮换 / 更多」这类
     * 四项操作列用，避免 64px × 4 撑破固定列被裁切。
     */
    compact?: boolean;
  }>(),
  {
    max: 3,
    size: 'small',
    compact: false,
  },
);

/** 过滤掉隐藏项后的可见操作 */
const visibleActions = computed(() => props.actions.filter((action) => !action.hidden));

/** 并排展示的按钮 */
const inlineActions = computed(() => visibleActions.value.slice(0, props.max));

/** 收进「更多」的按钮 */
const moreActions = computed(() => visibleActions.value.slice(props.max));

/** 是否需要「更多」下拉 */
const hasMore = computed(() => moreActions.value.length > 0);

/**
 * 触发下拉菜单项。
 *
 * @param action 操作项
 */
function onCommand(action: TableActionItem): void {
  if (action.disabled) {
    return;
  }
  action.onClick?.();
}
</script>

<template>
  <div class="table-action-bar" :class="{ 'table-action-bar--compact': compact }">
    <el-button
      v-for="action in inlineActions"
      :key="action.label"
      class="table-action-bar__btn"
      link
      :size="size"
      :type="action.type ?? 'primary'"
      :icon="action.icon"
      :disabled="action.disabled"
      @click="action.onClick?.()"
    >
      {{ action.label }}
    </el-button>

    <el-dropdown v-if="hasMore" trigger="click" @command="onCommand">
      <el-button class="table-action-bar__btn" link :size="size" type="primary">
        更多
        <el-icon class="el-icon--right"><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="action in moreActions"
            :key="action.label"
            :command="action"
            :disabled="action.disabled"
            :divided="action.divided"
            :class="{ 'table-action-bar__danger': action.danger ?? action.type === 'danger' }"
          >
            {{ action.label }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped lang="scss">
.table-action-bar {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 4px;

  /* 统一尺寸与点击热区：最小宽度 64px，图标与文案占位一致 */
  &__btn {
    min-width: 64px;
    height: 24px;
    padding: 0 4px;
    margin: 0;
    justify-content: center;
    transition: opacity var(--admin-transition-fast) ease;
  }

  &--compact {
    flex-wrap: nowrap;
    gap: 2px;

    .table-action-bar__btn {
      min-width: 40px;
      padding: 0 2px;
    }
  }

  /* 下拉项中的危险操作，与行内 danger 按钮语义保持一致 */
  :deep(.el-dropdown-menu__item.table-action-bar__danger) {
    color: var(--el-color-danger);
  }
}
</style>
