<script setup lang="ts">
/**
 * 登录页（风险分层验证）。
 *
 * <p>核心交互：
 * <ul>
 *     <li><b>默认没有任何验证控件</b>：页面不预判风险，是否需要验证完全由服务端决定；</li>
 *     <li><b>轻验证（1014）</b>：拖动滑块拼合图片（市面主流形态，用户无需学习），
 *         前端只采集"落点 + 轨迹"，是否通过由服务端判定；</li>
 *     <li><b>强验证（1015）</b>：一次性验证码，用于风险较高或自动化特征明显的场景；</li>
 *     <li><b>降级通道</b>：无法拖动滑块（辅助设备、触屏失灵、控件不可用）时可改为一次性码，
 *         保证任何用户都有一条走得通的路；</li>
 *     <li><b>信任此设备</b>：默认勾选，验证通过后服务端记住该设备，此后无感登录。</li>
 * </ul>
 *
 * <p>为什么不在页面上预先渲染验证控件：
 * 绝大多数登录本就无风险，预渲染等于让所有用户为少数攻击买单。
 *
 * <p>提示方式（行业通行做法）：
 * 登录页的每一条信息都需要用户<b>立即行动</b>，因此一律采用<b>内联提示</b>——
 * 出现在输入框/按钮附近且不会自动消失；全局 toast 一闪而过，用户根本来不及看，
 * 且会飘到下一个页面，故登录流程不再使用（对应请求已声明 silentError）。
 *
 * <p>视觉（2026 改版）：全屏"极光舞台"背景 + <b>居中</b>登录卡——
 * 用户的鼠标自然停留在视口中心，登录框就放在那里，不违反直觉。
 * 两侧为<b>与业务完全解耦</b>的内容：左侧是通用标语（纯文案），
 * 右侧是纯 CSS 抽象轨道装饰（无文字）。未来替换业务系统时，最多改一句标语。
 */
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { type FormInstance, type FormRules } from 'element-plus';
import SlideVerify from '@/components/SlideVerify.vue';
import BrandMark from '@/components/BrandMark.vue';
import { useUserStore } from '@/store/user';
import { useAppStore } from '@/store/app';
import { BRAND_NAME } from '@/config/brand';
import type { ChallengeResp, OneTimeCodeResp } from '@/types/api';

/** 版权年份（模板插值用） */
const year = new Date().getFullYear();

/** 需要轻验证（滑块拼图） */
const VERIFY_LIGHT_REQUIRED = 1014;
/** 需要强验证（一次性码） */
const VERIFY_STRONG_REQUIRED = 1015;

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const appStore = useAppStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
/** 滑块实例：验证失败时让它带动画回弹，而不是僵在原地 */
const slideRef = ref<InstanceType<typeof SlideVerify> | null>(null);
/** 静默重取票中：此时不显示"验证中"遮罩，否则会盖住回弹动画 */
const refreshing = ref(false);

/**
 * 表单级错误（内联展示）。
 *
 * <p>为什么不用 toast：错误必须停在用户视线焦点上，且不自动消失——
 * 用户改完密码再点登录，旧提示还在眼前，才知道"这次成功了"。
 */
const errorMessage = ref('');

/** 服务端下发的验证挑战（需要验证时才存在） */
const challenge = ref<ChallengeResp | null>(null);
/** 挑战下发时刻（用于计算"从下发到提交"的总耗时） */
const issuedAt = ref(0);

/** 一次性码通道（强验证或降级时使用） */
const codeMode = ref(false);
const code = ref('');
const codeSending = ref(false);
const codeInfo = ref<OneTimeCodeResp | null>(null);
/** 重新发送验证码的倒计时（秒），0 表示可重新发送 */
const countdown = ref(0);
let countdownTimer: number | undefined;

/** 启动重新发送倒计时 */
function startCountdown(seconds: number): void {
  window.clearInterval(countdownTimer);
  countdown.value = seconds;
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      window.clearInterval(countdownTimer);
    }
  }, 1000);
}

