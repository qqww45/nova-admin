<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const steps = [
  { id: 'connect', label: '连接', hint: '金蝶云账号', path: '/connect' },
  { id: 'sync', label: '同步', hint: '拉取单据', path: '/sync' },
  { id: 'statement', label: '对账', hint: '预览导出', path: '/statement' }
]

const activeStep = computed(() => Number(route.meta.step || 0))
</script>

<template>
  <div class="shell">
    <div class="title-drag" />
    <div class="aurora" aria-hidden="true" />
    <div class="grain" aria-hidden="true" />

    <aside class="rail">
      <div class="brand rise">
        <div class="mark" aria-hidden="true">衡</div>
        <div class="brand-copy">
          <div class="name">衡账</div>
          <div class="tag">星空对账 · 单机版</div>
        </div>
      </div>

      <nav class="steps">
        <button
          v-for="(s, i) in steps"
          :key="s.id"
          class="step"
          :class="{ active: route.path === s.path, done: activeStep > i + 1 }"
          type="button"
          @click="router.push(s.path)"
        >
          <span class="idx">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="meta">
            <span class="label">{{ s.label }}</span>
            <span class="hint">{{ s.hint }}</span>
          </span>
          <span class="pip" />
        </button>
      </nav>

      <button class="about-link" type="button" @click="router.push('/about')">
        关于与更新
      </button>
    </aside>

    <main class="stage">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<style scoped>
.shell {
  height: 100%;
  display: grid;
  grid-template-columns: 248px 1fr;
  position: relative;
  background: var(--bg-shell);
}

.title-drag {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--titlebar);
  z-index: 50;
  -webkit-app-region: drag;
}

.aurora {
  pointer-events: none;
  position: absolute;
  inset: -20% -10% auto auto;
  width: 70vw;
  height: 70vh;
  background:
    radial-gradient(circle at 30% 40%, rgba(45, 212, 191, 0.18), transparent 55%),
    radial-gradient(circle at 70% 20%, rgba(56, 120, 255, 0.12), transparent 50%);
  filter: blur(8px);
}

.grain {
  pointer-events: none;
  position: absolute;
  inset: 0;
  opacity: 0.05;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  mix-blend-mode: soft-light;
}

.rail {
  position: relative;
  z-index: 1;
  padding: calc(var(--titlebar) + 18px) 18px 20px;
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, rgba(11, 18, 32, 0.2), rgba(7, 11, 20, 0.55));
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  -webkit-app-region: no-drag;
}

.mark {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 20px;
  color: #042f2e;
  background: linear-gradient(145deg, var(--accent-2), var(--accent));
  box-shadow: 0 10px 30px rgba(45, 212, 191, 0.25);
}

.name {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 22px;
  letter-spacing: 0.04em;
}

.tag {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
  -webkit-app-region: no-drag;
}

.step {
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 14px;
  padding: 12px 12px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  align-items: center;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.step:hover {
  background: rgba(255, 255, 255, 0.03);
  transform: translateX(2px);
}

.step.active {
  background: var(--accent-dim);
  border-color: rgba(45, 212, 191, 0.35);
}

.idx {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-faint);
}

.step.active .idx {
  color: var(--accent-2);
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.label {
  font-weight: 600;
  font-size: 14px;
}

.hint {
  font-size: 12px;
  color: var(--text-muted);
}

.pip {
  width: 8px;
  height: 8px;
  border-radius: 99px;
  background: transparent;
  border: 1px solid var(--line-strong);
}

.step.active .pip {
  background: var(--accent);
  border-color: var(--accent);
  animation: pulse-glow 2.4s ease-in-out infinite;
}

.step.done .pip {
  background: var(--ok);
  border-color: var(--ok);
}

.about-link {
  margin-top: auto;
  appearance: none;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.02);
  color: var(--text-muted);
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  -webkit-app-region: no-drag;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.about-link:hover {
  color: var(--text);
  border-color: var(--line-strong);
  background: rgba(255, 255, 255, 0.04);
}

.stage {
  position: relative;
  z-index: 1;
  min-width: 0;
  padding: calc(var(--titlebar) + 8px) 28px 24px;
  overflow: auto;
}

.page-enter-active,
.page-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
