<script setup lang="ts">
/**
 * 仪表盘：统计卡片 + 近 7 日登录趋势（ECharts）+ 最近登录记录。
 *
 * <p>ECharts 图表按主题重绘：监听 store 的 isDark，切换时更新图表配色，
 * 否则暗色模式下会出现"白底图表"破坏整体观感。
 */
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import * as echarts from 'echarts';
import { dashboardApi } from '@/api/menu';
import type { DashboardSummary, LoginLogResp, SystemInfoResp } from '@/types/api';
import { useAppStore } from '@/store/app';

const appStore = useAppStore();

const summary = ref<DashboardSummary>({
  userCount: 0,
  roleCount: 0,
  menuCount: 0,
  deptCount: 0,
  todayLoginCount: 0,
  todayFailCount: 0,
  loginTrend: [],
});
const recentLogin = ref<LoginLogResp[]>([]);
const systemInfo = ref<SystemInfoResp | null>(null);

/** 图表实例用 shallowRef：ECharts 对象不需要深度响应式 */
const chartRef = shallowRef<echarts.ECharts>();
const chartDom = ref<HTMLDivElement>();

/** 统计卡片数据 */
const cards = computed(() => [
  { label: '用户总数', value: summary.value.userCount, icon: 'User', color: '#2563eb' },
  { label: '角色总数', value: summary.value.roleCount, icon: 'UserFilled', color: '#67c23a' },
  { label: '菜单总数', value: summary.value.menuCount, icon: 'Menu', color: '#e6a23c' },
  { label: '部门总数', value: summary.value.deptCount, icon: 'OfficeBuilding', color: '#f56c6c' },
  { label: '今日登录', value: summary.value.todayLoginCount, icon: 'Key', color: '#909399' },
  { label: '今日失败', value: summary.value.todayFailCount, icon: 'Warning', color: '#f78989' },
]);

/**
 * 图表配色：与 theme.css 的 slate / blue 体系保持一致。
 *
 * <p>为什么不能用 Element Plus 默认的 #4c4d4f / #33353b：
 * 全站暗色已切到深蓝黑（#0b1220 / #111827），图表若沿用纯灰色系会明显"出戏"。
 */
const chartTheme = computed(() =>
  appStore.isDark
    ? {
        axisLine: '#334155',
        splitLine: '#1e293b',
        label: '#94a3b8',
        line: '#60a5fa',
        tooltipBg: '#111827',
        tooltipBorder: '#1e293b',
        tooltipText: '#f1f5f9',
      }
    : {
        axisLine: '#dcdfe6',
        splitLine: '#ebeef5',
        label: '#606266',
        line: '#2563eb',
        tooltipBg: '#ffffff',
        tooltipBorder: '#e4e7ed',
        tooltipText: '#303133',
      },
);

/** 渲染趋势图 */
function renderChart(): void {
  if (!chartDom.value) {
    return;
  }
  if (!chartRef.value) {
    chartRef.value = echarts.init(chartDom.value);
  }
  const theme = chartTheme.value;
  chartRef.value.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: theme.tooltipBg,
      borderColor: theme.tooltipBorder,
      textStyle: { color: theme.tooltipText },
    },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: summary.value.loginTrend.map((item) => item.date),
      axisLine: { lineStyle: { color: theme.axisLine } },
      axisLabel: { color: theme.label },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: theme.splitLine } },
      axisLabel: { color: theme.label },
    },
    series: [
      {
        name: '登录次数',
        type: 'line',
        smooth: true,
        // 面积由主色渐变到透明，避免大色块压过卡片
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: appStore.isDark ? 'rgba(96, 165, 250, 0.32)' : 'rgba(37, 99, 235, 0.28)' },
              { offset: 1, color: 'rgba(37, 99, 235, 0.02)' },
            ],
          },
        },
        data: summary.value.loginTrend.map((item) => item.value),
        itemStyle: { color: theme.line },
      },
    ],
  });
}

/** 窗口尺寸变化自适应 */
function handleResize(): void {
  chartRef.value?.resize();
}

onMounted(async () => {
  const [sum, logs, info] = await Promise.all([
    dashboardApi.summary(),
    dashboardApi.recentLogin(10),
    // 系统信息需要运维权限（system:monitor:list），无权限时只应"卡片不显示"，
    // 不能让它的失败连带把整个仪表盘的数据带崩
    dashboardApi.systemInfo().catch(() => null),
  ]);
  summary.value = sum;
  recentLogin.value = logs ?? [];
  systemInfo.value = info ?? null;
  renderChart();
  window.addEventListener('resize', handleResize);
});

