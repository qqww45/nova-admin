<script setup lang="ts">
/**
 * 业务报表（M5 + P1 提效批次）。
 *
 * <p>三个标签页对应业务员每周都要问的三件事：
 * <ul>
 *     <li>客户出货汇总——这个客户下了多少、出了多少、还欠多少、值多少钱；</li>
 *     <li>催单清单——按客户聚合的未交明细（qtyOpen > 0 且未取消），
 *         对着一个客户把该催的单一次看完，打印或截图即可去催生产、回复客户；</li>
 *     <li>客户应收——期初 + 累计出货 − 退货 − 累计收款，按余额倒序，
 *         最近出货/收款时间标注"欠了多久"，催款优先级不凭记忆。</li>
 * </ul>
 */
import { computed, onMounted, ref, shallowRef } from 'vue';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { importExportApi, reportApi, statementApi } from '@/api/biz';
import type {
  CustomerSummaryResp,
  OpenOrderItemResp,
  ReceivableSummaryResp,
} from '@/types/biz';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

/* ---------------- 标签页 ---------------- */
const activeTab = ref('summary');

/* ---------------- 客户出货汇总 ---------------- */
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

/* ---------------- 催单清单 ---------------- */
const openLoading = ref(false);
const openRows = shallowRef<OpenOrderItemResp[]>([]);
const openCustomerFilter = ref('');

const openColumns: ProTableColumn[] = [
  { prop: 'customerName', label: '客户', minWidth: 180, align: 'left', showOverflowTooltip: true },
  { prop: 'orderNo', label: '订单号', width: 160, showOverflowTooltip: true },
  { prop: 'customerOrderNo', label: '客户PO', width: 130, showOverflowTooltip: true },
  { prop: 'model', label: '型号', width: 130, showOverflowTooltip: true },
  { prop: 'materialNo', label: '料号', width: 130, showOverflowTooltip: true },
  { prop: 'qtyOrdered', label: '下单', width: 90, align: 'right' },
  { prop: 'qtyShipped', label: '已出', width: 90, align: 'right' },
  { prop: 'qtyOpen', label: '未出', width: 100, align: 'right', slot: true },
  { prop: 'orderDate', label: '下单时间', width: 120 },
  { prop: 'expectedDeliveryDate', label: '预计交货', width: 120, slot: true },
  { prop: 'salesmanName', label: '业务员', width: 100 },
];

/** 客户过滤在展示层做：数据量万级以内，不用为过滤再发一次请求 */
const filteredOpenRows = computed(() => {
  const keyword = openCustomerFilter.value.trim().toLowerCase();
  if (!keyword) return openRows.value;
  return openRows.value.filter(
    (r) =>
      (r.customerName ?? '').toLowerCase().includes(keyword) ||
      (r.orderNo ?? '').toLowerCase().includes(keyword) ||
      (r.customerOrderNo ?? '').toLowerCase().includes(keyword),
  );
});

const openSummary = computed(() =>
  filteredOpenRows.value.reduce((acc, r) => acc + Number(r.qtyOpen ?? 0), 0),
);

async function loadOpenItems(): Promise<void> {
  openLoading.value = true;
  try {
    openRows.value = await reportApi.openItems();
  } finally {
    openLoading.value = false;
  }
}

/* ---------------- 客户应收 ---------------- */
const receivableLoading = ref(false);
const receivableRows = shallowRef<ReceivableSummaryResp[]>([]);

const receivableColumns: ProTableColumn[] = [
  { prop: 'customerName', label: '客户', minWidth: 200, align: 'left', showOverflowTooltip: true },
  { prop: 'openingBalance', label: '期初应收', width: 120, align: 'right', slot: true },
  { prop: 'shippedAmount', label: '累计出货', width: 120, align: 'right', slot: true },
  { prop: 'returnedAmount', label: '累计退货', width: 120, align: 'right', slot: true },
  { prop: 'receivedAmount', label: '累计收款', width: 120, align: 'right', slot: true },
  { prop: 'endingBalance', label: '期末应收', width: 130, align: 'right', slot: true },
  { prop: 'lastShipmentDate', label: '最近出货', width: 130, slot: true },
  { prop: 'lastReceiptDate', label: '最近收款', width: 130, slot: true },
];

const receivableTotal = computed(() =>
  receivableRows.value.reduce((acc, r) => acc + Number(r.endingBalance ?? 0), 0),
);

async function loadReceivable(): Promise<void> {
  receivableLoading.value = true;
  try {
    receivableRows.value = await statementApi.receivableSummary();
  } finally {
    receivableLoading.value = false;
  }
}

/** 金额统一两位小数 */
function money(value?: number | null): string {
  return Number(value ?? 0).toFixed(2);
}

