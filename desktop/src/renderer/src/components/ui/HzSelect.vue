<script setup lang="ts">
import { computed, ref } from 'vue'

interface HzSelectOption {
  value: string | number
  label: string
  sub?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: string | number | null
    options: HzSelectOption[]
    placeholder?: string
    allowFreeText?: boolean
    emptyText?: string
  }>(),
  { placeholder: '输入可搜索…', allowFreeText: false, emptyText: '无匹配结果' }
)

const emit = defineEmits<{ (e: 'update:modelValue', v: string | number): void }>()

const open = ref(false)
const query = ref('')
const active = ref(0)

const selected = computed(() => props.options.find((o) => o.value === props.modelValue))
const inputValue = computed(() => (open.value ? query.value : selected.value?.label ?? ''))

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter(
    (o) => o.label.toLowerCase().includes(q) || (o.sub ? o.sub.toLowerCase().includes(q) : false)
  )
})

function onFocus() {
  open.value = true
  query.value = ''
  active.value = 0
}

function onInput(e: Event) {
  query.value = (e.target as HTMLInputElement).value
  open.value = true
  active.value = 0
}

function commit(o: HzSelectOption) {
  emit('update:modelValue', o.value)
  query.value = ''
  open.value = false
}

function commitFreeText() {
  if (!props.allowFreeText) return
  const t = query.value.trim()
  if (t) emit('update:modelValue', t)
  query.value = ''
  open.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    if (!open.value) {
      open.value = true
      return
    }
    if (e.key === 'ArrowDown') active.value = Math.min(active.value + 1, filtered.value.length - 1)
    else active.value = Math.max(active.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const o = filtered.value[active.value]
    if (o) commit(o)
    else commitFreeText()
  } else if (e.key === 'Escape') {
    query.value = ''
    open.value = false
  }
}

function onBlur() {
  // mousedown.prevent 保证了点选项不会先触发 blur 丢数据，这里延迟收起
  setTimeout(() => {
    open.value = false
    query.value = ''
  }, 120)
}
</script>

<template>
  <div class="hz-select" :class="{ open }">
    <input
      :value="inputValue"
      :placeholder="selected ? selected.label : placeholder"
      spellcheck="false"
      autocomplete="off"
      @focus="onFocus"
      @input="onInput"
      @keydown="onKeydown"
      @blur="onBlur"
    />
    <span class="caret" aria-hidden="true">▾</span>
    <ul v-if="open" class="list">
      <li v-if="!filtered.length" class="empty">
        {{ emptyText }}
        <button v-if="allowFreeText && query.trim()" class="use-text" @mousedown.prevent="commitFreeText">
          使用「{{ query.trim() }}」
        </button>
      </li>
      <li
        v-for="(o, i) in filtered"
        :key="String(o.value)"
        :class="{ active: i === active }"
        @mousedown.prevent="commit(o)"
        @mouseenter="active = i"
      >
        <span class="label">{{ o.label }}</span>
        <span v-if="o.sub" class="sub">{{ o.sub }}</span>
        <span v-if="o.value === modelValue" class="check">✓</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.hz-select {
  position: relative;
}

input {
  width: 100%;
  padding-right: 30px;
}

.caret {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-faint);
  pointer-events: none;
  font-size: 12px;
}

.list {
  position: absolute;
  z-index: 40;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: 260px;
  overflow-y: auto;
  margin: 0;
  padding: 6px;
  list-style: none;
  background: var(--bg-elevated);
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}

li.active {
  background: var(--accent-dim);
}

.label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 11px;
  flex-shrink: 0;
}

.check {
  color: var(--accent);
  flex-shrink: 0;
}

.empty {
  color: var(--text-muted);
  cursor: default;
  flex-wrap: wrap;
}

.use-text {
  border: none;
  background: var(--accent-dim);
  color: var(--accent-2);
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}
</style>
