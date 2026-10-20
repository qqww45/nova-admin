<script setup lang="ts">
/**
 * 客户出货汇总（M5）。
 *
 * <p>一行回答四个问题：这个客户下了多少、出了多少、还欠多少、值多少钱。
 * 优先做这一张而不是做一堆图表，是因为它同时覆盖了跟单（催着要货）
 * 和财务（对账收钱）两个角色每周都要问的事。
 */
import { computed, onMounted, ref, shallowRef } from 'vue';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { importExportApi, reportApi } from '@/api/biz';
import type { CustomerSummaryResp } from '@/types/biz';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

const loading = ref(false);
const rows = shallowRef<CustomerSummaryResp[]>([]);
const dateRange = ref<[string, string] | null>(null);

const columns: ProTableColumn[] = [
  { prop: 'customerName', label: '客户', minWidth: 200, align: 'left', showOverflowTooltip: true },
  { prop: 'orderCount', label: '订单数', width: 100, align: 'right' },
  { prop: 'itemCount', label: '明细行', width: 100, align: 'right' },
  { prop: 'qtyOrdered', label: '下单总量', width: 130, align: 'right', slot: true },
  { prop: 'qtyShipped', label: '已出货', width: 130, align: 'right', slot: true },
  { prop: 'qtyOpen', label: '未出货', width: 130, align: 'right', slot: true },
  {
    prop: 'amountTaxIncluded',
    label: '价税合计',
    width: 150,
    align: 'right',
    slot: true,
  },
  { prop: 'progress', label: '出货率', width: 110, align: 'center', slot: true },
];

/** 合计行 */
const summary = computed(() => {
  const total = rows.value.reduce(
    (acc, row) => {
      acc.qtyOrdered += Number(row.qtyOrdered ?? 0);
      acc.qtyShipped += Number(row.qtyShipped ?? 0);
      acc.qtyOpen += Number(row.qtyOpen ?? 0);
      acc.amountTaxIncluded += Number(row.amountTaxIncluded ?? 0);
      return acc;
    },
    { qtyOrdered: 0, qtyShipped: 0, qtyOpen: 0, amountTaxIncluded: 0 },
  );
  return total;
});

/** 出货率（用于标红"欠货多"的客户） */
function progressOf(row: CustomerSummaryResp): number {
  const ordered = Number(row.qtyOrdered ?? 0);
  if (ordered === 0) {
    return 0;
  }
  return Math.round((Number(row.qtyShipped ?? 0) / ordered) * 100);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await reportApi.customerSummary(dateRange.value?.[0], dateRange.value?.[1]);
  } finally {
    loading.value = false;
  }
}

/**
 * 导出当前汇总。
 *
 * <p>走的是出货明细账导出：汇总只有十来行，真正需要发出去对账的是明细，
 * 光给一张汇总表对方没法核。
 */
async function exportLedger(): Promise<void> {
  await importExportApi.export('SHIPMENT', 'LEGACY', undefined, dateRange.value?.[0], dateRange.value?.[1]);
  ElMessage.success('导出已开始');
}

onMounted(load);
</script>

<template>
  <div class="report-page">
    <ProTable
      :columns="columns"
      :data="rows"
      :total="rows.length"
      :loading="loading"
      :page-num="1"
      :page-size="rows.length || 1"
      column-drag
      column-drag-key="biz:report"
    >
      <template #search>
        <el-form-item label="下单时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" @click="load">查询</el-button>
      </template>

      <template #toolbar>
        <el-button
          v-if="userStore.hasPermission('biz:report:export')"
          :icon="Download"
          @click="exportLedger"
        >
          导出出货明细账
        </el-button>
      </template>

      <template #col-qtyOrdered="{ row }">
        {{ Number((row as CustomerSummaryResp).qtyOrdered ?? 0).toLocaleString() }}
      </template>
      <template #col-qtyShipped="{ row }">
        {{ Number((row as CustomerSummaryResp).qtyShipped ?? 0).toLocaleString() }}
      </template>
      <template #col-qtyOpen="{ row }">
        <span :class="{ 'report-page__open': Number((row as CustomerSummaryResp).qtyOpen ?? 0) > 0 }">
          {{ Number((row as CustomerSummaryResp).qtyOpen ?? 0).toLocaleString() }}
        </span>
      </template>
      <template #col-amountTaxIncluded="{ row }">
        {{ Number((row as CustomerSummaryResp).amountTaxIncluded ?? 0).toFixed(2) }}
      </template>
      <template #col-progress="{ row }">
        <el-progress
          :percentage="progressOf(row as CustomerSummaryResp)"
          :stroke-width="10"
          :show-text="true"
        />
      </template>
    </ProTable>

    <el-card shadow="never" class="report-page__summary">
      <el-descriptions :column="4" border size="small" title="合计">
        <el-descriptions-item label="下单总量">
          {{ summary.qtyOrdered.toLocaleString() }}
        </el-descriptions-item>
        <el-descriptions-item label="已出货">
          {{ summary.qtyShipped.toLocaleString() }}
        </el-descriptions-item>
        <el-descriptions-item label="未出货">
          {{ summary.qtyOpen.toLocaleString() }}
        </el-descriptions-item>
        <el-descriptions-item label="价税合计">
          {{ summary.amountTaxIncluded.toFixed(2) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.report-page {
  &__open {
    color: var(--el-color-warning);
    font-weight: 600;
  }

  &__summary {
    margin-top: 16px;
  }
}
</style>
