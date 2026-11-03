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
      column-drag-key="kd:batch"
      empty-description="暂无同步批次：采集器未启动或未联网，可到「绑定管理」下发「立即同步」验证"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="实体">
          <el-select v-model="searchForm.entity" placeholder="全部" clearable style="width: 150px">
            <el-option label="客户" value="customer" />
            <el-option label="出库单头" value="outbound" />
            <el-option label="出库分录" value="outbound_line" />
            <el-option label="整批" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="进行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="账套">
          <el-input v-model="searchForm.acctId" placeholder="账套 ID" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="批次号 / 结果" clearable style="width: 180px" />
        </el-form-item>
      </template>
      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </template>

      <template #col-statusLabel="{ row }">
        <el-tag :type="statusType((row as KdSyncBatchResp).status)">
          {{ statusLabel((row as KdSyncBatchResp).status) }}
        </el-tag>
      </template>

      <template #col-counts="{ row }">
        {{ (row as KdSyncBatchResp).rowCount ?? 0 }} / {{ (row as KdSyncBatchResp).upsertCount ?? 0 }} /
        {{ (row as KdSyncBatchResp).skippedCount ?? 0 }}
      </template>

      <template #col-watermark="{ row }">
        <div class="cell-sub">{{ (row as KdSyncBatchResp).watermarkFrom || '—' }}</div>
        <div class="cell-sub">{{ (row as KdSyncBatchResp).watermarkTo || '—' }}</div>
      </template>

      <template #col-costMs="{ row }">
        {{ formatCost((row as KdSyncBatchResp).costMs) }}
      </template>

      <template #col-message="{ row }">
        <span :class="{ 'text-danger': (row as KdSyncBatchResp).status === 'FAILED' }">
          {{ (row as KdSyncBatchResp).message || '—' }}
        </span>
      </template>
    </ProTable>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { useTable } from '@/composables/useTable';
import { kdSyncApi } from '@/api/kd';
import type { KdBatchQuery, KdSyncBatchResp } from '@/types/kd';

const searchForm = reactive<KdBatchQuery>({
  entity: undefined,
  status: undefined,
  acctId: '',
  keyword: '',
});

/** 只把有值的条件带给后端，避免空串污染 SQL 的 like/eq */
function currentQuery(): KdBatchQuery {
  return {
    entity: searchForm.entity,
    status: searchForm.status,
    acctId: searchForm.acctId,
    keyword: searchForm.keyword,
  };
}

const { loading, list, total, pageNum, pageSize, reload, reset, onPageChange, onSizeChange } =
  useTable<KdSyncBatchResp>((params) => kdSyncApi.batches({ ...params, ...currentQuery() }));

const columns: ProTableColumn[] = [
  { prop: 'batchNo', label: '批次号', width: 220, showOverflowTooltip: true },
  { prop: 'acctId', label: '账套', width: 120, showOverflowTooltip: true },
  { prop: 'ownerUserName', label: '业务员', width: 90, showOverflowTooltip: true },
  { prop: 'entity', label: '实体', width: 120 },
  { prop: 'mode', label: '模式', width: 90 },
  { prop: 'statusLabel', label: '状态', width: 90, slot: true },
  { prop: 'counts', label: '上行 / 落库 / 跳过', width: 150, slot: true },
  { prop: 'watermark', label: '水位起 / 止', minWidth: 180, slot: true },
  { prop: 'costMs', label: '耗时', width: 90, slot: true },
  { prop: 'clientVersion', label: '版本', width: 90 },
  { prop: 'message', label: '结果', minWidth: 200, slot: true, showOverflowTooltip: true },
  { prop: 'createTime', label: '时间', width: 165 },
];

function handleReset(): void {
  Object.assign(searchForm, { entity: undefined, status: undefined, acctId: '', keyword: '' });
  reset();
}

function statusLabel(status: string): string {
  if (status === 'SUCCESS') return '成功';
  if (status === 'FAILED') return '失败';
  return '进行中';
}

function statusType(status: string): 'success' | 'danger' | 'warning' {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'danger';
  return 'warning';
}

function formatCost(ms?: number): string {
  if (ms == null) return '—';
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)} s` : `${ms} ms`;
}
</script>

<style scoped lang="scss">
.cell-sub {
  font-size: 12px;
  color: var(--admin-text-secondary);
}

.text-danger {
  color: var(--el-color-danger);
}
</style>
