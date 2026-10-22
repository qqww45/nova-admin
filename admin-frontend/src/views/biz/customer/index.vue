<script setup lang="ts">
/**
 * 客户管理页。
 *
 * <p>结构：列表（ProTable）+ 表单弹窗 + 详情抽屉（联系人 / 开票信息 / 关联订单三个页签）。
 *
 * <p>取舍：客户是主数据，改动频率低但影响面大（订单与出货都依赖它），
 * 所以删除做"有订单禁止删除，只允许停用"的强校验，避免误删导致历史单据失去归属。
 */
import { computed, reactive, ref, shallowRef } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Download, Plus, Refresh, Upload } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import { customerApi, importExportApi } from '@/api/biz';
import { useTable } from '@/composables/useTable';
import { useDict } from '@/composables/useDict';
import type {
  CustomerContactReq,
  CustomerContactResp,
  CustomerDetailResp,
  CustomerInvoiceReq,
  CustomerInvoiceResp,
  CustomerQuery,
  CustomerResp,
  CustomerSaveReq,
} from '@/types/biz';
import type { PageResult } from '@/types/api';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

const { options: dictOptions } = useDict();

/** 发票类型下拉（禁止硬编码，来自字典） */
const invoiceTypeOptions = dictOptions('invoice_type');

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
} = useTable<CustomerResp>(
  (params) => customerApi.page({ ...params, ...searchForm } as never),
  {},
);

/**
 * 搜索条件单独声明为强类型响应式对象。
 *
 * <p>为什么不直接用 useTable 内部的 query：它是 Record<string, unknown>，
 * 直接 v-model 到 el-input 会得到 unknown，模板类型检查过不去；
 * 单独声明后既能享受类型提示，又能通过展开合并进分页参数。
 */
const searchForm = reactive<{ keyword: string; status?: number }>({
  keyword: '',
  status: undefined,
});

/** 重置搜索条件（先还原表单，再触发 useTable 的 reset） */
function handleReset(): void {
  Object.assign(searchForm, { keyword: '', status: undefined });
  reset();
}

const columns: ProTableColumn[] = [
  { prop: 'customerName', label: '客户名称', minWidth: 200, align: 'left', showOverflowTooltip: true },
  { prop: 'customerCode', label: '客户编号', width: 120 },
  { prop: 'defaultContactName', label: '默认联系人', width: 120 },
  { prop: 'defaultContactPhone', label: '联系电话', width: 140 },
  { prop: 'address', label: '地址', minWidth: 200, align: 'left', showOverflowTooltip: true },
  { prop: 'orderCount', label: '关联订单', width: 100 },
  { prop: 'statusLabel', label: '状态', width: 90, slot: true },
  { prop: 'remark', label: '备注', minWidth: 160, align: 'left', showOverflowTooltip: true },
];

const selected = shallowRef<CustomerResp[]>([]);

/* ---------------- 表单弹窗 ---------------- */

const formVisible = ref(false);
const formLoading = ref(false);
const form = ref<CustomerSaveReq>({ customerName: '', status: 1 });
const editingId = ref<string | null>(null);

function openCreate(): void {
  editingId.value = null;
  form.value = { customerName: '', status: 1 };
  formVisible.value = true;
}

async function openEdit(row: CustomerResp): Promise<void> {
  editingId.value = row.id;
  // 先弹窗，避免网速慢时长时间无响应；拉取详情时在框内显示加载态
  formVisible.value = true;
  formLoading.value = true;
  try {
    const detail = await customerApi.detail(row.id);
    form.value = {
      id: detail.id,
      customerName: detail.customerName,
      customerCode: detail.customerCode,
      address: detail.address,
      remark: detail.remark,
      status: detail.status ?? 1,
    };
  } finally {
    formLoading.value = false;
  }
}

