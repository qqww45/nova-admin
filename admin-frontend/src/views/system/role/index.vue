<script setup lang="ts">
/**
 * 角色管理：增删改查 + 启停 + 菜单权限分配（树形勾选）。
 */
import { nextTick, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import TableActionBar, { type TableActionItem } from '@/components/TableActionBar/index.vue';
import { useTable } from '@/composables/useTable';
import { menuApi } from '@/api/menu';
import { roleApi } from '@/api/role';
import type { MenuVo, RoleResp, RoleSaveReq } from '@/types/api';

const columns = [
  { prop: 'roleName', label: '角色名称', minWidth: 140 },
  { prop: 'roleKey', label: '权限标识', minWidth: 140 },
  { prop: 'sort', label: '排序', width: 80 },
  { prop: 'status', label: '状态', width: 90, slot: true },
  { prop: 'createTime', label: '创建时间', minWidth: 160 },
];

const { loading, list, total, pageNum, pageSize, query, reload, reset, onPageChange, onSizeChange } =
  // 不显式指定泛型参数：行类型由 fetcher 返回值推断，查询类型由初始条件推断
  useTable((params) => roleApi.page(params), {
    roleName: '',
    roleKey: '',
    status: undefined as number | undefined,
  });

/** 角色表单弹窗 */
const dialogVisible = ref(false);
const dialogTitle = ref('');
/** 弹窗内加载态：详情拉取期间置 true，避免网速慢时用户对空表单误操作 */
const formLoading = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<RoleSaveReq>({
  id: undefined,
  roleName: '',
  roleKey: '',
  sort: 0,
  status: 0,
  remark: '',
});

const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [
    { required: true, message: '请输入权限标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '只能包含字母、数字与下划线，且以字母开头', trigger: 'blur' },
  ],
};

/** 菜单权限分配抽屉 */
const assignVisible = ref(false);
/** 抽屉内加载态：未拿到已选菜单前禁用保存，防止空集合提交清空角色权限 */
const assignLoading = ref(false);
const menuTree = ref<MenuVo[]>([]);
const currentRoleId = ref('');
const treeRef = ref<InstanceType<typeof import('element-plus').ElTree>>();
const checkedKeys = ref<string[]>([]);

function openCreate(): void {
  dialogTitle.value = '新增角色';
  Object.assign(form, { id: undefined, roleName: '', roleKey: '', sort: 0, status: 0, remark: '' });
  dialogVisible.value = true;
}

async function openEdit(row: RoleResp): Promise<void> {
  dialogTitle.value = '编辑角色';
  // 先置空表单并弹窗，避免网速慢时长时间无响应；详情在框内加载，失败则关窗（错误已由 http 拦截器统一提示）
  Object.assign(form, { id: undefined, roleName: '', roleKey: '', sort: 0, status: 0, remark: '' });
  dialogVisible.value = true;
  formLoading.value = true;
  try {
    const detail = await roleApi.detail(row.id);
    Object.assign(form, detail);
  } catch {
    dialogVisible.value = false;
  } finally {
    formLoading.value = false;
  }
}

async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (form.id) {
    await roleApi.update(form);
  } else {
    await roleApi.create(form);
  }
  ElMessage.success('保存成功');
  dialogVisible.value = false;
  reload();
}

/** 启停角色 */
async function toggleStatus(row: RoleResp): Promise<void> {
  const next = row.status === 0 ? 1 : 0;
  await roleApi.updateStatus(row.id, next);
  row.status = next;
  ElMessage.success(next === 0 ? '已启用' : '已停用');
}

/** 删除角色 */
async function handleDelete(row: RoleResp): Promise<void> {
  await ElMessageBox.confirm(`确定删除角色 ${row.roleName} 吗？`, '提示', { type: 'warning' }).catch(
    () => undefined,
  );
  await roleApi.remove([row.id]);
  ElMessage.success('删除成功');
  reload();
}

