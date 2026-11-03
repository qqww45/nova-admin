<template>
  <div class="app-container">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      empty-description="按物料编码 / 源单号 / 客户PO 检索；仍为空时，请到「同步批次」确认分录是否已上报"
      column-drag
      column-drag-key="kd:line-data"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="物料编码 / 名称 / 源单号 / 客户PO"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="出库单 FID">
          <el-input
            v-model="searchForm.outboundFid"
            placeholder="金蝶单据 FID"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="账套">
          <el-input v-model="searchForm.acctId" placeholder="账套 ID" clearable style="width: 140px" />
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
        <el-form-item label="同步日期">
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
        <span class="bill-no">{{ (row as KdOutboundLineDataResp).billNo || '—' }}</span>
      </template>

      <template #col-amount="{ row }">
        <span class="money">{{ money((row as KdOutboundLineDataResp).amount) }}</span>
      </template>

      <template #col-invalid="{ row }">
        <el-tag v-if="(row as KdOutboundLineDataResp).invalid === 1" type="info" size="small">
          已失效
        </el-tag>
        <span v-else class="cell-sub">—</span>
      </template>
    </ProTable>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { useTable } from '@/composables/useTable';
import { useKdOwners } from '@/composables/useKdOwners';
import { kdDataApi } from '@/api/kd';
import type { KdLineDataQuery, KdOutboundLineDataResp } from '@/types/kd';

const { isAdmin, options: ownerOptions } = useKdOwners();

const dateRange = ref<string[]>([]);
const searchForm = reactive<{
  acctId: string;
  keyword: string;
  outboundFid: string;
  invalid?: number;
  batchNo: string;
  ownerUserId?: string;
}>({ acctId: '', keyword: '', outboundFid: '', batchNo: '' });

function currentQuery(): KdLineDataQuery {
  return {
    acctId: searchForm.acctId || undefined,
    keyword: searchForm.keyword || undefined,
    outboundFid: searchForm.outboundFid || undefined,
    invalid: searchForm.invalid,
    batchNo: searchForm.batchNo || undefined,
    ownerUserId: searchForm.ownerUserId,
    syncDateFrom: dateRange.value?.[0] || undefined,
    syncDateTo: dateRange.value?.[1] || undefined,
  };
}

const { loading, list, total, pageNum, pageSize, reload, reset, onPageChange, onSizeChange } =
  useTable<KdOutboundLineDataResp>((params) => kdDataApi.lines({ ...params, ...currentQuery() }));

const columns: ProTableColumn[] = [
  { prop: 'billNo', label: '出库单号', minWidth: 160, slot: true, showOverflowTooltip: true },
  { prop: 'materialNumber', label: '物料编码', width: 130, showOverflowTooltip: true },
  { prop: 'materialName', label: '物料名称', minWidth: 160, showOverflowTooltip: true },
  { prop: 'spec', label: '规格型号', width: 120, showOverflowTooltip: true },
  { prop: 'unit', label: '单位', width: 70 },
  { prop: 'qty', label: '数量', width: 100, align: 'right', formatter: qtyFormatter },
  { prop: 'price', label: '单价', width: 110, align: 'right', formatter: priceFormatter },
  { prop: 'amount', label: '金额', width: 120, align: 'right', slot: true },
  { prop: 'srcOrderNo', label: '源单号', width: 140, showOverflowTooltip: true },
  { prop: 'customerPo', label: '客户PO', width: 130, showOverflowTooltip: true },
  { prop: 'invalid', label: '标记', width: 80, slot: true },
  { prop: 'acctId', label: '账套', width: 100, showOverflowTooltip: true },
  { prop: 'ownerUserName', label: '业务员', width: 90, showOverflowTooltip: true },
  { prop: 'batchNo', label: '最近批次', width: 160, showOverflowTooltip: true },
  { prop: 'syncAt', label: '同步时间', width: 165 },
];

function handleReset(): void {
  dateRange.value = [];
  Object.assign(searchForm, {
    acctId: '',
    keyword: '',
    outboundFid: '',
    invalid: undefined,
    batchNo: '',
    ownerUserId: undefined,
  });
  reset();
}

function qtyFormatter(_row: Record<string, unknown>, _column: unknown, value: unknown): string {
  return value == null ? '—' : Number(value).toFixed(3);
}

function priceFormatter(_row: Record<string, unknown>, _column: unknown, value: unknown): string {
  return value == null ? '—' : Number(value).toFixed(2);
}

function money(value?: number | null): string {
  if (value == null) return '—';
  return Number(value).toFixed(2);
}
</script>

<style scoped lang="scss">
.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.bill-no {
  font-weight: 500;
}

.cell-sub {
  font-size: 12px;
  color: var(--admin-text-secondary);
}
</style>