async function submitForm(): Promise<void> {
  if (!form.value.customerName?.trim()) {
    ElMessage.warning('请填写客户名称');
    return;
  }
  formLoading.value = true;
  try {
    if (editingId.value) {
      await customerApi.update(form.value);
      ElMessage.success('修改成功');
    } else {
      await customerApi.create(form.value);
      ElMessage.success('新增成功');
    }
    formVisible.value = false;
    reload();
  } finally {
    formLoading.value = false;
  }
}

/* ---------------- 删除 ---------------- */

async function removeOne(row: CustomerResp): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除客户【${row.customerName}】？已存在订单的客户将无法删除。`,
    '删除确认',
    { type: 'warning' },
  );
  await customerApi.remove([row.id]);
  ElMessage.success('删除成功');
  reload();
}

async function removeBatch(): Promise<void> {
  if (selected.value.length === 0) {
    ElMessage.warning('请先选择客户');
    return;
  }
  await ElMessageBox.confirm(`确认删除选中的 ${selected.value.length} 个客户？`, '批量删除', {
    type: 'warning',
  });
  await customerApi.remove(selected.value.map((item) => item.id));
  ElMessage.success('删除成功');
  reload();
}

/* ---------------- 详情抽屉 ---------------- */

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailTab = ref('contact');
const detail = shallowRef<CustomerDetailResp | null>(null);

async function openDetail(row: CustomerResp): Promise<void> {
  // 先开抽屉，再异步拉取详情，框内显示加载态
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = await customerApi.detail(row.id);
    detailTab.value = 'contact';
  } finally {
    detailLoading.value = false;
  }
}

/** 详情标题：客户名称 */
const detailTitle = computed(() => detail.value?.customerName ?? '客户详情');

/* ---------------- 联系人 / 开票信息维护 ---------------- */

/**
 * 子表操作后刷新详情与列表。
 *
 * <p>默认联系人/开票信息会反映到列表的「默认联系人」列，所以两边都要刷。
 */
async function reloadDetail(): Promise<void> {
  if (!detail.value) {
    return;
  }
  detail.value = await customerApi.detail(detail.value.id);
  reload();
}

/* ---- 联系人 ---- */

const contactDialogVisible = ref(false);
const contactFormLoading = ref(false);
const contactEditingId = ref<string | null>(null);
const contactForm = ref<CustomerContactReq>({ customerId: '', contactName: '', status: 1 });

function openContactCreate(): void {
  contactEditingId.value = null;
  contactForm.value = { customerId: detail.value?.id ?? '', contactName: '', status: 1 };
  contactDialogVisible.value = true;
}

function openContactEdit(row: CustomerContactResp): void {
  contactEditingId.value = row.id;
  contactForm.value = {
    id: row.id,
    customerId: row.customerId,
    contactName: row.contactName,
    title: row.title,
    phone: row.phone,
    mobile: row.mobile,
    email: row.email,
    isDefault: row.isDefault,
    status: row.status ?? 1,
    remark: row.remark,
  };
  contactDialogVisible.value = true;
}

async function submitContactForm(): Promise<void> {
  if (!contactForm.value.contactName?.trim()) {
    ElMessage.warning('请填写联系人姓名');
    return;
  }
  // 与后端 CustomerAppServiceImpl 的校验保持一致：电话与手机至少一个
  if (!contactForm.value.phone?.trim() && !contactForm.value.mobile?.trim()) {
    ElMessage.warning('电话与手机至少填写一个');
    return;
  }
  contactFormLoading.value = true;
  try {
    await customerApi.saveContact(contactForm.value);
    ElMessage.success('保存成功');
    contactDialogVisible.value = false;
    await reloadDetail();
  } finally {
    contactFormLoading.value = false;
  }
}

async function removeContact(row: CustomerContactResp): Promise<void> {
  await ElMessageBox.confirm(`确认删除联系人【${row.contactName}】？`, '删除确认', {
    type: 'warning',
  });
  await customerApi.removeContact(row.id);
  ElMessage.success('删除成功');
  await reloadDetail();
}

async function setDefaultContact(row: CustomerContactResp): Promise<void> {
  await customerApi.setDefaultContact(row.id);
  ElMessage.success('已设为默认联系人');
  await reloadDetail();
}

/* ---- 开票信息 ---- */

const invoiceDialogVisible = ref(false);
const invoiceFormLoading = ref(false);
const invoiceEditingId = ref<string | null>(null);
const invoiceForm = ref<CustomerInvoiceReq>({
  customerId: '',
  invoiceTitle: '',
  invoiceType: 'SPECIAL',
  status: 1,
});

function openInvoiceCreate(): void {
  invoiceEditingId.value = null;
  invoiceForm.value = {
    customerId: detail.value?.id ?? '',
    invoiceTitle: '',
    invoiceType: 'SPECIAL',
    status: 1,
  };
  invoiceDialogVisible.value = true;
}

function openInvoiceEdit(row: CustomerInvoiceResp): void {
  invoiceEditingId.value = row.id;
  invoiceForm.value = {
    id: row.id,
    customerId: row.customerId,
    invoiceTitle: row.invoiceTitle,
    taxNo: row.taxNo,
    bankName: row.bankName,
    bankAccount: row.bankAccount,
    invoiceAddress: row.invoiceAddress,
    invoicePhone: row.invoicePhone,
    invoiceType: row.invoiceType,
    isDefault: row.isDefault,
    status: row.status ?? 1,
    remark: row.remark,
  };
  invoiceDialogVisible.value = true;
}

async function submitInvoiceForm(): Promise<void> {
  if (!invoiceForm.value.invoiceTitle?.trim()) {
    ElMessage.warning('请填写开票名称');
    return;
  }
  invoiceFormLoading.value = true;
  try {
    await customerApi.saveInvoice(invoiceForm.value);
    ElMessage.success('保存成功');
    invoiceDialogVisible.value = false;
    await reloadDetail();
  } finally {
    invoiceFormLoading.value = false;
  }
}

async function removeInvoice(row: CustomerInvoiceResp): Promise<void> {
  await ElMessageBox.confirm(`确认删除开票信息【${row.invoiceTitle}】？`, '删除确认', {
    type: 'warning',
  });
  await customerApi.removeInvoice(row.id);
  ElMessage.success('删除成功');
  await reloadDetail();
}

async function setDefaultInvoice(row: CustomerInvoiceResp): Promise<void> {
  await customerApi.setDefaultInvoice(row.id);
  ElMessage.success('已设为默认开票信息');
  await reloadDetail();
}

/* ---------------- 导入导出 ---------------- */

async function exportData(): Promise<void> {
  await importExportApi.export('CUSTOMER', 'LEGACY');
  ElMessage.success('导出已开始');
}

async function downloadTemplate(): Promise<void> {
  await importExportApi.template('CUSTOMER', 'LEGACY');
}

/** 选择文件后走"预览 → 确认"两步（附录A 导入三步法） */
async function beforeUpload(file: File): Promise<boolean> {
  try {
    const preview = await importExportApi.preview('CUSTOMER', file);
    const summary = `解析 ${preview.total} 行：新增 ${preview.insertCount}，更新 ${preview.updateCount}，跳过 ${preview.skipCount}，失败 ${preview.failCount}`;
    if (preview.hasBlockingError) {
      ElMessageBox.alert(`${summary}。存在阻断级错误，请先修正后重新导入。`, '导入校验未通过', {
        type: 'error',
      });
      return false;
    }
    await ElMessageBox.confirm(`${summary}。确认导入？`, '导入确认', { type: 'warning' });
    const result = await importExportApi.confirm('CUSTOMER', file);
    ElMessage.success(`导入完成：新增 ${result.insertCount}，更新 ${result.updateCount}`);
    reload();
  } catch {
    // 用户取消或接口报错：不做额外提示，保持安静
  }
  return false;
}
</script>

<template>
  <div class="customer-page">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      selectable
      column-drag
      column-drag-key="biz:customer"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @selection-change="(rows: unknown[]) => (selected = rows as CustomerResp[])"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="客户名称 / 编号 / 地址" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" :icon="Plus" @click="reload">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </template>

      <template #toolbar>
        <el-button
          v-if="userStore.hasPermission('biz:customer:add')"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新增客户
        </el-button>
        <el-button
          v-if="userStore.hasPermission('biz:customer:remove')"
          type="danger"
          :disabled="selected.length === 0"
          @click="removeBatch"
        >
          批量删除
        </el-button>
        <el-button :icon="Download" @click="exportData">导出台账</el-button>
        <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
        <el-upload
          v-if="userStore.hasPermission('biz:customer:import')"
          :show-file-list="false"
          accept=".xlsx"
          :before-upload="beforeUpload"
        >
          <el-button :icon="Upload">导入客户</el-button>
        </el-upload>
      </template>

      <template #col-statusLabel="{ row }">
        <el-tag :type="(row as CustomerResp).status === 1 ? 'success' : 'info'" size="small">
          {{ (row as CustomerResp).statusLabel }}
        </el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :actions="[
                { label: '详情', type: 'primary', onClick: () => openDetail(row as CustomerResp) },
                {
                  label: '编辑',
                  type: 'primary',
                  hidden: !userStore.hasPermission('biz:customer:edit'),
                  onClick: () => openEdit(row as CustomerResp),
                },
                {
                  label: '删除',
                  type: 'danger',
                  hidden: !userStore.hasPermission('biz:customer:remove'),
                  onClick: () => removeOne(row as CustomerResp),
                },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑客户' : '新增客户'" width="560px">
      <el-form v-loading="formLoading" element-loading-text="加载中…" :model="form" label-width="100px">
        <el-form-item label="客户名称" required>
          <el-input v-model="form.customerName" placeholder="必填，重复名称无法保存" />
        </el-form-item>
        <el-form-item label="客户编号">
          <el-input v-model="form.customerCode" placeholder="选填" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：联系人 / 开票信息 / 关联订单 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="60%">
      <div v-loading="detailLoading" element-loading-text="加载中…" style="min-height: 200px">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="联系人" name="contact">
          <div class="detail-toolbar">
            <el-button
              v-if="userStore.hasPermission('biz:customer:edit')"
              type="primary"
              :icon="Plus"
              size="small"
              @click="openContactCreate"
            >
              新增联系人
            </el-button>
          </div>
          <el-table :data="detail?.contacts ?? []" size="small" border>
            <el-table-column prop="contactName" label="姓名" width="100" />
            <el-table-column prop="title" label="职务" width="100" />
            <el-table-column prop="phone" label="电话" width="130" />
            <el-table-column prop="mobile" label="手机" width="130" />
            <el-table-column prop="email" label="邮箱" min-width="160" />
            <el-table-column label="默认" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ row }">
                <TableActionBar
                  :actions="[
                    {
                      label: '设为默认',
                      type: 'primary',
                      hidden: !userStore.hasPermission('biz:customer:edit') || (row as CustomerContactResp).isDefault === 1,
                      onClick: () => setDefaultContact(row as CustomerContactResp),
                    },
                    {
                      label: '编辑',
                      type: 'primary',
                      hidden: !userStore.hasPermission('biz:customer:edit'),
                      onClick: () => openContactEdit(row as CustomerContactResp),
                    },
                    {
                      label: '删除',
                      type: 'danger',
                      hidden: !userStore.hasPermission('biz:customer:edit'),
                      onClick: () => removeContact(row as CustomerContactResp),
                    },
                  ]"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="开票信息" name="invoice">
          <div class="detail-toolbar">
            <el-button
              v-if="userStore.hasPermission('biz:customer:edit')"
              type="primary"
              :icon="Plus"
              size="small"
              @click="openInvoiceCreate"
            >
              新增开票信息
            </el-button>
          </div>
          <el-table :data="detail?.invoices ?? []" size="small" border>
            <el-table-column prop="invoiceTitle" label="开票名称" min-width="180" />
            <el-table-column prop="taxNo" label="纳税人识别号" width="160" />
            <el-table-column prop="bankName" label="开户行" width="140" />
            <el-table-column prop="bankAccount" label="银行账号" width="150" />
            <el-table-column prop="invoiceTypeLabel" label="发票类型" width="130" />
            <el-table-column label="默认" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ row }">
                <TableActionBar
                  :actions="[
                    {
                      label: '设为默认',
                      type: 'primary',
                      hidden: !userStore.hasPermission('biz:customer:edit') || (row as CustomerInvoiceResp).isDefault === 1,
                      onClick: () => setDefaultInvoice(row as CustomerInvoiceResp),
                    },
                    {
                      label: '编辑',
                      type: 'primary',
                      hidden: !userStore.hasPermission('biz:customer:edit'),
                      onClick: () => openInvoiceEdit(row as CustomerInvoiceResp),
                    },
                    {
                      label: '删除',
                      type: 'danger',
                      hidden: !userStore.hasPermission('biz:customer:edit'),
                      onClick: () => removeInvoice(row as CustomerInvoiceResp),
                    },
                  ]"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`关联订单(${detail?.orderCount ?? 0})`" name="order">
          <el-table :data="detail?.orders ?? []" size="small" border>
            <el-table-column prop="orderNo" label="订单号" width="150" />
            <el-table-column prop="customerOrderNo" label="客户订单号" width="140" />
            <el-table-column prop="orderDate" label="下单时间" width="120" />
            <el-table-column prop="statusLabel" label="状态" width="100" />
            <el-table-column prop="totalQtyOrdered" label="下单量" width="90" align="right" />
            <el-table-column prop="totalQtyShipped" label="已出货" width="90" align="right" />
            <el-table-column prop="amountTaxIncluded" label="价税合计" minWidth="120" align="right" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
      </div>
    </el-drawer>

    <!-- 联系人表单 -->
    <el-dialog
      v-model="contactDialogVisible"
      :title="contactEditingId ? '编辑联系人' : '新增联系人'"
      width="560px"
    >
      <el-form v-loading="contactFormLoading" :model="contactForm" label-width="100px">
        <el-form-item label="姓名" required>
          <el-input v-model="contactForm.contactName" placeholder="必填" />
        </el-form-item>
        <el-form-item label="职务">
          <el-input v-model="contactForm.title" placeholder="选填" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="contactForm.phone" placeholder="与手机至少填写一个" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="contactForm.mobile" placeholder="与电话至少填写一个" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="contactForm.email" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="contactForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="contactForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="contactFormLoading" @click="submitContactForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 开票信息表单 -->
    <el-dialog
      v-model="invoiceDialogVisible"
      :title="invoiceEditingId ? '编辑开票信息' : '新增开票信息'"
      width="560px"
    >
      <el-form v-loading="invoiceFormLoading" :model="invoiceForm" label-width="100px">
        <el-form-item label="开票名称" required>
          <el-input v-model="invoiceForm.invoiceTitle" placeholder="必填" />
        </el-form-item>
        <el-form-item label="纳税人识别号">
          <el-input v-model="invoiceForm.taxNo" />
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="invoiceForm.invoiceType" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="item in invoiceTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开户行">
          <el-input v-model="invoiceForm.bankName" />
        </el-form-item>
        <el-form-item label="银行账号">
          <el-input v-model="invoiceForm.bankAccount" />
        </el-form-item>
        <el-form-item label="开票地址">
          <el-input v-model="invoiceForm.invoiceAddress" />
        </el-form-item>
        <el-form-item label="开票电话">
          <el-input v-model="invoiceForm.invoicePhone" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="invoiceForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="invoiceForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invoiceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="invoiceFormLoading" @click="submitInvoiceForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.customer-page {
  :deep(.el-upload) {
    display: inline-flex;
    margin-left: 8px;
  }
}

/* 详情抽屉子表工具栏（新增按钮） */
.detail-toolbar {
  margin-bottom: 8px;
}
</style>