onBeforeUnmount(() => window.clearInterval(countdownTimer));

/** 验证码区说明：已发送则显示去向与时效，否则显示服务端下发的提示 */
const verifyHint = computed(() => {
  if (!codeInfo.value) {
    return challenge.value?.hint ?? '';
  }
  const base = `验证码已发送至 ${codeInfo.value.target}，${codeInfo.value.expireSeconds} 秒内有效`;
  return codeInfo.value.devCode ? `${base}（演示环境已自动填充）` : base;
});

/** 重新发送按钮文案：倒计时期间显示剩余秒数，避免用户反复点发送 */
const resendText = computed(() => (countdown.value > 0 ? `重新发送（${countdown.value}s）` : '重新发送'));

/**
 * 验证区展开：从 0 过渡到内容实际高度。
 *
 * <p>面板是"长"出来的，不是"啪"地插进表单把下方内容顶下去——
 * 那一刻的跳动正是"突兀感"的主要来源。
 *
 * @param el 过渡元素
 */
function onVerifyEnter(el: Element): void {
  const node = el as HTMLElement;
  node.style.height = '0px';
  node.style.opacity = '0';
  void node.offsetHeight; // 强制回流，否则浏览器会把两次赋值合并成一次
  node.style.height = `${node.scrollHeight}px`;
  node.style.opacity = '1';
}

/**
 * 展开结束：清掉内联高度，交还给内容自适应。
 *
 * @param el 过渡元素
 */
function onVerifyAfterEnter(el: Element): void {
  (el as HTMLElement).style.height = '';
}

/**
 * 验证区收起：先固定当前高度再收到 0，否则高度是 auto 无法过渡。
 *
 * @param el 过渡元素
 */
function onVerifyLeave(el: Element): void {
  const node = el as HTMLElement;
  node.style.height = `${node.scrollHeight}px`;
  void node.offsetHeight;
  node.style.height = '0px';
  node.style.opacity = '0';
}

/** 后端错误码：用户名或密码错误（不区分账号是否存在，防账号枚举） */
const CODE_CREDENTIAL_ERROR = 1006;

/**
 * 凭据错误统一文案。
 *
 * <p>刻意不区分"账号不存在"与"密码错误"：
 * 区分等于帮攻击者做账号枚举，这是登录页的安全惯例。
 */
const CREDENTIAL_ERROR = '用户名或密码错误';

/**
 * 把登录/验证失败映射成面向用户的文案。
 *
 * <p>规则：网络层失败（无业务码）→ 网络提示；凭据错误 → 统一文案；
 * 其余业务异常（账号停用、账号锁定等）沿用后端文案，因为它才是准确的那一个。
 *
 * @param error 失败对象
 */
function messageOf(error: unknown): string {
  const err = error as Error & { code?: number };
  if (!err.code) {
    return '网络连接异常，请检查网络后重试';
  }
  return err.code === CODE_CREDENTIAL_ERROR ? CREDENTIAL_ERROR : err.message || '登录失败，请稍后再试';
}

/**
 * 演示凭据：只在开发构建中存在。
 *
 * <p>生产构建会把 {@code import.meta.env.DEV} 替换为 {@code false}，
 * 下面的三元式经死代码消除后，凭据字符串不会进入前端产物——
 * 只靠 {@code v-if} 在运行时隐藏是不够的：字符串本体仍会被打进 bundle。
 * 同理，占位符与页面提示也必须从这些常量取值，而不是把明文写死在模板里。
 */
const demoUsername = import.meta.env.DEV ? 'admin' : '';
const demoPassword = import.meta.env.DEV ? 'admin123' : '';
/** 输入框占位符：开发构建提示演示凭据，生产构建为中性文案 */
const usernamePlaceholder = import.meta.env.DEV ? `用户名：${demoUsername}` : '请输入用户名';
const passwordPlaceholder = import.meta.env.DEV ? `密码：${demoPassword}` : '请输入密码';

