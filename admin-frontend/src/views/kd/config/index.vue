<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" class="kd-config">
      <!-- 任务清单 -->
      <el-tab-pane label="同步任务清单" name="task">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="客户端启动时拉取本清单，按账套 + 实体生效；修改后无需发版，通知客户端重新拉取即可。"
          class="tip"
        />
        <ProTable :columns="taskColumns" :data="tasks" :loading="taskLoading" :total="0">
          <template #toolbar>
            <el-button type="primary" @click="openTask()">新增任务</el-button>
            <el-button @click="loadTasks">刷新</el-button>
          </template>
          <template #col-enabled="{ row }">
            <el-tag :type="(row as KdSyncTaskResp).enabled === 1 ? 'success' : 'info'">
              {{ (row as KdSyncTaskResp).enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
          <template #col-fieldKeys="{ row }">
            <span class="ellipsis">{{ (row as KdSyncTaskResp).fieldKeys }}</span>
          </template>
          <template #columns>
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <TableActionBar
                  :actions="[{ label: '编辑', onClick: () => openTask(row as KdSyncTaskResp) }]"
                />
              </template>
            </el-table-column>
          </template>
        </ProTable>
      </el-tab-pane>

      <!-- 字段映射 -->
      <el-tab-pane label="账套字段映射" name="mapping">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="金蝶每家账套的字段名不同（如分录内码 FEntryID / FEntity_FEntryID）。映射按绑定保存，改这里即可，客户端不用发版。"
          class="tip"
        />
        <div class="mapping-bar">
          <el-select
            v-model="selectedBindingId"
            placeholder="选择账套绑定"
            filterable
            style="width: 320px"
            @change="loadMappings"
          >
            <el-option
              v-for="b in bindings"
              :key="b.id"
              :label="`${b.userName || b.userId} · ${b.acctName || b.acctId}`"
              :value="b.id"
            />
          </el-select>
          <el-button type="primary" :disabled="!selectedBindingId" @click="openMapping()">新增映射</el-button>
        </div>
        <ProTable :columns="mappingColumns" :data="mappings" :loading="mappingLoading" :total="0">
          <template #col-mappingValue="{ row }">
            <span class="ellipsis">{{ (row as KdFieldMappingResp).mappingValue }}</span>
          </template>
          <template #columns>
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <TableActionBar
                  :actions="[{ label: '编辑', onClick: () => openMapping(row as KdFieldMappingResp) }]"
                />
              </template>
            </el-table-column>
          </template>
        </ProTable>
      </el-tab-pane>
    </el-tabs>

    <!-- 任务编辑 -->
    <el-dialog v-model="taskVisible" :title="taskForm.id ? '编辑任务' : '新增任务'" width="640px">
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="账套">
          <el-input v-model="taskForm.acctId" placeholder="* 表示全局模板" />
        </el-form-item>
        <el-form-item label="实体" required>
          <el-select v-model="taskForm.entity" placeholder="请选择" style="width: 100%">
            <el-option label="客户 customer" value="customer" />
            <el-option label="出库单头 outbound" value="outbound" />
            <el-option label="出库分录 outbound_line" value="outbound_line" />
          </el-select>
        </el-form-item>
        <el-form-item label="FormId" required>
          <el-input v-model="taskForm.formId" placeholder="BD_Customer / SAL_OUTSTOCK" />
        </el-form-item>
        <el-form-item label="上报接口" required>
          <el-input v-model="taskForm.endpoint" placeholder="/kd/sync/outbound" />
        </el-form-item>
        <el-form-item label="水位字段">
          <el-input v-model="taskForm.watermarkField" placeholder="FModifyDate（留空则按 FDate）" />
        </el-form-item>
        <el-form-item label="FieldKeys" required>
          <el-input v-model="taskForm.fieldKeys" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="taskForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="taskForm.sortNo" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="taskForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- 映射编辑 -->
    <el-dialog v-model="mappingVisible" :title="mappingForm.editing ? '编辑映射' : '新增映射'" width="600px">
      <el-form :model="mappingForm" label-width="100px">
        <el-form-item label="映射键" required>
          <el-select
            v-model="mappingForm.mappingKey"
            allow-create
            filterable
            default-first-option
            :disabled="mappingForm.editing"
            placeholder="选择或输入，如 line_field_keys"
            style="width: 100%"
          >
            <el-option label="line_field_keys（出库分录字段集）" value="line_field_keys" />
            <el-option label="customer_po_field（客户采购订单号字段）" value="customer_po_field" />
            <el-option label="customer_field_keys（客户字段集）" value="customer_field_keys" />
            <el-option label="outbound_field_keys（出库单头字段集）" value="outbound_field_keys" />
          </el-select>
        </el-form-item>
        <el-form-item label="映射值" required>
          <el-input v-model="mappingForm.mappingValue" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="mappingForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mappingVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMapping">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import { kdConfigApi, kdBindingApi } from '@/api/kd';
import type {
  KdBindingResp,
  KdFieldMappingResp,
  KdSyncTaskResp,
  KdTaskSaveReq,
} from '@/types/kd';

const activeTab = ref<'task' | 'mapping'>('task');

/* ---------------- 任务清单 ---------------- */
const taskLoading = ref(false);
const tasks = ref<KdSyncTaskResp[]>([]);

const taskColumns: ProTableColumn[] = [
  { prop: 'acctId', label: '账套', width: 110 },
  { prop: 'entity', label: '实体', width: 130 },
  { prop: 'formId', label: 'FormId', width: 140 },
  { prop: 'endpoint', label: '上报接口', width: 180 },
  { prop: 'watermarkField', label: '水位字段', width: 130 },
  { prop: 'fieldKeys', label: 'FieldKeys', minWidth: 240, slot: true, showOverflowTooltip: true },
  { prop: 'enabled', label: '状态', width: 80, slot: true },
  { prop: 'sortNo', label: '排序', width: 70 },
  { prop: 'remark', label: '备注', minWidth: 120, showOverflowTooltip: true },
];

async function loadTasks(): Promise<void> {
  taskLoading.value = true;
  try {
    tasks.value = await kdConfigApi.tasks();
  } finally {
    taskLoading.value = false;
  }
}

const taskVisible = ref(false);
const saving = ref(false);
const taskForm = reactive<Required<Pick<KdTaskSaveReq, 'entity' | 'formId' | 'endpoint' | 'fieldKeys'>> & {
  id?: string;
  acctId?: string;
  watermarkField?: string;
  enabled: number;
  sortNo: number;
  remark?: string;
}>({
  entity: 'outbound',
  formId: '',
  endpoint: '',
  fieldKeys: '',
  acctId: '*',
  watermarkField: 'FModifyDate',
  enabled: 1,
  sortNo: 0,
  remark: '',
});

function openTask(row?: KdSyncTaskResp): void {
  Object.assign(taskForm, {
    id: row?.id,
    acctId: row?.acctId ?? '*',
    entity: row?.entity ?? 'outbound',
    formId: row?.formId ?? '',
    endpoint: row?.endpoint ?? '',
    fieldKeys: row?.fieldKeys ?? '',
    watermarkField: row?.watermarkField ?? 'FModifyDate',
    enabled: row?.enabled ?? 1,
    sortNo: row?.sortNo ?? 0,
    remark: row?.remark ?? '',
  });
  taskVisible.value = true;
}

async function saveTask(): Promise<void> {
  if (!taskForm.formId || !taskForm.endpoint || !taskForm.fieldKeys) {
    ElMessage.warning('FormId / 上报接口 / FieldKeys 不能为空');
    return;
  }
  saving.value = true;
  try {
    await kdConfigApi.saveTask({ ...taskForm });
    ElMessage.success('已保存');
    taskVisible.value = false;
    await loadTasks();
  } finally {
    saving.value = false;
  }
}

/* ---------------- 字段映射 ---------------- */
const bindings = ref<KdBindingResp[]>([]);
const selectedBindingId = ref('');
const mappingLoading = ref(false);
const mappings = ref<KdFieldMappingResp[]>([]);

const mappingColumns: ProTableColumn[] = [
  { prop: 'mappingKey', label: '映射键', width: 220 },
  { prop: 'mappingValue', label: '映射值', minWidth: 320, slot: true, showOverflowTooltip: true },
  { prop: 'remark', label: '备注', minWidth: 160, showOverflowTooltip: true },
];

async function loadMappings(): Promise<void> {
  if (!selectedBindingId.value) {
    mappings.value = [];
    return;
  }
  mappingLoading.value = true;
  try {
    mappings.value = await kdConfigApi.mappings(selectedBindingId.value);
  } finally {
    mappingLoading.value = false;
  }
}

const mappingVisible = ref(false);
const mappingForm = reactive<{
  editing: boolean;
  mappingKey: string;
  mappingValue: string;
  remark: string;
}>({ editing: false, mappingKey: 'line_field_keys', mappingValue: '', remark: '' });

function openMapping(row?: KdFieldMappingResp): void {
  Object.assign(mappingForm, {
    editing: Boolean(row),
    mappingKey: row?.mappingKey ?? 'line_field_keys',
    mappingValue: row?.mappingValue ?? '',
    remark: row?.remark ?? '',
  });
  mappingVisible.value = true;
}

async function saveMapping(): Promise<void> {
  if (!selectedBindingId.value) {
    ElMessage.warning('请先选择账套绑定');
    return;
  }
  if (!mappingForm.mappingKey.trim()) {
    ElMessage.warning('请填写映射键');
    return;
  }
  saving.value = true;
  try {
    await kdConfigApi.saveMapping({
      bindingId: selectedBindingId.value,
      mappingKey: mappingForm.mappingKey.trim(),
      mappingValue: mappingForm.mappingValue,
      remark: mappingForm.remark,
    });
    ElMessage.success('已保存');
    mappingVisible.value = false;
    await loadMappings();
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadTasks(), kdBindingApi.list().then((rows) => (bindings.value = rows))]);
});
</script>

<style scoped lang="scss">
.kd-config {
  .tip {
    margin-bottom: 12px;
  }

  .mapping-bar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }

  .ellipsis {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }
}
</style>
