<script setup lang="ts">
/**
 * 在线用户与设备管理。
 *
 * <p>能力：查看当前谁在线、用什么设备（IP / 浏览器 / 系统）、何时登录；
 * 对可疑设备可单台强制下线，也可把某用户全部设备清退；
 * 并可查看/移除该用户的可信设备（移除后下次登录需重新验证）。
 *
 * <p>交互取舍：
 * <ul>
 *     <li>在线会话是运行时数据、量不大，故不做分页，前端一次性加载并即时过滤；</li>
 *     <li>强制下线属高危操作，必须二次确认，且明确告知"下次登录需重新验证"；</li>
 *     <li>"移除信任"只撤销记住状态、不立即踢人，两个动作语义分离，避免误操作。</li>
 * </ul>
 */
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import TableActionBar, { type TableActionItem } from '@/components/TableActionBar/index.vue';
import { onlineApi } from '@/api/online';
import type { DeviceTrustResp, OnlineSessionResp } from '@/types/api';

const loading = ref(false);
const list = ref<OnlineSessionResp[]>([]);
const keyword = ref('');

/**
 * 列定义。
 *
 * <p>与其它列表页保持一致：统一由 ProTable 承载"搜索区 + 工具栏 + 表格 + 分页"，
 * 本页因数据量小不传 total，ProTable 会自动隐藏分页区域。
 */
const columns = [
  { prop: 'username', label: '用户名', minWidth: 110 },
  { prop: 'nickname', label: '昵称', minWidth: 110 },
  { prop: 'deptName', label: '部门', minWidth: 100 },
  { prop: 'ip', label: '登录 IP', minWidth: 130 },
  { prop: 'terminal', label: '终端', minWidth: 160, slot: true },
  { prop: 'loginTime', label: '登录时间', minWidth: 165 },
  { prop: 'lastAccessTime', label: '最近活跃', minWidth: 165 },
  { prop: 'deviceId', label: '设备', minWidth: 150, slot: true },
  { prop: 'trusted', label: '可信', minWidth: 90, slot: true },
];

/** 可信设备弹窗 */
const deviceDialog = ref(false);
const devices = ref<DeviceTrustResp[]>([]);
const currentUser = ref<OnlineSessionResp | null>(null);

/** 加载在线会话 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await onlineApi.list(keyword.value);
  } finally {
    loading.value = false;
  }
}

/** 重置关键字后重新加载 */
function handleReset(): void {
  keyword.value = '';
  void load();
}

/** el-table 插槽行类型收敛 */
function asSession(row: unknown): OnlineSessionResp {
  return row as OnlineSessionResp;
}

/** el-table 插槽行类型收敛 */
function asDevice(row: unknown): DeviceTrustResp {
  return row as DeviceTrustResp;
}

/** 拼装单行操作项：高危操作用"更多"下拉收纳，避免首屏按钮过密 */
function buildSessionActions(row: OnlineSessionResp): TableActionItem[] {
  return [
    { label: '设备', type: 'primary', icon: 'Monitor', onClick: () => openDevices(row) },
    { label: '强制下线', type: 'warning', icon: 'CircleClose', onClick: () => kick(row, false) },
    { label: '全部下线', type: 'danger', icon: 'Delete', onClick: () => kick(row, true) },
  ];
}

/**
 * 设备标识截断显示（完整值用 tooltip 展示）。
 *
 * @param id 设备标识
 */
function shortDevice(id?: string): string {
  if (!id) {
    return '-';
  }
  return id.length > 14 ? `${id.slice(0, 8)}…${id.slice(-4)}` : id;
}

/**
 * 强制下线。
 *
 * @param row 会话
 * @param all true 表示该用户所有设备
 */
