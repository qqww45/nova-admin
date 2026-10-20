<script setup lang="ts">
/**
 * 历史数据体检报告（附录A A.6）。
 *
 * <p>回答一个问题：这份历史 Excel 能不能直接导进系统用。
 * 五类问题一次列全（重复客户 / 缺料号 / 数量异常 / 税率异常 / 无源出货），
 * 让人一眼判断先修哪一类，而不是点五个菜单各查一遍。
 */
import { computed, onMounted, ref, shallowRef } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import { reportApi } from '@/api/biz';
import type { HealthIssue, HealthReportResp } from '@/types/biz';

const loading = ref(false);
const report = shallowRef<HealthReportResp | null>(null);
const category = ref<string>('');

/** 各类问题的显示名与配色（服务端已给 categoryLabel，这里只补配色） */
const CATEGORY_TYPES: Record<string, 'danger' | 'warning' | 'info'> = {
  DUPLICATE_CUSTOMER: 'warning',
  MISSING_MATERIAL_NO: 'warning',
  QUANTITY_ANOMALY: 'danger',
  TAX_RATE_ANOMALY: 'info',
  ORPHAN_SHIPMENT: 'danger',
};

const columns: ProTableColumn[] = [
  { prop: 'categoryLabel', label: '类别', width: 110, slot: true },
  { prop: 'subject', label: '问题对象', minWidth: 220, align: 'left', showOverflowTooltip: true },
  { prop: 'detail', label: '说明', minWidth: 320, align: 'left', showOverflowTooltip: true },
];

/** 当前筛选下的问题明细 */
const issues = computed<HealthIssue[]>(() => {
  const all = report.value?.issues ?? [];
  return category.value ? all.filter((i) => i.category === category.value) : all;
});

/** 概览卡片：总数 + 各类问题数 */
const overview = computed(() => {
  const r = report.value;
  if (!r) {
    return [];
  }
  return [
    { label: '客户', value: r.customerCount, type: 'info' as const },
    { label: '订单', value: r.orderCount, type: 'info' as const },
    { label: '明细行', value: r.itemCount, type: 'info' as const },
    { label: '出货记录', value: r.shipmentCount, type: 'info' as const },
    ...Object.entries(r.counts ?? {}).map(([key, value]) => ({
      label: (r.issues ?? []).find((i) => i.category === key)?.categoryLabel ?? key,
      value,
      type: 'danger' as const,
    })),
  ];
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    report.value = await reportApi.health();
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="health-page">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="health-page__header">
          <span class="health-page__title">历史数据体检报告</span>
          <el-button :icon="Refresh" size="small" @click="load">重新体检</el-button>
        </div>
      </template>

      <div class="health-page__overview">
        <div v-for="item in overview" :key="item.label" class="health-page__card">
          <div class="health-page__card-value" :class="`is-${item.type}`">{{ item.value }}</div>
          <div class="health-page__card-label">{{ item.label }}</div>
        </div>
      </div>

      <el-empty v-if="issues.length === 0" description="未发现异常，历史数据可以直接导入使用" />
    </el-card>

    <ProTable
      v-if="issues.length > 0"
      :columns="columns"
      :data="issues"
      :total="issues.length"
      :loading="loading"
      :page-num="1"
      :page-size="200"
      class="health-page__table"
    >
      <template #search>
        <el-form-item label="问题类别">
          <el-select v-model="category" placeholder="全部" clearable style="width: 160px">
            <el-option
              v-for="(label, key) in Object.fromEntries(
                (report?.issues ?? []).map((i) => [i.category, i.categoryLabel ?? i.category]),
              )"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
      </template>

      <template #col-categoryLabel="{ row }">
        <el-tag size="small" :type="CATEGORY_TYPES[(row as HealthIssue).category] ?? 'info'">
          {{ (row as HealthIssue).categoryLabel }}
        </el-tag>
      </template>
    </ProTable>
  </div>
</template>

<style scoped lang="scss">
.health-page {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__overview {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
  }

  &__card {
    min-width: 96px;
    padding: 12px 16px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    text-align: center;
  }

  &__card-value {
    font-size: 22px;
    font-weight: 600;
    line-height: 1.2;

    &.is-danger {
      color: var(--el-color-danger);
    }

    &.is-info {
      color: var(--admin-text-primary, var(--el-text-color-primary));
    }
  }

  &__card-label {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__table {
    margin-top: 16px;
  }
}
</style>
