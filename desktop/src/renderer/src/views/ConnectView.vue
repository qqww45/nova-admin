<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../components/ui/PageHeader.vue'
import HzField from '../components/ui/HzField.vue'
import HzButton from '../components/ui/HzButton.vue'
import HzSelect from '../components/ui/HzSelect.vue'
import type { DataCenterOption, MappingState, NovaState, ProbeReport } from '../../../shared/types'

const router = useRouter()
const saving = ref(false)
const testing = ref(false)
const detecting = ref(false)
const probing = ref(false)
const uploadingMapping = ref(false)
const report = ref<ProbeReport | null>(null)
const mapping = ref<MappingState | null>(null)
const poField = ref('')
const centers = ref<DataCenterOption[]>([])
const toast = ref<{ type: 'ok' | 'err'; text: string } | null>(null)
const hasPassword = ref(false)
const savedAcctName = ref('')

/** 对账平台（nova）绑定状态：令牌只在主进程，本页只拿得到状态 */
const nova = ref<NovaState | null>(null)
const novaBaseUrl = ref('')
const novaCode = ref('')
const novaBinding = ref(false)

const form = reactive({
  serverUrl: '',
  acctId: '',
  acctName: '',
  username: '',
  password: '',
  companyName: '',
  companyShortName: '',
  companyAddress: '',
  companyPhone: '',
  preparerName: ''
})

const centerOptions = computed(() =>
  centers.value.map((c) => ({ value: c.id, label: c.name, sub: c.id }))
)

const acctDisplay = computed(() => {
  const hit = centers.value.find((c) => c.id === form.acctId)
  return hit?.name || form.acctName || form.acctId || '—'
})

const serverDisplay = computed(() => form.serverUrl || '—')

onMounted(async () => {
  const cfg = await window.hengzhang.getConfig()
  form.serverUrl = cfg.serverUrl
  form.acctId = cfg.acctId
  form.acctName = cfg.acctName || ''
  savedAcctName.value = cfg.acctName || ''
  form.username = cfg.username
  form.companyName = cfg.companyName
  form.companyShortName = cfg.companyShortName
  form.companyAddress = cfg.companyAddress
  form.companyPhone = cfg.companyPhone
  form.preparerName = cfg.preparerName
  hasPassword.value = cfg.hasPassword

  // 恢复上次检测到的数据中心列表，便于显示友好名称
  try {
    const cached = localStorage.getItem('hz.dataCenters')
    if (cached) centers.value = JSON.parse(cached) as DataCenterOption[]
  } catch {
    /* ignore */
  }

  try {
    mapping.value = await window.hengzhang.getMapping()
    poField.value = mapping.value.mapping.customerPoField || ''
  } catch {
    /* ignore */
  }

  try {
    nova.value = await window.hengzhang.getNovaState()
    novaBaseUrl.value = nova.value.baseUrl
  } catch {
    /* ignore */
  }
})

/** 用 nova 页面生成的绑定码换取机器令牌并落盘 */
async function bindNovaAccount() {
  novaBinding.value = true
  toast.value = null
  try {
    const res = await window.hengzhang.bindNova({
      baseUrl: novaBaseUrl.value.trim(),
      code: novaCode.value.trim()
    })
    nova.value = res.state
    novaBaseUrl.value = res.state.baseUrl
    if (res.ok) novaCode.value = ''
    toast.value = { type: res.ok ? 'ok' : 'err', text: res.message }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  } finally {
    novaBinding.value = false
  }
}

/** 解除本机绑定（服务端令牌请在网页上吊销） */
async function unbindNovaAccount() {
  try {
    nova.value = await window.hengzhang.unbindNova()
    toast.value = { type: 'ok', text: '已解除本机绑定，可在网页「绑定管理」吊销令牌' }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  }
}

async function detect() {
  detecting.value = true
  toast.value = null
  try {
    const found = await window.hengzhang.detectEnv()
    if (found.serverUrl) form.serverUrl = found.serverUrl
    if (found.username) form.username = found.username
    if (found.acctId) form.acctId = found.acctId
    if (found.dataCenters.length) {
      centers.value = found.dataCenters
      try {
        localStorage.setItem('hz.dataCenters', JSON.stringify(found.dataCenters))
      } catch {
        /* ignore */
      }
    }
    toast.value = { type: found.serverUrl ? 'ok' : 'err', text: found.message }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  } finally {
    detecting.value = false
  }
}

