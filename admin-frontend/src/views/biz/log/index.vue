<script setup lang="ts">
/**
 * 业务操作日志（M6 审计）。
 *
 * <p>只承载业务侧的敏感操作：出货录入 / 作废 / 导入 / 回滚 / 对账 / 取消订单。
 * 系统登录日志另有一套，不混在这里——混在一起会让"谁改了我这笔账"这个问题变难回答。
 */
import { reactive, ref } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { logApi } from '@/api/biz';
import { useTable } from '@/composables/useTable';
import type { OperationLogResp } from '@/types/biz';

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
} = useTable<OperationLogResp>(
  (params) => logApi.page({ ...params, ...searchForm } as never),
  {},
);

/** 搜索条件（强类型，理由同客户页） */
const searchForm = reactive<{ module: string; action: string; keyword: string }>({
  module: '',
  action: '',
  keyword: '',
});

/** 重置搜索条件 */
function handleReset(): void {
  Object.assign(searchForm, { module: '', action: '', keyword: '' });
  reset();
}

const columns: ProTableColumn[] = [
  { prop: 'operateTime', label: '操作时间', width: 170, slot: true },
  { prop: 'operatorName', label: '操作人', width: 110 },
  { prop: 'moduleLabel', label: '模块', width: 90, slot: true },
  { prop: 'actionLabel', label: '动作', width: 90, slot: true },
  { prop: 'bizId', label: '业务ID', width: 190, showOverflowTooltip: true },
  { prop: 'reason', label: '操作原因', minWidth: 220, align: 'left', showOverflowTooltip: true },
];

const ACTION_TYPES: Record<string, 'danger' | 'warning' | 'success' | 'primary' | 'info'> = {
  VOID: 'danger',
  ROLLBACK: 'danger',
  REMOVE: 'danger',
  CANCEL: 'warning',
  IMPORT: 'primary',
  RECONCILE: 'info',
  LINK: 'warning',
};
</script>

<template>
  <div class="log-page">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      column-drag
      column-drag-key="biz:log"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="模块">
          <el-select v-model="searchForm.module" placeholder="全部" clearable style="width: 120px">
            <el-option label="客户" value="CUSTOMER" />
            <el-option label="订单" value="ORDER" />
            <el-option label="出货" value="SHIPMENT" />
            <el-option label="导入" value="IMPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="searchForm.action" placeholder="全部" clearable style="width: 120px">
            <el-option label="新增" value="CREATE" />
            <el-option label="修改" value="UPDATE" />
            <el-option label="作废" value="VOID" />
            <el-option label="导入" value="IMPORT" />
            <el-option label="回滚" value="ROLLBACK" />
            <el-option label="对账" value="RECONCILE" />
            <el-option label="补挂订单" value="LINK" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="业务ID / 原因 / 操作人"
            clearable
            style="width: 200px"
          />
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </template>

      <template #col-operateTime="{ row }">
        {{ String((row as OperationLogResp).operateTime ?? '').replace('T', ' ').slice(0, 19) }}
      </template>

      <template #col-moduleLabel="{ row }">
        <el-tag size="small" type="info">{{ (row as OperationLogResp).moduleLabel }}</el-tag>
      </template>

      <template #col-actionLabel="{ row }">
        <el-tag
          size="small"
          :type="ACTION_TYPES[(row as OperationLogResp).action ?? ''] ?? 'primary'"
        >
          {{ (row as OperationLogResp).actionLabel }}
        </el-tag>
      </template>
    </ProTable>
  </div>
</template>
