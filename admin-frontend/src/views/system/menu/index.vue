<script setup lang="ts">
/**
 * 菜单管理：树形表格展示（目录 / 菜单 / 按钮），支持增删改。
 *
 * <p>树形表格直接以 children 渲染，不分页（菜单量级有限，一次加载更简单可靠）。
 */
import { onMounted, reactive, ref, type Component } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import ProTable from '@/components/ProTable/index.vue';
import TableActionBar, { type TableActionItem } from '@/components/TableActionBar/index.vue';
import { menuApi } from '@/api/menu';
import type { MenuVo } from '@/types/api';
import { MENU_TYPE, MENU_TYPE_LABEL } from '@/utils/menu';
import { resolveIcon } from '@/utils/icon';

const loading = ref(false);
const menus = ref<MenuVo[]>([]);

const columns = [
  { prop: 'menuName', label: '菜单名称', minWidth: 180, align: 'left' as const, slot: true },
  { prop: 'icon', label: '图标', width: 90, slot: true },
  { prop: 'menuType', label: '类型', width: 90, slot: true },
  { prop: 'sort', label: '排序', width: 70 },
  { prop: 'permission', label: '权限标识', minWidth: 160 },
  { prop: 'component', label: '组件路径', minWidth: 160 },
  { prop: 'path', label: '路由地址', minWidth: 140 },
  { prop: 'visible', label: '显示', width: 80, slot: true },
  { prop: 'status', label: '状态', width: 80, slot: true },
];

/** 表单弹窗 */
const dialogVisible = ref(false);
const dialogTitle = ref('');
const formRef = ref<FormInstance>();
const form = reactive<Partial<MenuVo>>({
  id: undefined,
  parentId: '0',
  menuName: '',
  menuType: MENU_TYPE.MENU,
  path: '',
  component: '',
  permission: '',
  icon: '',
  sort: 0,
  // 后端语义：visible 1=显示 0=隐藏
  visible: 1,
  status: 0,
});

const rules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }],
};

/** 上级菜单下拉：只展示目录与菜单，按钮不能作为父级 */
const parentOptions = ref<MenuVo[]>([]);

/** 把 el-table 的行数据类型化（操作列为页面自写列，框架给出的行类型是 DefaultRow） */
function asMenu(row: unknown): MenuVo {
  return row as MenuVo;
}

/** 行图标组件：解析不到（未设置或名字对不上）时返回 undefined，列表显示"未设置" */
function iconOf(row: unknown): Component | undefined {
  return resolveIcon(asMenu(row).icon);
}

/** 拼装单行操作项：按钮类型不展示"新增子项" */
function buildActions(row: MenuVo): TableActionItem[] {
  return [
    { label: '编辑', type: 'primary', icon: 'Edit', onClick: () => openEdit(row) },
    {
      label: '新增子项',
      type: 'success',
      icon: 'Plus',
      hidden: row.menuType === MENU_TYPE.BUTTON,
      onClick: () => openCreate(row.id),
    },
    { label: '删除', type: 'danger', icon: 'Delete', onClick: () => handleDelete(row) },
  ];
}

/** 加载菜单列表 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await menuApi.list();
    menus.value = data ?? [];
    parentOptions.value = [{ id: '0', menuName: '顶级菜单', children: buildParentTree(data ?? []) } as MenuVo];
  } finally {
    loading.value = false;
  }
}

/** 过滤出可作为父级的节点（M 目录 / C 菜单） */
function buildParentTree(list: MenuVo[]): MenuVo[] {
  return list
    .filter((item) => item.menuType === MENU_TYPE.DIR)
    .map((item) => ({ ...item, children: item.children ? buildParentTree(item.children) : [] }));
}

function openCreate(parentId?: string): void {
  dialogTitle.value = '新增菜单';
  Object.assign(form, {
    id: undefined,
    parentId: parentId ?? '0',
    menuName: '',
    menuType: MENU_TYPE.MENU,
    path: '',
    component: '',
    permission: '',
    icon: '',
    sort: 0,
    visible: 1,
    status: 0,
  });
  dialogVisible.value = true;
}

/**
 * 打开编辑弹窗。
 *
 * <p>两次防御：
 * <ol>
 *     <li>detail 请求失败（如无 system:menu:query 权限）时 http 层已 toast，
 *         这里 catch 住避免 unhandled rejection，且不打开空弹窗；</li>
 *     <li>后端返回空数组时 detail 为 undefined，直接打开弹窗会呈现"全空表单"，
 *         与"编辑"语义不符 —— 提示后中止。</li>
 * </ol>
 */
