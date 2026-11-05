<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../components/ui/PageHeader.vue'
import HzField from '../components/ui/HzField.vue'
import HzButton from '../components/ui/HzButton.vue'
import type {
  LastSyncStat,
  NovaHealth,
  NovaState,
  SyncProgress,
  SyncRunRecord,
  NovaUploadRecord
} from '../../../shared/types'

const router = useRouter()
// 双水位增量上线后默认自动（有水位增量、无水位全量）；
// 日期框变成"纠错用的显式重拉起点"，留空即自动。旧存储键里的默认日期不再劫持新逻辑
const sinceDate = ref(localStorage.getItem('hz.resyncDate') || '')
const running = ref(false)
const items = ref<SyncProgress[]>([
  { entity: 'customer', status: 'running', rowCount: 0, message: '等待开始' },
  { entity: 'sale_order', status: 'running', rowCount: 0, message: '等待开始' },
  { entity: 'outbound', status: 'running', rowCount: 0, message: '等待开始' }
])
const error = ref('')
const lastStats = ref<LastSyncStat[]>([])
const nova = ref<NovaState | null>(null)
const uploading = ref(false)

const labels: Record<string, string> = {
  customer: '客户',
  sale_order: '销售订单',
  outbound: '销售出库',
  return: '退货/不良',
  payment: '收款',
  invoice: '发票'
}

const lastSyncTime = computed(() => {
  const times = lastStats.value.map((s) => s.finishedAt).filter(Boolean) as string[]
  if (!times.length) return ''
  const d = new Date(Math.max(...times.map((t) => new Date(t).getTime())))
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
})

const neverSynced = computed(
  () => !lastStats.value.length && items.value.every((x) => !x.rowCount && x.message === '等待开始')
)

let off: (() => void) | undefined
let offNova: (() => void) | undefined
let healthTimer: ReturnType<typeof setInterval> | undefined

/* ---------------- 服务状态 / 同步历史 ---------------- */

const health = ref<NovaHealth | null>(null)
const probing = ref(false)
const historyLoading = ref(false)

/** 合并后的历史行（金蝶拉取 + 上行上报，时间倒序） */
interface HistoryRow {
  key: string
  time: string
  kind: string
  trigger: string
  status: string
  rowClass: 'success' | 'failed' | 'skipped_no_perm' | 'running' | 'noop'
  count: string
  message: string
}

const history = ref<HistoryRow[]>([])

/** 时间显示：yyyy-MM-dd HH:mm（两种落库格式都能解析） */
function fmtTime(value?: string | null): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const triggerLabels: Record<string, string> = {
  manual: '手动',
  auto: '定时',
  command: '远程指令'
}

function statusTextOf(status: string): string {
  if (status === 'success') return '成功'
  if (status === 'failed') return '失败'
  if (status === 'skipped_no_perm') return '无权限'
  if (status === 'noop') return '无新数据'
  if (status === 'running') return '进行中'
  return status
}

async function refreshHealth() {
  try {
    health.value = await window.hengzhang.getNovaHealth()
  } catch {
    /* ignore */
  }
}

async function refreshHistory() {
  historyLoading.value = true
  try {
    const [syncs, uploads] = await Promise.all([
      window.hengzhang.syncHistory(),
      window.hengzhang.novaUploadHistory()
    ])
    const syncRows: HistoryRow[] = syncs.map((r: SyncRunRecord) => ({
      key: `s-${r.id}`,
      time: fmtTime(r.finishedAt || r.startedAt),
      kind: labels[r.entity] || r.entity,
      trigger: r.source ? triggerLabels[r.source] || r.source : '—',
      status: statusTextOf(r.status),
      rowClass: r.status,
      count: `${r.rowCount} 行`,
      message: r.message
    }))
    const uploadRows: HistoryRow[] = uploads.map((r: NovaUploadRecord) => ({
      key: `u-${r.id}`,
      time: fmtTime(r.finishedAt || r.startedAt),
      kind: '上行上报',
      trigger: '自动',
      status: statusTextOf(r.status),
      rowClass: r.status === 'noop' ? 'noop' : r.status === 'success' ? 'success' : 'failed',
      count: r.status === 'noop' ? '0 行' : `上报 ${r.received} / 入库 ${r.upserted}`,
      message: r.message
    }))
    history.value = [...syncRows, ...uploadRows]
      .sort((a, b) => (b.time > a.time ? 1 : b.time < a.time ? -1 : 0))
      .slice(0, 40)
  } catch {
    /* ignore */
  } finally {
    historyLoading.value = false
  }
}