const form = reactive({
  // 演示账号只在开发环境预填：生产构建若保留默认值，等于把可用凭据写进前端产物，
  // 且登录页一打开就自动填好了（配合弱口令就是直接可用的入口）。
  username: demoUsername,
  password: demoPassword,
  /** 是否信任此设备：默认勾选 */
  trustDevice: true,
});

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 5, max: 20, message: '长度在 5 到 20 个字符', trigger: 'blur' },
  ],
};

/**
 * 提交验证（轻验证带落点与轨迹，强验证带一次性码）。
 *
 * <p>失败处理为什么是"自动重新登录"而不是退回登录页：
 * 票据被消费后必然失效，用户必须重新取票；但让他再点一次登录按钮是多余的摩擦。
 * 这里自动重新走一次登录（服务端会换发新票据、新缺口位置），
 * 界面仍停留在验证态，用户直接再拖一次即可。
 */
async function submitVerify(payload: {
  slideX?: number;
  track?: number[];
  durationMs?: number;
  startDelayMs?: number;
  elapsedMs?: number;
  code?: string;
}): Promise<void> {
  if (!challenge.value) {
    return;
  }
  loading.value = true;
  try {
    await userStore.verifyChallenge({
      ticketId: challenge.value.ticketId,
      username: form.username,
      trustDevice: form.trustDevice,
      ...payload,
    });
    await afterLogin();
  } catch (error) {
    const err = error as Error & { code?: number };
    // 1015 = 滑块已通过，但强验证还要一次性码。
    // 此时票据仍然有效（服务端只是把它标记为"人机已过"），绝不能重新登录，
    // 否则票据作废、用户白滑一次。直接进入输码阶段并请求下发 —— 发码接口正需要这个标记。
    if (err.code === 1015) {
      errorMessage.value = '';
      code.value = '';
      codeMode.value = true;
      await requestCode();
      return;
    }
    if (codeMode.value) {
      // 输码阶段失败：票据大概率还在（服务端只在尝试次数超限时才销毁），
      // 留在原界面让用户改错，不把他打回登录页重来一遍
      errorMessage.value =
        err.code === 1016 ? '验证码不正确，请重试（超过次数需重新登录）' : '验证失败，请重试';
      code.value = '';
      return;
    }
    // 1016 = 判定未通过（票据失效或行为判定失败）；内联展示，不再走 toast
    errorMessage.value = err.code === 1016 ? '验证未通过，请再试一次' : '验证失败，请重试';
    code.value = '';
    // 先撤掉"验证中"遮罩，再让滑块带动画弹回起点——回弹是用户唯一能看懂的
    // "这次没过"的信号，不能被遮住；弹完再静默重新登录换票（服务端换发新缺口）。
    // 顺序刻意如此：若先换票，滑块会被重建，回弹动画等于白播。
    loading.value = false;
    await slideRef.value?.reset();
    refreshing.value = true;
    try {
      await handleLogin(true);
    } finally {
      refreshing.value = false;
    }
  } finally {
    loading.value = false;
  }
}

/** 滑块拖动完成：把采集到的过程特征连同票据一起提交（前端不做任何判定） */
function onSlideDone(payload: {
  slideX: number;
  track: number[];
  durationMs: number;
  startDelayMs: number;
}): void {
  void submitVerify({ ...payload, elapsedMs: Date.now() - issuedAt.value });
}

/** 请求一次性验证码（强验证通道 / 无障碍降级路径） */
async function requestCode(): Promise<void> {
  if (!challenge.value) {
    return;
  }
  codeSending.value = true;
  try {
    const resp = await userStore.sendOneTimeCode({
      ticketId: challenge.value.ticketId,
      username: form.username,
    });
    codeInfo.value = resp;
    codeMode.value = true;
    // 轻验证降级时会换发新票据，后续提交必须用新票据
    if (resp.ticketId) {
      challenge.value = { ...challenge.value, ticketId: resp.ticketId, level: 'STRONG' };
    }
    // 演示环境直接回填（生产由短信/邮件下发）；去向与时效由 verifyHint 就地展示，不再弹 toast
    if (resp.devCode) {
      code.value = resp.devCode;
    }
    // 有效期倒计时即"重新发送"冷却，避免用户反复点发送造成短信轰炸
    startCountdown(resp.expireSeconds);
  } catch (error) {
    errorMessage.value = messageOf(error);
  } finally {
    codeSending.value = false;
  }
}

