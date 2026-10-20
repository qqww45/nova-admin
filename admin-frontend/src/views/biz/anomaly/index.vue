<script setup lang="ts">
/**
 * 异常数据池（M4）。
 *
 * <p>这里的每一行都是"出货已发生、但没匹配上订单"的记录。
 * 它们不是错误，是历史数据缺失的产物（客户下单时没填物料号、订单还没录进系统等），
 * 所以不能简单删掉 —— 删了就少一笔出货，对不上账。
 *
 * <p>修复方式是"补挂订单明细"：给这条出货找到它应该属于的明细，
 * 服务端会按正常出货的同一套规则回写数量与金额，不产生新的口径。
 */
import { reactive, ref, shallowRef } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import { orderApi, shipmentApi } from '@/api/biz';
import { useTable } from '@/composables/useTable';
import type { OrderDetailResp, OrderItemResp, OrderResp, ShipmentResp } from '@/types/biz';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

const { loading, list, total, pageNum, pageSize, reload, onPageChange, onSizeChange } = useTable<
  ShipmentResp
>(
  (params) =>
    shipmentApi.page({ ...params, onlyOrphan: true, ...searchForm }),
  {},
);

/**
 * 搜索条件（强类型，理由同客户页）。
 *
 * <p>异常池只查无源出货，所以这里没有"仅异常池"开关；
 * 保留关键字与日期区间，是因为补挂时通常要按客户或某天集中处理。
 */
const searchForm = reactive<{ keyword: string; customerOrderNo: string }>({
  keyword: '',
  customerOrderNo: '',
});

const columns: ProTableColumn[] = [
  { prop: 'shipDate', label: '出货日期', width: 120, slot: true },
  { prop: 'processOrderNo', label: '加工单号', width: 140, showOverflowTooltip: true },
  { prop: 'customerName', label: '客户', minWidth: 150, align: 'left', showOverflowTooltip: true },
  { prop: 'customerOrderNo', label: '客户订单号', width: 140, showOverflowTooltip: true },
  { prop: 'customerMaterialNo', label: '客户物料号', width: 140, showOverflowTooltip: true },
  { prop: 'model', label: '型号', width: 130, showOverflowTooltip: true },
  { prop: 'qty', label: '数量', width: 90, align: 'right' },
  { prop: 'salesmanName', label: '业务', width: 90 },
  { prop: 'remark', label: '备注', minWidth: 120, align: 'left', showOverflowTooltip: true },
];

/* ---------------- 补挂订单 ---------------- */

const linkVisible = ref(false);
const linkLoading = ref(false);
const current = ref<ShipmentResp | null>(null);
const candidateOrders = shallowRef<OrderResp[]>([]);
const orderItems = shallowRef<OrderItemResp[]>([]);
const selectedOrderId = ref<string>('');
const selectedItemId = ref<string>('');

/**
 * 打开补挂弹窗。
 *
 * <p>候选订单按"客户订单号"反查：这是异常池里最容易对上的键，
 * 因为客户 PO 号通常写在出货单上。查不到再让用户按关键字搜。
 */
async function openLink(row: ShipmentResp): Promise<void> {
  current.value = row;
  selectedOrderId.value = '';
  selectedItemId.value = '';
  orderItems.value = [];
  linkVisible.value = true;
  candidateOrders.value = row.customerOrderNo
    ? (await orderApi.page({ keyword: row.customerOrderNo, pageSize: 20 })).records
    : (await orderApi.page({ keyword: row.customerName ?? '', pageSize: 20 })).records;
}

/** 选中订单后加载其明细，供用户挑一条 */
async function onOrderChange(orderId: string): Promise<void> {
  selectedItemId.value = '';
  if (!orderId) {
    orderItems.value = [];
    return;
  }
  const detail: OrderDetailResp = await orderApi.detail(orderId);
  orderItems.value = detail.items;
}

/** 提交补挂 */
async function submitLink(): Promise<void> {
  if (!current.value || !selectedItemId.value) {
    ElMessage.warning('请选择要关联的订单明细');
    return;
  }
  linkLoading.value = true;
  try {
    await shipmentApi.linkToItem(current.value.id, selectedItemId.value);
    ElMessage.success('已补挂订单，明细出货数与金额已回写');
    linkVisible.value = false;
    await reload();
  } finally {
    linkLoading.value = false;
  }
}

