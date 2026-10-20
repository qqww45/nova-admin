<script setup lang="ts">
/**
 * 订单管理页。
 *
 * <p>核心：订单头 + 明细的"整单保存"。明细用 EditableTable 直接编辑，
 * 支持从 Excel 粘贴 —— 订单明细本来就是从客户 PO 里一行行抄进来的，
 * 让用户点弹窗逐条填是不现实的。
 *
 * <p>金额口径：不含税金额 / 税额 / 价税合计 三列在界面上实时算（与后端 AmountCalculator
 * 同一口径：金额四舍五入 2 位、税额倒挤），但<b>保存后一律以服务端返回为准</b>，
 * 前端算出来的只用于录入时的即时反馈，不参与落库。
 */
import { computed, reactive, ref, shallowRef } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Download, Plus, Refresh, Upload } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import EditableTable from '@/components/EditableTable/index.vue';
import type { EditableColumn } from '@/types/editable';
import { customerApi, importExportApi, orderApi } from '@/api/biz';
import { useTable } from '@/composables/useTable';
import { useDict } from '@/composables/useDict';
import type {
  CustomerResp,
  OrderDetailResp,
  OrderItemSaveReq,
  OrderQuery,
  OrderResp,
  OrderSaveReq,
} from '@/types/biz';
import type { PageResult } from '@/types/api';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();
const { options: dictOptions } = useDict();

/** 税率下拉（禁止硬编码，来自字典） */
const taxRateOptions = dictOptions('tax_rate');

/* ---------------- 列表 ---------------- */

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
} = useTable<OrderResp>((params) => orderApi.page({ ...params, ...searchForm } as never), {});

/** 搜索条件（强类型，理由同客户页） */
const searchForm = reactive<{
  keyword: string;
  status?: string;
  customerId?: string;
  onlyOpen?: boolean;
}>({ keyword: '', status: undefined, customerId: undefined, onlyOpen: undefined });

/** 重置搜索条件 */
function handleReset(): void {
  Object.assign(searchForm, {
    keyword: '',
    status: undefined,
    customerId: undefined,
    onlyOpen: undefined,
  });
  reset();
}

const columns: ProTableColumn[] = [
  { prop: 'orderNo', label: '订单号', width: 150, showOverflowTooltip: true },
  { prop: 'customerName', label: '客户', minWidth: 160, align: 'left', showOverflowTooltip: true },
  { prop: 'customerOrderNo', label: '客户订单号', width: 140, showOverflowTooltip: true },
  { prop: 'salesmanName', label: '业务员', width: 100 },
  { prop: 'orderDate', label: '下单时间', width: 120, slot: true },
  { prop: 'statusLabel', label: '状态', width: 100, slot: true },
  { prop: 'progress', label: '已出/总量', width: 130 },
  { prop: 'amountTaxIncluded', label: '价税合计', width: 130, align: 'right', slot: true },
  { prop: 'remark', label: '备注', minWidth: 140, align: 'left', showOverflowTooltip: true },
];

const selected = shallowRef<OrderResp[]>([]);
const customerOptions = shallowRef<CustomerResp[]>([]);

/* ---------------- 订单编辑 ---------------- */

const formVisible = ref(false);
const formLoading = ref(false);
const editingId = ref<string | null>(null);
const form = ref<OrderSaveReq>(blankOrder());
const items = ref<OrderItemSaveReq[]>([blankItem()]);

function blankItem(): OrderItemSaveReq {
  return { model: '', qtyOrdered: undefined, unitPrice: undefined };
}

function blankOrder(): OrderSaveReq {
  return {
    customerId: '',
    salesmanId: '',
    orderDate: today(),
    currency: 'CNY',
    items: [],
    requirement: '',
    mainboardLabel: '',
    cartonLabel: '',
    customerOrderNo: '',
    remark: '',
  };
}

