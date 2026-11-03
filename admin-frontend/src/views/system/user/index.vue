<script setup lang="ts">
/**
 * 用户管理：搜索 + 新增/编辑 + 启停 + 重置密码 + 批量删除。
 *
 * <p>列表数据由 useTable 统一管理，表格结构由 ProTable 承载。
 * 角色下拉与部门树为表单所需，进入页面时一次性加载。
 */
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import ResetPasswordDialog, { type ResetPasswordSubmit } from '@/components/ResetPasswordDialog/index.vue';
import TableActionBar, { type TableActionItem } from '@/components/TableActionBar/index.vue';
import { useTable } from '@/composables/useTable';
import { deptApi } from '@/api/menu';
import { userApi } from '@/api/user';
import type { DeptVo, RoleOptionResp, UserResp, UserSaveReq } from '@/types/api';

/** 列定义 */
const columns = [
  { prop: 'username', label: '用户名', minWidth: 110 },
  { prop: 'nickname', label: '昵称', minWidth: 110 },
  { prop: 'phone', label: '手机号', minWidth: 120 },
  { prop: 'email', label: '邮箱', minWidth: 160 },
  { prop: 'deptName', label: '部门', minWidth: 100 },
  { prop: 'roleNames', label: '角色', minWidth: 140, slot: true },
  { prop: 'status', label: '状态', width: 90, slot: true },
  { prop: 'createTime', label: '创建时间', minWidth: 160 },
];

const { loading, list, total, pageNum, pageSize, query, reload, reset, onPageChange, onSizeChange } =
  // 不显式指定泛型参数：行类型由 fetcher 返回值推断，查询类型由初始条件推断
  useTable((params) => userApi.page(params as never), {
    username: '',
    phone: '',
    status: undefined as number | undefined,
  });

/** 角色与部门选项 */
const roleOptions = ref<RoleOptionResp[]>([]);
const deptTree = ref<DeptVo[]>([]);

/** 弹窗状态 */
const dialogVisible = ref(false);
const dialogTitle = ref('');
const formRef = ref<FormInstance>();
/** 弹窗内加载态：详情拉取期间置 true，避免网速慢时用户对空表单误操作 */
const formLoading = ref(false);
const selectedIds = ref<string[]>([]);

const form = reactive<UserSaveReq>({
  id: undefined,
  username: '',
  nickname: '',
  password: '',
  email: '',
  phone: '',
  sex: 0,
  status: 0,
  deptId: undefined,
  roleIds: [],
  remark: '',
});

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ min: 5, max: 20, message: '长度在 5 到 20 个字符', trigger: 'blur' }],
  roleIds: [{ required: true, message: '请至少选择一个角色', trigger: 'change' }],
};

/** 打开新增弹窗 */
function openCreate(): void {
  dialogTitle.value = '新增用户';
  Object.assign(form, {
    id: undefined,
    username: '',
    nickname: '',
    password: '',
    email: '',
    phone: '',
    sex: 0,
    status: 0,
    deptId: undefined,
    roleIds: [],
    remark: '',
  });
  dialogVisible.value = true;
}

/** 打开编辑弹窗 */
async function openEdit(row: UserResp): Promise<void> {
  dialogTitle.value = '编辑用户';
  // 先置空表单并弹窗，避免网速慢时长时间无响应；详情在框内加载，失败则关窗（错误已由 http 拦截器统一提示）
  Object.assign(form, {
    id: undefined,
    username: '',
    nickname: '',
    password: '',
    email: '',
    phone: '',
    sex: 0,
    status: 0,
    deptId: undefined,
    roleIds: [],
    remark: '',
  });
  dialogVisible.value = true;
  formLoading.value = true;
  try {
    const detail = await userApi.detail(row.id);
    Object.assign(form, {
      id: detail.id,
      username: detail.username,
      nickname: detail.nickname,
      password: '',
      email: detail.email,
      phone: detail.phone,
      sex: detail.sex,
      status: detail.status,
      deptId: detail.deptId,
      roleIds: detail.roleIds ?? [],
      remark: detail.remark,
    });
  } catch {
    dialogVisible.value = false;
  } finally {
    formLoading.value = false;
  }
}

/** 提交表单 */
async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (form.id) {
    await userApi.update({ ...form, password: form.password || undefined });
  } else {
    await userApi.create(form);
  }
  ElMessage.success('保存成功');
  dialogVisible.value = false;
  reload();
}

/** 启停用户 */
async function toggleStatus(row: UserResp): Promise<void> {
  const next = row.status === 0 ? 1 : 0;
  try {
    await userApi.updateStatus(row.id, next);
    row.status = next;
    ElMessage.success(next === 0 ? '已启用' : '已停用');
  } catch {
    // 失败时状态由后端拒绝，界面保持不变
  }
}

/** 重置密码弹窗：状态与当前行 */
const passwordDialogVisible = ref(false);
const passwordTarget = ref<UserResp | null>(null);