function onAcctPick(v: string | number) {
  form.acctId = String(v)
}

function metaFieldsText(fields: { key: string; name: string }[]) {
  return fields.map((f) => `${f.key}(${f.name})`).join(', ')
}

const billBlocks = computed(() => {
  const bf = report.value?.meta?.billFields
  if (!bf) return []
  return [
    { label: '销售订单实例字段', data: bf.saleOrder },
    { label: '销售出库实例字段', data: bf.outstock }
  ].filter((x) => x.data) as Array<{
    label: string
    data: { total: number; custom: { key: string; value: string }[]; poHints: { key: string; value: string }[]; error?: string }
  }>
})

function resolveAcctName(): string {
  const hit = centers.value.find((c) => c.id === form.acctId.trim())
  if (hit) return hit.name
  return form.acctId.trim() === (form.acctName || '').trim() ? '' : form.acctName || savedAcctName.value || ''
}

async function save() {
  saving.value = true
  toast.value = null
  try {
    const acctName = resolveAcctName()
    const cfg = await window.hengzhang.saveConfig({
      ...form,
      acctName,
      password: form.password || undefined
    })
    hasPassword.value = cfg.hasPassword
    savedAcctName.value = cfg.acctName || ''
    form.acctName = cfg.acctName || ''
    form.password = ''
    toast.value = { type: 'ok', text: '配置已保存到本机（密码经系统加密）' }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  } finally {
    saving.value = false
  }
}

/** 跑任何联网动作前先落盘，保证主进程读到的是最新配置 */
async function saveFirst() {
  const acctName = resolveAcctName()
  const cfg = await window.hengzhang.saveConfig({
    ...form,
    acctName,
    password: form.password || undefined
  })
  hasPassword.value = cfg.hasPassword
  savedAcctName.value = cfg.acctName || ''
  form.acctName = cfg.acctName || ''
  form.password = ''
}

async function test() {
  testing.value = true
  toast.value = null
  try {
    await saveFirst()
    const res = await window.hengzhang.testConnection()
    toast.value = { type: res.ok ? 'ok' : 'err', text: res.message }
    if (res.ok) setTimeout(() => router.push('/sync'), 500)
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  } finally {
    testing.value = false
  }
}

/** 独立于「测试连接」：登录 + 抽样三个单据 + 逐套试出库分录字段集 */
async function probe() {
  probing.value = true
  toast.value = null
  report.value = null
  try {
    await saveFirst()
    report.value = await window.hengzhang.runProbe()
    toast.value = {
      type: report.value.ok ? 'ok' : 'err',
      text: report.value.ok ? '字段自检完成，可复制报告' : report.value.loginMessage || '字段自检未通过'
    }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  } finally {
    probing.value = false
  }
}

async function copyReport() {
  if (!report.value) return
  const text = JSON.stringify(report.value, null, 2)
  try {
    await navigator.clipboard.writeText(text)
    toast.value = { type: 'ok', text: '诊断报告已复制，可直接发给实施人员' }
  } catch {
    toast.value = { type: 'err', text: '复制失败，请手动选择下方文本复制' }
  }
}

async function applyMapping() {
  if (!report.value?.suggestedLineFieldKeys) return
  try {
    mapping.value = await window.hengzhang.applyMapping({
      lineFieldKeys: report.value.suggestedLineFieldKeys,
      customerPoField: poField.value.trim()
    })
    toast.value = { type: 'ok', text: `已写入本机映射：${mapping.value.path}` }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  }
}

/**
 * 自检结果一键回传：写到服务端本绑定的字段映射，运维确认后下发 config.reload 生效。
 * 不用再把报告复制给运维、运维手抄进配置页。
 */
async function uploadMappingToNova() {
  if (!report.value?.suggestedLineFieldKeys) return
  uploadingMapping.value = true
  try {
    const result = await window.hengzhang.uploadMapping({
      lineFieldKeys: report.value.suggestedLineFieldKeys,
      customerPoField: poField.value.trim() || undefined,
      note: `字段自检回传 ${new Date().toLocaleString()}`
    })
    toast.value = { type: result.ok ? 'ok' : 'err', text: result.message }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  } finally {
    uploadingMapping.value = false
  }
}