function fmtDate(value?: string): string {
  if (!value) return '—';
  return value.length > 10 ? value.slice(0, 10) : value;
}

/** 预计交货已过期（用于标红该催的单） */
function isOverdue(expected?: string): boolean {
  return Boolean(expected) && fmtDate(expected) < new Date().toISOString().slice(0, 10);
}

/** 切标签页时懒加载，避免一次拉三份数据 */
function onTabChange(tab: string | number): void {
  if (tab === 'open-items' && openRows.value.length === 0) void loadOpenItems();
  if (tab === 'receivable' && receivableRows.value.length === 0) void loadReceivable();
}

onMounted(load);
</script>

<template>
  <div class="report-page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="客户出货汇总" name="summary">
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
      </el-tab-pane>

      <el-tab-pane label="催单清单" name="open-items">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="全部未交订单明细（未取消且还有未出数量），行序先按客户再按下单时间——对着一个客户把该催的单一次看完。"
          class="report-page__tip"
        />
        <ProTable
          :columns="openColumns"
          :data="filteredOpenRows"
          :total="filteredOpenRows.length"
          :loading="openLoading"
          :page-num="1"
          :page-size="filteredOpenRows.length || 1"
          column-drag
          column-drag-key="biz:report-open"
        >
          <template #search>
            <el-form-item label="关键字">
              <el-input
                v-model="openCustomerFilter"
                placeholder="客户 / 订单号 / 客户PO"
                clearable
                style="width: 220px"
              />
            </el-form-item>
          </template>
          <template #search-buttons>
            <el-button type="primary" @click="loadOpenItems">刷新</el-button>
          </template>
          <template #toolbar>
            <span class="report-page__hint">未出合计：{{ openSummary.toLocaleString() }}</span>
          </template>
          <template #col-qtyOpen="{ row }">
            <span class="report-page__open">{{ Number((row as OpenOrderItemResp).qtyOpen ?? 0).toLocaleString() }}</span>
          </template>
          <template #col-expectedDeliveryDate="{ row }">
            <span :class="{ 'report-page__late': isOverdue((row as OpenOrderItemResp).expectedDeliveryDate) }">
              {{ fmtDate((row as OpenOrderItemResp).expectedDeliveryDate) }}
            </span>
          </template>
        </ProTable>
      </el-tab-pane>

      <el-tab-pane label="客户应收" name="receivable">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="口径与对账单一致：期末应收 = 期初 + 累计出货 − 退货 − 累计收款（实时计算，不含金蝶数据）。按余额倒序，催款优先级从上往下。"
          class="report-page__tip"
        />
        <ProTable
          :columns="receivableColumns"
          :data="receivableRows"
          :total="receivableRows.length"
          :loading="receivableLoading"
          :page-num="1"
          :page-size="receivableRows.length || 1"
          column-drag
          column-drag-key="biz:report-receivable"
        >
          <template #search-buttons>
            <el-button type="primary" @click="loadReceivable">刷新</el-button>
          </template>
          <template #toolbar>
            <span class="report-page__hint">应收总额：¥{{ money(receivableTotal) }}</span>
          </template>
          <template #col-openingBalance="{ row }">
            {{ money((row as ReceivableSummaryResp).openingBalance) }}
          </template>
          <template #col-shippedAmount="{ row }">
            {{ money((row as ReceivableSummaryResp).shippedAmount) }}
          </template>
          <template #col-returnedAmount="{ row }">
            {{ money((row as ReceivableSummaryResp).returnedAmount) }}
          </template>
          <template #col-receivedAmount="{ row }">
            {{ money((row as ReceivableSummaryResp).receivedAmount) }}
          </template>
          <template #col-endingBalance="{ row }">
            <span :class="{ 'report-page__open': Number((row as ReceivableSummaryResp).endingBalance ?? 0) > 0 }">
              {{ money((row as ReceivableSummaryResp).endingBalance) }}
            </span>
          </template>
          <template #col-lastShipmentDate="{ row }">
            {{ fmtDate((row as ReceivableSummaryResp).lastShipmentDate) }}
          </template>
          <template #col-lastReceiptDate="{ row }">
            {{ fmtDate((row as ReceivableSummaryResp).lastReceiptDate) }}
          </template>
        </ProTable>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.report-page {
  &__open {
    color: var(--el-color-warning);
    font-weight: 600;
  }

  &__late {
    color: var(--el-color-danger);
  }

  &__summary {
    margin-top: 16px;
  }

  &__tip {
    margin-bottom: 12px;
  }

  &__hint {
    font-size: 13px;
    color: var(--admin-text-secondary);
  }
}
</style>
