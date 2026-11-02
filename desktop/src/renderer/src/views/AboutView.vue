<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import PageHeader from '../components/ui/PageHeader.vue'
import HzButton from '../components/ui/HzButton.vue'
import type { AppInfo, UpdateStatus } from '../../../shared/types'

const info = ref<AppInfo | null>(null)
const status = ref<UpdateStatus>({
  checking: false,
  available: false,
  downloading: false
})
const busy = ref(false)
let off: (() => void) | undefined

onMounted(async () => {
  info.value = await window.hengzhang.getInfo()
  status.value = await window.hengzhang.getUpdateStatus()
  off = window.hengzhang.onUpdateStatus((s) => {
    status.value = s
  })
})

onUnmounted(() => off?.())

async function check() {
  busy.value = true
  try {
    await window.hengzhang.checkUpdate()
  } catch (e) {
    status.value = {
      ...status.value,
      error: e instanceof Error ? e.message : String(e)
    }
  } finally {
    busy.value = false
  }
}

async function download() {
  busy.value = true
  try {
    await window.hengzhang.downloadUpdate()
  } finally {
    busy.value = false
  }
}

async function install() {
  await window.hengzhang.installUpdate()
}
</script>

<template>
  <section class="page">
    <PageHeader
      eyebrow="Product · Update"
      title="关于衡账"
      subtitle="独立桌面工具 · 金蝶云星空只读对账。更新覆盖程序文件，本地数据保留在用户目录。"
    >
      <HzButton variant="ghost" :loading="busy || status.checking" @click="check">检查更新</HzButton>
      <HzButton
        v-if="status.available && !status.downloading && (status.progress ?? 0) < 100"
        :loading="busy"
        @click="download"
      >
        下载 {{ status.version }}
      </HzButton>
      <HzButton v-if="(status.progress ?? 0) >= 100" @click="install">重启安装</HzButton>
    </PageHeader>

    <div class="grid">
      <article class="panel rise">
        <div class="kicker">版本</div>
        <div class="ver">{{ info?.version || '—' }}</div>
        <p>产品名 {{ info?.productName }}</p>
        <p class="path">数据目录<br /><code>{{ info?.userDataPath }}</code></p>
      </article>

      <article class="panel rise" style="animation-delay: 0.08s">
        <div class="kicker">更新通道</div>
        <h3>GitHub Releases</h3>
        <p>公开仓库 · latest.yml · 用户确认后下载安装</p>
        <div v-if="status.downloading" class="bar">
          <div class="fill" :style="{ width: `${status.progress || 0}%` }" />
        </div>
        <p v-if="status.error" class="err">{{ status.error }}</p>
        <p v-else-if="!status.available && !status.checking" class="muted">当前已是最新，或尚未配置发布仓。</p>
        <p v-else-if="status.available" class="ok">发现新版本 {{ status.version }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page {
  max-width: 900px;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel {
  background: var(--bg-panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 22px;
  backdrop-filter: blur(16px);
  box-shadow: var(--shadow);
}

.kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--accent-2);
}

.ver {
  margin-top: 10px;
  font-family: var(--font-display);
  font-size: 40px;
  font-weight: 700;
}

h3 {
  margin: 10px 0 8px;
  font-size: 20px;
}

p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.path {
  margin-top: 16px;
}

code {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text);
  word-break: break-all;
}

.bar {
  margin-top: 16px;
  height: 8px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--accent-2));
}

.err {
  margin-top: 12px;
  color: #ffc2c9;
}
.ok {
  margin-top: 12px;
  color: #b6f5d4;
}
.muted {
  margin-top: 12px;
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