/** 立即检测对账平台（真实请求一次任务清单接口并计时） */
async function probeService() {
  probing.value = true
  try {
    await window.hengzhang.probeNovaHealth()
    await refreshHealth()
  } catch {
    /* 探测失败也会写进 lastProbe，刷新即见 */
    await refreshHealth()
  } finally {
    probing.value = false
  }
}

/** 定时同步卡片说明：绑定门槛 + 墙钟调度 + 关窗进托盘 */
const autoSyncHint = computed(() => {
  const h = health.value
  if (!h?.autoSync.enabled) return '已关闭'
  if (!h.state.bound) return '未绑定，暂不自动同步。绑定后约 1 分钟会自动跑第一次'
  if (!h.autoSync.lastRunAt) {
    const grace = h.autoSync.startupGraceSec ?? 60
    return `已绑定。启动约 ${grace} 秒后会自动跑第一次，无需点击「开始同步」。之后每 ${h.autoSync.intervalHours} 小时按墙上时钟补跑（电脑睡眠醒来也会补）。关窗即可，请勿从托盘退出。`
  }
  return `关窗后仍在托盘后台执行。按墙上时钟每 ${h.autoSync.intervalHours} 小时一次，睡眠/重启会按上次自动同步补跑。`
})

/** 服务状态卡的聚合文案 */
const serviceState = computed(() => {
  const h = health.value
  if (!h) return { cls: 'off', text: '加载中…', sub: '' }
  if (!h.state.bound) {
    return { cls: 'off', text: '未绑定', sub: '绑定对账平台后才会检测与自动上报' }
  }
  const probe = h.lastProbe
  if (probe?.status === 'ok') {
    return {
      cls: 'ok',
      text: '服务正常',
      sub: `最近检测 ${fmtTime(probe.checkedAt)} · 延迟 ${probe.latencyMs ?? '—'} ms`
    }
  }
  if (probe?.status === 'error') {
    return { cls: 'bad', text: '服务异常', sub: `${probe.message}（${fmtTime(probe.checkedAt)}）` }
  }
  // 没有主动检测过：用心跳兜底
  const hb = h.heartbeat
  if (hb.lastOkAt && (!hb.lastFailAt || hb.lastFailAt < hb.lastOkAt)) {
    return { cls: 'ok', text: '服务正常（心跳）', sub: `上次心跳 ${fmtTime(hb.lastOkAt)}` }
  }
  if (hb.lastFailAt) {
    return { cls: 'bad', text: '连接异常', sub: `${hb.lastFailMessage || ''}（${fmtTime(hb.lastFailAt)}）` }
  }
  return { cls: 'off', text: '未检测', sub: '点击「立即检测」或等待 15 秒心跳' }
})

/** 同步 / 上行结束后等一小会儿再刷新，避免一轮 3 个实体触发 3 次 */
let refreshDebounce: ReturnType<typeof setTimeout> | undefined
function scheduleHistoryRefresh() {
  if (refreshDebounce) clearTimeout(refreshDebounce)
  refreshDebounce = setTimeout(() => {
    refreshDebounce = undefined
    void refreshHistory()
    void refreshHealth()
    void refreshNova()
  }, 600)
}

async function refreshNova() {
  try {
    nova.value = await window.hengzhang.getNovaState()
  } catch {
    /* ignore */
  }
}