/** 登录成功后的收尾：主题同步 + 跳转 */
async function afterLogin(): Promise<void> {
  // 这里刻意不生成动态路由：菜单路由的"生成 + 注册"统一由路由守卫完成，
  // 否则会提前把 loaded 置为 true，导致守卫跳过 addRoute，点击菜单时匹配不到路由而白屏
  const info = await userStore.getInfo();
  if (info.user?.theme) {
    appStore.setTheme(info.user.theme as 'light' | 'dark' | 'auto');
  }
  // 不弹"欢迎回来"：登录成功本身就是结果，且 toast 会飘到跳转后的页面上，
  // 属于典型的"提示错位"；系统内原本就能看到当前用户
  const redirect = (route.query.redirect as string) ?? '/dashboard';
  await router.replace(redirect);
}

/**
 * 提交登录（第一阶段：凭据 + 风险评级）。
 *
 * @param quiet 静默模式：验证失败后自动重新取票时使用，避免重复弹提示刷屏
 */
async function handleLogin(quiet = false): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  loading.value = true;
  // 每次提交都清掉上一次的错误：用户改完再点时，旧提示不该还挂着。
  // 静默重取票（验证失败后自动续票）不在此列——那次提示正是要留给用户看的。
  if (!quiet) {
    errorMessage.value = '';
  }
  try {
    await userStore.login({
      username: form.username,
      password: form.password,
      trustDevice: form.trustDevice,
    });
    await afterLogin();
  } catch (error) {
    const err = error as Error & { code?: number; data?: unknown };
    if (err.code === VERIFY_LIGHT_REQUIRED || err.code === VERIFY_STRONG_REQUIRED) {
      // 服务端判定需要验证：验证区就地展开，说明写在该区域内（verify-hint）——
      // 用户视线本来就在卡上，不需要任何浮层提示
      challenge.value = (err.data as ChallengeResp | undefined) ?? null;
      issuedAt.value = Date.now();
      codeMode.value = false;
      code.value = '';
      if (challenge.value?.level === 'STRONG') {
        await requestCode();
      }
      return;
    }
    // 静默重取票（验证失败后自动重新登录）不覆盖已有的验证失败提示
    if (!quiet) {
      errorMessage.value = messageOf(error);
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login">
    <!-- 舞台装饰层：光斑 / 网格 / 顶部光晕（纯视觉，与业务无关） -->
    <div class="login__orb login__orb--a" aria-hidden="true" />
    <div class="login__orb login__orb--b" aria-hidden="true" />
    <div class="login__grid" aria-hidden="true" />
    <div class="login__beam" aria-hidden="true" />

    <!-- 品牌：左上角一行，用统一 BrandMark 组件，与侧边栏 / favicon 同源 -->
    <header class="login__brand">
      <BrandMark :size="32" />
      <span class="login__brand-name">{{ BRAND_NAME }}</span>
    </header>

    <!-- 三列居中构图：标语 | 登录卡 | 装饰，卡片恒在视口水平中心 -->
    <main class="login__center">
      <!-- 左：通用标语。刻意不放任何系统/业务信息，替换业务系统时无需改动 -->
      <section class="login__slogan">
        <span class="login__slogan-rule" aria-hidden="true" />
        <p class="login__slogan-cn">大道至简</p>
        <p class="login__slogan-cn login__slogan-cn--soft">以简驭繁</p>
        <p class="login__slogan-en">Simplicity is the ultimate sophistication.</p>
      </section>

      <!-- 中：登录卡（视口中心，鼠标自然落点） -->
      <section class="login__card">
        <h2 class="login__card-title">欢迎回来</h2>
        <p class="login__card-sub">请使用账号登录管理控制台</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin()">
          <!--
            表单级错误：内联、常驻、不自动消失——登录失败的正确呈现方式。
            同样走高度过渡：告警在验证区上方，若它瞬间出现，整个卡片
            （连同刚展开的验证区）会被再顶下去一次，等于跳两下。
          -->
          <Transition
            name="verify"
            @enter="onVerifyEnter"
            @after-enter="onVerifyAfterEnter"
            @leave="onVerifyLeave"
          >
            <div v-if="errorMessage" class="login__alert-slot">
              <el-alert
                class="login__alert"
                type="error"
                show-icon
                :closable="false"
                :title="errorMessage"
              />
            </div>
          </Transition>

          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              :placeholder="usernamePlaceholder"
              autocomplete="username"
              :prefix-icon="'User'"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              :placeholder="passwordPlaceholder"
              :prefix-icon="'Lock'"
            />
          </el-form-item>

          <!--
            验证区：整体带高度过渡地展开（详见 onVerifyEnter）。
            面板样式收在内层 .login__verify-panel —— 外层的 padding/边框会让
            height:0 收不干净，过渡时留出一条空白。
          -->
          <Transition
            name="verify"
            mode="out-in"
            @enter="onVerifyEnter"
            @after-enter="onVerifyAfterEnter"
            @leave="onVerifyLeave"
          >
            <!-- 轻验证：拖动滑块拼合图片（用户熟悉的主流形态） -->
            <div v-if="challenge && challenge.level === 'LIGHT' && !codeMode" key="light" class="login__verify">
              <div class="login__verify-panel">
                <p class="login__verify-hint">{{ challenge.hint }}</p>
                <SlideVerify
                  ref="slideRef"
                  :key="challenge.ticketId"
                  :target-x="challenge.targetX ?? 120"
                  :pending="loading && !refreshing"
                  @done="onSlideDone"
                />
                <button class="login__fallback" type="button" @click="requestCode">
                  无法拖动滑块？改用验证码
                </button>
              </div>
            </div>

            <!-- 强验证 / 降级通道：一次性验证码 -->
            <div v-else-if="challenge && (challenge.level === 'STRONG' || codeMode)" key="code" class="login__verify">
              <div class="login__verify-panel">
                <p class="login__verify-hint">{{ verifyHint }}</p>
                <div class="login__code">
                  <el-input
                    v-model="code"
                    placeholder="请输入 6 位验证码"
                    maxlength="6"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    :prefix-icon="'Key'"
                  />
                  <el-button
                    :loading="codeSending"
                    :disabled="countdown > 0"
                    @click="requestCode"
                  >
                    {{ resendText }}
                  </el-button>
                </div>
                <el-button
                  class="login__submit"
                  type="primary"
                  :loading="loading"
                  :disabled="code.length < 6"
                  @click="submitVerify({ code })"
                >
                  验证并登录
                </el-button>
              </div>
            </div>
          </Transition>

          <div class="login__options">
            <el-checkbox v-model="form.trustDevice">信任此设备（30 天内免验证）</el-checkbox>
          </div>

          <!--
            登录按钮常驻：需要验证时只变文案并禁用，不消失。
            按钮突然消失会让卡片高度二次跳变，用户会怀疑"我是不是点错了"。
          -->
          <el-button
            v-if="!challenge || (challenge.level === 'LIGHT' && !codeMode)"
            class="login__submit"
            :class="{ 'is-waiting': !!challenge }"
            type="primary"
            :loading="loading"
            :disabled="!!challenge"
            @click="handleLogin()"
          >
            {{ challenge ? '请先完成上方验证' : '登 录' }}
          </el-button>
        </el-form>

        <!-- 演示凭据提示仅开发构建渲染：凭据本体来自上面的常量，生产构建经 DCE 不进产物 -->
        <p v-if="demoUsername" class="login__tip">演示账号 <code>{{ demoUsername }}</code> / <code>{{ demoPassword }}</code></p>
      </section>

      <!-- 右：抽象轨道装饰。纯 CSS、无文字，永不随业务变化 -->
      <div class="login__deco" aria-hidden="true">
        <span class="login__ring login__ring--outer" />
        <span class="login__ring login__ring--mid" />
        <span class="login__ring login__ring--core" />
        <span class="login__satellite" />
      </div>
    </main>

    <footer class="login__foot">© {{ year }} Nova Admin · All Rights Reserved</footer>
  </div>
</template>

<style scoped lang="scss">
.login {
  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto;
  width: 100%;
  min-height: 100%;
  overflow: hidden;
  background-color: var(--admin-bg-color);
}

/* ============================================================
 * 舞台背景：光斑 + 网格 + 顶部光晕（亮暗两套，跟随主题）
 * ========================================================== */
.login__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(12px);
  pointer-events: none;

  &--a {
    top: -140px;
    right: -100px;
    width: 460px;
    height: 460px;
    background: radial-gradient(circle at 30% 30%, rgba(37, 99, 235, 0.14), transparent 62%);
    animation: login-orb 20s ease-in-out infinite alternate;
  }

  &--b {
    bottom: -160px;
    left: -120px;
    width: 420px;
    height: 420px;
    background: radial-gradient(circle at 60% 40%, rgba(56, 189, 248, 0.1), transparent 60%);
    animation: login-orb 26s ease-in-out infinite alternate-reverse;
  }
}

/* 亮色主题下光斑更淡，保持"浅色也有氛围" */
:global(html:not(.dark)) .login__orb--a {
  background: radial-gradient(circle at 30% 30%, rgba(37, 99, 235, 0.1), transparent 60%);
}

:global(html:not(.dark)) .login__orb--b {
  background: radial-gradient(circle at 60% 40%, rgba(14, 165, 233, 0.08), transparent 58%);
}

.login__grid {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  pointer-events: none;
  background-image:
    linear-gradient(var(--admin-border-color) 1px, transparent 1px),
    linear-gradient(90deg, var(--admin-border-color) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at 50% 42%, #000 18%, transparent 72%);
  -webkit-mask-image: radial-gradient(ellipse at 50% 42%, #000 18%, transparent 72%);
}

/* 顶部一道横向光晕：给整页"压"住重心 */
.login__beam {
  position: absolute;
  top: -120px;
  left: 50%;
  width: 720px;
  height: 240px;
  background: radial-gradient(ellipse at center, rgba(37, 99, 235, 0.12), transparent 68%);
  transform: translateX(-50%);
  pointer-events: none;
}

/* ============================================================
 * 品牌行 / 版权行：绝对定位在四角，克制的存在感
 * ========================================================== */
.login__brand {
  position: absolute;
  top: 28px;
  left: 40px;
  z-index: 2;
  display: flex;
  gap: 10px;
  align-items: center;
}

/*
 * 旧的 .login__mark 是手写的 "N" 方块字，已由 components/BrandMark.vue 取代：
 * 登录页、侧边栏、favicon 三处 logo 必须同源，否则会出现"三个地方三个 N"。
 * 这里只保留品牌行的排版。
 */
.login__brand-name {
  font-size: 12.5px;
  font-weight: 600;
  /* 大字距：让连贯字母串读起来有"逐字确认"的仪式感，接近 logo 的呼吸节奏 */
  letter-spacing: 0.32em;
  color: var(--admin-text-regular);
  /* 抵消末尾字距造成的右侧空白，保证整行视觉居中 */
  margin-right: -0.32em;
}

.login__foot {
  position: absolute;
  bottom: 20px;
  left: 0;
  width: 100%;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--admin-text-regular);
  opacity: 0.65;
  text-align: center;
  pointer-events: none;
}

/* ============================================================
 * 中央三列：标语 | 登录卡 | 装饰
 * 中列固定宽度并居中 → 卡片恒在视口水平中心
 * ========================================================== */
.login__center {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr minmax(380px, 410px) 1fr;
  align-items: center;
  width: 100%;
  padding: 96px 48px 72px;
}

/* ---------------- 左：通用标语 ---------------- */
.login__slogan {
  justify-self: end;
  padding-right: 76px;
  text-align: right;
  animation: login-rise 0.7s 0.08s cubic-bezier(0.22, 1, 0.36, 1) both;
}

/*
 * 主标语：细字重（300）+ 大字距（0.34em）+ 低饱和——
 * 高端感来自克制，不是靠大字号和渐变喊出来。
 * 字距会在行尾多出一格空白，用负 margin 补偿，保证右对齐视觉上真正贴边。
 */
.login__slogan-cn {
  margin: 0;
  margin-right: -0.34em;
  font-size: 30px;
  font-weight: 300;
  line-height: 1.75;
  letter-spacing: 0.34em;
  color: var(--admin-text-primary);
  opacity: 0.92;
}

/* 第二行再降一档透明度，两句之间形成节奏，而不是两行等重的"标语墙" */
.login__slogan-cn--soft {
  opacity: 0.6;
}

/* 细线：1px、短、半透明，只作气质点缀 */
.login__slogan-rule {
  display: inline-block;
  width: 56px;
  height: 1px;
  margin-bottom: 22px;
  background: linear-gradient(90deg, transparent, rgba(96, 165, 250, 0.55));
}

.login__slogan-en {
  max-width: 320px;
  margin: 20px 0 0 auto;
  font-size: 11.5px;
  font-weight: 400;
  line-height: 1.8;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--admin-text-regular);
  opacity: 0.5;
}

/* ---------------- 右：抽象轨道装饰 ---------------- */
.login__deco {
  position: relative;
  justify-self: start;
  width: 240px;
  height: 240px;
  margin-left: 76px;
  pointer-events: none;
  /* 整体降调：装饰是"氛围"，不是主角，不能和登录卡抢注意力 */
  opacity: 0.72;
  animation: login-rise 0.7s 0.16s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.login__ring {
  position: absolute;
  border-radius: 50%;

  /* 外环：最淡，最缓 */
  &--outer {
    inset: 0;
    border: 1px solid var(--admin-border-color);
    animation: login-spin 48s linear infinite;
  }

  &--mid {
    inset: 34px;
    border: 1px dashed var(--admin-border-strong, var(--admin-border-color));
    animation: login-spin 32s linear infinite reverse;
  }

  /* 核心环：只留一圈极淡的主色微光，作为装饰的"锚" */
  &--core {
    inset: 92px;
    background: radial-gradient(circle, rgba(37, 99, 235, 0.14), rgba(37, 99, 235, 0.03) 70%, transparent);
    border: 1px solid rgba(37, 99, 235, 0.18);
    animation: login-breathe 5s ease-in-out infinite;
  }
}

/* 轨道光点：挂在外环上公转 */
.login__satellite {
  position: absolute;
  top: -3px;
  left: 50%;
  width: 6px;
  height: 6px;
  background: var(--admin-color-primary-light, #60a5fa);
  border-radius: 50%;
  box-shadow: 0 0 10px 1px rgba(96, 165, 250, 0.4);
  opacity: 0.8;
  transform-origin: center 123px; /* 外环半径 + 光点半径 */
  animation: login-spin 12s linear infinite;
}

/* ============================================================
 * 登录卡：玻璃拟态，跟随明暗主题
 * ========================================================== */
.login__card {
  width: 100%;
  padding: 36px 34px 28px;
  background-color: var(--admin-bg-page);
  border: 1px solid var(--admin-border-color);
  border-radius: 14px;
  box-shadow: var(--admin-shadow-card);
  animation: login-rise 0.7s 0.12s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.login__card-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: var(--admin-text-primary);
  text-align: center;
}

.login__card-sub {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--admin-text-regular);
  text-align: center;
}

/*
 * 告警插槽：与验证区共用一组高度过渡。
 * 外边距写在外层会让 height:0 收不干净，所以间距放在内层告警上，
 * 由外层的 overflow:hidden 一并裁掉。
 */
.login__alert-slot {
  overflow: hidden;
}

/* 登录失败的内联告警：贴在输入框上方，常驻到下次提交 */
.login__alert {
  margin-bottom: 16px;
  border-radius: 8px;

  :deep(.el-alert__title) {
    font-size: 13px;
    line-height: 1.5;
  }
}

/*
 * 验证区外层：只负责"高度过渡"，不承载任何可见样式。
 * 虚线框 + 主色浅底是上一版的写法，出现在克制的登录卡里像一块临时补丁；
 * 现改为内层实线浅底面板，观感上是卡片里的一层凹陷，而非外来控件。
 */
.login__verify {
  overflow: hidden;
}

.login__verify-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px;
  background-color: var(--admin-bg-color);
  border: 1px solid var(--admin-border-color);
  border-radius: 10px;
}

.login__verify-hint {
  align-self: stretch;
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--admin-text-regular);
}

