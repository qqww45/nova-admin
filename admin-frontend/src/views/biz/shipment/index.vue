<script setup lang="ts">
/**
 * 订单出货页（本系统最高频的页面）。
 *
 * <p>布局取舍：把"录入区"常驻在页面顶部，列表在下方。
 * 出货是每天重复几十次的操作，如果每次都要"点新增 → 弹窗 → 保存"，
 * 用户最终会回到 Excel 里录完再导入 —— 那系统就白做了。
 * 因此这里采用"录入区常驻 + 支持整块粘贴"的形态，让系统比 Excel 更快。
 *
 * <p>提交结果：批量接口返回逐行成功/失败，失败行保留在录入区并标红显示原因，
 * 用户就地改完再提交，不需要重新填一遍。
 */
import { computed, onMounted, reactive, ref, shallowRef } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Check, Delete, Download, Refresh, Upload, Warning } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import EditableTable from '@/components/EditableTable/index.vue';
import type { EditableColumn } from '@/types/editable';
import { customerApi, importExportApi, shipmentApi } from '@/api/biz';
import { userApi } from '@/api/user';
import { useTable } from '@/composables/useTable';
import type {
  BatchLineResult,
  CustomerResp,
  ShipmentQuery,
  ShipmentResp,
  ShipmentRowReq,
} from '@/types/biz';
import type { PageResult, UserQuery } from '@/types/api';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

/* ==================== 录入区 ==================== */

/** 录入行（界面上的类型与提交类型一致，空值用 undefined 便于后端判空） */
type ShipmentDraft = Record<string, unknown> & ShipmentRowReq;

const drafts = ref<ShipmentDraft[]>([blankRow(), blankRow(), blankRow(), blankRow(), blankRow()]);
const submitting = ref(false);
const todayCount = ref(0);
const failedRows = shallowRef<BatchLineResult[]>([]);

const customerOptions = shallowRef<CustomerResp[]>([]);
const salesmanOptions = shallowRef<{ id: string; name: string }[]>([]);

function blankRow(): ShipmentDraft {
  return {
    shipDate: todayStr(),
    processOrderNo: '',
    salesmanId: '',
    customerId: '',
    customerOrderNo: '',
    customerMaterialNo: '',
    model: '',
    qty: undefined,
    materialNo: '',
    spec: '',
    remark: '',
  };
}

