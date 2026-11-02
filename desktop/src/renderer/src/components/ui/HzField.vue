<script setup lang="ts">
defineProps<{
  label: string
  hint?: string
}>()
</script>

<template>
  <label class="field">
    <span class="label">{{ label }}</span>
    <slot />
    <span v-if="hint" class="hint">{{ hint }}</span>
  </label>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
  font-weight: 600;
}

.hint {
  font-size: 12px;
  color: var(--text-faint);
}

:deep(input),
:deep(select),
:deep(textarea) {
  width: 100%;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: var(--bg-input);
  padding: 12px 14px;
  outline: none;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

:deep(input:focus),
:deep(select:focus),
:deep(textarea:focus) {
  border-color: rgba(45, 212, 191, 0.55);
  box-shadow: 0 0 0 3px rgba(45, 212, 191, 0.12);
}

:deep(textarea) {
  min-height: 84px;
  resize: vertical;
}
</style>