.login__code {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

/* 降级入口：不用 <el-button link> 以保证键盘可达与足够点击区域 */
.login__fallback {
  align-self: flex-start;
  margin-top: 10px;
  padding: 0;
  font-size: 13px;
  color: var(--admin-color-primary);
  cursor: pointer;
  background: none;
  border: none;
}

.login__options {
  margin: -4px 0 16px;
}

.login__submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 0.4em;
  text-indent: 0.4em; /* 抵消末尾字距，保证视觉居中 */
  border-radius: 8px;

  /*
   * 等待验证态：文案比"登 录"长得多，沿用 0.4em 字距会松得像要散架，
   * 这里收窄到常规字距（text-indent 同步，否则整体会偏左）。
   */
  &.is-waiting {
    letter-spacing: 0.12em;
    text-indent: 0.12em;
  }
}

.login__tip {
  margin: 18px 0 0;
  font-size: 12px;
  color: var(--admin-text-regular);
  text-align: center;

  code {
    padding: 2px 6px;
    font-size: 12px;
    color: var(--admin-tag-active-text);
    background-color: var(--admin-tag-active-bg);
    border-radius: 4px;
  }
}

/* ---------------- 动画 ---------------- */
@keyframes login-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes login-orb {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-36px, 26px, 0) scale(1.08);
  }
}

