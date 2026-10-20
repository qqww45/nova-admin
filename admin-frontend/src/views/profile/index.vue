<script setup lang="ts">
/**
 * 个人中心：资料修改、密码修改、主题偏好。
 */
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import ResetPasswordDialog, { type ResetPasswordSubmit } from '@/components/ResetPasswordDialog/index.vue';
import { userApi } from '@/api/user';
import { useAppStore, type ThemeMode } from '@/store/app';
import { useUserStore } from '@/store/user';

const appStore = useAppStore();
const userStore = useUserStore();

const profileRef = ref<FormInstance>();

const profile = reactive({
  nickname: '',
  email: '',
  phone: '',
  sex: 0,
});

const profileRules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
};

/** 密码改用全局重置密码弹窗（mode='profile'），体验与用户管理一致 */
const passwordDialogVisible = ref(false);

/** 加载个人资料 */
async function loadProfile(): Promise<void> {
  const data = await userApi.getProfile();
  profile.nickname = data.nickname ?? '';
  profile.email = data.email ?? '';
  profile.phone = data.phone ?? '';
  profile.sex = data.sex ?? 0;
}

/** 保存资料 */
async function saveProfile(): Promise<void> {
  const valid = await profileRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  await userApi.updateProfile({ ...profile });
  await userStore.getInfo();
  ElMessage.success('资料已更新');
}

/** 提交修改密码（弹窗 emit 出来后由父组件调 API） */
async function onChangePasswordSubmit(payload: ResetPasswordSubmit): Promise<void> {
  if (!payload.oldPassword) {
    return;
  }
  await userApi.changePassword(payload.oldPassword, payload.newPassword);
  ElMessage.success('密码修改成功');
  passwordDialogVisible.value = false;
}

/** 主题偏好（与顶栏共用 store，自动保存到后端） */
function changeTheme(mode: ThemeMode): void {
  appStore.setTheme(mode);
}

onMounted(() => {
  void loadProfile();
});
</script>

<template>
  <div class="profile page-container">
    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>基本资料</template>
          <el-form ref="profileRef" :model="profile" :rules="profileRules" label-width="90px">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.userInfo?.username" disabled />
            </el-form-item>
            <el-form-item label="所属部门">
              <el-input :model-value="userStore.userInfo?.deptName || '-'" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profile.nickname" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profile.email" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profile.phone" />
            </el-form-item>
            <el-form-item label="性别">
              <el-radio-group v-model="profile.sex">
                <el-radio :value="0">男</el-radio>
                <el-radio :value="1">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>修改密码</template>
          <p class="profile__password-desc">
            密码由 5~20 个字符组成，建议包含大小写字母、数字与符号以提升账号安全。
          </p>
          <el-button type="primary" @click="passwordDialogVisible = true">修改密码</el-button>
        </el-card>

        <el-card shadow="never" class="profile__theme">
          <template #header>主题偏好</template>
          <el-radio-group :model-value="appStore.theme" @change="(val) => changeTheme(val as ThemeMode)">
            <el-radio-button value="light">浅色</el-radio-button>
            <el-radio-button value="dark">深色</el-radio-button>
            <el-radio-button value="auto">跟随系统</el-radio-button>
          </el-radio-group>
          <p class="profile__theme-tip">主题偏好会保存到账号，换设备登录后依然生效</p>
        </el-card>
      </el-col>
    </el-row>

    <ResetPasswordDialog
      v-model="passwordDialogVisible"
      mode="profile"
      title="修改密码"
      @submit="onChangePasswordSubmit"
    />
  </div>
</template>

<style scoped lang="scss">
.profile {
  &__theme {
    margin-top: 12px;
  }

  &__theme-tip {
    margin: 12px 0 0;
    font-size: 12px;
    color: var(--admin-text-regular);
  }

  &__password-desc {
    margin: 0 0 12px;
    font-size: 13px;
    color: var(--admin-text-regular);
  }
}
</style>
