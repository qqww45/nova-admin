<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'danger'
    loading?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'primary', type: 'button' }
)
</script>

<template>
  <button
    class="btn"
    :class="[variant, { loading }]"
    :disabled="disabled || loading"
    :type="type"
  >
    <span v-if="loading" class="spinner" />
    <slot />
  </button>
</template>

<style scoped>
.btn {
  appearance: none;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 10px 18px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition:
    transform 0.15s ease,
    background 0.15s ease,
    border-color 0.15s ease,
    opacity 0.15s ease;
}

.btn:hover:not(:disabled) {
  transform: translateY(-1px);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.primary {
  background: linear-gradient(135deg, var(--accent-2), var(--accent));
  color: #042f2e;
  box-shadow: 0 10px 30px rgba(45, 212, 191, 0.22);
}
.ghost {
  background: rgba(255, 255, 255, 0.03);
  border-color: var(--line-strong);
  color: var(--text);
}
.danger {
  background: rgba(255, 107, 122, 0.12);
  border-color: rgba(255, 107, 122, 0.35);
  color: #ffc2c9;
}

.spinner {
  width: 14px;
  height: 14px;
  border-radius: 99px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