function todayStr(): string {
  const now = new Date();
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** 录入列（顺序与用户现有 xlsx 台账一致，便于整块粘贴） */
const draftColumns = computed<EditableColumn[]>(() => [
  { prop: 'shipDate', label: '出货日期', width: 130, type: 'date' },
  { prop: 'processOrderNo', label: '加工单号', width: 140 },
  {
    prop: 'salesmanId',
    label: '业务',
    width: 120,
    type: 'select',
    required: true,
    options: salesmanOptions.value.map((item) => ({ label: item.name, value: item.id })),
  },
  {
    prop: 'customerId',
    label: '客户',
    minWidth: 160,
    type: 'select',
    required: true,
    options: customerOptions.value.map((item) => ({ label: item.customerName, value: item.id })),
  },
  { prop: 'customerOrderNo', label: '客户订单号', width: 140 },
  { prop: 'customerMaterialNo', label: '客户物料号', width: 140, placeholder: '匹配订单用' },
  { prop: 'model', label: '型号', width: 140, required: true },
  { prop: 'qty', label: '出货数量', width: 110, type: 'number', required: true, align: 'right' },
  { prop: 'materialNo', label: '料号', width: 130 },
  { prop: 'spec', label: '规格型号', width: 130 },
  { prop: 'remark', label: '备注', width: 130 },
]);

/** 有效行：至少填了型号与数量 */
const validDrafts = computed(() =>
  drafts.value.filter(
    (row) => String(row.model ?? '').trim() !== '' && Number(row.qty ?? 0) > 0,
  ),
);

/** 提交出货 */
async function submit(): Promise<void> {
  if (validDrafts.value.length === 0) {
    ElMessage.warning('请先录入出货数据（型号与数量必填）');
    return;
  }
  submitting.value = true;
  try {
    const result = await shipmentApi.create({ source: 'MANUAL', rows: validDrafts.value });
    failedRows.value = result.results.filter((item) => !item.success);
    if (result.failCount === 0) {
      ElMessage.success(`成功录入 ${result.successCount} 条`);
      // 全部成功：清空录入区并重置为空白行，方便继续录下一批
      drafts.value = [blankRow(), blankRow(), blankRow(), blankRow(), blankRow()];
    } else {
      ElMessageBox.alert(
        `成功 ${result.successCount} 条，失败 ${result.failCount} 条。失败行已保留在录入区，修正后可重新提交。`,
        '部分录入失败',
        { type: 'warning' },
      );
      // 只清除成功的行，失败行留在原地等待修正
      const failedIndexes = new Set(
        failedRows.value.map((item) => item.lineNo),
      );
      drafts.value = validDrafts.value.filter((_, index) => failedIndexes.has(index + 1));
    }
    await Promise.all([reload(), loadTodayCount()]);
  } finally {
    submitting.value = false;
  }
}

/** 清空录入区 */
async function clearDrafts(): Promise<void> {
  await ElMessageBox.confirm('确认清空录入区？已填写的内容将丢失。', '清空确认', {
    type: 'warning',
  });
  drafts.value = [blankRow(), blankRow(), blankRow(), blankRow(), blankRow()];
  failedRows.value = [];
}

/* ==================== 列表 ==================== */

const {
  loading,
  list,
  total,
  pageNum,
  pageSize,
  query,
  reload,
  reset,
  onPageChange,
  onSizeChange,
} = useTable<ShipmentResp>((params) => shipmentApi.page({ ...params, ...searchForm } as never), {});

/** 搜索条件（强类型，理由同客户页） */
const searchForm = reactive<{
  keyword: string;
  customerId?: string;
  status?: string;
  onlyOrphan?: boolean;
}>({ keyword: '', customerId: undefined, status: undefined, onlyOrphan: undefined });

/** 重置搜索条件 */
function handleReset(): void {
  Object.assign(searchForm, {
    keyword: '',
    customerId: undefined,
    status: undefined,
    onlyOrphan: undefined,
  });
  reset();
}

const columns: ProTableColumn[] = [
  { prop: 'shipDate', label: '出货日期', width: 120, slot: true },
  { prop: 'processOrderNo', label: '加工单号', width: 140, showOverflowTooltip: true },
  { prop: 'customerName', label: '客户', minWidth: 160, align: 'left', showOverflowTooltip: true },
  { prop: 'customerOrderNo', label: '客户订单号', width: 140, showOverflowTooltip: true },
  { prop: 'model', label: '型号', width: 140, showOverflowTooltip: true },
  { prop: 'qty', label: '出货数量', width: 100, align: 'right' },
  { prop: 'salesmanName', label: '业务', width: 100 },
  { prop: 'orderNo', label: '关联订单', width: 150, slot: true },
  { prop: 'amountTaxIncluded', label: '价税合计', width: 120, align: 'right', slot: true },
  { prop: 'statusLabel', label: '状态', width: 90, slot: true },
  { prop: 'sourceLabel', label: '来源', width: 90 },
  { prop: 'remark', label: '备注', minWidth: 120, align: 'left', showOverflowTooltip: true },
];

async function voidOne(row: ShipmentResp): Promise<void> {
  const { value } = await ElMessageBox.prompt('请填写作废原因（将记录到操作日志）', '作废出货', {
    inputPattern: /\S+/,
    inputErrorMessage: '作废原因不能为空',
  });
  await shipmentApi.voidShipment(row.id, value);
  ElMessage.success('已作废，明细出货数已回退');
  await Promise.all([reload(), loadTodayCount()]);
}

async function loadTodayCount(): Promise<void> {
  todayCount.value = await shipmentApi.todayCount();
}

/* ==================== 快速带出（出货自动带出 §6.3） ==================== */

/**
 * 录入区顶部"快速带出"：按 A.4 三级匹配查明细，
 * 单条候选自动填到第一个空白行，多条候选展示列表让用户挑（带未出货数）。
 */
const fillForm = reactive<{ customerOrderNo: string; customerMaterialNo: string }>({
  customerOrderNo: '',
  customerMaterialNo: '',
});
const fillHints = shallowRef<{ itemId: string; qtyOpen: number; model: string }[]>([]);

async function fillFromOrder(): Promise<void> {
  if (!fillForm.customerOrderNo) {
    ElMessage.warning('请先填写客户订单号');
    return;
  }
  const items = await shipmentApi.matchItems({
    customerOrderNo: fillForm.customerOrderNo,
    customerMaterialNo: fillForm.customerMaterialNo || undefined,
  });
  if (items.length === 0) {
    fillHints.value = [];
    ElMessage.warning('未匹配到候选明细，请检查订单号或客户物料号');
    return;
  }
  if (items.length === 1) {
    const row = pickBlankRow();
    Object.assign(row, {
      customerOrderNo: fillForm.customerOrderNo,
      customerMaterialNo: fillForm.customerMaterialNo || items[0].customerMaterialNo,
      model: items[0].model,
      materialNo: items[0].materialNo,
      spec: items[0].spec,
    });
    fillHints.value = [
      { itemId: String(items[0].id), qtyOpen: Number(items[0].qtyOpen ?? 0), model: items[0].model },
    ];
    return;
  }
  fillHints.value = items.map((item) => ({
    itemId: String(item.id),
    qtyOpen: Number(item.qtyOpen ?? 0),
    model: item.model ?? '',
  }));
}

/** 候选 → 填到空白行（多条候选时用户点哪条就带哪条） */
function applyFillHint(hint: { itemId: string; qtyOpen: number; model: string }): void {
  const row = pickBlankRow();
  Object.assign(row, {
    customerOrderNo: fillForm.customerOrderNo,
    model: hint.model,
  });
  ElMessage.success(`已带出型号 ${hint.model}，该明细未出货 ${hint.qtyOpen}`);
}

function pickBlankRow(): ShipmentDraft {
  const blank = drafts.value.find((row) => !row.model && !row.qty);
  if (blank) {
    return blank;
  }
  const row = blankRow();
  drafts.value.push(row);
  return row;
}

/* ==================== 导入导出 ==================== */

async function exportData(): Promise<void> {
  await importExportApi.export('SHIPMENT', 'LEGACY');
  ElMessage.success('导出已开始');
}

async function downloadTemplate(): Promise<void> {
  await importExportApi.template('SHIPMENT', 'LEGACY');
}

async function beforeUpload(file: File): Promise<boolean> {
  try {
    const preview = await importExportApi.preview('SHIPMENT', file);
    const summary = `解析 ${preview.total} 行：新增 ${preview.insertCount}，跳过 ${preview.skipCount}，失败 ${preview.failCount}`;
    if (preview.hasBlockingError) {
      ElMessageBox.alert(`${summary}。存在阻断级错误，请先修正后重新导入。`, '导入校验未通过', {
        type: 'error',
      });
      return false;
    }
    await ElMessageBox.confirm(`${summary}。确认导入？`, '导入确认', { type: 'warning' });
    const result = await importExportApi.confirm('SHIPMENT', file);
    ElMessage.success(`导入完成：新增 ${result.insertCount}，失败 ${result.failCount}`);
    await Promise.all([reload(), loadTodayCount()]);
  } catch {
    // 用户取消
  }
  return false;
}

/* ==================== 初始化 ==================== */

onMounted(async () => {
  await loadTodayCount();
  // 客户与业务员下拉：录入区是高频操作，选项一次拉够，避免每次点单元格都请求
  customerOptions.value = await customerApi.search('', 200);
  const users = await userApi.page({ pageNum: 1, pageSize: 200 } as UserQuery);
  salesmanOptions.value = users.records.map((item) => ({
    id: String(item.id),
    name: item.nickname || item.username,
  }));
});
</script>

<template>
  <div class="shipment-page">
    <!-- 录入区：常驻，支持整块粘贴 -->
    <el-card shadow="never" class="shipment-page__entry">
      <template #header>
        <div class="shipment-page__entry-header">
          <span class="shipment-page__entry-title">出货录入</span>
          <el-tag type="info" size="small">今日已录 {{ todayCount }} 条</el-tag>
          <span class="shipment-page__entry-tip">
            列顺序与现有台账一致，可直接从 Excel 整块复制粘贴
          </span>
        </div>
      </template>

      <!-- 快速带出：按客户订单号反查明细，单条自动填行，多条点选 -->
      <div class="shipment-page__fillbar">
        <el-input
          v-model="fillForm.customerOrderNo"
          placeholder="客户订单号"
          clearable
          style="width: 220px"
        />
        <el-input
          v-model="fillForm.customerMaterialNo"
          placeholder="客户物料号（可选）"
          clearable
          style="width: 220px"
        />
        <el-button type="primary" @click="fillFromOrder">按订单带出</el-button>
        <div v-if="fillHints.length" class="shipment-page__hints">
          <template v-if="fillHints.length === 1">
            <el-tag size="small" type="success">
              {{ fillHints[0].model }} · 未出货 {{ fillHints[0].qtyOpen }}
            </el-tag>
          </template>
          <template v-else>
            <span class="shipment-page__hint-label">候选明细（点选带出）：</span>
            <el-tag
              v-for="hint in fillHints"
              :key="hint.itemId"
              size="small"
              class="shipment-page__hint"
              @click="applyFillHint(hint)"
            >
              {{ hint.model }} · 未出货 {{ hint.qtyOpen }}
            </el-tag>
          </template>
        </div>
      </div>

      <EditableTable
        v-model="drafts"
        :columns="draftColumns"
        :row-factory="blankRow"
        :min-rows="5"
      />

      <div class="shipment-page__entry-footer">
        <el-button type="primary" :icon="Check" :loading="submitting" @click="submit">
          提交出货（{{ validDrafts.length }} 行）
        </el-button>
        <el-button :icon="Delete" @click="clearDrafts">清空</el-button>
        <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
        <el-button :icon="Download" @click="exportData">导出台账</el-button>
        <el-upload
          v-if="userStore.hasPermission('biz:shipment:import')"
          :show-file-list="false"
          accept=".xlsx"
          :before-upload="beforeUpload"
        >
          <el-button :icon="Upload">导入出货</el-button>
        </el-upload>
      </div>

      <!-- 失败行提示：把服务端返回的逐行原因原样展示，用户就地修正 -->
      <el-alert
        v-if="failedRows.length"
        type="warning"
        :closable="false"
        class="shipment-page__errors"
      >
        <template #title>
          <span><el-icon><Warning /></el-icon> 以下行未通过校验，已保留在录入区：</span>
        </template>
        <ul class="shipment-page__error-list">
          <li v-for="item in failedRows" :key="item.lineNo">第 {{ item.lineNo }} 行：{{ item.message }}</li>
        </ul>
      </el-alert>
    </el-card>

    <!-- 列表区 -->
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      column-drag
      column-drag-key="biz:shipment"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="加工单号 / 客户订单号 / 型号 / 料号"
            clearable
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="客户">
          <el-select
            v-model="searchForm.customerId"
            filterable
            clearable
            placeholder="全部"
            style="width: 180px"
          >
            <el-option
              v-for="item in customerOptions"
              :key="item.id"
              :label="item.customerName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" value="NORMAL" />
            <el-option label="已作废" value="VOID" />
          </el-select>
        </el-form-item>
        <el-form-item label="仅异常池">
          <el-switch v-model="searchForm.onlyOrphan" />
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </template>

      <template #col-shipDate="{ row }">
        {{ String((row as ShipmentResp).shipDate ?? '').slice(0, 10) }}
      </template>

      <template #col-orderNo="{ row }">
        <span v-if="(row as ShipmentResp).orderNo">{{ (row as ShipmentResp).orderNo }}</span>
        <el-tag v-else type="warning" size="small">无源出货</el-tag>
      </template>

      <template #col-amountTaxIncluded="{ row }">
        {{ (row as ShipmentResp).amountTaxIncluded ? Number((row as ShipmentResp).amountTaxIncluded).toFixed(2) : '-' }}
      </template>

      <template #col-statusLabel="{ row }">
        <el-tag
          size="small"
          :type="(row as ShipmentResp).status === 'VOID' ? 'info' : 'success'"
        >
          {{ (row as ShipmentResp).statusLabel }}
        </el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :max="1"
              :actions="[
                {
                  label: '作废',
                  type: 'danger',
                  hidden:
                    !userStore.hasPermission('biz:shipment:void') ||
                    (row as ShipmentResp).status === 'VOID',
                  onClick: () => voidOne(row as ShipmentResp),
                },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>
  </div>
</template>

<style scoped lang="scss">
.shipment-page {
  &__entry {
    margin-bottom: 16px;

    :deep(.el-card__header) {
      padding: 12px 16px;
    }
  }

  &__entry-header {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__entry-title {
    font-size: 15px;
    font-weight: 600;
  }

  &__entry-tip {
    font-size: 12px;
    color: var(--admin-text-secondary);
  }

  &__entry-footer {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
  }

  &__errors {
    margin-top: 12px;
  }

  &__fillbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  &__hints {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-left: 12px;
    flex-wrap: wrap;
  }

  &__hint-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__hint {
    cursor: pointer;
  }

  &__error-list {
    margin: 4px 0 0;
    padding-left: 20px;
    font-size: 12px;
    line-height: 1.8;
  }

  :deep(.el-upload) {
    display: inline-flex;
    margin-left: 8px;
  }
}
</style>
