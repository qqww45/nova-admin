<template>
  <div class="app-container">
    <!-- 数据体检条幅：金额对不上时，第一嫌疑就是这三类不进对账单的单据 -->
    <el-alert
      v-if="health"
      :type="health.healthy ? 'success' : 'warning'"
      :closable="false"
      show-icon
      class="health-bar"
    >
      <template #title>
        <span v-if="health.healthy">
          本期体检（{{ health.dateFrom }} ~ {{ health.dateTo }}）：有效出库单
          {{ health.billCount }} 张，全部有分录、未作废、未失效
        </span>
        <span v-else>
          本期体检（{{ health.dateFrom }} ~ {{ health.dateTo }}）：有效出库单 {{ health.billCount }} 张；
          <b>无分录 {{ health.noLineCount }} 张</b>（对账单退回表头金额）、
          作废 {{ health.cancelledCount }} 张、已失效 {{ health.invalidCount }} 张
          ——这三类单据不进对账单，金额对不上时请到「金蝶同步 → 出库数据」核对标记
        </span>
      </template>
    </el-alert>

    <ProTable
      v-if="!isMobile"
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
            :shortcuts="dateShortcuts"
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
        <el-button type="primary" @click="handleSearch">查询</el-button>
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
        <el-button link type="primary" @click="copySummaryAsText(row as KdStatementSummaryResp)">
          复制报数
        </el-button>
      </template>
    </ProTable>

    <!-- 移动端轻报数（R10）：见客户场景掏手机就能看汇总、复制报数、单客户导出 -->
    <div v-else v-loading="loading" class="mobile-report">
      <div class="mobile-report__quick">
        <el-button
          v-for="s in dateShortcuts"
          :key="s.text"
          size="small"
          :type="activeQuick === s.text ? 'primary' : 'default'"
          plain
          @click="applyQuickRange(s)"
        >
          {{ s.text }}
        </el-button>
        <el-button size="small" :loading="loading" @click="handleSearch">刷新</el-button>
      </div>

      <div v-if="!loading && list.length === 0" class="mobile-report__empty">
        该期间没有出货数据，可切换上方日期范围
      </div>

      <div
        v-for="row in list"
        :key="`${row.acctId}-${row.customerFid}`"
        class="mobile-report__card"
      >
        <div class="mobile-report__row">
          <span class="mobile-report__name">
            {{ row.customerName || '客户' }}
            <span v-if="row.customerNumber" class="mono">({{ row.customerNumber }})</span>
          </span>
          <span class="mobile-report__amount money">¥{{ money(row.totalAmount) }}</span>
        </div>
        <div class="mobile-report__meta">
          <span>{{ row.acctId || '—' }}</span>
          <span>{{ row.billCount }} 单</span>
          <span class="mono">{{ row.firstDate || '—' }} ~ {{ row.lastDate || '—' }}</span>
        </div>
        <div class="mobile-report__actions">
          <el-button link type="primary" size="small" @click="openDetail(row)">明细</el-button>
          <el-button link type="primary" size="small" @click="copySummaryAsText(row)">复制报数</el-button>
          <el-button
            v-if="canExport"
            link
            type="primary"
            size="small"
            :loading="exportingDetail"
            @click="exportCustomer(row.acctId, row.customerFid)"
          >
            导出
          </el-button>
        </div>
      </div>

      <el-pagination
        v-if="total > pageSize"
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :pager-count="5"
        small
        class="mobile-report__pager"
        @current-change="onPageChange"
      />
    </div>

    <el-drawer v-model="detailVisible" :title="detailTitle" :size="isMobile ? '100%' : '70%'">
      <div v-loading="detailLoading" class="detail-body">
        <div class="detail-head">
          <span>出库单 <b>{{ detail?.billCount ?? 0 }}</b> 张</span>
          <span>分录 <b>{{ detail?.lineCount ?? 0 }}</b> 行</span>
          <span>
            合计 <b class="money">{{ money(detail?.totalAmount) }}</b>
          </span>
          <span class="cell-sub">{{ detail?.dateFrom }} ~ {{ detail?.dateTo }}</span>
          <span class="detail-head__spacer" />
          <el-button
            v-if="canExport"
            type="primary"
            size="small"
            :loading="exportingDetail"
            @click="exportCurrentCustomer"
          >
            导出该客户对账单
          </el-button>
   70|        </div>

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
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { useTable } from '@/composables/useTable';
import { useIsMobile } from '@/composables/useIsMobile';
import { useUserStore } from '@/store/user';
import { kdStatementApi } from '@/api/kd';
import type {
  KdStatementBillResp,
  KdStatementDetailResp,
  KdStatementHealthResp,
  KdStatementLineResp,
  KdStatementQuery,
  KdStatementSummaryResp,
} from '@/types/kd';

const userStore = useUserStore();
const canExport = computed(() => userStore.hasPermission('kd:statement:export'));
const { isMobile } = useIsMobile();

// el-date-picker 的 daterange + value-format 返回 string[]；清空时是 []
const dateRange = ref<string[]>([]);
const searchForm = ref<{ acctId: string; keyword: string }>({ acctId: '', keyword: '' });
const exporting = ref(false);