function today(): string {
  const now = new Date();
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** 明细列定义 */
const itemColumns = computed<EditableColumn[]>(() => [
  { prop: 'model', label: '型号', minWidth: 140, required: true, placeholder: '必填' },
  { prop: 'materialNo', label: '物料编号', width: 130 },
  { prop: 'customerMaterialNo', label: '客户物料号', width: 140, placeholder: '出货匹配优先键' },
  { prop: 'spec', label: '规格型号', width: 130 },
  { prop: 'qtyOrdered', label: '下单数量', width: 110, type: 'number', required: true, align: 'right' },
  {
    prop: 'taxRate',
    label: '税率',
    width: 110,
    type: 'select',
    options: taxRateOptions.value,
    placeholder: '空=不含税',
  },
  { prop: 'unitPrice', label: '单价', width: 110, type: 'number', required: true, align: 'right' },
  { prop: 'amountExTax', label: '不含税金额', width: 120, computed: true, align: 'right', precision: 2 },
  { prop: 'taxAmount', label: '税额', width: 110, computed: true, align: 'right', precision: 2 },
  {
    prop: 'amountTaxIncluded',
    label: '价税合计',
    width: 130,
    computed: true,
    align: 'right',
    precision: 2,
  },
  { prop: 'reconcileDate', label: '对账日期', width: 130, type: 'date' },
  { prop: 'remark', label: '备注', width: 130 },
]);

/**
 * 实时重算金额（仅用于录入反馈，落库以服务端为准）。
 *
 * <p>口径与后端 AmountCalculator 保持一致：税率为空或 0 → 单价视为不含税；
 * 否则视为含税，先算价税合计再反算不含税金额，税额倒挤。
 */
function recalcRow(row: OrderItemSaveReq): void {
  const qty = Number(row.qtyOrdered ?? 0);
  const price = Number(row.unitPrice ?? 0);
  const rate = Number(row.taxRate ?? 0);
  const record = row as unknown as Record<string, number>;
  const total = round2(price * qty);
  if (!rate) {
    record.amountExTax = total;
    record.taxAmount = 0;
    record.amountTaxIncluded = total;
    return;
  }
  const exTax = round2(total / (1 + rate));
  record.amountExTax = exTax;
  record.taxAmount = round2(total - exTax);
  record.amountTaxIncluded = total;
}

/** 四舍五入到 2 位 */
function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

/** 明细变化时重算金额 */
function onItemsChange(): void {
  items.value.forEach(recalcRow);
}

async function openCreate(): Promise<void> {
  editingId.value = null;
  form.value = blankOrder();
  form.value.orderNo = await orderApi.generateOrderNo();
  items.value = [blankItem(), blankItem(), blankItem()];
  formVisible.value = true;
}

async function openEdit(row: OrderResp): Promise<void> {
  editingId.value = row.id;
  const detail: OrderDetailResp = await orderApi.detail(row.id);
  form.value = {
    id: detail.id,
    orderNo: detail.orderNo,
    customerId: detail.customerId,
    customerOrderNo: detail.customerOrderNo ?? '',
    requirement: detail.requirement ?? '',
    mainboardLabel: detail.mainboardLabel ?? '',
    cartonLabel: detail.cartonLabel ?? '',
    salesmanId: detail.salesmanId,
    orderDate: detail.orderDate ?? '',
    expectedDeliveryDate: detail.expectedDeliveryDate ?? '',
    currency: detail.currency ?? 'CNY',
    settlementMethod: detail.settlementMethod ?? '',
    deliveryMethod: detail.deliveryMethod ?? '',
    remark: detail.remark ?? '',
    items: [],
  };
  items.value =
    detail.items.length > 0
      ? detail.items.map((item) => ({
          id: item.id,
          lineNo: item.lineNo,
          model: item.model,
          materialNo: item.materialNo ?? '',
          customerMaterialNo: item.customerMaterialNo ?? '',
          spec: item.spec ?? '',
          qtyOrdered: item.qtyOrdered,
          taxRate: item.taxRate,
          unitPrice: item.unitPrice,
          reconcileDate: item.reconcileDate ?? '',
          remark: item.remark ?? '',
        }))
      : [blankItem()];
  items.value.forEach(recalcRow);
  formVisible.value = true;
}

async function submitOrder(): Promise<void> {
  if (!form.value.customerId) {
    ElMessage.warning('请选择客户');
    return;
  }
  if (!form.value.salesmanId) {
    ElMessage.warning('请选择业务员');
    return;
  }
  const validItems = items.value.filter((item) => String(item.model ?? '').trim() !== '');
  if (validItems.length === 0) {
    ElMessage.warning('请至少填写一条明细');
    return;
  }
  formLoading.value = true;
  try {
    const payload: OrderSaveReq = { ...form.value, items: validItems };
    if (editingId.value) {
      await orderApi.update(payload);
      ElMessage.success('保存成功');
    } else {
      await orderApi.create(payload);
      ElMessage.success('新增成功');
    }
    formVisible.value = false;
    reload();
  } finally {
    formLoading.value = false;
  }
}

/* ---------------- 状态流转 ---------------- */

async function confirmOrder(row: OrderResp): Promise<void> {
  await orderApi.confirm(row.id);
  ElMessage.success('订单已确认');
  reload();
}

async function cancelOrder(row: OrderResp): Promise<void> {
  const { value } = await ElMessageBox.prompt('请填写取消原因（将记录到操作日志）', '取消订单', {
    inputPattern: /\S+/,
    inputErrorMessage: '取消原因不能为空',
  });
  await orderApi.cancel(row.id, value);
  ElMessage.success('订单已取消');
  reload();
}

async function removeOne(row: OrderResp): Promise<void> {
  await ElMessageBox.confirm(`确认删除订单【${row.orderNo}】？已出货的订单无法删除。`, '删除确认', {
    type: 'warning',
  });
  await orderApi.remove([row.id]);
  ElMessage.success('删除成功');
  reload();
}

/* ---------------- 客户下拉 ---------------- */

async function searchCustomer(keyword: string): Promise<void> {
  customerOptions.value = await customerApi.search(keyword, 20);
}

/** 打开表单时预加载一批客户，避免下拉空白 */
async function preloadCustomers(): Promise<void> {
  if (customerOptions.value.length === 0) {
    customerOptions.value = await customerApi.search('', 50);
  }
}

/* ---------------- 导入导出 ---------------- */

async function exportData(): Promise<void> {
  await importExportApi.export('ORDER', 'LEGACY');
  ElMessage.success('导出已开始');
}

async function beforeUpload(file: File): Promise<boolean> {
  try {
    const preview = await importExportApi.preview('ORDER', file);
    const summary = `解析 ${preview.total} 行：新增 ${preview.insertCount}，更新 ${preview.updateCount}，跳过 ${preview.skipCount}，失败 ${preview.failCount}`;
    if (preview.hasBlockingError) {
      ElMessageBox.alert(`${summary}。存在阻断级错误，请先修正后重新导入。`, '导入校验未通过', {
        type: 'error',
      });
      return false;
    }
    await ElMessageBox.confirm(`${summary}。确认导入？`, '导入确认', { type: 'warning' });
    const result = await importExportApi.confirm('ORDER', file);
    ElMessage.success(`导入完成：新增 ${result.insertCount}，更新 ${result.updateCount}`);
    reload();
  } catch {
    // 用户取消
  }
  return false;
}

async function downloadTemplate(): Promise<void> {
  await importExportApi.template('ORDER', 'LEGACY');
}
</script>

<template>
  <div class="order-page">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      selectable
      column-drag
      column-drag-key="biz:order"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @selection-change="(rows: unknown[]) => (selected = rows as OrderResp[])"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="订单号 / 客户订单号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="部分出货" value="PARTIAL_SHIPPED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELED" />
          </el-select>
        </el-form-item>
        <el-form-item label="仅看未出货">
          <el-switch v-model="searchForm.onlyOpen" />
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" :icon="Plus" @click="reload">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </template>

      <template #toolbar>
        <el-button
          v-if="userStore.hasPermission('biz:order:add')"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新增订单
        </el-button>
        <el-button :icon="Download" @click="exportData">导出台账</el-button>
        <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
        <el-upload
          v-if="userStore.hasPermission('biz:order:import')"
          :show-file-list="false"
          accept=".xlsx"
          :before-upload="beforeUpload"
        >
          <el-button :icon="Upload">导入订单</el-button>
        </el-upload>
      </template>

      <template #col-orderDate="{ row }">
        {{ String((row as OrderResp).orderDate ?? '').slice(0, 10) }}
      </template>

      <template #col-statusLabel="{ row }">
        <el-tag
          size="small"
          :type="
            (row as OrderResp).status === 'COMPLETED'
              ? 'success'
              : (row as OrderResp).status === 'PARTIAL_SHIPPED'
                ? 'warning'
                : (row as OrderResp).status === 'CANCELED'
                  ? 'info'
                  : 'primary'
          "
        >
          {{ (row as OrderResp).statusLabel }}
        </el-tag>
      </template>

      <template #col-amountTaxIncluded="{ row }">
        {{ Number((row as OrderResp).amountTaxIncluded ?? 0).toFixed(2) }}
      </template>

      <template #columns>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :max="2"
              :actions="[
                { label: '查看/编辑', type: 'primary', onClick: () => openEdit(row as OrderResp) },
                {
                  label: '确认',
                  type: 'success',
                  hidden:
                    !userStore.hasPermission('biz:order:confirm') ||
                    (row as OrderResp).status !== 'DRAFT',
                  onClick: () => confirmOrder(row as OrderResp),
                },
                {
                  label: '取消',
                  type: 'warning',
                  hidden:
                    !userStore.hasPermission('biz:order:cancel') ||
                    (row as OrderResp).status === 'CANCELED',
                  onClick: () => cancelOrder(row as OrderResp),
                },
                {
                  label: '删除',
                  type: 'danger',
                  danger: true,
                  divided: true,
                  hidden: !userStore.hasPermission('biz:order:remove'),
                  onClick: () => removeOne(row as OrderResp),
                },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 订单编辑：头 + 明细 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑订单' : '新增订单'"
      width="96%"
      top="4vh"
      @open="preloadCustomers"
    >
      <el-form :model="form" label-width="100px" class="order-form">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="订单号">
              <el-input v-model="form.orderNo" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="客户" required>
              <el-select
                v-model="form.customerId"
                filterable
                remote
                :remote-method="searchCustomer"
                placeholder="输入名称搜索"
                style="width: 100%"
              >
                <el-option
                  v-for="item in customerOptions"
                  :key="item.id"
                  :label="item.customerName"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="客户订单号">
              <el-input v-model="form.customerOrderNo" placeholder="客户 PO 号，出货匹配用" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="下单时间">
              <el-date-picker
                v-model="form.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="业务员" required>
              <el-input v-model="form.salesmanId" placeholder="用户 ID，待接入用户下拉" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预计交货">
              <el-date-picker
                v-model="form.expectedDeliveryDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="主板标签">
              <el-input v-model="form.mainboardLabel" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="外箱标签">
              <el-input v-model="form.cartonLabel" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="客户要求">
              <el-input v-model="form.requirement" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">订单明细（可直接编辑 / 从 Excel 粘贴）</el-divider>

      <EditableTable
        v-model="items"
        :columns="itemColumns"
        :row-factory="blankItem"
        :min-rows="1"
        @change="onItemsChange"
      />

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="submitOrder">保存订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.order-page {
  :deep(.el-upload) {
    display: inline-flex;
    margin-left: 8px;
  }
}

.order-form {
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}
</style>