@keyframes login-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/*
 * 验证区过渡：高度由 JS 钩子给出具体数值（auto 不可过渡），
 * 透明度与位移只做点睛——面板是"长出来"的，不是"闪出来"的。
 */
.verify-enter-active,
.verify-leave-active {
  transition:
    height 0.34s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.24s ease,
    transform 0.34s cubic-bezier(0.22, 1, 0.36, 1);
}

.verify-enter-from,
.verify-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes login-breathe {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.72;
    transform: scale(1.06);
  }
}

/* 偏好减少动态效果：全部动画降级为静态 */
@media (prefers-reduced-motion: reduce) {
  .login__orb,
  .login__ring,
  .login__satellite,
  .login__slogan,
  .login__deco,
  .login__verify,
  .login__card {
    animation: none;
  }

  /* 高度过渡同样关掉：JS 钩子仍会写死起止值，于是直接切换，不再补间 */
  .verify-enter-active,
  .verify-leave-active {
    transition: none;
  }
}

/* 中屏：两侧内容退场，卡片居中 */
@media (width <= 1180px) {
  .login__center {
    display: flex;
    justify-content: center;
  }

  .login__slogan,
  .login__deco {
    display: none;
  }
}

/* 窄屏：卡片全宽 */
@media (width <= 560px) {
  .login__center {
    padding: 88px 20px 64px;
  }

  .login__brand {
    left: 20px;
  }
}
</style>
