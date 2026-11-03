<template>
  <div class="app-container">
    <ProTable
      :columns="columns"
      :data="list"
      :loading="loading"
      :total="0"
      column-drag
      column-drag-key="kd:binding"
    >
      <template #toolbar>
        <el-button type="primary" @click="openCode">生成绑定码</el-button>
        <el-button @click="load">刷新</el-button>
      </template>

      <template #col-acctInfo="{ row }">
        <div class="cell-main">{{ (row as KdBindingResp).acctName || (row as KdBindingResp).acctId }}</div>
        <div class="cell-sub">{{ (row as KdBindingResp).kdUsername || '—' }}</div>
      </template>

      <template #col-statusLabel="{ row }">
        <el-tag :type="(row as KdBindingResp).status === 'ACTIVE' ? 'success' : 'info'">
          {{ (row as KdBindingResp).statusLabel }}
        </el-tag>
      </template>

      <template #col-tokenPrefix="{ row }">
        <span class="mono">{{ (row as KdBindingResp).tokenPrefix || '—' }}…</span>
      </template>

      <template #col-counts="{ row }">
        {{ (row as KdBindingResp).customerCount ?? 0 }} / {{ (row as KdBindingResp).outboundCount ?? 0 }}
      </template>

      <template #col-watermark="{ row }">
        <div class="cell-sub">客户：{{ (row as KdBindingResp).watermarkCustomer || '—' }}</div>
        <div class="cell-sub">出库：{{ (row as KdBindingResp).watermarkOutbound || '—' }}</div>
      </template>

      <template #col-syncInfo="{ row }">
        <div class="cell-sub">{{ (row as KdBindingResp).lastSyncAt || '从未同步' }}</div>
        <div class="cell-sub">{{ (row as KdBindingResp).lastSyncResult || '' }}</div>
      </template>

      <template #col-online="{ row }">
        <el-tooltip
          :content="`心跳时间：${(row as KdBindingResp).lastUsedAt || '从未上线'}（采集器每 15 秒轮询一次指令）`"
          placement="top"
        >
          <el-tag :type="isOnline((row as KdBindingResp).lastUsedAt) ? 'success' : 'info'" size="small">
            {{ onlineLabel((row as KdBindingResp).lastUsedAt) }}
          </el-tag>
        </el-tooltip>
      </template>

      <template #columns>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <TableActionBar
              :max="3"
              :actions="[
                {
                  label: '立即同步',
                  hidden: (row as KdBindingResp).status !== 'ACTIVE',
                  onClick: () => syncNow(row as KdBindingResp),
                },
                {
                  label: '指令',
                  hidden: (row as KdBindingResp).status !== 'ACTIVE',
                  onClick: () => openCommands(row as KdBindingResp),
                },
                {
                  label: '轮换',
                  hidden: (row as KdBindingResp).status !== 'ACTIVE',
                  onClick: () => rotate(row as KdBindingResp),
                },
                {
                  label: '吊销',
                  type: 'danger',
                  hidden: (row as KdBindingResp).status !== 'ACTIVE',
                  onClick: () => revoke(row as KdBindingResp),
                },
              ]"
            />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 生成绑定码 -->
    <el-dialog v-model="codeVisible" title="生成绑定码" width="440px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="在桌面采集器中输入绑定码即可完成绑定，5 分钟内有效、仅可使用一次。"
        class="tip"
      />
      <el-form label-width="70px">
        <el-form-item label="备注">
          <el-input v-model="codeRemark" placeholder="如：客户现场电脑（可不填）" maxlength="50" />
        </el-form-item>
      </el-form>
      <div v-if="code" class="code-box">
        <div class="code-box__value">{{ code?.code }}</div>
        <div class="code-box__hint">
          {{ code?.expireAt }} 过期（{{ code?.expireSeconds }} 秒）
        </div>
        <el-button link type="primary" @click="copy(code?.code || '')">复制绑定码</el-button>
      </div>
      <template #footer>
        <el-button @click="codeVisible = false">关闭</el-button>
        <el-button type="primary" :loading="codeLoading" @click="createCode">
          {{ code ? '重新生成' : '生成' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 令牌展示（轮换后） -->
    <el-dialog v-model="tokenVisible" title="新的机器令牌" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="令牌只显示这一次。请在采集器里重新绑定，旧令牌立即失效。"
        class="tip"
      />
      <el-input :model-value="newToken" readonly type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="tokenVisible = false">关闭</el-button>
        <el-button type="primary" @click="copy(newToken)">复制令牌</el-button>
      </template>
    </el-dialog>

    <!-- 指令记录：采集器每 15 秒轮询一次，这里能看到"取走 / 回执"全过程 -->
    <el-dialog v-model="commandsVisible" title="采集器指令" width="720px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="采集器每 15 秒取一次指令；PENDING 表示还没被取走（客户端未启动或未联网）。"
        class="tip"
      />
      <el-table v-loading="commandsLoading" :data="commands" size="small" border>
        <el-table-column prop="command" label="指令" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="commandTagType((row as KdCommandResp).status)" size="small">
              {{ commandStatusLabel((row as KdCommandResp).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下发" width="165" />
        <el-table-column prop="doneTime" label="完成" width="165" />
        <el-table-column prop="result" label="结果" min-width="160" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="commandsVisible = false">关闭</el-button>
        <el-button :loading="commandsLoading" @click="refreshCommands">刷新</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import type { ProTableColumn } from '@/components/ProTable/index.vue';
import TableActionBar from '@/components/TableActionBar/index.vue';
import { kdBindingApi } from '@/api/kd';
import type { KdBindingCodeResp, KdBindingResp, KdCommandResp } from '@/types/kd';

const loading = ref(false);
const list = ref<KdBindingResp[]>([]);

const columns: ProTableColumn[] = [
  { prop: 'userName', label: '业务员', width: 120 },
  { prop: 'acctInfo', label: '账套 / 金蝶账号', minWidth: 180, slot: true },
  { prop: 'online', label: '采集器在线', width: 110, slot: true },
  { prop: 'machineName', label: '机器', width: 140, showOverflowTooltip: true },
  { prop: 'clientVersion', label: '版本', width: 90 },
  { prop: 'tokenPrefix', label: '令牌', width: 110, slot: true },
  { prop: 'statusLabel', label: '状态', width: 90, slot: true },
  { prop: 'counts', label: '客户 / 出库', width: 110, slot: true },
  { prop: 'watermark', label: '水位', minWidth: 180, slot: true },
  { prop: 'syncInfo', label: '上次同步', minWidth: 180, slot: true },
];

async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await kdBindingApi.list();
  } finally {
    loading.value = false;
  }
}

/* ---------------- 绑定码 ---------------- */
const codeVisible = ref(false);
const codeLoading = ref(false);
const codeRemark = ref('');
const code = ref<KdBindingCodeResp | null>(null);

function openCode(): void {
  codeRemark.value = '';
  code.value = null;
  codeVisible.value = true;
}

async function createCode(): Promise<void> {
  codeLoading.value = true;
  try {
    code.value = await kdBindingApi.createCode(codeRemark.value || undefined);
  } finally {
    codeLoading.value = false;
  }
}

/* ---------------- 轮换 / 吊销 ---------------- */
const tokenVisible = ref(false);
const newToken = ref('');

async function rotate(row: KdBindingResp): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `轮换后旧令牌立即失效，需要在采集器「${row.machineName || row.acctName || row.acctId}」上重新绑定。是否继续？`,
      '轮换令牌',
      { type: 'warning' },
    );
  } catch {
    // 取消：什么都不做（尤其不能继续轮换）
    return;
  }
  const result = await kdBindingApi.refresh(row.id);
  newToken.value = result.token;
  tokenVisible.value = true;
  await load();
}

