<template>
  <div class="app-container">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      expandable
      empty-description="调整时间范围或清空筛选后再试；仍为空时，请到「同步批次」确认采集器是否已上报该期间数据"
      column-drag
      column-drag-key="kd:outbound-data"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @expand-change="onExpandChange"
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
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="单据编号 / 客户名称"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="账套">
          <el-input v-model="searchForm.acctId" placeholder="账套 ID" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="单据状态">
          <el-select v-model="searchForm.docStatus" placeholder="全部" clearable style="width: 130px">
            <el-option label="创建" value="A" />
            <el-option label="审核中" value="B" />
            <el-option label="已审核" value="C" />
            <el-option label="重新审核" value="D" />
          </el-select>
        </el-form-item>
        <el-form-item label="作废">
          <el-select v-model="searchForm.cancelled" placeholder="全部" clearable style="width: 110px">
            <el-option label="正常" :value="0" />
            <el-option label="已作废" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="失效">
          <el-select v-model="searchForm.invalid" placeholder="全部" clearable style="width: 110px">
            <el-option label="正常" :value="0" />
            <el-option label="已失效" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="searchForm.batchNo" placeholder="上报批次" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="业务员">
          <el-select
            v-model="searchForm.ownerUserId"
            placeholder="全部业务员"
            clearable
            filterable
            style="width: 140px"
          >
            <el-option v-for="o in ownerOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </template>
      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </template>

      <template #col-billNo="{ row }">
        <span class="bill-no">{{ (row as KdOutboundDataResp).billNo || '—' }}</span>
        <el-tooltip
          v-if="(row as KdOutboundDataResp).remark"
          :content="(row as KdOutboundDataResp).remark!"
          placement="top"
        >
          <el-icon class="remark-mark"><ChatLineSquare /></el-icon>
        </el-tooltip>
      </template>

      <template #col-docStatusLabel="{ row }">
        <el-tag :type="docStatusType((row as KdOutboundDataResp).docStatus)" effect="plain" size="small">
          {{ (row as KdOutboundDataResp).docStatusLabel || (row as KdOutboundDataResp).docStatus || '—' }}
        </el-tag>
      </template>

      <template #col-marks="{ row }">
        <el-tag v-if="(row as KdOutboundDataResp).cancelled === 1" type="danger" size="small">
          已作废
        </el-tag>
        <el-tag v-if="(row as KdOutboundDataResp).invalid === 1" type="info" size="small">
          已失效
        </el-tag>
        <span
          v-if="(row as KdOutboundDataResp).cancelled !== 1 && (row as KdOutboundDataResp).invalid !== 1"
          class="cell-sub"
        >
          —
        </span>
      </template>

      <template #col-amount="{ row }">
        <span class="money">{{ money((row as KdOutboundDataResp).amount) }}</span>
      </template>

      <template #col-lineCount="{ row }">
        <el-tooltip
          v-if="!((row as KdOutboundDataResp).lineCount ?? 0)"
          content="该单无分录数据：对账单会退回表头金额，请检查采集器分录字段映射"
          placement="top"
        >
          <el-tag type="warning" size="small">0 行</el-tag>
        </el-tooltip>
        <span v-else>{{ (row as KdOutboundDataResp).lineCount }}</span>
      </template>

      <template #expand="{ row }">
        <div v-loading="isLinesLoading(row)" class="expand-body">
          <div class="expand-meta">
            <span>金蝶 FID：<b>{{ (row as KdOutboundDataResp).kdFid }}</b></span>
            <span>客户内码：<b>{{ (row as KdOutboundDataResp).customerFid || '—' }}</b></span>
            <span>金蝶修改时间：<b>{{ (row as KdOutboundDataResp).kdModifyTime || '—' }}</b></span>
            <span>最近批次：<b>{{ (row as KdOutboundDataResp).batchNo || '—' }}</b></span>
            <span>同步时间：<b>{{ (row as KdOutboundDataResp).syncAt || '—' }}</b></span>
          </div>
          <el-table
            v-if="linesOf(row).length"
            :data="linesOf(row)"
            size="small"
            border
            class="expand-lines"
          >
            <el-table-column prop="materialNumber" label="物料编码" width="130" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格型号" width="120" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="70" />
            <el-table-column label="数量" width="100" align="right">
              <template #default="{ row: line }">{{ qty((line as KdOutboundLineDataResp).qty) }}</template>
            </el-table-column>
            <el-table-column label="单价" width="110" align="right">
              <template #default="{ row: line }">{{ money((line as KdOutboundLineDataResp).price) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row: line }">{{ money((line as KdOutboundLineDataResp).amount) }}</template>
            </el-table-column>
            <el-table-column prop="srcOrderNo" label="源单号" width="140" show-overflow-tooltip />
            <el-table-column prop="customerPo" label="客户PO" width="130" show-overflow-tooltip />
            <el-table-column prop="entryId" label="分录内码" width="120" show-overflow-tooltip />
            <el-table-column label="标记" width="80">
              <template #default="{ row: line }">
                <el-tag v-if="(line as KdOutboundLineDataResp).invalid === 1" type="info" size="small">
                  已失效
                </el-tag>
                <span v-else class="cell-sub">—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-alert
            v-else-if="!isLinesLoading(row)"
            title="该单无分录数据"
            description="对账单会退回表头金额。请到「同步配置」检查该账套的分录字段映射，或查看「同步批次」的跳过行数。"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
      </template>
    </ProTable>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ChatLineSquare } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { useTable } from '@/composables/useTable';