async function resetMapping() {
  try {
    mapping.value = await window.hengzhang.resetMapping()
    poField.value = ''
    toast.value = { type: 'ok', text: '已恢复为内置映射' }
  } catch (e) {
    toast.value = { type: 'err', text: e instanceof Error ? e.message : String(e) }
  }
}
</script>

<template>
  <section class="page">
    <PageHeader
      eyebrow="Step 01 · Connect"
      title="连接金蝶云"
      subtitle="可先检测本机金蝶客户端里保存过的地址。密码仍需手填，测通后进入同步。"
    >
      <HzButton variant="ghost" :loading="detecting" @click="detect">检测环境</HzButton>
      <HzButton variant="ghost" :loading="probing" @click="probe">字段自检</HzButton>
      <HzButton variant="ghost" :loading="saving" @click="save">保存</HzButton>
      <HzButton :loading="testing" @click="test">测试连接</HzButton>
    </PageHeader>

    <div class="conn-banner rise" :class="{ active: form.acctId }">
      <span class="dot" />
      <span class="conn-item"><b>服务器</b>{{ serverDisplay }}</span>
      <span class="conn-item"><b>数据中心</b>{{ acctDisplay }}</span>
      <span class="conn-item"><b>账号</b>{{ form.username || '—' }}</span>
    </div>

    <div class="grid">
      <div class="panel rise" style="animation-delay: 0.05s">
        <div class="panel-title">金蝶连接</div>
        <div class="fields">
          <HzField label="服务器地址" hint="例：https://xxx.kingdee.com — 以 M0 抓包为准">
            <input v-model="form.serverUrl" placeholder="https://" spellcheck="false" />
          </HzField>
          <div class="two">
            <HzField
              label="数据中心"
              hint="点「检测环境」后可下拉搜索选择；也可直接输入内码"
            >
              <HzSelect
                :model-value="form.acctId"
                :options="centerOptions"
                :allow-free-text="true"
                placeholder="输入内码或搜索数据中心"
                @update:model-value="onAcctPick"
              />
            </HzField>
            <HzField label="账号">
              <input v-model="form.username" autocomplete="username" />
            </HzField>
          </div>
          <HzField
            label="密码"
            :hint="hasPassword ? '已保存加密密码；留空则保持不变' : '密码仅存本机当前 Windows 用户'"
          >
            <input
              v-model="form.password"
              type="password"
              :placeholder="hasPassword ? '••••••••' : '请输入密码'"
              autocomplete="current-password"
            />
          </HzField>
        </div>
      </div>

      <div class="panel rise" style="animation-delay: 0.12s">
        <div class="panel-title">本公司信息（对账单页眉）</div>
        <div class="fields">
          <HzField label="公司全称">
            <input v-model="form.companyName" />
          </HzField>
          <div class="two">
            <HzField label="简称（文件名）">
              <input v-model="form.companyShortName" />
            </HzField>
            <HzField label="制单人">
              <input v-model="form.preparerName" />
            </HzField>
          </div>
          <HzField label="公司地址">
            <textarea v-model="form.companyAddress" />
          </HzField>
          <HzField label="联系人电话">
            <input v-model="form.companyPhone" />
          </HzField>
        </div>
      </div>
    </div>

    <div class="panel nova-panel rise">
      <div class="panel-title">对账平台（nova）绑定</div>
      <div class="fields">
        <div class="two">
          <HzField label="平台地址" hint="默认生产后端；私有部署时改这里">
            <input v-model="novaBaseUrl" :disabled="nova?.bound" placeholder="https://" spellcheck="false" />
          </HzField>
          <HzField label="绑定码" hint="在 nova「金蝶同步 → 绑定管理」生成，5 分钟有效、仅一次">
            <input
              v-model="novaCode"
              :disabled="nova?.bound"
              placeholder="8 位绑定码"
              maxlength="12"
              spellcheck="false"
            />
          </HzField>
        </div>
        <div class="nova-row">
          <span class="tag" :class="nova?.bound ? 'ok' : 'err'">
            {{ nova?.bound ? '已绑定' : '未绑定' }}
          </span>
          <span v-if="nova?.bound" class="muted">
            账套 {{ nova.acctName || nova.acctId }} · 待上报 {{ nova.pendingRows ?? 0 }} 行{{
              nova.lastUploadAt ? ` · 上次上报 ${nova.lastUploadAt}` : ''
            }}
          </span>
          <span v-else class="muted">未绑定不影响本地对账导出，绑定后每次同步会自动上报到对账平台</span>
          <span class="nova-actions">
            <HzButton v-if="!nova?.bound" :loading="novaBinding" @click="bindNovaAccount">绑定</HzButton>
            <HzButton v-else variant="ghost" @click="unbindNovaAccount">解除本机绑定</HzButton>
          </span>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast" :class="toast.type">{{ toast.text }}</div>

    <div v-if="report" class="probe rise">
      <div class="probe-head">
        <span class="probe-title">字段自检报告</span>
        <span class="muted small">{{ report.finishedAt }}</span>
        <span class="probe-actions">
          <HzButton variant="ghost" @click="copyReport">复制报告</HzButton>
          <HzButton
            variant="ghost"
            :disabled="!report.suggestedLineFieldKeys"
            @click="applyMapping"
          >
            应用到本机映射
          </HzButton>
          <HzButton
            variant="ghost"
            :disabled="!report.suggestedLineFieldKeys"
            :loading="uploadingMapping"
            @click="uploadMappingToNova"
          >
            上传到对账平台
          </HzButton>
          <HzButton variant="ghost" @click="resetMapping">恢复内置</HzButton>
        </span>
      </div>

      <div class="probe-block">
        <div class="probe-label">登录</div>
        <div class="probe-line">
          <span class="tag" :class="report.ok ? 'ok' : 'err'">{{ report.ok ? 'OK' : 'FAIL' }}</span>
          <span>{{ report.loginMessage }}</span>
        </div>
      </div>

      <div class="probe-block">
        <div class="probe-label">单据抽样</div>
        <div v-for="e in report.entities" :key="e.name" class="probe-item">
          <div class="probe-line">
            <span class="tag" :class="e.ok ? 'ok' : 'err'">{{ e.label }}</span>
            <span class="mono">{{ e.formId }}</span>
            <span class="muted">rows={{ e.rowCount }}</span>
          </div>
          <div v-if="e.message || e.sample" class="probe-line">
            <span v-if="e.message" class="muted wrap">{{ e.message }}</span>
            <span v-if="e.sample" class="mono wrap">{{ e.sample.join(' | ') }}</span>
          </div>
        </div>
        <div v-if="!report.entities.length" class="probe-line muted">未执行（登录失败）</div>
      </div>

      <div class="probe-block">
        <div class="probe-label">分录字段逐个验证（绿=该账套存在）</div>
        <div class="field-checks">
          <span
            v-for="c in report.fieldChecks || []"
            :key="c.field"
            class="tag"
            :class="c.ok ? 'ok' : 'err'"
            :title="c.error || c.field"
          >
            {{ c.field }}
          </span>
        </div>
        <div v-if="!report.fieldChecks || !report.fieldChecks.length" class="muted">
          未执行（登录失败）
        </div>
      </div>

      <div class="probe-block">
        <div class="probe-label">出库分录字段集（自动拼装优先，失败再逐套试）</div>
        <div v-for="s in report.lineFieldSets" :key="s.index" class="probe-item">
          <div class="probe-line">
            <span class="tag" :class="s.ok ? 'ok' : 'err'">#{{ s.index + 1 }} {{ s.ok ? '可用' : '失败' }}</span>
            <span class="mono wrap">{{ s.fieldKeys }}</span>
          </div>
          <div v-if="s.error || s.sample" class="probe-line">
            <span v-if="s.error" class="muted wrap">{{ s.error }}</span>
            <span v-if="s.sample" class="mono wrap">{{ s.sample.join(' | ') }}</span>
          </div>
          <div v-if="s.sampleByField" class="probe-cells">
            <div v-for="c in s.sampleByField" :key="c.field" class="probe-cell">
              <span class="mono">{{ c.field }}</span>
              <span class="tag" :class="c.type === 'number' || c.type === 'string' ? 'ok' : 'warn'">
                {{ c.type }}
              </span>
              <span class="mono wrap">{{ c.value || '（空）' }}</span>
            </div>
          </div>
        </div>
        <div v-if="!report.lineFieldSets.length" class="probe-line muted">未执行（登录失败）</div>
      </div>

      <div class="probe-block" v-if="report.meta">
        <div class="probe-label">
          单据元数据（金蝶返回的字段名+中文标题，可替代 BOS 设计器）
        </div>
        <div class="probe-line muted small">
          销售订单：共 {{ report.meta.saleOrder?.total ?? 0 }} 个字段{{
            report.meta.saleOrder?.service ? ` · ${report.meta.saleOrder.service.split('.').pop()}` : ''
          }}
          ；销售出库：共 {{ report.meta.outstock?.total ?? 0 }} 个字段
        </div>
        <div v-if="report.meta.saleOrder?.error" class="probe-line muted wrap">
          销售订单元数据失败：{{ report.meta.saleOrder.error }}
        </div>
        <div v-if="report.meta.outstock?.error" class="probe-line muted wrap">
          销售出库元数据失败：{{ report.meta.outstock.error }}
        </div>

        <div v-if="report.meta.poCandidates.length" class="probe-sub">
          疑似「客户采购订单号」字段（点一下即可填入下方输入框）：
          <span class="field-checks">
            <button
              v-for="f in report.meta.poCandidates"
              :key="f.key"
              class="po-chip"
              :title="f.name"
              @click="poField = f.key"
            >
              {{ f.key }} · {{ f.name }}
            </button>
          </span>
        </div>
        <div v-else class="probe-line muted">未找到明显匹配的采购订单号字段</div>

        <div v-if="report.meta.outstock?.fields?.length" class="probe-sub">
          <div class="muted small">出库单相关字段（数量/单价/金额/单位/物料等）：</div>
          <div class="mono wrap small">{{ metaFieldsText(report.meta.outstock.fields) }}</div>
        </div>

        <template v-if="report.meta.billFields">
          <div
            v-for="bf in billBlocks"
            :key="bf.label"
            class="probe-sub"
          >
            <div class="muted small">
              {{ bf.label }}：{{ bf.data.error ? `读取失败（${bf.data.error}）` : `共 ${bf.data.total} 个字段值` }}
            </div>
            <div v-if="bf.data.poHints.length" class="probe-sub">
              疑似订单号/PO 字段（点一下填入）：
              <span class="field-checks">
                <button
                  v-for="f in bf.data.poHints"
                  :key="f.key"
                  class="po-chip"
                  :title="f.value"
                  @click="poField = f.key"
                >
                  {{ f.key }} = {{ f.value }}
                </button>
              </span>
            </div>
            <div v-if="bf.data.custom.length" class="probe-sub">
              自定义字段（F_ 开头，值已列出）：
              <span class="field-checks">
                <button
                  v-for="f in bf.data.custom"
                  :key="f.key"
                  class="po-chip ghost"
                  :title="f.value"
                  @click="poField = f.key"
                >
                  {{ f.key }} = {{ f.value }}
                </button>
              </span>
            </div>
          </div>
        </template>
      </div>

      <div class="probe-block">
        <div class="probe-label">客户采购订单号字段（自定义字段，需在金蝶确认后填写）</div>
        <input v-model="poField" placeholder="如 F_PO_No，留空则该列为空" spellcheck="false" />
        <div class="muted small">
          当前生效映射：{{ mapping?.path || '—' }}{{ mapping?.userOverride ? '（本机已覆盖）' : '（内置）' }}
        </div>
      </div>
    </div>

    <aside class="tips rise" style="animation-delay: 0.18s">
      <div class="tips-title">连接说明</div>
      <ol>
        <li>点「检测环境」会读取本机金蝶客户端曾经保存的服务器地址、数据中心和账号。读不到的项保持手填。</li>
        <li>登录界面第一项「数据中心」对应本页的内码；检测到多个数据中心时可直接下拉搜索中文名选择，名称会随配置一起保存并在页顶展示。</li>
        <li>测连前建议先退出金蝶客户端，避免同账号会话互踢。</li>
        <li>本工具只读查询，不会改写金蝶单据。</li>
        <li>「字段自检」独立于测试连接：额外抽样客户 / 销售订单 / 出库单，并逐套试出库分录字段集，用于锁定数量、单价、金额、单位、源单号的真实字段名。结果可复制回传，也可把通过的字段集写入本机映射。</li>
      </ol>
    </aside>
  </section>