async function openEdit(row: MenuVo): Promise<void> {
  let detail: MenuVo | undefined;
  try {
    detail = await menuApi.detail(row.id);
  } catch {
    return; // 错误提示已由 http 拦截器统一弹出
  }
  if (!detail) {
    ElMessage.error('菜单详情加载失败，请刷新后重试');
    return;
  }
  dialogTitle.value = '编辑菜单';
  Object.assign(form, detail);
  dialogVisible.value = true;
}

async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (form.id) {
    await menuApi.update(form);
  } else {
    await menuApi.create(form);
  }
  ElMessage.success('保存成功');
  dialogVisible.value = false;
  await load();
}

async function handleDelete(row: MenuVo): Promise<void> {
  await ElMessageBox.confirm(`确定删除菜单 ${row.menuName} 吗？子菜单会一并删除`, '提示', {
    type: 'warning',
  }).catch(() => undefined);
  await menuApi.remove([row.id]);
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
      :data="menus"
      :loading="loading"
      tree
      row-key="id"
      column-drag-key="system:menu"
      @selection-change="() => undefined"
    >
      <template #toolbar>
        <el-button type="primary" :icon="'Plus'" @click="openCreate()">新增菜单</el-button>
      </template>

      <template #col-menuName="{ row }">
        <span class="menu-name">
          <el-icon v-if="iconOf(row)" class="menu-name__icon"><component :is="iconOf(row)" /></el-icon>
          <span>{{ asMenu(row).menuName }}</span>
        </span>
      </template>

      <template #col-icon="{ row }">
        <el-icon v-if="iconOf(row)" class="menu-icon-cell"><component :is="iconOf(row)" /></el-icon>
        <span v-else class="menu-icon-cell menu-icon-cell--empty">未设置</span>
      </template>

      <template #col-menuType="{ row }">
        <el-tag
          :type="asMenu(row).menuType === MENU_TYPE.DIR ? 'info' : asMenu(row).menuType === MENU_TYPE.MENU ? 'success' : 'warning'"
          size="small"
        >
          {{ MENU_TYPE_LABEL[asMenu(row).menuType] ?? '未知' }}
        </el-tag>
      </template>

      <template #col-visible="{ row }">
        <el-tag :type="asMenu(row).visible === 1 ? 'success' : 'info'" size="small">
          {{ asMenu(row).visible === 1 ? '显示' : '隐藏' }}
        </el-tag>
      </template>

      <template #col-status="{ row }">
        <el-tag :type="asMenu(row).status === 0 ? 'success' : 'danger'" size="small">
          {{ asMenu(row).status === 0 ? '正常' : '停用' }}
        </el-tag>
      </template>

      <template #columns>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <TableActionBar :actions="buildActions(asMenu(row))" />
          </template>
        </el-table-column>
      </template>
    </ProTable>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', children: 'children' }"
            node-key="id"
            check-strictly
            placeholder="请选择上级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.menuType">
            <el-radio-button :value="MENU_TYPE.DIR">目录</el-radio-button>
            <el-radio-button :value="MENU_TYPE.MENU">菜单</el-radio-button>
            <el-radio-button :value="MENU_TYPE.BUTTON">按钮</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="如：用户管理" />
        </el-form-item>
        <!-- 按钮不出现在侧边栏，无需图标 -->
        <el-form-item v-if="form.menuType !== MENU_TYPE.BUTTON" label="菜单图标">
          <IconSelect v-model="form.icon" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== MENU_TYPE.BUTTON" label="路由地址">
          <el-input v-model="form.path" placeholder="如：system/user" />
        </el-form-item>
        <el-form-item v-if="form.menuType === MENU_TYPE.MENU" label="组件路径">
          <el-input v-model="form.component" placeholder="如：system/user/index" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.permission" placeholder="如：system:user:list" />
        </el-form-item>
        <el-form-item label="显示排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="菜单状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="显示状态">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
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

<style scoped lang="scss">
/* 名称列：图标与文字同行，未配图标的行不留空位，避免出现一截对不齐的空白 */
.menu-name {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  vertical-align: middle;
}

.menu-name__icon {
  color: var(--admin-text-regular);
}

/* 图标列：占位符弱化处理——"没配图标"是正常状态，不该和错误信息一个视觉重量 */
.menu-icon-cell {
  font-size: 16px;
  color: var(--admin-text-regular);

  &--empty {
    font-size: 12px;
    color: var(--admin-text-regular);
    opacity: 0.45;
  }
}
</style>