/** 手动触发一次上行（同步结束后也会自动跑一次） */
async function uploadNow() {
  uploading.value = true
  try {
    const r = await window.hengzhang.uploadToNova()
    error.value = r.ok ? '' : `上行失败：${r.message}`
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    uploading.value = false
    await refreshNova()
    await refreshHistory()
  }
}

onMounted(async () => {
  off = window.hengzhang.onSyncProgress((p) => {
    const idx = items.value.findIndex((x) => x.entity === p.entity)
    if (idx >= 0) items.value[idx] = p
    else items.value.push(p)
    // 某个实体完成（含托盘定时 / 远程指令触发的同步）→ 刷新历史与状态
    if (p.status !== 'running') scheduleHistoryRefresh()
  })
  // 自动上行完成后刷新"待上报 / 上次结果"；上行历史与服务状态一并刷新
  offNova = window.hengzhang.onNovaUpload(() => scheduleHistoryRefresh())
  await refreshNova()
  await refreshHealth()
  await refreshHistory()
  // 心跳 / 定时同步的状态每分钟自然推进一次，不用用户手点刷新
  healthTimer = setInterval(() => void refreshHealth(), 60_000)

  // 进入页面先恢复上次同步结果，而不是显示 0
  try {
    lastStats.value = await window.hengzhang.lastSyncStats()
    if (lastStats.value.length) {
      items.value = items.value.map((x) => {
        const s = lastStats.value.find((t) => t.entity === x.entity)
        return s
          ? {
              entity: x.entity,
              status: s.status,
              rowCount: s.rowCount,
              message: s.message || '上次同步结果',
              finishedAt: s.finishedAt || undefined
            }
          : x
      })
    }
  } catch {
    /* ignore */
  }
})

onUnmounted(() => {
  off?.()
  offNova?.()
  if (healthTimer) clearInterval(healthTimer)
  if (refreshDebounce) clearTimeout(refreshDebounce)
})

async function start() {
  running.value = true
  error.value = ''
  localStorage.setItem('hz.resyncDate', sinceDate.value)
  items.value = items.value.map((x) => ({
    ...x,
    status: 'running',
    rowCount: 0,
    message: '排队中…'
  }))
  try {
    // 留空 = 自动：双水位增量（抓得到改过的历史单）；填了 = 从该业务日期显式重拉
    const res = await window.hengzhang.runSync({
      sinceDate: sinceDate.value || undefined
    })
    items.value = res
    lastStats.value = await window.hengzhang.lastSyncStats()
    await refreshHistory()
    await refreshHealth()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    running.value = false
  }
}

function statusLabel(s: SyncProgress['status']) {
  if (s === 'success') return '完成'
  if (s === 'failed') return '失败'
  if (s === 'skipped_no_perm') return '无权限'
  return '进行中'
}
</script>