</template>

<style scoped>
.page {
  max-width: 1080px;
}

.grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 16px;
}

.panel {
  background: var(--bg-panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 20px;
  backdrop-filter: blur(16px);
  box-shadow: var(--shadow);
}

/* 含数据中心下拉的面板要在右侧公司信息面板之上，避免下拉被盖住 */
.grid .panel:first-child {
  position: relative;
  z-index: 30;
}

.panel-title {
  font-weight: 700;
  margin-bottom: 16px;
  font-size: 15px;
}

.fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.nova-panel {
  margin-top: 16px;
}

.nova-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
}

.nova-row .nova-actions {
  margin-left: auto;
}

.nova-row .muted {
  color: var(--text-muted);
}

.nova-row .tag.ok {
  background: rgba(61, 220, 151, 0.12);
  border-color: rgba(61, 220, 151, 0.3);
  color: #b6f5d4;
}

.nova-row .tag.err {
  background: rgba(255, 107, 122, 0.12);
  border-color: rgba(255, 107, 122, 0.32);
  color: #ffc2c9;
}

.toast {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 13px;
  border: 1px solid var(--line);
}

.conn-banner {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  padding: 12px 16px;
  margin-bottom: 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.02);
  font-size: 13px;
  color: var(--text-muted);
}

