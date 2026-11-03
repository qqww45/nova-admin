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
      column-drag-key="kd:statement"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="业务日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            unlink-panels
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item label="客户">
          <el-input
            v-model="searchForm.keyword"
            placeholder="客户名称关键字"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="账套">
          <el-input v-model="searchForm.acctId" placeholder="账套 ID" clearable style="width: 140px" />
        </el-form-item>
      </template>
      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button v-if="canExport" :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </template>

      <template #col-totalAmount="{ row }">
        <span class="money">{{ money((row as KdStatementSummaryResp).totalAmount) }}</span>
      </template>

      <template #col-period="{ row }">
        <div class="cell-sub">{{ (row as KdStatementSummaryResp).firstDate || '—' }}</div>
        <div class="cell-sub">{{ (row as KdStatementSummaryResp).lastDate || '—' }}</div>
      </template>

      <template #col-actions="{ row }">
        <el-button link type="primary" @click="openDetail(row as KdStatementSummaryResp)">
          查看明细
        </el-button>
        <el-button
          v-if="canExport"
          link
          type="primary"
          :loading="exportingRow === (row as KdStatementSummaryResp).customerFid"
          @click="handleExportRow(row as KdStatementSummaryResp)"
        >
          导出
        </el-button>
      </template>
    </ProTable>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="70%">
      <div v-loading="detailLoading" class="detail-body">
        <div class="detail-head">
          <span>出库单 <b>{{ detail?.billCount ?? 0 }}</b> 张</span>
          <span>分录 <b>{{ detail?.lineCount ?? 0 }}</b> 行</span>
          <span>
            合计 <b class="money">{{ money(detail?.totalAmount) }}</b>
          </span>
          <span class="cell-sub">{{ detail?.dateFrom }} ~ {{ detail?.dateTo }}</span>
          <el-button
            v-if="canExport && detail?.customerFid"
            type="primary"
            size="small"
            :loading="exportingRow === detail?.customerFid"
            @click="handleExportDetail"
          >
            导出对账单
          </el-button>
        </div>

        <el-table :data="detail?.bills ?? []" border size="small" row-key="billNo" default-expand-all>
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-table :data="(row as KdStatementBillResp).lines" size="small" border>
                <el-table-column prop="materialNumber" label="物料编码" width="140" />
                <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
                <el-table-column prop="spec" label="规格" width="120" show-overflow-tooltip />
                <el-table-column prop="unit" label="单位" width="80" />
                <el-table-column prop="qty" label="数量" width="100" align="right" />
                <el-table-column label="单价" width="110" align="right">
                  <template #default="{ row: line }">{{ money((line as KdStatementLineResp).price) }}</template>
                </el-table-column>
                <el-table-column label="金额" width="120" align="right">
                  <template #default="{ row: line }">{{ money((line as KdStatementLineResp).amount) }}</template>
                </el-table-column>
                <el-table-column prop="customerPo" label="客户PO" width="130" show-overflow-tooltip />
                <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
              </el-table>
            </template>
          </el-table-column>
          <el-table-column prop="billNo" label="出库单号" width="200" />
          <el-table-column prop="bizDate" label="业务日期" width="120" />
          <el-table-column prop="docStatus" label="单据状态" width="100" />
          <el-table-column label="金额" width="140" align="right">
            <template #default="{ row }">{{ money((row as KdStatementBillResp).amount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { useTable } from '@/composables/useTable';
import { useUserStore } from '@/store/user';
import { kdStatementApi } from '@/api/kd';
import type {
  KdStatementBillResp,
  KdStatementDetailResp,
  KdStatementLineResp,
  KdStatementQuery,
  KdStatementSummaryResp,
} from '@/types/kd';

const userStore = useUserStore();
const canExport = computed(() => userStore.hasPermission('kd:statement:export'));

