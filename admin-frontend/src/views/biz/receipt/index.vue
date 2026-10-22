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
      column-drag-key="biz:receipt"
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
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" value="NORMAL" />
            <el-option label="已作废" value="VOID" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="收款单号/发票号" clearable style="width: 160px" />
        </el-form-item>
      </template>
      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </template>
      <template #toolbar>
        <el-button type="primary" @click="openSave()">新增收款</el-button>
        <el-button @click="openOpening()">期初应收</el-button>
      </template>

      <template #col-receiptDate="{ row }">
        {{ String((row as ReceiptResp).receiptDate ?? '').slice(0, 10) }}
      </template>
      <template #col-amount="{ row }">
        {{ formatMoney((row as ReceiptResp).amount) }}
      </template>
      <template #col-statusLabel="{ row }">
        <el-tag :type="(row as ReceiptResp).status === 'VOID' ? 'info' : 'success'">
          {{ (row as ReceiptResp).statusLabel }}
        </el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :max="2"
              :actions="[
                {
                  label: '编辑',
                  hidden: (row as ReceiptResp).status === 'VOID',
                  onClick: () => openSave((row as ReceiptResp).id),
                },
                {
                  label: '作废',
                  type: 'danger',
                  hidden: (row as ReceiptResp).status === 'VOID',
                  onClick: () => openVoid(row as ReceiptResp),
                },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 收款新增/编辑 -->
    <el-dialog v-model="saveVisible" :title="editingId ? '编辑收款' : '新增收款'" width="520px">
      <el-form v-loading="saveLoading" element-loading-text="加载中…" :model="saveForm" label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="saveForm.customerId" placeholder="请选择客户" filterable style="width: 100%">
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="收款日期">
          <el-date-picker v-model="saveForm.receiptDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款金额" required>
          <el-input v-model="saveForm.amount" type="number" placeholder="大于 0" />
        </el-form-item>
        <el-form-item label="结算方式">
          <el-select v-model="saveForm.settleMethod" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="o in settleOpts" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票号">
          <el-input v-model="saveForm.invoiceNo" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="saveForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 期初应收 -->
    <el-dialog v-model="openingVisible" title="客户期初应收" width="480px">
      <el-form :model="openingForm" label-width="90px">
        <el-form-item label="客户" required>
          <el-select v-model="openingForm.customerId" placeholder="请选择客户" filterable style="width: 100%">
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="基准日">
          <el-date-picker v-model="openingForm.asOfDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="期初应收" required>
          <el-input v-model="openingForm.openingBalance" type="number" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="openingForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openingVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOpening">确定</el-button>
      </template>
    </el-dialog>

    <!-- 作废原因 -->
    <el-dialog v-model="voidVisible" title="作废收款" width="420px">
      <el-input v-model="voidReason" type="textarea" :rows="3" placeholder="请填写作废原因（敏感操作）" />
      <template #footer>
        <el-button @click="voidVisible = false">取消</el-button>
        <el-button type="danger" @click="submitVoid">确定作废</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import { useTable } from '@/composables/useTable';
import { receiptApi, customerApi } from '@/api/biz';
import type { CustomerResp, ReceiptResp, ReceiptQuery } from '@/types/biz';
import { useDict } from '@/composables/useDict';

const { options: settleMethodOptions } = useDict();
const settleOpts = settleMethodOptions('settlement_method');
const customerOptions = ref<CustomerResp[]>([]);

const searchForm = reactive<ReceiptQuery>({ keyword: '', customerId: undefined, status: undefined });
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
} = useTable<ReceiptResp>((params) => receiptApi.page({ ...params, ...searchForm } as never), {});

function handleReset(): void {
  Object.assign(searchForm, { keyword: '', customerId: undefined, status: undefined });
  reset();
}