/** 打开权限分配抽屉 */
/**
 * 打开分配权限抽屉。
 *
 * <p>必须带回角色的已选菜单：<code>roleApi.detail().menuIds</code> 就是已分配的菜单 ID。
 * 之前这里直接清空勾选，带来两个问题：
 * <ol>
 *     <li>给已有角色分配权限时看不到当前配置，体验断裂；</li>
 *     <li><b>更严重</b>：管理员打开抽屉后不做任何勾选直接点「确定」，
 *         submitAssign 会把空集合提交上去 —— 角色权限被整组清空。</li>
 * </ol>
 * detail 加载失败时直接放弃打开（错误已由 http 层提示），
 * 绝不在"未知当前权限"的状态下放行提交入口。
 * <p>网速慢时先开抽屉再在框内异步加载，避免长时间无响应；
 * 加载期间禁用保存按钮，加载失败则关抽屉，安全语义不变。
 */
async function openAssign(row: RoleResp): Promise<void> {
  currentRoleId.value = row.id;
  // 先清空勾选再开抽屉，避免残留上一个角色的已选菜单
  checkedKeys.value = [];
  assignVisible.value = true;
  assignLoading.value = true;
  try {
    const [tree, detail] = await Promise.all([
      // 菜单树已随页面预加载过则不重复请求
      menuTree.value.length > 0 ? Promise.resolve(menuTree.value) : menuApi.tree(),
      roleApi.detail(row.id),
    ]);
    menuTree.value = tree ?? [];
    checkedKeys.value = detail?.menuIds ?? [];
    // default-checked-keys 只在树挂载时生效，而数据晚于抽屉打开到达，需手动回填勾选
    void nextTick(() => treeRef.value?.setCheckedKeys(checkedKeys.value));
  } catch {
    assignVisible.value = false;
  } finally {
    assignLoading.value = false;
  }
}

/** 把 el-table 的行数据类型化（操作列为页面自写列，框架给出的行类型是 DefaultRow） */
function asRole(row: unknown): RoleResp {
  return row as RoleResp;
}

/** 拼装单行操作项：主操作在前，危险操作在后 */
function buildActions(row: RoleResp): TableActionItem[] {
  return [
    { label: '编辑', type: 'primary', icon: 'Edit', onClick: () => openEdit(row) },
    { label: '分配权限', type: 'success', icon: 'Setting', onClick: () => openAssign(row) },
    { label: '删除', type: 'danger', icon: 'Delete', onClick: () => handleDelete(row) },
  ];
}

/** 提交权限分配 */
async function submitAssign(): Promise<void> {
  const tree = treeRef.value;
  // 半选中的父节点也要提交，否则目录不会展开
  const keys = [...(tree?.getCheckedKeys() ?? []), ...(tree?.getHalfCheckedKeys() ?? [])] as string[];
  await roleApi.assignMenus(currentRoleId.value, keys);
  ElMessage.success('权限分配成功');
  assignVisible.value = false;
}

onMounted(() => {
  // 预加载菜单树，打开抽屉时更快
  void menuApi.tree().then((tree) => (menuTree.value = tree ?? []));
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
      column-drag
      column-drag-key="system:role"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #search>
        <el-form-item label="角色名称">
          <el-input v-model="query.roleName" placeholder="请输入角色名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="query.roleKey" placeholder="请输入权限标识" clearable style="width: 160px" />
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
      </template>

      <template #col-status="{ row }">
        <el-switch
          :model-value="asRole(row).status === 0"
          inline-prompt
          active-text="正常"
          inactive-text="停用"
          @change="toggleStatus(asRole(row))"
        />
      </template>

      <template #columns>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <TableActionBar :actions="buildActions(asRole(row))" />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <!-- 角色表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
      <el-form ref="formRef" v-loading="formLoading" element-loading-text="加载中…" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如：系统管理员" />
        </el-form-item>
        <el-form-item label="权限标识" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="如：admin" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" :disabled="formLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 菜单权限分配。
         destroy-on-close：default-checked-keys 只在树挂载时生效，
         不销毁的话换一个角色再打开，勾选状态还是上一次的残留。 -->
    <el-drawer v-model="assignVisible" title="分配菜单权限" size="420px" destroy-on-close>
      <div v-loading="assignLoading" element-loading-text="加载中…">
        <el-tree
          ref="treeRef"
          :data="menuTree"
          :props="{ label: 'menuName', children: 'children' }"
          node-key="id"
          show-checkbox
          default-expand-all
          :default-checked-keys="checkedKeys"
        />
      </div>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignLoading" :disabled="assignLoading" @click="submitAssign">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>