import { useKdOwners } from '@/composables/useKdOwners';
import { kdDataApi } from '@/api/kd';
import type { KdOutboundDataQuery, KdOutboundDataResp, KdOutboundLineDataResp } from '@/types/kd';

/** 展开行附加的分录缓存（挂在本行上，翻页/重查后随行销毁） */
interface OutboundRow extends KdOutboundDataResp {
  lines?: KdOutboundLineDataResp[];
  linesLoading?: boolean;
}

const { isAdmin, options: ownerOptions } = useKdOwners();

// el-date-picker 的 daterange + value-format 返回 string[]；清空时是 []
const dateRange = ref<string[]>([]);
const searchForm = reactive<{
  acctId: string;
  keyword: string;
  docStatus?: string;
  cancelled?: number;
  invalid?: number;
  batchNo: string;
  ownerUserId?: string;
}>({ acctId: '', keyword: '', batchNo: '' });

function currentQuery(): KdOutboundDataQuery {
  return {
    acctId: searchForm.acctId || undefined,
    keyword: searchForm.keyword || undefined,
    docStatus: searchForm.docStatus,
    cancelled: searchForm.cancelled,
    invalid: searchForm.invalid,
    batchNo: searchForm.batchNo || undefined,
    ownerUserId: searchForm.ownerUserId,
    dateFrom: dateRange.value?.[0] || undefined,
    dateTo: dateRange.value?.[1] || undefined,
  };
}

const { loading, list, total, pageNum, pageSize, reload, reset, onPageChange, onSizeChange } =
  useTable<KdOutboundDataResp>((params) => kdDataApi.outbounds({ ...params, ...currentQuery() }));

const columns: ProTableColumn[] = [
  { prop: 'billNo', label: '出库单号', minWidth: 180, slot: true, showOverflowTooltip: true },
  { prop: 'bizDate', label: '业务日期', width: 110, formatter: (_, __, value) => dateOnly(value) },
  { prop: 'customerName', label: '客户', minWidth: 180, showOverflowTooltip: true },
  { prop: 'docStatusLabel', label: '单据状态', width: 96, slot: true },
  { prop: 'amount', label: '金额', width: 130, align: 'right', slot: true },
  { prop: 'lineCount', label: '分录行数', width: 96, slot: true },
  { prop: 'marks', label: '标记', width: 130, slot: true },
  { prop: 'acctId', label: '账套', width: 110, showOverflowTooltip: true },
  { prop: 'ownerUserName', label: '业务员', width: 90, showOverflowTooltip: true },
  { prop: 'batchNo', label: '最近批次', width: 170, showOverflowTooltip: true },
  { prop: 'syncAt', label: '同步时间', width: 165 },
];

function handleReset(): void {
  dateRange.value = [];
  Object.assign(searchForm, {
    acctId: '',
    keyword: '',
    docStatus: undefined,
    cancelled: undefined,
    invalid: undefined,
    batchNo: '',
    ownerUserId: undefined,
  });
  reset();
}

/* ---------------- 行展开：懒加载分录 ---------------- */

/** el-table 展开事件有"全量行集合"与"布尔"两种形态（受控 / 非受控），这里统一归一 */
async function onExpandChange(row: unknown, expanded: unknown): Promise<void> {
  const bill = row as OutboundRow;
  const expandedNow = Array.isArray(expanded) ? expanded.includes(row) : Boolean(expanded);
  if (!expandedNow || bill.lines || bill.linesLoading) {
    return;
  }
  bill.linesLoading = true;
  try {
    bill.lines = await kdDataApi.outboundLines(bill.id);
  } finally {
    bill.linesLoading = false;
  }
}

function linesOf(row: unknown): KdOutboundLineDataResp[] {
  return (row as OutboundRow).lines ?? [];
}

function isLinesLoading(row: unknown): boolean {
  return Boolean((row as OutboundRow).linesLoading);
}

/* ---------------- 展示工具 ---------------- */

function money(value?: number | null): string {
  if (value == null) return '—';
  return Number(value).toFixed(2);
}

function qty(value?: number | null): string {
  if (value == null) return '—';
  return Number(value).toFixed(3);
}

function dateOnly(value: unknown): string {
  if (value == null) return '—';
  const text = String(value);
  return text.length > 10 ? text.slice(0, 10) : text;
}

function docStatusType(status?: string): 'success' | 'primary' | 'warning' {
  if (status === 'C') return 'success';
  if (status === 'B' || status === 'D') return 'warning';
  return 'primary';
}
</script>

<style scoped lang="scss">
.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.bill-no {
  font-weight: 600;
}

.remark-mark {
  margin-left: 4px;
  color: var(--admin-text-secondary);
  vertical-align: -2px;
}

.cell-sub {
  font-size: 12px;
  color: var(--admin-text-secondary);
}

.expand-body {
  padding: 4px 8px 8px;
}

.expand-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 24px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--admin-text-secondary);

  b {
    color: var(--admin-text-regular);
    font-weight: 500;
  }
}

.expand-lines {
  width: 100%;
}
</style>