async function kick(row: OnlineSessionResp, all: boolean): Promise<void> {
  const tip = all
    ? `确定将用户「${row.username}」的所有设备全部下线？该用户需要重新登录。`
    : `确定将用户「${row.username}」的这台设备强制下线？该设备下次登录需重新验证。`;
  try {
    await ElMessageBox.confirm(tip, '强制下线', { type: 'warning', confirmButtonText: '确定下线', cancelButtonText: '取消' });
  } catch {
    return;
  }
  const count = await onlineApi.kick({ userId: row.userId, deviceId: all ? undefined : row.deviceId });
  ElMessage.success(`已下线 ${count} 台设备`);
  await load();
}

/** 打开可信设备弹窗 */
async function openDevices(row: OnlineSessionResp): Promise<void> {
  currentUser.value = row;
  devices.value = await onlineApi.devices(row.userId);
  deviceDialog.value = true;
}

/**
 * 移除可信设备。
 *
 * @param device 设备
 */
async function removeTrust(device: DeviceTrustResp): Promise<void> {
  const user = currentUser.value;
  if (!user) {
    return;
  }
  try {
    await ElMessageBox.confirm('移除后该设备下次登录需要重新输入验证码，是否继续？', '移除可信设备', { type: 'warning' });
  } catch {
    return;
  }
  await onlineApi.removeTrust(user.userId, device.deviceId);
  ElMessage.success('已移除该设备');
  devices.value = await onlineApi.devices(user.userId);
  await load();
}

onMounted(load);
</script>

<template>
  <div class="page-container">
    <ProTable
      :columns="columns"
      :data="list"
      :loading="loading"
      row-key="userId"
      column-drag
      column-drag-key="system:online"
      @selection-change="() => undefined"
    >
      <template #search>
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            placeholder="用户名 / 昵称"
            clearable
            style="width: 200px"
            @keyup.enter="load"
            @clear="load"
          />
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" :icon="'Search'" @click="load">搜索</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
      </template>

      <template #toolbar>
        <el-button :loading="loading" :icon="'Refresh'" @click="load">刷新</el-button>
      </template>

      <template #toolbar-meta>
        <span class="online__count">共 {{ list.length }} 台设备在线</span>
      </template>

      <template #col-terminal="{ row }">
        {{ asSession(row).browser }} / {{ asSession(row).os }}
      </template>

      <template #col-deviceId="{ row }">
        <el-tooltip :content="asSession(row).deviceId" placement="top">
          <span>{{ shortDevice(asSession(row).deviceId) }}</span>
        </el-tooltip>
      </template>

      <template #col-trusted="{ row }">
        <el-tag :type="asSession(row).trusted ? 'success' : 'info'" size="small" effect="plain">
          {{ asSession(row).trusted ? '已信任' : '陌生' }}
        </el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <TableActionBar :actions="buildSessionActions(asSession(row))" />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <el-dialog v-model="deviceDialog" :title="`可信设备 - ${currentUser?.username ?? ''}`" width="680px">
      <el-table :data="devices" border size="small">
        <el-table-column label="设备标识" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="asDevice(row).deviceId" placement="top">
              <span>{{ shortDevice(asDevice(row).deviceId) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="trustTime" label="信任时间" min-width="160" />
        <el-table-column prop="expireTime" label="到期时间" min-width="160" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="asDevice(row).online ? 'success' : 'info'" size="small" effect="plain">
              {{ asDevice(row).online ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeTrust(asDevice(row))">移除信任</el-button>
          </template>
        </el-table-column>
        <template #empty>该用户暂无可信设备</template>
      </el-table>
      <template #footer>
        <el-button @click="deviceDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/*
 * 旧的手写工具栏（.online__toolbar / .online__keyword）已随 ProTable 迁移删除：
 * 搜索区、按钮组、统计文案现在统一由 ProTable 的 search / search-buttons /
 * toolbar-meta 插槽承载，样式也随之由组件统一维护。
 */
.online {
  /* 统计数字用等宽字形 + 主色，让"台数"在 toolbar-meta 里成为唯一视觉焦点 */
  &__count {
    font-size: 13px;
    color: var(--admin-text-regular);

    :deep(b) {
      font-variant-numeric: tabular-nums;
      color: var(--admin-color-primary);
    }
  }
}
</style>