.conn-banner.active {
  border-color: rgba(61, 220, 151, 0.35);
}

.conn-banner .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-faint);
  flex-shrink: 0;
}

.conn-banner.active .dot {
  background: var(--ok);
  box-shadow: 0 0 0 3px rgba(61, 220, 151, 0.18);
}

.conn-item b {
  display: inline-block;
  margin-right: 8px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
  font-weight: 600;
}

.conn-item {
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.toast.ok {
  background: rgba(61, 220, 151, 0.1);
  border-color: rgba(61, 220, 151, 0.28);
  color: #b6f5d4;
}
.toast.err {
  background: rgba(255, 107, 122, 0.1);
  border-color: rgba(255, 107, 122, 0.28);
  color: #ffc2c9;
}

.tips {
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  border: 1px dashed var(--line-strong);
  background: rgba(255, 255, 255, 0.02);
}

.tips-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.probe {
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line);
  background: var(--bg-panel);
}

.probe-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.probe-title {
  font-weight: 700;
  font-size: 14px;
}

.probe-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.probe-block {
  margin-top: 14px;
}

.probe-label {
  font-size: 11px;
  letter-spacing: 0.08em;
  font-weight: 600;
  color: var(--text-faint);
  margin-bottom: 6px;
}

.probe-item {
  padding: 4px 0;
}

