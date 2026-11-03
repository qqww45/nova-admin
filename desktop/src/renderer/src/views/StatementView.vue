<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '../components/ui/PageHeader.vue'
import HzField from '../components/ui/HzField.vue'
import HzButton from '../components/ui/HzButton.vue'
import HzSelect from '../components/ui/HzSelect.vue'
import type { CustomerOption, StatementDTO } from '../../../shared/types'

const customers = ref<CustomerOption[]>([])
const customerFid = ref<number | null>(null)
// 默认查最近 3 个完整月（含本月）：写死固定年月会在时间推移后默认查到空区间，
// 业务员误以为"同步坏了"；首次进入仍以 localStorage 记忆优先
function rollingMonth(offset: number) {
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() - offset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
const fromMonth = ref(localStorage.getItem('hz.fromMonth') || rollingMonth(2))
const toMonth = ref(localStorage.getItem('hz.toMonth') || rollingMonth(0))
const activeMonth = ref(0)
const dto = ref<StatementDTO | null>(null)
const loading = ref(false)
const exporting = ref(false)
const message = ref('')

const sheet = computed(() => dto.value?.sheets[activeMonth.value] || null)

const customerOptions = computed(() =>
  customers.value.map((c) => ({ value: c.fid, label: c.fname, sub: c.fnumber }))
)

const customerCount = computed(() => customers.value.length)

function onCustomerPick(v: string | number) {
  customerFid.value = Number(v)
}

onMounted(async () => {
  customers.value = await window.hengzhang.listCustomers()
  // 恢复上次选择的客户，其次默认第一个
  const saved = Number(localStorage.getItem('hz.customerFid'))
  const hit = customers.value.find((c) => c.fid === saved)
  if (customers.value.length) customerFid.value = (hit || customers.value[0]).fid
  // 条件齐全时自动预览，省一次点击
  if (customerFid.value) preview()
})

watch([customerFid, fromMonth, toMonth], () => {
  if (customerFid.value) localStorage.setItem('hz.customerFid', String(customerFid.value))
  localStorage.setItem('hz.fromMonth', fromMonth.value)
  localStorage.setItem('hz.toMonth', toMonth.value)
  dto.value = null
  activeMonth.value = 0
})

async function preview() {
  if (!customerFid.value) {
    message.value = '请先同步并选择客户'
    return
  }
  loading.value = true
  message.value = ''
  try {
    dto.value = await window.hengzhang.previewStatement({
      customerFid: customerFid.value,
      fromMonth: fromMonth.value,
      toMonth: toMonth.value
    })
    activeMonth.value = 0
  } catch (e) {
    message.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function exportXlsx() {
  if (!customerFid.value) return
  exporting.value = true
  message.value = ''
  try {
    const { path } = await window.hengzhang.exportXlsx({
      customerFid: customerFid.value,
      fromMonth: fromMonth.value,
      toMonth: toMonth.value
    })
    message.value = `已导出：${path}`
    await window.hengzhang.showItemInFolder(path)
  } catch (e) {
    message.value = e instanceof Error ? e.message : String(e)
  } finally {
    exporting.value = false
  }
}

function money(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<template>
  <section class="page">
    <PageHeader
      eyebrow="Step 03 · Statement"
      title="对账单"
      subtitle="按纳斯达金样板结构预览：按月明细 + 页脚汇总。导出为 Excel，可直接打印签字。"
    >
      <HzButton variant="ghost" :loading="loading" @click="preview">预览</HzButton>
      <HzButton :loading="exporting" :disabled="!dto" @click="exportXlsx">导出 Excel</HzButton>
    </PageHeader>

    <div class="filters panel rise">
      <HzField label="客户" :hint="customerCount ? `${customerCount} 家，输入名称或编码可搜索` : '请先同步客户'">
        <HzSelect
          :model-value="customerFid"
          :options="customerOptions"
          placeholder="输入客户名称或编码搜索"
          @update:model-value="onCustomerPick"
        />
      </HzField>
      <HzField label="起始月">
        <input v-model="fromMonth" type="month" />
      </HzField>
      <HzField label="结束月">
        <input v-model="toMonth" type="month" />
      </HzField>
    </div>

    <div v-if="message" class="msg">{{ message }}</div>

    <div v-if="dto" class="ledger rise" style="animation-delay: 0.08s">
      <div class="ledger-top">
        <div>
          <div class="co">{{ dto.companyName }}</div>
          <div class="cust">{{ dto.customerName }}</div>
        </div>
        <div class="tabs">
          <button
            v-for="(s, i) in dto.sheets"
            :key="s.monthKey"
            type="button"
            class="tab"
            :class="{ active: i === activeMonth }"
            @click="activeMonth = i"
          >
            {{ s.footer.month }}
          </button>
        </div>
      </div>

      <div v-if="sheet" class="sheet">
        <div class="period">{{ sheet.monthLabel }}</div>
        <div class="meta-grid">
          <div><span>公司地址</span>{{ dto.companyAddress || '—' }}</div>
          <div><span>联系电话</span>{{ dto.companyPhone || '—' }}</div>
          <div><span>客户地址</span>{{ dto.customerAddress || '—' }}</div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>送货日期</th>
                <th>送货单号</th>
                <th>采购订单号</th>
                <th>销售订单号</th>
                <th>物料号</th>
                <th>产品名称</th>
                <th>单位</th>
                <th class="num">数量</th>
                <th class="num">单价</th>
                <th class="num">金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!sheet.lines.length">
                <td colspan="10" class="empty">该月暂无明细。请先完成出库分录同步，或稍后手补。</td>
              </tr>
              <tr v-for="(line, idx) in sheet.lines" :key="idx">
                <td>{{ line.date }}</td>
                <td>{{ line.deliveryNo }}</td>
                <td>{{ line.purchaseOrderNo }}</td>
                <td>{{ line.salesOrderNo }}</td>
                <td>{{ line.materialNumber }}</td>
                <td>{{ line.materialName }}</td>
                <td>{{ line.unit }}</td>
                <td class="num">{{ line.qty }}</td>
                <td class="num">{{ money(line.price) }}</td>
                <td class="num" :class="{ neg: line.amount < 0 }">{{ money(line.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="totals">
          <div class="total-pill">
            <span>本月对账金额</span>
            <strong>{{ money(sheet.totalAmount) }}</strong>
          </div>
        </div>

        <div class="footer-table">
          <div class="ft-title">滚动汇总</div>
          <table>
            <thead>
              <tr>
                <th>月份</th>
                <th class="num">对账金额</th>
                <th class="num">已付</th>
                <th class="num">未付</th>
                <th class="num">已开票</th>
                <th class="num">未开票</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in sheet.cumulative" :key="c.month">
                <td>{{ c.month }}</td>
                <td class="num">{{ money(c.statementAmount) }}</td>
                <td class="num">{{ money(c.paidAmount) }}</td>
                <td class="num">{{ money(c.unpaidAmount) }}</td>
                <td class="num">{{ money(c.invoicedAmount) }}</td>
                <td class="num">{{ money(c.uninvoicedAmount) }}</td>
              </tr>
            </tbody>
          </table>
          <div class="sign">制单人：{{ dto.preparerName }}　　复核：____________</div>
        </div>
      </div>
    </div>

    <div v-else class="placeholder panel rise">
      选择客户与月份后点击「预览」。若列表为空，请先到「同步」拉取客户与出库。
    </div>
  </section>
</template>

<style scoped>
.page {
  max-width: 1180px;
}

.panel {
  background: var(--bg-panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
  backdrop-filter: blur(16px);
}

.filters {
  display: grid;
  grid-template-columns: 1.4fr 0.8fr 0.8fr;
  gap: 14px;
  margin-bottom: 14px;
  /* 面板的 backdrop-filter/transform 会形成层叠上下文，
     提高层级避免下方的对账单预览盖住客户搜索下拉 */
  position: relative;
  z-index: 30;
}

.msg {
  margin-bottom: 12px;
  color: var(--accent-2);
  font-size: 13px;
}

.ledger {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent 120px),
    var(--bg-panel-solid);
  box-shadow: var(--shadow);
}

.ledger-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--line);
  align-items: end;
}

.co {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
}

.cust {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tab {
  appearance: none;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text-muted);
  border-radius: 999px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
}

.tab.active {
  color: #042f2e;
  background: var(--accent);
  border-color: var(--accent);
  font-weight: 700;
}

.sheet {
  padding: 18px 20px 22px;
}

.period {
  font-weight: 600;
  margin-bottom: 12px;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  margin-bottom: 16px;
  font-size: 12px;
  color: var(--text-muted);
}

.meta-grid span {
  display: block;
  color: var(--text-faint);
  margin-bottom: 2px;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.table-wrap {
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 12px;
  max-height: 380px;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

th,
td {
  padding: 10px 10px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  white-space: nowrap;
}

th {
  position: sticky;
  top: 0;
  background: #152038;
  color: var(--text-muted);
  font-weight: 600;
  z-index: 1;
}

.num {
  text-align: right;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.neg {
  color: #ff8f9b;
}

.empty {
  text-align: center !important;
  color: var(--text-faint);
  padding: 28px !important;
}

.totals {
  display: flex;
  justify-content: flex-end;
  margin: 14px 0;
}

.total-pill {
  display: flex;
  gap: 16px;
  align-items: baseline;
  padding: 12px 16px;
  border-radius: 14px;
  background: var(--accent-dim);
  border: 1px solid rgba(45, 212, 191, 0.28);
}

.total-pill span {
  color: var(--text-muted);
  font-size: 12px;
}

.total-pill strong {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 500;
  color: var(--accent-2);
}

.footer-table {
  margin-top: 8px;
}

.ft-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.sign {
  margin-top: 14px;
  color: var(--text-muted);
  font-size: 13px;
}

.placeholder {
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .filters,
  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
