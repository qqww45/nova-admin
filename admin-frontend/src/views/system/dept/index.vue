<script setup lang="ts">
/**
 * 部门管理：树形表格展示组织架构，支持新增 / 编辑 / 删除（含子部门）。
 */
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import TableActionBar, { type TableActionItem } from '@/components/TableActionBar/index.vue';
import { deptApi } from '@/api/menu';
import type { DeptVo } from '@/types/api';

const loading = ref(false);
const depts = ref<DeptVo[]>([]);

const columns = [
  { prop: 'deptName', label: '部门名称', minWidth: 200, align: 'left' as const },
  { prop: 'sort', label: '排序', width: 80 },
  { prop: 'leader', label: '负责人', width: 100 },
  { prop: 'phone', label: '联系电话', width: 130 },
  { prop: 'email', label: '邮箱', minWidth: 160 },
  { prop: 'status', label: '状态', width: 90, slot: true },
];

/** 查询条件（简单场景：名称模糊 + 状态） */
const query = reactive<{ deptName: string; status?: number }>({ deptName: '', status: undefined });

/** 把 el-table 的行数据类型化（操作列为页面自写列，框架给出的行类型是 DefaultRow） */
function asDept(row: unknown): DeptVo {
  return row as DeptVo;
}

/** 拼装单行操作项 */
function buildActions(row: DeptVo): TableActionItem[] {
  return [
    { label: '编辑', type: 'primary', icon: 'Edit', onClick: () => openEdit(row) },
    { label: '新增子部门', type: 'success', icon: 'Plus', onClick: () => openCreate(row.id) },
    { label: '删除', type: 'danger', icon: 'Delete', onClick: () => handleDelete(row) },
  ];
}

/** 加载部门树 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    depts.value = (await deptApi.tree(query.deptName || undefined, query.status)) ?? [];
  } finally {
    loading.value = false;
  }
}

/** 表单弹窗 */
const dialogVisible = ref(false);
const dialogTitle = ref('');
const formRef = ref<FormInstance>();
const form = reactive<Partial<DeptVo>>({
  id: undefined,
  parentId: '0',
  deptName: '',
  sort: 0,
  leader: '',
  phone: '',
  email: '',
  status: 0,
});

const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }],
};

function openCreate(parentId?: string): void {
  dialogTitle.value = '新增部门';
  Object.assign(form, {
    id: undefined,
    parentId: parentId ?? '0',
    deptName: '',
    sort: 0,
    leader: '',
    phone: '',
    email: '',
    status: 0,
  });
  dialogVisible.value = true;
}

function openEdit(row: DeptVo): void {
  dialogTitle.value = '编辑部门';
  Object.assign(form, { ...row });
  dialogVisible.value = true;
}

/** 提交（新增 / 编辑共用一套表单，后端按 id 是否存在区分） */
async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (form.id) {
    await deptApi.update(form);
  } else {
    await deptApi.create(form);
  }
  ElMessage.success('保存成功');
  dialogVisible.value = false;
  await load();
}

/** 删除部门（含子部门；部门下有用户时后端会拒绝并提示） */
async function handleDelete(row: DeptVo): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`确定删除部门 ${row.deptName} 吗？子部门会一并删除`, '提示', {
    type: 'warning',
  })
    .then(() => true)
    .catch(() => false);
  if (!confirmed) {
    return;
  }
  await deptApi.remove(row.id);
  ElMessage.success('删除成功');
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="page-container">
    <ProTable
      :columns="columns"
      :data="depts"
      :loading="loading"
      tree
      row-key="id"
      column-drag-key="system:dept"
      @selection-change="() => undefined"
    >
      <template #search>
        <el-form-item label="部门名称">
          <el-input v-model="query.deptName" placeholder="请输入部门名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
      </template>

      <template #search-buttons>
        <el-button type="primary" :icon="'Search'" @click="load">搜索</el-button>
        <el-button
          :icon="'Refresh'"
          @click="
            query.deptName = '';
            query.status = undefined;
            load();
          "
        >
          重置
        </el-button>
      </template>

      <template #toolbar>
        <el-button type="primary" :icon="'Plus'" @click="openCreate()">新增部门</el-button>
      </template>

      <template #col-status="{ row }">
        <el-tag :type="asDept(row).status === 0 ? 'success' : 'danger'" size="small">
          {{ asDept(row).status === 0 ? '正常' : '停用' }}
        </el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <TableActionBar :actions="buildActions(asDept(row))" />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="[{ id: '0', deptName: '顶级部门', children: depts }]"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            placeholder="请选择上级部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="如：技术部" />
        </el-form-item>
        <el-form-item label="显示排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="部门状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