.probe-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  padding: 3px 0;
  font-size: 12.5px;
  color: var(--text-muted);
}

.probe input {
  width: 100%;
}

.mono {
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 12px;
  color: var(--text);
}

.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
}

.tag.ok {
  background: rgba(61, 220, 151, 0.12);
  border-color: rgba(61, 220, 151, 0.3);
  color: #b6f5d4;
}

.tag.err {
  background: rgba(255, 107, 122, 0.12);
  border-color: rgba(255, 107, 122, 0.32);
  color: #ffc2c9;
}

.tag.warn {
  background: rgba(245, 185, 66, 0.12);
  border-color: rgba(245, 185, 66, 0.35);
  color: #ffe2a8;
}

.probe-cells {
  margin-top: 6px;
  padding-left: 10px;
  border-left: 1px solid var(--line);
}

.field-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.probe-sub {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.po-chip {
  border: 1px solid var(--line-strong);
  background: var(--accent-dim);
  color: var(--accent-2);
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
}

.po-chip:hover {
  border-color: rgba(45, 212, 191, 0.55);
}

.po-chip.ghost {
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-muted);
  border-color: var(--line);
}

.probe-cell {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 3px 0;
  font-size: 12px;
  flex-wrap: wrap;
}

.probe-cell .mono:first-child {
  min-width: 170px;
  color: var(--text);
}

.muted {
  color: var(--text-muted);
}

.small {
  font-size: 12px;
}

.wrap {
  word-break: break-all;
}

ol {
  margin: 0;
  padding-left: 18px;
  color: var(--text-muted);
  line-height: 1.7;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