const columns: ProTableColumn[] = [
  { prop: 'receiptNo', label: '收款单号', width: 160, showOverflowTooltip: true },
  { prop: 'customerName', label: '客户', minWidth: 160, showOverflowTooltip: true },
  { prop: 'receiptDate', label: '收款日期', width: 160, slot: true },
  { prop: 'amount', label: '收款金额', width: 130, align: 'right', slot: true },
  { prop: 'settleMethod', label: '结算方式', width: 120 },
  { prop: 'invoiceNo', label: '发票号', width: 140, showOverflowTooltip: true },
  { prop: 'statusLabel', label: '状态', width: 100, slot: true },
  { prop: 'remark', label: '备注', minWidth: 140, showOverflowTooltip: true },
];

function formatMoney(v?: number): string {
  if (v == null) return '';
  return '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/* ---------------- 新增/编辑 ---------------- */
const saveVisible = ref(false);
const saveLoading = ref(false);
const editingId = ref<string | null>(null);
const saveForm = reactive<{ customerId?: string; receiptDate?: string; amount?: number; settleMethod?: string; invoiceNo?: string; remark?: string }>({});

async function openSave(id?: string): Promise<void> {
  editingId.value = id || null;
  Object.assign(saveForm, {
    customerId: undefined,
    receiptDate: undefined,
    amount: undefined,
    settleMethod: undefined,
    invoiceNo: '',
    remark: '',
  });
  // 先弹窗，避免网速慢时长时间无响应；需要拉取详情时在框内显示加载态
  saveVisible.value = true;
  if (id) {
    saveLoading.value = true;
    try {
      const detail = await receiptApi.detail(id);
      saveForm.customerId = detail.customerId;
      saveForm.receiptDate = detail.receiptDate;
      saveForm.amount = detail.amount;
      saveForm.settleMethod = detail.settleMethod;
      saveForm.invoiceNo = detail.invoiceNo;
      saveForm.remark = detail.remark;
    } finally {
      saveLoading.value = false;
    }
  }
}

async function submitSave(): Promise<void> {
  if (!saveForm.customerId) {
    ElMessage.warning('请选择客户');
    return;
  }
  if (!saveForm.amount || saveForm.amount <= 0) {
    ElMessage.warning('收款金额必须大于 0');
    return;
  }
  await receiptApi.save({
    id: editingId.value || undefined,
    customerId: saveForm.customerId,
    receiptDate: saveForm.receiptDate,
    amount: saveForm.amount,
    currency: 'CNY',
    settleMethod: saveForm.settleMethod,
    invoiceNo: saveForm.invoiceNo,
    remark: saveForm.remark,
  });
  ElMessage.success('已保存');
  saveVisible.value = false;
  reload();
}

/* ---------------- 作废 ---------------- */
const voidVisible = ref(false);
const voidId = ref<string | null>(null);
const voidReason = ref('');

function openVoid(row: ReceiptResp): void {
  voidId.value = row.id;
  voidReason.value = '';
  voidVisible.value = true;
}

async function submitVoid(): Promise<void> {
  if (!voidReason.value.trim()) {
    ElMessage.warning('请填写作废原因');
    return;
  }
  await receiptApi.voidReceipt(voidId.value!, voidReason.value);
  ElMessage.success('已作废');
  voidVisible.value = false;
  reload();
}

/* ---------------- 期初应收 ---------------- */
const openingVisible = ref(false);
const openingForm = reactive<{ customerId?: string; asOfDate?: string; openingBalance?: number; currency?: string; remark?: string }>({});

function openOpening(): void {
  Object.assign(openingForm, {
    customerId: undefined,
    asOfDate: undefined,
    openingBalance: undefined,
    currency: 'CNY',
    remark: '',
  });
  openingVisible.value = true;
}

async function submitOpening(): Promise<void> {
  if (!openingForm.customerId) {
    ElMessage.warning('请选择客户');
    return;
  }
  if (openingForm.openingBalance == null) {
    ElMessage.warning('请填写期初应收');
    return;
  }
  await receiptApi.saveOpening(openingForm);
  ElMessage.success('已保存期初应收');
  openingVisible.value = false;
}

onMounted(async () => {
  customerOptions.value = await customerApi.search('', 200);
});
</script>