/** 明细是否可挂（未出货数足够） */
function canLink(item: OrderItemResp, qty: number): boolean {
  return Number(item.qtyOpen ?? 0) >= qty;
}

/* ---------------- 作废（确实是不该存在的记录） ---------------- */

async function voidOne(row: ShipmentResp): Promise<void> {
  const { value } = await ElMessageBox.prompt('请填写作废原因（将记录到操作日志）', '作废出货', {
    inputPattern: /\S+/,
    inputErrorMessage: '作废原因不能为空',
  });
  await shipmentApi.voidShipment(row.id, value);
  ElMessage.success('已作废');
  await reload();
}
</script>

<template>
  <div class="anomaly-page">
    <el-alert type="warning" :closable="false" class="anomaly-page__tip">
      <template #title>
        以下出货未匹配到订单明细。请先<span class="anomaly-page__strong">补挂订单</span>；
        确认是重复或错误录入的，再作作废。
      </template>
    </el-alert>

    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      column-drag
      column-drag-key="biz:anomaly"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="加工单号 / 型号 / 料号"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="客户订单号">
          <el-input v-model="searchForm.customerOrderNo" clearable style="width: 180px" />
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
      </template>

      <template #col-shipDate="{ row }">
        {{ String((row as ShipmentResp).shipDate ?? '').slice(0, 10) }}
      </template>

      <template #columns>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :max="2"
              :actions="[
                {
                  label: '补挂订单',
                  type: 'primary',
                  hidden: !userStore.hasPermission('biz:anomaly:fix'),
                  onClick: () => openLink(row as ShipmentResp),
                },
                {
                  label: '作废',
                  type: 'danger',
                  danger: true,
                  divided: true,
                  hidden: !userStore.hasPermission('biz:shipment:void'),
                  onClick: () => voidOne(row as ShipmentResp),
                },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 补挂订单明细 -->
    <el-dialog v-model="linkVisible" title="补挂订单明细" width="720px">
      <el-descriptions :column="2" border size="small" class="anomaly-page__desc">
        <el-descriptions-item label="出货日期">
          {{ String(current?.shipDate ?? '').slice(0, 10) }}
        </el-descriptions-item>
        <el-descriptions-item label="加工单号">{{ current?.processOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ current?.customerName }}</el-descriptions-item>
        <el-descriptions-item label="客户订单号">
          {{ current?.customerOrderNo }}
        </el-descriptions-item>
        <el-descriptions-item label="型号">{{ current?.model }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ current?.qty }}</el-descriptions-item>
      </el-descriptions>

      <el-form label-width="110px" class="anomaly-page__form">
        <el-form-item label="关联订单" required>
          <el-select
            v-model="selectedOrderId"
            filterable
            placeholder="按客户订单号/客户名匹配，也可直接搜索"
            style="width: 100%"
            @change="onOrderChange"
          >
            <el-option
              v-for="order in candidateOrders"
              :key="order.id"
              :label="`${order.orderNo}（${order.customerName ?? ''}）`"
              :value="order.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单明细" required>
          <el-select
            v-model="selectedItemId"
            placeholder="请选择对应明细"
            style="width: 100%"
            :disabled="orderItems.length === 0"
          >
            <el-option
              v-for="item in orderItems"
              :key="item.id"
              :label="`${item.model} · 未出 ${item.qtyOpen} / 下单 ${item.qtyOrdered}`"
              :value="item.id"
              :disabled="!canLink(item, Number(current?.qty ?? 0))"
            />
          </el-select>
          <div v-if="orderItems.length" class="anomaly-page__hint">
            置灰的明细未出货数不足本次出货量（{{ current?.qty }}），请先确认数量
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="linkVisible = false">取消</el-button>
        <el-button type="primary" :loading="linkLoading" @click="submitLink">确认补挂</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.anomaly-page {
  &__tip {
    margin-bottom: 16px;
  }

  &__strong {
    font-weight: 600;
    margin: 0 2px;
  }

  &__desc {
    margin-bottom: 16px;
  }

  &__form {
    :deep(.el-form-item) {
      margin-bottom: 14px;
    }
  }

  &__hint {
    font-size: 12px;
    color: var(--admin-text-secondary);
    line-height: 1.6;
  }
}
</style>
