<script setup lang="ts">
/**
 * 重置密码 / 修改密码 弹窗。
 *
 * <p>使用场景：
 * <ul>
 *     <li>mode='admin'：管理员在用户管理里重置某个用户的新密码（无需旧密码）；</li>
 *     <li>mode='profile'：个人中心修改自己的密码（需要旧密码）。</li>
 * </ul>
 *
 * <p>体验规范：
 * <ul>
 *     <li>弹窗宽度、圆角、底栏对齐方式由 styles/element/global.scss 统一管控；</li>
 *     <li>输入框间距、标签间距、校验文案与全局表单规范一致；</li>
 *     <li>新密码强度分四档（弱/较弱/中等/强），用 4 段进度条 + 文字提示；</li>
 *     <li>亮色 / 暗色主题下背景、边框、遮罩全部由 CSS 令牌自动适配。</li>
 * </ul>
 */
import { computed, reactive, ref, watch } from 'vue';
import { type FormInstance, type FormRules } from 'element-plus';

export type ResetPasswordMode = 'admin' | 'profile';

export interface ResetPasswordSubmit {
  /** 新密码 */
  newPassword: string;
  /** 旧密码（仅 profile 模式） */
  oldPassword?: string;
  /**
   * 确认新密码（两种模式都随 submit 事件上抛）。
   *
   * <p>profile 模式必须随请求发给后端（ChangePasswordReq 的 confirmPassword
   * 为 @NotBlank 必填，且服务端校验两次一致）；admin 模式由调用方决定是否使用
   * （ResetPasswordReq 只有 userId + password，确认框仅做客户端校验）。
   */
  confirmPassword: string;
}

const props = withDefaults(
  defineProps<{
    /** 双向绑定显示状态 */
    modelValue: boolean;
    /** 模式：管理员重置 / 个人修改 */
    mode?: ResetPasswordMode;
    /** 弹窗标题 */
    title?: string;
    /** 被重置的用户名（管理员模式显示，让管理员一眼能确认是谁） */
    username?: string;
  }>(),
  {
    mode: 'admin',
    title: '重置密码',
    username: '',
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'submit', payload: ResetPasswordSubmit): void;
}>();

const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

/** 同步双向绑定 */
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

/** 关闭后重置表单，避免下次打开残留旧数据 */
watch(visible, (value) => {
  if (!value) {
    form.oldPassword = '';
    form.newPassword = '';
    form.confirmPassword = '';
    formRef.value?.clearValidate();
  }
});

/** 校验规则 */
const rules = computed<FormRules>(() => {
  const base: FormRules = {
    newPassword: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 5, max: 20, message: '长度在 5 到 20 个字符', trigger: 'blur' },
    ],
    confirmPassword: [
      { required: true, message: '请再次输入新密码', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          if (value !== form.newPassword) {
            callback(new Error('两次输入的密码不一致'));
          } else {
            callback();
          }
        },
        trigger: 'blur',
      },
    ],
  };
  if (props.mode === 'profile') {
    base.oldPassword = [{ required: true, message: '请输入原密码', trigger: 'blur' }];
  }
  return base;
});

/* ---------------- 密码强度 ---------------- */

interface StrengthInfo {
  /** 0~4 */
  score: number;
  /** 提示文案 */
  label: string;
  /** 4 段中已激活的颜色 */
  color: string;
  /** 整体水平条底色 */
  emptyColor: string;
}

/**
 * 简单强度评估：长度 + 大小写 + 数字 / 符号。
 *
 * <p>不依赖外部库，避免引入新依赖；
 * 评分 0~4，对应'请输入' / '弱' / '较弱' / '中等' / '强'。
 */
const strength = computed<StrengthInfo>(() => {
  const v = form.newPassword;
  if (!v) {
    return { score: 0, label: '请输入新密码', color: 'transparent', emptyColor: 'var(--admin-border-color)' };
  }
  let score = 0;
  if (v.length >= 5) score++;
  if (v.length >= 8) score++;
  if (/[A-Z]/.test(v) && /[a-z]/.test(v)) score++;
  if (/\d/.test(v) && /[^\w]/.test(v)) score++;
  score = Math.min(score, 4);
  const labels = ['请输入', '弱：仅长度达标', '较弱：建议加入字母与数字', '中等：继续加入大写或符号', '强：满足全部要求'];
  const colors = ['transparent', 'var(--el-color-danger)', 'var(--el-color-warning)', 'var(--el-color-success)', 'var(--el-color-primary)'];
  return {
    score,
    label: labels[score] ?? labels[0]!,
    color: colors[score] ?? 'transparent',
    emptyColor: 'var(--admin-border-color)',
  };
});

/* ---------------- 提交 ---------------- */

async function onSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    emit('submit', {
      newPassword: form.newPassword,
      oldPassword: props.mode === 'profile' ? form.oldPassword : undefined,
      confirmPassword: form.confirmPassword,
    });
  } finally {
    submitting.value = false;
  }
}

/** 暴露给父组件调用：父组件拿到结果后调用 close 关闭弹窗 */
function close(): void {
  visible.value = false;
}

defineExpose({ close });
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
    @keyup.enter="onSubmit"
  >
    <p v-if="mode === 'admin' && username" class="reset-password__hint">
      正在为用户
      <strong>{{ username }}</strong>
      重置密码
    </p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="84px"
      class="reset-password__form"
    >
      <el-form-item v-if="mode === 'profile'" label="原密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          placeholder="请输入原密码"
          autocomplete="current-password"
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="5~20 个字符"
          autocomplete="new-password"
        />
        <!-- 强度提示：4 段进度条 + 文字 -->
        <div class="reset-password__strength" :class="{ 'is-empty': strength.score === 0 }">
          <div class="reset-password__bar">
            <span
              v-for="i in 4"
              :key="i"
              class="reset-password__bar-segment"
              :class="{ 'is-active': i <= strength.score }"
              :style="i === strength.score ? { background: strength.color } : undefined"
            />
          </div>
          <span class="reset-password__strength-label" :style="{ color: strength.color }">{{ strength.label }}</span>
        </div>
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="请再次输入新密码"
          autocomplete="new-password"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.reset-password {
  &__hint {
    margin: 0 0 12px;
    padding: 8px 12px;
    font-size: 13px;
    color: var(--admin-text-regular);
    background-color: var(--admin-table-row-hover-bg);
    border-radius: var(--admin-radius-small);
  }

  &__strength {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    margin-top: 6px;
  }

  &__bar {
    display: flex;
    gap: 4px;
    flex: 0 0 160px;
  }

  &__bar-segment {
    flex: 1;
    height: 4px;
    border-radius: 2px;
    background-color: var(--admin-border-color);
    transition: background-color var(--admin-transition-fast) ease;

    &.is-active {
      /* 当前激活段才用 strong color，其它已激活段使用 success */
      background-color: var(--el-color-success);
    }
  }

  &__strength-label {
    flex: 1;
    font-size: 12px;
    color: var(--admin-text-regular);
  }
}
</style>