/** 对账基本按月发生：本月 / 上月 / 近 30 天 / 本季度一键选定，免手点日历 */
const dateShortcuts = [
  {
    text: '本月',
    value: (): [Date, Date] => {
      const now = new Date();
      return [new Date(now.getFullYear(), now.getMonth(), 1), now];
    },
  },
  {
    text: '上月',
    value: (): [Date, Date] => {
      const now = new Date();
      return [
        new Date(now.getFullYear(), now.getMonth() - 1, 1),
        new Date(now.getFullYear(), now.getMonth(), 0),
      ];
    },
  },
  {
    text: '近 30 天',
    value: (): [Date, Date] => {
      const now = new Date();
      return [new Date(now.getTime() - 29 * 86_400_000), now];
    },
  },
  {
    text: '本季度',
    value: (): [Date, Date] => {
      const now = new Date();
      const quarterStart = new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1);
      return [quarterStart, now];
    },
  },
];

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

/* ---------------- 数据体检条幅 ---------------- */
const health = ref<KdStatementHealthResp | null>(null);
const healthLoading = ref(false);

async function loadHealth(): Promise<void> {
  healthLoading.value = true;
  try {
    health.value = await kdStatementApi.health(currentQuery());
  } catch {
    // 体检失败不阻塞对账单主流程，只是少一条提示
    health.value = null;
  } finally {
    healthLoading.value = false;
  }
}

function handleSearch(): void {
  reload();
  void loadHealth();
}

const columns: ProTableColumn[] = [
  { prop: 'customerName', label: '客户', minWidth: 200, showOverflowTooltip: true },
  { prop: 'customerNumber', label: '客户编码', width: 120, showOverflowTooltip: true },
  { prop: 'acctId', label: '账套', width: 140, showOverflowTooltip: true },
  { prop: 'billCount', label: '出库单数', width: 100, align: 'right' },
  { prop: 'totalAmount', label: '金额合计', width: 150, align: 'right', slot: true },
  { prop: 'period', label: '起 / 止', width: 140, slot: true },
  { prop: 'actions', label: '操作', width: 160, fixed: 'right', slot: true },
];

const detailVisible = ref(false);
const detailLoading = ref(false);
const exportingDetail = ref(false);
const detail = ref<KdStatementDetailResp | null>(null);
const detailTitle = computed(() =>
  detail.value?.customerName ? `对账明细 · ${detail.value.customerName}` : '对账明细',
);

function handleReset(): void {
  dateRange.value = [];
  activeQuick.value = '';
  searchForm.value = { acctId: '', keyword: '' };
  reset();
  void loadHealth();
}

/**
 * 汇总行一键复制成报数文本：业务员在微信里给客户报"这段时间出了多少货"，
 * 不用打开导出文件再手抄一遍
 */
async function copySummaryAsText(row: KdStatementSummaryResp): Promise<void> {
  const range = row.firstDate && row.lastDate ? `${row.firstDate} ~ ${row.lastDate}` : '本期';
  const namePart = row.customerNumber
    ? `${row.customerName || '客户'}（编码 ${row.customerNumber}）`
    : row.customerName || '客户';
  const text =
    `【${namePart}】${range} 出库 ${row.billCount} 单，合计金额 ¥${money(row.totalAmount)}` +
    (row.acctId ? `（账套 ${row.acctId}）` : '');
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制，可直接粘贴发给客户');
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请手动记录');
  }
}

async function openDetail(row: KdStatementSummaryResp): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
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

/** 业务员最常见的对账动作是"给某一个客户出单"：明细页一键导出，不用回列表拼查询条件 */
async function exportCurrentCustomer(): Promise<void> {
  if (!detail.value?.customerFid) return;
  await exportCustomer(detail.value.acctId, detail.value.customerFid);
}

/** 单客户导出（桌面明细抽屉与移动卡片共用） */
async function exportCustomer(acctId?: string, customerFid?: string): Promise<void> {
  if (!customerFid) return;
  exportingDetail.value = true;
  try {
    await kdStatementApi.export({
      ...currentQuery(),
      acctId: acctId || currentQuery().acctId,
      customerFid,
    });
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  } finally {
    exportingDetail.value = false;
  }
}

/* ---------------- 移动端快捷日期 ---------------- */

const activeQuick = ref('');

function isQuickActive(text: string): boolean {
  return activeQuick.value === text;
}

/** 移动端一键切换常用期间（复用桌面快捷的同一组计算） */
function applyQuickRange(shortcut: { text: string; value: () => [Date, Date] }): void {
  const range = shortcut.value();
  const fmt = (d: Date): string =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  dateRange.value = [fmt(range[0]), fmt(range[1])];
  activeQuick.value = shortcut.text;
  handleSearch();
}

/** 金额统一两位小数；接口返回是 number（元） */
function money(value?: number | null): string {
  if (value == null) return '0.00';
  return Number(value).toFixed(2);
}

onMounted(() => {
  void loadHealth();
});
</script>

<style scoped lang="scss">
.health-bar {
  margin-bottom: 12px;
}

/* 移动端轻报数卡片 */
.mobile-report {
  &__quick {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
  }

  &__empty {
    padding: 24px 0;
    font-size: 13px;
    color: var(--admin-text-secondary);
    text-align: center;
  }

  &__card {
    padding: 12px;
    margin-bottom: 10px;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 10px;
  }

  &__row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
  }

  &__name {
    font-size: 14px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    .mono {
      font-size: 12px;
      font-weight: 400;
    }
  }

  &__amount {
    font-size: 16px;
    white-space: nowrap;
  }

  &__meta {
    display: flex;
    gap: 12px;
    margin-top: 4px;
    font-size: 12px;
    color: var(--admin-text-secondary);
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 4px;
    margin-top: 4px;
  }

  &__pager {
    display: flex;
    justify-content: center;
    margin-top: 8px;
  }
}

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

  &__spacer {
    flex: 1;
  }
}
</style>
