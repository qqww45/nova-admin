<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../components/ui/PageHeader.vue'
import HzField from '../components/ui/HzField.vue'
import HzButton from '../components/ui/HzButton.vue'
import type { LastSyncStat, NovaState, SyncProgress } from '../../../shared/types'

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
  }
}

onMounted(async () => {
  off = window.hengzhang.onSyncProgress((p) => {
    const idx = items.value.findIndex((x) => x.entity === p.entity)
    if (idx >= 0) items.value[idx] = p
    else items.value.push(p)
  })
  // 自动上行完成后刷新"待上报 / 上次结果"
  offNova = window.hengzhang.onNovaUpload(() => void refreshNova())
  await refreshNova()

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

@media (max-width: 1100px) {
  .cards,
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
