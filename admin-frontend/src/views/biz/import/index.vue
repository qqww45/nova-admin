<script setup lang="ts">
/**
 * 导入批次页（M4）。
 *
 * <p>存在的意义是"可回退"：使用者从 Excel 迁过来，最怕的是一批数据导错了没法撤。
 * 因此这里把每个批次的结果摊开（新增多少 / 跳过多少 / 失败多少），
 * 失败的直接给《错误清单》下载，整批导错了就一键回滚本批次的增量。
 */
import { onMounted, ref, shallowRef } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import { importExportApi } from '@/api/biz';
import type { ImportBatchResp } from '@/types/biz';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

const loading = ref(false);
const list = shallowRef<ImportBatchResp[]>([]);
const typeFilter = ref<string>('');
const limit = ref(50);

const columns: ProTableColumn[] = [
  { prop: 'batchType', label: '模块', width: 100, slot: true },
  { prop: 'fileName', label: '文件名', minWidth: 200, align: 'left', showOverflowTooltip: true },
  { prop: 'sheetName', label: '工作表', width: 120 },
  { prop: 'totalCount', label: '解析行数', width: 100, align: 'right' },
  { prop: 'insertCount', label: '新增', width: 90, align: 'right' },
  { prop: 'updateCount', label: '更新', width: 90, align: 'right' },
  { prop: 'skipCount', label: '跳过', width: 90, align: 'right' },
  { prop: 'failCount', label: '失败', width: 90, align: 'right', slot: true },
  { prop: 'idempotent', label: '幂等策略', width: 100, slot: true },
  { prop: 'statusLabel', label: '状态', width: 100, slot: true },
  { prop: 'createTime', label: '导入时间', width: 170, slot: true },
];

const TYPE_LABELS: Record<string, string> = {
  CUSTOMER: '客户',
  ORDER: '订单',
  SHIPMENT: '出货',
};

const IDEMPOTENT_LABELS: Record<string, string> = {
  SKIP: '跳过重复',
  OVERWRITE: '覆盖更新',
  APPEND: '追加',
};

async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await importExportApi.batches(typeFilter.value || undefined, limit.value);
  } finally {
    loading.value = false;
  }
}

/** 下载错误清单 */
async function downloadErrors(row: ImportBatchResp): Promise<void> {
  await importExportApi.errorReport(row.id);
  ElMessage.success('错误清单已开始下载');
}

/** 回滚批次：仅回滚本批次新增的出货，必须填原因 */
async function rollback(row: ImportBatchResp): Promise<void> {
  const { value } = await ElMessageBox.prompt(
    '回滚将作废本批次导入的出货并回退明细出货数，请填写原因（将记录到操作日志）',
    '回滚导入批次',
    { inputPattern: /\S+/, inputErrorMessage: '回滚原因不能为空', type: 'warning' },
  );
  await importExportApi.rollback(row.id, value);
  ElMessage.success('批次已回滚');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="import-page">
    <ProTable
      :columns="columns"
      :data="list"
      :total="list.length"
      :loading="loading"
      :page-num="1"
      :page-size="limit"
      column-drag
      column-drag-key="biz:import"
    >
      <template #search>
        <el-form-item label="模块">
          <el-select v-model="typeFilter" placeholder="全部" clearable style="width: 130px">
            <el-option label="客户" value="CUSTOMER" />
            <el-option label="订单" value="ORDER" />
            <el-option label="出货" value="SHIPMENT" />
          </el-select>
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" @click="load">查询</el-button>
      </template>

      <template #col-batchType="{ row }">
        {{ TYPE_LABELS[(row as ImportBatchResp).batchType ?? ''] ?? (row as ImportBatchResp).batchType }}
      </template>

      <template #col-failCount="{ row }">
        <span :class="{ 'import-page__fail': Number((row as ImportBatchResp).failCount ?? 0) > 0 }">
          {{ (row as ImportBatchResp).failCount ?? 0 }}
        </span>
      </template>

      <template #col-idempotent="{ row }">
        {{ IDEMPOTENT_LABELS[(row as ImportBatchResp).idempotent ?? ''] ?? '-' }}
      </template>

      <template #col-statusLabel="{ row }">
        <el-tag
          size="small"
          :type="(row as ImportBatchResp).status === 'ROLLED_BACK' ? 'info' : 'success'"
        >
          {{ (row as ImportBatchResp).statusLabel ?? (row as ImportBatchResp).status }}
        </el-tag>
      </template>

      <template #col-createTime="{ row }">
        {{ String((row as ImportBatchResp).createTime ?? '').replace('T', ' ').slice(0, 19) }}
      </template>

      <template #columns>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :max="2"
              :actions="[
                {
                  label: '错误清单',
                  type: 'warning',
                  icon: 'Download',
                  hidden:
                    Number((row as ImportBatchResp).failCount ?? 0) === 0 ||
                    !userStore.hasPermission('biz:import:error'),
                  onClick: () => downloadErrors(row as ImportBatchResp),
                },
                {
                  label: '回滚',
                  type: 'danger',
                  danger: true,
                  divided: true,
                  icon: 'RefreshLeft',
                  hidden:
                    (row as ImportBatchResp).status === 'ROLLED_BACK' ||
                    !userStore.hasPermission('biz:import:rollback'),
                  onClick: () => rollback(row as ImportBatchResp),
                },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <el-alert type="info" :closable="false" class="import-page__tip">
      <template #title>
        回滚只撤销<b>该批次</b>导入的出货并回退明细出货数，不影响手工录入或其它批次的数据。
      </template>
    </el-alert>
  </div>
</template>

<style scoped lang="scss">
.import-page {
  &__fail {
    color: var(--el-color-danger);
    font-weight: 600;
  }

  &__tip {
    margin-top: 16px;
  }
}
</style>
