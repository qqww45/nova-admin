<script setup lang="ts">
/**
 * 图标选择器：从全量 Element Plus 图标中挑一个，存的是图标名（与后端 icon 字段一致）。
 *
 * <p>为什么存名字而不是组件引用：icon 落库为字符串，渲染处经 utils/icon 的
 * resolveIcon 还原成组件；规范化（ep: 前缀、短横线）也在那里统一处理。
 *
 * <p>体验要点：
 * <ul>
 *     <li>可搜索：三百来个图标名靠滚动找不现实；</li>
 *     <li>可清空：菜单可以不带图标（侧边栏会退化为纯文字，不算错误状态）；</li>
 *     <li>选中项与选项都带图标预览，纯文字选图标等于盲选。</li>
 * </ul>
 */
import { computed } from 'vue';
import { ICON_MAP, ICON_NAMES, resolveIcon } from '@/utils/icon';

const props = withDefaults(
  defineProps<{
    /** 当前图标名（Element Plus 图标组件名，如 Search） */
    modelValue?: string;
    /** 占位文案 */
    placeholder?: string;
    /** 是否允许清空（不带图标） */
    clearable?: boolean;
  }>(),
  { modelValue: '', placeholder: '请选择图标（可搜索）', clearable: true },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
}>();

/** 预览用图标组件：走 resolveIcon，兼容库里的历史写法 */
const previewIcon = computed(() => resolveIcon(props.modelValue));

function onChange(value: string | number | boolean | object | undefined): void {
  emit('update:modelValue', typeof value === 'string' ? value : '');
}
</script>

<template>
  <el-select
    class="icon-select"
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    filterable
    @update:model-value="onChange"
  >
    <!-- 选中项回显：带图标，否则选完后输入框里只剩一串英文 -->
    <template #label="{ value }">
      <span class="icon-select__selected">
        <el-icon v-if="previewIcon"><component :is="previewIcon" /></el-icon>
        <span>{{ value }}</span>
      </span>
    </template>

    <el-option v-for="name in ICON_NAMES" :key="name" :label="name" :value="name">
      <span class="icon-select__option">
        <el-icon><component :is="ICON_MAP[name]" /></el-icon>
        <span>{{ name }}</span>
      </span>
    </el-option>
  </el-select>
</template>

<style scoped lang="scss">
.icon-select {
  width: 100%;
}

.icon-select__selected,
.icon-select__option {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