/** 打开重置密码弹窗 */
function openResetPassword(row: UserResp): void {
  passwordTarget.value = row;
  passwordDialogVisible.value = true;
}

/** 提交重置密码（弹窗 emit 出来后由父组件调 API） */
async function onResetPasswordSubmit(payload: ResetPasswordSubmit): Promise<void> {
  if (!passwordTarget.value) {
    return;
  }
  await userApi.resetPassword(passwordTarget.value.id, payload.newPassword);
  ElMessage.success(`${passwordTarget.value.username} 的密码已重置`);
  passwordDialogVisible.value = false;
}

/** 批量删除 */
async function handleBatchDelete(): Promise<void> {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要删除的用户');
    return;
  }
  // 必须判断确认结果：.catch(() => undefined) 会把"取消"也变成 undefined，
  // 若不判断就继续执行删除，用户点了取消数据照样被删。
  const confirmed = await ElMessageBox.confirm(
    `确定删除选中的 ${selectedIds.value.length} 个用户吗？`,
    '提示',
    { type: 'warning' },
  )
    .then(() => true)
    .catch(() => false);
  if (!confirmed) {
    return;
  }
  await userApi.remove(selectedIds.value);
  ElMessage.success('删除成功');
  reload();
}

/** 单行删除 */
async function handleDelete(row: UserResp): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`确定删除用户 ${row.username} 吗？`, '提示', {
    type: 'warning',
  })
    .then(() => true)
    .catch(() => false);
  if (!confirmed) {
    return;
  }
  await userApi.remove([row.id]);
  ElMessage.success('删除成功');
  reload();
}

function onSelectionChange(rows: unknown[]): void {
  selectedIds.value = (rows as UserResp[]).map((item) => item.id);
}

/**
 * 把 el-table 的行数据类型化。
 *
 * <p>操作列是页面自己写的 el-table-column，其插槽行类型为框架内部的 DefaultRow，
 * 这里统一收敛成 UserResp，保证模板里取值与传参都是精确类型。
 *
 * @param row 行数据
 */
function asUser(row: unknown): UserResp {
  return row as UserResp;
}

/**
 * 拼装单行操作列的操作项。
 *
 * <p>封装在方法里而内联在模板，可读性更好；
 * 按"主操作在前，危险操作在后"的顺序排列，便于 TableActionBar 横向并排展示。
 *
 * @param row 当前行
 */
function buildActions(row: UserResp): TableActionItem[] {
  return [
    { label: '编辑', type: 'primary', icon: 'Edit', onClick: () => openEdit(row) },
    { label: '重置密码', type: 'warning', icon: 'Key', onClick: () => openResetPassword(row) },
    { label: '删除', type: 'danger', icon: 'Delete', onClick: () => handleDelete(row) },
  ];
}

onMounted(async () => {
  const [roles, depts] = await Promise.all([userApi.roleOptions(), deptApi.tree()]);
  roleOptions.value = roles ?? [];
  deptTree.value = depts ?? [];
});
</script>

<template>
  <div class="page-container">
    <ProTable
      :columns="columns"
      :data="list"
      :total="total"
      :loading="loading"
      :page-num="pageNum"
      :page-size="pageSize"
      selectable
      column-drag
      column-drag-key="system:user"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @selection-change="onSelectionChange"
    >
      <template #search>
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="请输入用户名" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" placeholder="请输入手机号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" :icon="'Search'" @click="reload">搜索</el-button>
        <el-button :icon="'Refresh'" @click="reset">重置</el-button>
      </template>

      <template #toolbar>
        <el-button type="primary" :icon="'Plus'" @click="openCreate">新增</el-button>
        <el-button type="danger" :icon="'Delete'" plain @click="handleBatchDelete">批量删除</el-button>
      </template>

      <template #col-roleNames="{ row }">
        <el-tag v-for="name in asUser(row).roleNames ?? []" :key="name" size="small" type="info" class="mr-1">
          {{ name }}
        </el-tag>
      </template>

      <template #col-status="{ row }">
        <el-switch
          :model-value="asUser(row).status === 0"
          inline-prompt
          active-text="正常"
          inactive-text="停用"
          @change="toggleStatus(asUser(row))"
        />
      </template>

      <template #columns>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <TableActionBar :actions="buildActions(asUser(row))" />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" v-loading="formLoading" element-loading-text="加载中…" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(form.id)" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改密码' : '请输入初始密码（5-20 位）'"
          />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            value-key="id"
            node-key="id"
            check-strictly
            placeholder="请选择部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" :disabled="formLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗：复用全局弹窗样式，强度提示与校验全站统一 -->
    <ResetPasswordDialog
      v-model="passwordDialogVisible"
      mode="admin"
      title="重置密码"
      :username="passwordTarget?.username ?? ''"
      @submit="onResetPasswordSubmit"
    />
  </div>
</template>