<template>
  <section class="page">
    <PageHeader
      eyebrow="Step 02 · Sync"
      title="同步业务数据"
      subtitle="按起始日期从金蝶只读拉取。实体级提交：某一类失败不影响已成功的快照。同步前建议退出金蝶客户端。"
    >
      <HzButton variant="ghost" :disabled="running" @click="router.push('/connect')">返回连接</HzButton>
      <HzButton :loading="running" @click="start">开始同步</HzButton>
    </PageHeader>

    <div class="last-banner rise" v-if="!neverSynced">
      <template v-if="lastSyncTime">
        <b>上次同步</b>
        <span>{{ lastSyncTime }}</span>
        <span class="sep">·</span>
        <span v-for="s in lastStats" :key="s.entity" class="stat">
          {{ labels[s.entity] || s.entity }} {{ s.rowCount }}
        </span>
      </template>
    </div>

    <div class="nova-bar panel rise">
      <div class="nova-main">
        <b>对账平台</b>
        <template v-if="nova?.bound">
          <span>已绑定 {{ nova.acctName || nova.acctId }}</span>
          <span class="sep">·</span>
          <span>待上报 {{ nova.pendingRows ?? 0 }} 行</span>
        </template>
        <template v-else>
          <span class="muted">未绑定</span>
          <button class="link" :disabled="running" @click="router.push('/connect')">去绑定</button>
        </template>
      </div>
      <div class="nova-side">
        <span v-if="nova?.lastUploadResult" class="muted small">
          {{ nova.lastUploadAt }} · {{ nova.lastUploadResult }}
        </span>
        <HzButton
          variant="ghost"
          :disabled="!nova?.bound || running"
          :loading="uploading"
          @click="uploadNow"
        >
          立即上报
        </HzButton>
      </div>
    </div>

    <div class="toolbar panel rise">
      <HzField label="从指定日期重拉（可选）" hint="留空 = 自动增量（推荐）：首次全量，之后只拉新增与修改过的单，改过的历史单也能抓到；填日期则从该业务日期起显式重拉（纠错用）">
        <input v-model="sinceDate" type="date" />
      </HzField>
      <div class="note">
        <div class="note-kicker">原则</div>
        金蝶能取到的尽量自动补；无权限会标红，不会假装成 0。
      </div>
    </div>

    <div class="cards">
      <article
        v-for="(it, i) in items"
        :key="it.entity"
        class="card rise"
        :class="it.status"
        :style="{ animationDelay: `${0.06 * (i + 1)}s` }"
      >
        <div class="top">
          <div class="name">{{ labels[it.entity] || it.entity }}</div>
          <div class="badge">{{ statusLabel(it.status) }}</div>
        </div>
        <div class="count">
          <span class="num">{{ it.rowCount }}</span>
          <span class="unit">行</span>
        </div>
        <p>{{ it.message || (it.rowCount ? '上次同步结果' : '等待开始') }}</p>
      </article>
    </div>

    <div class="status-grid">
      <article class="panel status-card rise">
        <div class="sc-head">
          <span class="sc-title">服务状态</span>
          <button class="link" :disabled="probing" @click="probeService">
            {{ probing ? '检测中…' : '立即检测' }}
          </button>
        </div>
        <div class="sc-main">
          <span class="dot" :class="serviceState.cls" />
          <div class="sc-text">
            <div class="sc-state">{{ serviceState.text }}</div>
            <div class="sc-sub">{{ serviceState.sub }}</div>
          </div>
        </div>
        <div class="sc-rows">
          <div class="sc-row">
            <span>平台地址</span>
            <b>{{ health?.state.baseUrl || '—' }}</b>
          </div>
          <div class="sc-row">
            <span>绑定账套</span>
            <b>{{ health?.state.bound ? health.state.acctName || health.state.acctId || '已绑定' : '未绑定' }}</b>
          </div>
          <div class="sc-row">
            <span>指令心跳</span>
            <b>
              <template v-if="health?.heartbeat.active">
                运行中 · 每 {{ health.heartbeat.pollIntervalSec }} 秒
              </template>
              <template v-else>未运行</template>
            </b>
          </div>
        </div>
      </article>

      <article class="panel status-card rise" style="animation-delay: 0.07s">
        <div class="sc-head">
          <span class="sc-title">定时同步</span>
        </div>
        <div class="sc-main">
          <span class="dot" :class="health?.autoSync.enabled && health?.state.bound ? 'ok' : 'off'" />
          <div class="sc-text">
            <div class="sc-state">
              {{ health?.autoSync.enabled ? `每 ${health.autoSync.intervalHours} 小时自动同步` : '已关闭' }}
            </div>
            <div class="sc-sub">
              {{ autoSyncHint }}
            </div>
          </div>
        </div>
        <div class="sc-rows">
          <div class="sc-row">
            <span>上次自动同步</span>
            <b>
              <template v-if="health?.autoSync.lastRunAt">
                {{ fmtTime(health.autoSync.lastRunAt) }}
                <i v-if="health.autoSync.lastRunOk === false" class="warn">（失败）</i>
              </template>
              <template v-else>尚未自动同步（不用点「开始同步」）</template>
            </b>
          </div>
          <div class="sc-row">
            <span>下次预计</span>
            <b>{{ fmtTime(health?.autoSync.nextRunAt) }}</b>
          </div>
          <div class="sc-row">
            <span>上行水位</span>
            <b :title="health?.state.uploadWatermark || ''">
              {{ health?.state.uploadWatermark ? fmtTime(health.state.uploadWatermark) : '—' }}
            </b>
          </div>
        </div>
      </article>

      <article class="panel status-card rise" style="animation-delay: 0.14s">
        <div class="sc-head">
          <span class="sc-title">指令通道</span>
        </div>
        <div class="sc-main">
          <span class="dot" :class="health?.heartbeat.active ? 'ok' : 'off'" />
          <div class="sc-text">
            <div class="sc-state">{{ health?.heartbeat.active ? '轮询中' : '已停止' }}</div>
            <div class="sc-sub">平台可远程下发「立即同步」等指令</div>
          </div>
        </div>
        <div class="sc-rows">
          <div class="sc-row">
            <span>上次心跳</span>
            <b>{{ fmtTime(health?.heartbeat.lastOkAt) }}</b>
          </div>
          <div class="sc-row">
            <span>最近异常</span>
            <b>
              <template v-if="health?.heartbeat.lastFailAt">
                {{ fmtTime(health.heartbeat.lastFailAt) }}
                <i class="warn">{{ health.heartbeat.lastFailMessage }}</i>
              </template>
              <template v-else>无</template>
            </b>
          </div>
          <div class="sc-row">
            <span>待上报</span>
            <b>{{ health?.state.pendingRows ?? 0 }} 行</b>
          </div>
        </div>
      </article>
    </div>

    <section class="panel history-panel rise">
      <div class="hist-head">
        <b>同步历史</b>
        <button class="link" :disabled="historyLoading" @click="refreshHistory">
          {{ historyLoading ? '刷新中…' : '刷新' }}
        </button>
      </div>
      <p v-if="!history.length" class="hist-empty">
        还没有同步记录。已绑定的话大约 1 分钟后会自动跑第一次（触发列显示「定时」），无需点「开始同步」。关窗进托盘即可，不要从托盘退出。
      </p>
      <table v-else class="hist-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>类型</th>
            <th>触发</th>
            <th>状态</th>
            <th>数量</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in history" :key="row.key">
            <td class="mono">{{ row.time }}</td>
            <td>{{ row.kind }}</td>
            <td>{{ row.trigger }}</td>
            <td><span class="pill" :class="row.rowClass">{{ row.status }}</span></td>
            <td>{{ row.count }}</td>
            <td class="msg" :title="row.message">{{ row.message || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="error" class="err">{{ error }}</div>

    <div class="footer-actions">
      <HzButton variant="ghost" :disabled="running" @click="router.push('/statement')">
        前往对账导出 →
      </HzButton>
    </div>
  </section>
</template>

<style scoped>
.page {
  max-width: 1080px;
}

.panel {
  background: var(--bg-panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 18px;
  backdrop-filter: blur(16px);
}

.toolbar {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 18px;
  align-items: end;
  margin-bottom: 18px;
}

.last-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 11px 16px;
  margin-bottom: 14px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(61, 220, 151, 0.28);
  background: rgba(61, 220, 151, 0.06);
  font-size: 13px;
  color: var(--text-muted);
}

.last-banner b {
  color: #b6f5d4;
  font-weight: 600;
}

.last-banner .sep {
  color: var(--text-faint);
}

.last-banner .stat {
  color: var(--text);
}

.nova-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--text-muted);
}