async function revoke(row: KdBindingResp): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `吊销后该采集器将无法继续上报（离职 / 换电脑场景）。是否吊销「${row.acctName || row.acctId}」？`,
      '吊销令牌',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await kdBindingApi.revoke(row.id);
  ElMessage.success('已吊销');
  await load();
}

/* ---------------- 指令 ---------------- */
const commandsVisible = ref(false);
const commandsLoading = ref(false);
const commands = ref<KdCommandResp[]>([]);
const activeBindingId = ref('');

async function syncNow(row: KdBindingResp): Promise<void> {
  try {
    await kdBindingApi.sendCommand(row.id, 'sync.now');
    ElMessage.success('已下发，采集器会在 15 秒内开始同步');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  }
}

async function openCommands(row: KdBindingResp): Promise<void> {
  activeBindingId.value = row.id;
  commands.value = [];
  commandsVisible.value = true;
  await refreshCommands();
}

async function refreshCommands(): Promise<void> {
  if (!activeBindingId.value) return;
  commandsLoading.value = true;
  try {
    commands.value = await kdBindingApi.commands(activeBindingId.value);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e));
  } finally {
    commandsLoading.value = false;
  }
}

function commandStatusLabel(status: string): string {
  if (status === 'PENDING') return '待取';
  if (status === 'SENT') return '已取走';
  if (status === 'DONE') return '已完成';
  return '已过期';
}

