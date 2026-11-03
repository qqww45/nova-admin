<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="金蝶客户与内部客户的对应关系：先「自动匹配建议」（按标准化名称唯一命中），再逐行确认——同名歧义的客户机器不敢猜，需要人工指定。确认过的映射是后续出货口径核对的地基。"
    />

    <ProTable
      :columns="columns"
      :data="rows"
      :total="rows.length"
      :loading="loading"
      :page-num="1"
      :page-size="rows.length || 1"
      column-drag
      column-drag-key="kd:customer-mapping"
      empty-description="没有金蝶客户数据：请先启动采集器同步客户"
    >
      <template #search>
        <el-form-item label="账套">
          <el-input v-model="searchForm.acctId" placeholder="账套 ID" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 150px">
            <el-option label="未匹配" value="UNMATCHED" />
            <el-option label="建议待确认" value="SUGGESTED" />
            <el-option label="已确认" value="CONFIRMED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="金蝶客户名称 / 编码"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="业务员">
          <el-select
            v-model="searchForm.ownerUserId"
            placeholder="全部业务员"
            clearable
            filterable
            style="width: 160px"
          >
            <el-option v-for="o in ownerOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button
          v-if="canEdit"
          :loading="suggesting"
          type="success"
          plain
          @click="autoSuggest"
        >
          自动匹配建议
        </el-button>
      </template>

      <template #col-status="{ row }">
        <el-tag
          :type="statusTag((row as KdCustomerMappingResp).status)"
          size="small"
        >
          {{ statusLabel((row as KdCustomerMappingResp).status) }}
        </el-tag>
      </template>

      <template #col-bizCustomer="{ row }">
        <span v-if="(row as KdCustomerMappingResp).bizCustomerId" class="cell-main">
          {{ (row as KdCustomerMappingResp).bizCustomerName }}
        </span>
        <span v-else class="cell-sub">—</span>
      </template>

      <template #col-actions="{ row }">
        <template v-if="canEdit">
          <el-select
            :model-value="(row as KdCustomerMappingResp).bizCustomerId"
            filterable
            remote
            :remote-method="searchCustomers"
            :loading="customerSearching"
            placeholder="搜索内部客户"
            size="small"
            style="width: 200px"
            @focus="searchCustomers('')"
            @change="(value: string) => saveMapping(row as KdCustomerMappingResp, value)"
          >
            <el-option
              v-for="c in customerOptions"
              :key="c.id"
              :label="c.customerName"
              :value="c.id"
            />
          </el-select>
        </template>
        <span v-else class="cell-sub">无编辑权限</span>
      </template>
    </ProTable>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { customerApi } from '@/api/biz';
import { kdMappingApi } from '@/api/kd';
import { useUserStore } from '@/store/user';
import type { CustomerResp } from '@/types/biz';
import type { KdAutoSuggestResult, KdCustomerMappingResp } from '@/types/kd';
import { useKdOwners } from '@/composables/useKdOwners';

const userStore = useUserStore();
const canEdit = computed(() => userStore.hasPermission('kd:customer-mapping:edit'));
const { isAdmin, options: ownerOptions } = useKdOwners();

const loading = ref(false);
const suggesting = ref(false);
const rows = ref<KdCustomerMappingResp[]>([]);
const searchForm = ref<{ acctId: string; status: string; keyword: string; ownerUserId: string }>({
  acctId: '',
  status: '',
  keyword: '',
  ownerUserId: '',
});

/** 行内选择内部客户（远程搜索，所有行共用一份候选） */
const customerOptions = ref<CustomerResp[]>([]);
const customerSearching = ref(false);

const columns: ProTableColumn[] = [
  { prop: 'kdName', label: '金蝶客户', minWidth: 220, align: 'left', showOverflowTooltip: true },
  { prop: 'kdNumber', label: '金蝶编码', width: 120, showOverflowTooltip: true },
  { prop: 'acctId', label: '账套', width: 140, showOverflowTooltip: true },
  { prop: 'status', label: '映射状态', width: 110, slot: true },
  { prop: 'bizCustomer', label: '对应内部客户', minWidth: 220, slot: true },
  { prop: 'actions', label: '确认映射', width: 230, fixed: 'right', slot: true },
];

async function reload(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await kdMappingApi.list({
      acctId: searchForm.value.acctId || undefined,
      status: searchForm.value.status || undefined,
      keyword: searchForm.value.keyword || undefined,
      ownerUserId: searchForm.value.ownerUserId || undefined,
    });
  } finally {
    loading.value = false;
  }
}

function handleReset(): void {
  searchForm.value = { acctId: '', status: '', keyword: '', ownerUserId: '' };
  reload();
}

/** 自动匹配：名称标准化唯一命中的生成建议，同名歧义留人工 */
async function autoSuggest(): Promise<void> {
  suggesting.value = true;
  try {
    const result: KdAutoSuggestResult = await kdMappingApi.autoSuggest(
      searchForm.value.acctId || undefined,
      searchForm.value.ownerUserId || undefined,
    );
    ElMessage.success(
      `已生成 ${result.suggested} 条建议（${result.skipped} 条无唯一命中、需要人工指定），请逐行确认`,
    );
    await reload();
  } finally {
    suggesting.value = false;
  }
}

/** 行内选择内部客户即保存（视为已确认） */
async function saveMapping(row: KdCustomerMappingResp, bizCustomerId: string): Promise<void> {
  if (!bizCustomerId) return;
  try {
    await kdMappingApi.save({
      acctId: row.acctId,
      kdFid: row.kdFid,
      bizCustomerId,
      ownerUserId: searchForm.value.ownerUserId || undefined,
    });
    ElMessage.success(`已确认：${row.kdName || '客户'} → 内部客户`);
    await reload();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  }
}

async function searchCustomers(keyword: string): Promise<void> {
  customerSearching.value = true;
  try {
    customerOptions.value = await customerApi.search(keyword || '', 30);
  } finally {
    customerSearching.value = false;
  }
}

function statusLabel(status: string): string {
  if (status === 'SUGGESTED') return '建议待确认';
  if (status === 'CONFIRMED') return '已确认';
  return '未匹配';
}

function statusTag(status: string): 'info' | 'warning' | 'success' {
  if (status === 'CONFIRMED') return 'success';
  if (status === 'SUGGESTED') return 'warning';
  return 'info';
}

onMounted(() => {
  void reload();
});
</script>

<style scoped lang="scss">
.tip {
  margin-bottom: 12px;
}

.cell-main {
  font-size: 13px;
}

.cell-sub {
  font-size: 12px;
  color: var(--admin-text-secondary);
}
</style>