.nova-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.nova-main b {
  color: var(--text);
  font-weight: 600;
}

.nova-side {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.nova-bar .sep {
  color: var(--text-faint);
}

.nova-bar .small {
  font-size: 12px;
}

.nova-bar .muted {
  color: var(--text-muted);
}

.nova-bar .link {
  background: none;
  border: none;
  padding: 0;
  color: var(--accent-2);
  cursor: pointer;
  font-size: 13px;
}

.note {
  border-left: 2px solid rgba(45, 212, 191, 0.5);
  padding-left: 14px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.note-kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--accent-2);
  margin-bottom: 4px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: var(--bg-panel-solid);
  padding: 18px;
  min-height: 160px;
}

.card.success {
  border-color: rgba(61, 220, 151, 0.35);
}
.card.failed {
  border-color: rgba(255, 107, 122, 0.4);
}
.card.skipped_no_perm {
  border-color: rgba(245, 185, 66, 0.4);
}

.top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 18px;
}

.name {
  font-weight: 700;
}

.badge {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
  color: var(--text-muted);
}

.card.success .badge {
  color: #b6f5d4;
  border-color: rgba(61, 220, 151, 0.35);
}
.card.failed .badge {
  color: #ffc2c9;
  border-color: rgba(255, 107, 122, 0.35);
}
.card.skipped_no_perm .badge {
  color: #ffe2a8;
  border-color: rgba(245, 185, 66, 0.35);
}