// el-date-picker 的 daterange + value-format 返回 string[]；清空时是 []
const dateRange = ref<string[]>([]);
const searchForm = ref<{ acctId: string; keyword: string }>({ acctId: '', keyword: '' });
const exporting = ref(false);

/** 界面上是日期区间，后端要的是两个独立参数 */
function currentQuery(): KdStatementQuery {
  return {
    acctId: searchForm.value.acctId || undefined,
    keyword: searchForm.value.keyword || undefined,
    dateFrom: dateRange.value?.[0] || undefined,
    dateTo: dateRange.value?.[1] || undefined,
  };
}

const { loading, list, total, pageNum, pageSize, reload, reset, onPageChange, onSizeChange } =
  useTable<KdStatementSummaryResp>((params) => kdStatementApi.summary({ ...params, ...currentQuery() }));

const columns: ProTableColumn[] = [
  { prop: 'customerName', label: '客户', minWidth: 220, showOverflowTooltip: true },
  { prop: 'acctId', label: '账套', width: 140, showOverflowTooltip: true },
  { prop: 'billCount', label: '出库单数', width: 100, align: 'right' },
  { prop: 'totalAmount', label: '金额合计', width: 150, align: 'right', slot: true },
  { prop: 'period', label: '起 / 止', width: 140, slot: true },
  { prop: 'actions', label: '操作', width: 140, fixed: 'right', slot: true },
];

const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<KdStatementDetailResp | null>(null);
/** 抽屉打开时的汇总行（导出对账单要复用它的客户 + 账套 + 日期条件） */
const detailRow = ref<KdStatementSummaryResp | null>(null);
/** 正在导出的客户内码（行内按钮 loading） */
const exportingRow = ref<string>('');
const detailTitle = computed(() =>
  detail.value?.customerName ? `对账明细 · ${detail.value.customerName}` : '对账明细',
);

function handleReset(): void {
  dateRange.value = [];
  searchForm.value = { acctId: '', keyword: '' };
  reset();
}

async function openDetail(row: KdStatementSummaryResp): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
  detailRow.value = row;
  try {
    detail.value = await kdStatementApi.detail({
      ...currentQuery(),
      acctId: row.acctId || currentQuery().acctId,
      customerFid: row.customerFid,
    });
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  } finally {
    detailLoading.value = false;
  }
}

async function handleExport(): Promise<void> {
  exporting.value = true;
  try {
    await kdStatementApi.export(currentQuery());
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  } finally {
    exporting.value = false;
  }
}

/** 文件名里的客户名清洗（与浏览器下载兼容） */
function exportFileName(customerName?: string | null): string {
  const range = `${currentQuery().dateFrom || '起'}_${currentQuery().dateTo || '今'}`;
  const cleaned = (customerName || '客户').replace(/[\\/:*?"<>|\s]/g, '');
  return `对账单-${cleaned}-${range}.xlsx`;
}

/** 按汇总行导出该客户对账单（版式与签字样板一致，客户 × 物料分 sheet） */
async function handleExportRow(row: KdStatementSummaryResp): Promise<void> {
  exportingRow.value = row.customerFid || '';
  try {
    await kdStatementApi.export(
      {
        ...currentQuery(),
        acctId: row.acctId || currentQuery().acctId,
        customerFid: row.customerFid,
      },
      exportFileName(row.customerName),
    );
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  } finally {
    exportingRow.value = '';
  }
}

/** 明细抽屉里导出当前客户 */
async function handleExportDetail(): Promise<void> {
  if (!detailRow.value) return;
  await handleExportRow(detailRow.value);
}

/** 金额统一两位小数；接口返回是 number（元） */
function money(value?: number | null): string {
  if (value == null) return '0.00';
  return Number(value).toFixed(2);
}
</script>

<style scoped lang="scss">
.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.cell-sub {
  font-size: 12px;
  color: var(--admin-text-secondary);
}

.detail-body {
  padding: 0 4px 16px;
}

.detail-head {
  display: flex;
  align-items: baseline;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  font-size: 13px;
}
</style>