function commandTagType(status: string): 'info' | 'warning' | 'success' | 'danger' {
  if (status === 'DONE') return 'success';
  if (status === 'SENT') return 'warning';
  if (status === 'EXPIRED') return 'danger';
  return 'info';
}

/* ---------------- 工具 ---------------- */

/** 在线判定窗口：指令轮询周期 15 秒，放宽到 2 分钟以容忍网络抖动 */
const ONLINE_THRESHOLD_MS = 2 * 60 * 1000;

/**
 * 采集器是否在线（按指令轮询心跳判断）。
 *
 * <p>业务员自助排查第一步："数据没上来"十有八九是采集器没开机 / 没联网，
 * 在线状态让这一眼就能看出来，不用管理员翻指令记录。
 */
function isOnline(lastUsedAt?: string): boolean {
  if (!lastUsedAt) return false;
  const time = new Date(lastUsedAt.replace(' ', 'T')).getTime();
  return Number.isFinite(time) && Date.now() - time <= ONLINE_THRESHOLD_MS;
}

function onlineLabel(lastUsedAt?: string): string {
  if (!lastUsedAt) return '未上线';
  const time = new Date(lastUsedAt.replace(' ', 'T')).getTime();
  if (!Number.isFinite(time)) return '未知';
  if (isOnline(lastUsedAt)) return '在线';
  const minutes = Math.floor((Date.now() - time) / 60_000);
  if (minutes < 60) return `离线 ${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `离线 ${hours} 小时`;
  return `离线 ${Math.floor(hours / 24)} 天`;
}

async function copy(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制');
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请手动选中复制');
  }
}

onMounted(load);
</script>

<style scoped lang="scss">
.cell-main {
  font-size: 13px;
}

.cell-sub {
  font-size: 12px;
  color: var(--admin-text-secondary);
}

.mono {
  font-family: var(--admin-font-mono, monospace);
  font-size: 12px;
}

.tip {
  margin-bottom: 12px;
}

.code-box {
  margin-top: 12px;
  padding: 16px;
  text-align: center;
  background: var(--el-fill-color-light);
  border-radius: 8px;

  &__value {
    font-size: 30px;
    font-weight: 700;
    letter-spacing: 6px;
    color: var(--el-color-primary);
  }

  &__hint {
    margin: 6px 0;
    font-size: 12px;
    color: var(--admin-text-secondary);
  }
}
</style>
