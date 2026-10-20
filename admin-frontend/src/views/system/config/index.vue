<script setup lang="ts">
/**
 * 系统配置：运行期可调的参数（站点标题、登录安全阈值等）。
 *
 * <p>交互取舍：
 * <ul>
 *     <li>配置量不大（几十条），不做分页，前端一次性加载并即时过滤；</li>
 *     <li><b>内置项只可改值不可删除</b>：删除会让依赖它的代码路径拿不到值，
 *         界面上直接禁用删除按钮而不是等后端报错；</li>
 *     <li>提供"刷新缓存"：多实例部署时，修改只落在处理请求的那个实例上，
 *         用它让其它实例重新加载（当前缓存为内存实现，切 Redis 后此项可去掉）。</li>
 * </ul>
 */
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import TableActionBar, { type TableActionItem } from '@/components/TableActionBar/index.vue';
import { configApi } from '@/api/config';
import type { ConfigResp, ConfigSaveReq } from '@/types/api';

const loading = ref(false);
const list = ref<ConfigResp[]>([]);
const keyword = ref('');

/**
 * 列定义。
 *
 * <p>与其它列表页保持一致：统一由 ProTable 承载"搜索区 + 工具栏 + 表格"，
 * 本页配置量小不做分页，因此不传 total（ProTable 会自动隐藏分页区域）。
 */
const columns = [
  { prop: 'configKey', label: '配置键', minWidth: 200 },
  { prop: 'configValue', label: '配置值', minWidth: 180, showOverflowTooltip: true },
  { prop: 'configType', label: '类型', minWidth: 100, slot: true },
  { prop: 'builtin', label: '内置', minWidth: 90, slot: true },
  { prop: 'remark', label: '说明', minWidth: 200, showOverflowTooltip: true },
  { prop: 'updateTime', label: '更新时间', minWidth: 165 },
];

/** 编辑/新增弹窗 */
const dialog = ref(false);
const editing = ref(false);
const form = ref<ConfigSaveReq>({ configKey: '', configValue: '', configType: 'STRING', remark: '' });
const formRef = ref<{ validate: () => Promise<boolean>; resetFields: () => void }>();

const rules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
};

/** el-table 插槽行类型收敛 */
function asConfig(row: unknown): ConfigResp {
  return row as ConfigResp;
}

/**
 * 单行操作项：操作列不进入 ProTable 的列数组（每页行为不同），
 * 统一在 #columns 插槽里手写 —— 与 user / role 等页保持一致。
 */
function buildActions(row: ConfigResp): TableActionItem[] {
  return [
    { label: '修改', type: 'primary', icon: 'Edit', onClick: () => openEdit(row) },
    {
      label: '删除',
      type: 'danger',
      icon: 'Delete',
      onClick: () => remove(row),
      hidden: row.builtin,
    },
  ];
}

/** 加载配置列表 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await configApi.list(keyword.value);
  } finally {
    loading.value = false;
  }
}

/** 重置关键字后重新加载 */
function handleReset(): void {
  keyword.value = '';
  void load();
}

/** 打开新增 */
function openCreate(): void {
  editing.value = false;
  form.value = { configKey: '', configValue: '', configType: 'STRING', remark: '' };
  dialog.value = true;
}

/** 打开编辑（key 不可改，因为是业务标识符） */
function openEdit(row: ConfigResp): void {
  editing.value = true;
  form.value = {
    configKey: row.configKey,
    configValue: row.configValue,
    configType: row.configType,
    remark: row.remark ?? '',
  };
  dialog.value = true;
}

/** 提交 */
async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (editing.value) {
    await configApi.update(form.value);
    ElMessage.success('已保存');
  } else {
    await configApi.create(form.value);
    ElMessage.success('已新增');
  }
  dialog.value = false;
  await load();
}

/** 删除 */
async function remove(row: ConfigResp): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除配置「${row.configKey}」？依赖它的功能将回退到默认值。`,
      '删除配置',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await configApi.remove(row.configKey);
  ElMessage.success('已删除');
  await load();
}

/** 刷新服务端缓存 */
async function refresh(): Promise<void> {
  const count = await configApi.refresh();
  ElMessage.success(`已重新加载 ${count} 条配置`);
  await load();
}

/** 类型标签配色 */
function typeTag(type: string): 'primary' | 'success' | 'warning' | 'info' {
  return ({
    STRING: 'info',
    NUMBER: 'primary',
    BOOLEAN: 'success',
    JSON: 'warning',
  })[type] as 'primary' | 'success' | 'warning' | 'info' ?? 'info';
}

onMounted(load);
</script>

<template>
  <div class="page-container">
    <ProTable
      :columns="columns"
      :data="list"
      :loading="loading"
      row-key="configKey"
      column-drag
      column-drag-key="system:config"
      @selection-change="() => undefined"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            placeholder="配置键 / 备注"
            clearable
            style="width: 220px"
            @keyup.enter="load"
            @clear="load"
          />
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" :icon="'Search'" @click="load">搜索</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
      </template>

      <template #toolbar>
        <el-button type="primary" :icon="'Plus'" @click="openCreate">新增配置</el-button>
        <el-button :icon="'Refresh'" @click="refresh">刷新缓存</el-button>
      </template>

      <template #toolbar-meta>
        <span class="config__count">共 {{ list.length }} 条</span>
      </template>

      <template #col-configType="{ row }">
        <el-tag :type="typeTag(asConfig(row).configType)" size="small" effect="plain">
          {{ asConfig(row).configType }}
        </el-tag>
      </template>

      <template #col-builtin="{ row }">
        <el-tag :type="asConfig(row).builtin ? 'warning' : 'info'" size="small" effect="plain">
          {{ asConfig(row).builtin ? '内置' : '自定义' }}
        </el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <TableActionBar :actions="buildActions(asConfig(row))" />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <el-dialog v-model="dialog" :title="editing ? '修改配置' : '新增配置'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" :disabled="editing" placeholder="如 admin.title" />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="form.configValue" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select v-model="form.configType" :disabled="editing">
            <el-option label="STRING" value="STRING" />
            <el-option label="NUMBER" value="NUMBER" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
            <el-option label="JSON" value="JSON" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/*
 * 旧的手写工具栏（.config__toolbar / .config__keyword）已随 ProTable 迁移删除：
 * 搜索区、按钮组、统计文案现在统一由 ProTable 承载。
 */
.config {
  /* 统计数字用等宽字形 + 主色，与其它列表页的计数风格保持一致 */
  &__count {
    font-size: 13px;
    color: var(--admin-text-regular);
  }
}
</style>
