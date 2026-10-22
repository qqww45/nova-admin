<template>
  <div class="app-container">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      column-drag
      column-drag-key="biz:statement"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="客户">
          <el-select v-model="searchForm.customerId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已签发" value="ISSUED" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="有差异" value="DIFFERENT" />
          </el-select>
        </el-form-item>
      </template>
      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </template>
      <template #toolbar>
        <el-button type="primary" @click="openGenerate()">新建对账单</el-button>
      </template>

      <template #col-period="{ row }">
        {{ formatDate((row as StatementResp).startDate) }} ~ {{ formatDate((row as StatementResp).endDate) }}
      </template>
      <template #col-openingBalance="{ row }">
        {{ formatMoney((row as StatementResp).openingBalance) }}
      </template>
      <template #col-shippedAmount="{ row }">
        {{ formatMoney((row as StatementResp).shippedAmount) }}
      </template>
      <template #col-returnedAmount="{ row }">
        {{ formatMoney((row as StatementResp).returnedAmount) }}
      </template>
      <template #col-receivedAmount="{ row }">
        {{ formatMoney((row as StatementResp).receivedAmount) }}
      </template>
      <template #col-endingBalance="{ row }">
        <strong>{{ formatMoney((row as StatementResp).endingBalance) }}</strong>
      </template>
      <template #col-statusLabel="{ row }">
        <el-tag :type="statusType((row as StatementResp).status)">{{ (row as StatementResp).statusLabel }}</el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :max="3"
              :actions="[
                { label: '详情', onClick: () => openDetail((row as StatementResp).id) },
                {
                  label: '签发',
                  type: 'success',
                  hidden: (row as StatementResp).status !== 'DRAFT',
                  onClick: () => doIssue((row as StatementResp).id),
                },
                {
                  label: '确认',
                  type: 'success',
                  hidden: (row as StatementResp).status !== 'ISSUED',
                  onClick: () => doConfirm((row as StatementResp).id),
                },
                {
                  label: '差异',
                  type: 'warning',
                  hidden: (row as StatementResp).status !== 'ISSUED',
                  onClick: () => openDifferent((row as StatementResp).id),
                },
                { label: '导出', onClick: () => doExport((row as StatementResp).id) },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 新建对账单：选客户 + 期间 → 预览 → 生成 -->
    <el-dialog v-model="genVisible" title="新建对账单" width="900px">
      <el-form :inline="true" :model="genForm" class="search-form">
        <el-form-item label="客户" required>
          <el-select v-model="genForm.customerId" placeholder="请选择客户" filterable style="width: 200px" @change="runPreview">
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对账期间" required>
          <el-date-picker
            v-model="genRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            @change="runPreview"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runPreview">预览</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions :column="4" border size="small" class="summary">
        <el-descriptions-item label="期初应收">{{ formatMoney(preview?.openingBalance) }}</el-descriptions-item>
        <el-descriptions-item label="本期出货">{{ formatMoney(preview?.shippedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="退货/退厂">{{ formatMoney(preview?.returnedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="本期收款">{{ formatMoney(preview?.receivedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="期末应收" :span="4">
          <strong>{{ formatMoney(preview?.endingBalance) }}</strong>
        </el-descriptions-item>
      </el-descriptions>

      <el-table :data="preview?.lines || []" max-height="320" size="small" border class="lines">
        <el-table-column prop="bizDate" label="日期" width="110" />
        <el-table-column prop="docNo" label="单号" width="150" />
        <el-table-column prop="materialNo" label="物料编号" width="110" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="unitPrice" label="单价" width="90" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column prop="lineTypeLabel" label="类型" width="80" />
      </el-table>

      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!preview" @click="submitGenerate">生成对账单</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="'对账单 ' + (current?.stmtNo || '')" width="920px">
      <div v-loading="detailLoading" element-loading-text="加载中…" class="statement-detail__body">
      <el-descriptions :column="4" border size="small" class="summary">
        <el-descriptions-item label="客户">{{ current?.customerName }}</el-descriptions-item>
        <el-descriptions-item label="期间">{{ formatDate(current?.startDate) }} ~ {{ formatDate(current?.endDate) }}</el-descriptions-item>
        <el-descriptions-item label="期初应收">{{ formatMoney(current?.openingBalance) }}</el-descriptions-item>
        <el-descriptions-item label="期末应收"><strong>{{ formatMoney(current?.endingBalance) }}</strong></el-descriptions-item>
        <el-descriptions-item label="出货">{{ formatMoney(current?.shippedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="退货/退厂">{{ formatMoney(current?.returnedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="收款">{{ formatMoney(current?.receivedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ current?.statusLabel }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="current?.lines || []" max-height="360" size="small" border class="lines">
        <el-table-column prop="bizDate" label="日期" width="110" />
        <el-table-column prop="docNo" label="单号" width="150" />
        <el-table-column prop="customerOrderNo" label="客户订单" width="120" />
        <el-table-column prop="materialNo" label="物料编号" width="110" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" />
        <el-table-column prop="spec" label="规格" min-width="120" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="unitPrice" label="单价" width="90" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column prop="lineTypeLabel" label="类型" width="80" />
      </el-table>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="current?.status === 'DRAFT'" type="success" @click="doIssue(current.id)">签发</el-button>
        <el-button v-if="current?.status === 'ISSUED'" type="success" @click="doConfirm(current.id)">客户确认</el-button>
        <el-button v-if="current?.status === 'ISSUED'" type="warning" @click="openDifferent(current.id)">标记差异</el-button>
        <el-button v-if="current" type="primary" @click="doExport(current.id)">导出</el-button>
      </template>
    </el-dialog>

    <!-- 差异说明 -->
    <el-dialog v-model="diffVisible" title="标记差异" width="420px">
      <el-input v-model="diffReason" type="textarea" :rows="3" placeholder="请填写差异说明" />
      <template #footer>
        <el-button @click="diffVisible = false">取消</el-button>
        <el-button type="warning" @click="submitDifferent">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import { useTable } from '@/composables/useTable';
import { statementApi, customerApi } from '@/api/biz';
import type { CustomerResp, StatementResp, StatementQuery, StatementPreviewResp } from '@/types/biz';

const customerOptions = ref<CustomerResp[]>([]);

const searchForm = reactive<StatementQuery>({ customerId: undefined, status: undefined });
const {
  loading,
  list,
  total,
  pageNum,
  pageSize,
  reload,
  reset,
  onPageChange,
  onSizeChange,
} = useTable<StatementResp>((params) => statementApi.page({ ...params, ...searchForm } as never), {});

function handleReset(): void {
  Object.assign(searchForm, { customerId: undefined, status: undefined });
  reset();
}

const columns: ProTableColumn[] = [
  { prop: 'stmtNo', label: '对账单号', width: 170, showOverflowTooltip: true },
  { prop: 'customerName', label: '客户', minWidth: 160, showOverflowTooltip: true },
  { prop: 'period', label: '对账期间', width: 220, slot: true },
  { prop: 'openingBalance', label: '期初', width: 110, align: 'right', slot: true },
  { prop: 'shippedAmount', label: '出货', width: 110, align: 'right', slot: true },
  { prop: 'returnedAmount', label: '退货', width: 110, align: 'right', slot: true },
  { prop: 'receivedAmount', label: '收款', width: 110, align: 'right', slot: true },
  { prop: 'endingBalance', label: '期末应收', width: 130, align: 'right', slot: true },
  { prop: 'statusLabel', label: '状态', width: 90, slot: true },
];

function formatMoney(v?: number): string {
  if (v == null) return '';
  return '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function formatDate(v?: string): string {
  return v ? v.slice(0, 10) : '';
}
function statusType(status?: string): 'success' | 'primary' | 'warning' | 'info' {
  return status === 'CONFIRMED'
    ? 'success'
    : status === 'ISSUED'
      ? 'primary'
      : status === 'DIFFERENT'
        ? 'warning'
        : 'info';
}

/* ---------------- 生成对账单 ---------------- */
const genVisible = ref(false);
const genForm = reactive<{ customerId?: string; startDate?: string; endDate?: string }>({});
const genRange = ref<[string, string] | null>(null);
const preview = ref<StatementPreviewResp | null>(null);

function openGenerate(): void {
  Object.assign(genForm, { customerId: undefined, startDate: undefined, endDate: undefined });
  genRange.value = null;
  preview.value = null;
  genVisible.value = true;
}

async function runPreview(): Promise<void> {
  if (!genForm.customerId || !genRange.value) {
    preview.value = null;
    return;
  }
  genForm.startDate = genRange.value[0];
  genForm.endDate = genRange.value[1];
  preview.value = await statementApi.preview({
    customerId: genForm.customerId,
    startDate: genForm.startDate,
    endDate: genForm.endDate,
  });
}

async function submitGenerate(): Promise<void> {
  if (!genForm.customerId || !genForm.startDate || !genForm.endDate) {
    ElMessage.warning('请选择客户与对账期间');
    return;
  }
  await statementApi.generate(genForm);
  ElMessage.success('已生成对账单');
  genVisible.value = false;
  reload();
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false);
const detailLoading = ref(false);
const current = ref<StatementResp | null>(null);

async function openDetail(id: string): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    current.value = await statementApi.detail(id);
  } finally {
    detailLoading.value = false;
  }
}

/* ---------------- 状态流转 ---------------- */
async function doIssue(id: string): Promise<void> {
  await statementApi.issue(id);
  ElMessage.success('已签发');
  detailVisible.value = false;
  reload();
}

async function doConfirm(id: string): Promise<void> {
  try {
    await ElMessageBox.prompt('确认无误请填写确认说明（可留空）', '客户确认', { inputType: 'textarea' });
  } catch {
    return;
  }
  await statementApi.confirm(id, '');
  ElMessage.success('已确认');
  detailVisible.value = false;
  reload();
}

const diffVisible = ref(false);
const diffId = ref<string | null>(null);
const diffReason = ref('');

function openDifferent(id: string): void {
  diffId.value = id;
  diffReason.value = '';
  diffVisible.value = true;
}
async function submitDifferent(): Promise<void> {
  if (!diffReason.value.trim()) {
    ElMessage.warning('请填写差异说明');
    return;
  }
  await statementApi.markDifferent(diffId.value!, diffReason.value);
  ElMessage.success('已标记差异');
  diffVisible.value = false;
  detailVisible.value = false;
  reload();
}

function doExport(id: string): void {
  statementApi.export(id);
}

onMounted(async () => {
  customerOptions.value = await customerApi.search('', 200);
});
</script>