.count {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.num {
  font-family: var(--font-mono);
  font-size: 36px;
  font-weight: 500;
}

.unit {
  color: var(--text-faint);
  font-size: 13px;
}

p {
  margin: 12px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.err {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 107, 122, 0.1);
  border: 1px solid rgba(255, 107, 122, 0.28);
  color: #ffc2c9;
  font-size: 13px;
}

.footer-actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* ---------------- 服务状态卡片 ---------------- */

.status-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.status-card {
  padding: 16px 18px;
}

.sc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.sc-title {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--accent-2);
}

.status-card .link {
  background: none;
  border: none;
  padding: 0;
  color: var(--accent-2);
  cursor: pointer;
  font-size: 12px;
}

.status-card .link:disabled {
  cursor: default;
  opacity: 0.6;
}

.sc-main {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 99px;
  flex-shrink: 0;
  background: var(--text-faint);
}

.dot.ok {
  background: var(--ok);
  box-shadow: 0 0 10px rgba(61, 220, 151, 0.5);
}

.dot.bad {
  background: #ff6b7a;
  box-shadow: 0 0 10px rgba(255, 107, 122, 0.5);
}

.dot.off {
  background: var(--text-faint);
}

.sc-state {
  font-weight: 700;
  font-size: 14px;
}

.sc-sub {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}

.sc-rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sc-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--text-muted);
}

.sc-row b {
  color: var(--text);
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}

.sc-row .warn {
  color: #ffc2c9;
  font-style: normal;
}

/* ---------------- 同步历史 ---------------- */

.history-panel {
  margin-top: 14px;
  padding: 16px 18px;
}

.hist-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.hist-head b {
  font-size: 14px;
}

.hist-head .link {
  background: none;
  border: none;
  padding: 0;
  color: var(--accent-2);
  cursor: pointer;
  font-size: 12px;
}

.hist-head .link:disabled {
  cursor: default;
  opacity: 0.6;
}

.hist-empty {
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.hist-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.hist-table th {
  text-align: left;
  font-weight: 600;
  color: var(--text-muted);
  padding: 6px 10px 6px 0;
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}

.hist-table td {
  padding: 7px 10px 7px 0;
  border-bottom: 1px solid var(--line);
  color: var(--text);
  vertical-align: top;
}

.hist-table .mono {
  font-family: var(--font-mono);
  white-space: nowrap;
  color: var(--text-muted);
}

.hist-table .msg {
  color: var(--text-muted);
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pill {
  display: inline-block;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
  color: var(--text-muted);
  white-space: nowrap;
}

.pill.success {
  color: #b6f5d4;
  border-color: rgba(61, 220, 151, 0.35);
}

.pill.failed {
  color: #ffc2c9;
  border-color: rgba(255, 107, 122, 0.35);
}

.pill.skipped_no_perm {
  color: #ffe2a8;
  border-color: rgba(245, 185, 66, 0.35);
}

.pill.running {
  color: var(--accent-2);
  border-color: rgba(45, 212, 191, 0.35);
}

.pill.noop {
  color: var(--text-faint);
}

@media (max-width: 1100px) {
  .cards,
  .toolbar,
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
