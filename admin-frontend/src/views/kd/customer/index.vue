<template>
  <div class="app-container">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      empty-description="换个关键字或清空筛选再试；仍为空时，请到「同步批次」确认客户主数据是否已上报"
      column-drag
      column-drag-key="kd:customer-data"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="客户名称 / 金蝶编码"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="账套">
          <el-input v-model="searchForm.acctId" placeholder="账套 ID" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.disabled" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="0" />
            <el-option label="禁用" :value="1" />
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

      <template #col-disabled="{ row }">
        <el-tag v-if="(row as KdCustomerDataResp).disabled === 1" type="danger" size="small">
          已禁用
        </el-tag>
        <el-tag v-else type="success" effect="plain" size="small">启用</el-tag>
      </template>

      <template #col-customerName="{ row }">
        <span class="name">{{ (row as KdCustomerDataResp).customerName || '—' }}</span>
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
import type { KdCustomerDataQuery, KdCustomerDataResp } from '@/types/kd';

const { isAdmin, options: ownerOptions } = useKdOwners();

const dateRange = ref<string[]>([]);
const searchForm = reactive<{
  acctId: string;
  keyword: string;
  disabled?: number;
  batchNo: string;
  ownerUserId?: string;
}>({ acctId: '', keyword: '', batchNo: '' });

function currentQuery(): KdCustomerDataQuery {
  return {
    acctId: searchForm.acctId || undefined,
    keyword: searchForm.keyword || undefined,
    disabled: searchForm.disabled,
    batchNo: searchForm.batchNo || undefined,
    ownerUserId: searchForm.ownerUserId,
    syncDateFrom: dateRange.value?.[0] || undefined,
    syncDateTo: dateRange.value?.[1] || undefined,
  };
}

const { loading, list, total, pageNum, pageSize, reload, reset, onPageChange, onSizeChange } =
  useTable<KdCustomerDataResp>((params) => kdDataApi.customers({ ...params, ...currentQuery() }));

const columns: ProTableColumn[] = [
  { prop: 'customerName', label: '客户名称', minWidth: 200, slot: true, showOverflowTooltip: true },
  { prop: 'kdNumber', label: '金蝶编码', width: 130, showOverflowTooltip: true },
  { prop: 'kdFid', label: '金蝶内码', width: 120, showOverflowTooltip: true },
  { prop: 'disabled', label: '状态', width: 90, slot: true },
  { prop: 'acctId', label: '账套', width: 110, showOverflowTooltip: true },
  { prop: 'ownerUserName', label: '业务员', width: 90, showOverflowTooltip: true },
  { prop: 'address', label: '地址', minWidth: 180, showOverflowTooltip: true },
  { prop: 'batchNo', label: '最近批次', width: 170, showOverflowTooltip: true },
  { prop: 'syncAt', label: '同步时间', width: 165 },
];

function handleReset(): void {
  dateRange.value = [];
  Object.assign(searchForm, {
    acctId: '',
    keyword: '',
    disabled: undefined,
    batchNo: '',
    ownerUserId: undefined,
  });
  reset();
}
</script>

<style scoped lang="scss">
.name {
  font-weight: 500;
}
</style>