// 主题切换后重绘图表，保持配色一致
watch(
  () => appStore.isDark,
  () => renderChart(),
);

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  chartRef.value?.dispose();
});
</script>

<template>
  <div class="dashboard page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="12">
      <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="8" :md="6" :lg="4">
        <el-card shadow="never" class="dashboard__card">
          <div class="dashboard__card-body">
            <!-- 图标底衬：用卡片主色做 12% 透明底，避免纯色图标过于突兀 -->
            <span class="dashboard__card-icon" :style="{ color: card.color, backgroundColor: `${card.color}1f` }">
              <el-icon :size="20"><component :is="card.icon" /></el-icon>
            </span>
            <div class="dashboard__card-info">
              <p class="dashboard__card-value">{{ card.value }}</p>
              <p class="dashboard__card-label">{{ card.label }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="dashboard__charts">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never">
          <template #header>近 7 日登录趋势</template>
          <div ref="chartDom" class="dashboard__chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never">
          <template #header>最近登录记录</template>
          <el-table :data="recentLogin" size="small" max-height="320">
            <el-table-column prop="username" label="用户" width="90" />
            <el-table-column prop="ip" label="IP" width="110" />
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
                  {{ row.status === 0 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="loginTime" label="时间" min-width="120" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统参数：只展示非敏感的运行时指标，不含任何凭据与连接串 -->
    <el-row :gutter="12" class="dashboard__charts">
      <el-col :span="24">
        <el-card v-if="systemInfo" shadow="never">
          <template #header>
            <span>系统参数</span>
            <el-tag size="small" effect="plain" class="dashboard__env">{{ systemInfo.activeProfile }}</el-tag>
          </template>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="应用版本">{{ systemInfo.version }}</el-descriptions-item>
            <el-descriptions-item label="启动时间">{{ systemInfo.startTime }}</el-descriptions-item>
            <el-descriptions-item label="运行时长">{{ systemInfo.uptime }}</el-descriptions-item>
            <el-descriptions-item label="在线会话">{{ systemInfo.onlineSessionCount }}</el-descriptions-item>
            <el-descriptions-item label="Java 版本">{{ systemInfo.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="堆内存">
              {{ systemInfo.jvmHeap.usedMb }} / {{ systemInfo.jvmHeap.maxMb }} MB
            </el-descriptions-item>
            <el-descriptions-item label="处理器">{{ systemInfo.availableProcessors }} 核</el-descriptions-item>
            <el-descriptions-item label="操作系统">{{ systemInfo.os }}</el-descriptions-item>
            <el-descriptions-item label="数据库">{{ systemInfo.databaseType }}</el-descriptions-item>
            <el-descriptions-item label="连接池">
              {{ systemInfo.connectionPool.active }} 活跃 / {{ systemInfo.connectionPool.max }} 上限
            </el-descriptions-item>
            <el-descriptions-item label="验证码通道">{{ systemInfo.switches['verify.channel'] }}</el-descriptions-item>
            <el-descriptions-item label="回传验证码">
              <el-tag :type="systemInfo.switches['verify.reveal-code'] === 'true' ? 'danger' : 'success'" size="small">
                {{ systemInfo.switches['verify.reveal-code'] === 'true' ? '开启（生产应关闭）' : '关闭' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  &__card {
    margin-bottom: 12px;
    transition:
      transform var(--admin-transition-fast) ease,
      box-shadow var(--admin-transition-fast) ease,
      border-color var(--admin-transition-fast) ease;

    &:hover {
      border-color: var(--admin-tag-active-border);
      box-shadow: var(--admin-shadow-card);
      transform: translateY(-2px);
    }
  }

  &__card-body {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__card-icon {
    display: grid;
    flex: 0 0 44px;
    place-items: center;
    width: 44px;
    height: 44px;
    border-radius: 12px;
  }

  &__card-value {
    margin: 0;
    font-size: 22px;
    font-weight: 600;
    color: var(--admin-text-primary);
    font-variant-numeric: tabular-nums;
  }

  &__card-label {
    margin: 2px 0 0;
    font-size: 12px;
    color: var(--admin-text-regular);
  }

  &__charts {
    margin-top: 4px;
  }

  &__chart {
    width: 100%;
    height: 320px;
  }

  &__env {
    margin-left: 8px;
  }
}
</style>
