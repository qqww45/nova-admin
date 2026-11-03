import { computed, ref } from 'vue';
import { kdDataApi } from '@/api/kd';
import type { KdOwnerResp } from '@/types/kd';
import { useUserStore } from '@/store/user';

/**
 * 金蝶数据归属人（业务员）下拉缓存。
 *
 * <p>三个排查页（客户 / 出库 / 分录）的"按业务员过滤"共用同一份选项；
 * 接口本身仅管理员可用，业务员登录时下拉不渲染也不请求。
 *
 * <p>与 useDict 同套路：进程内单例缓存，避免每个页面重复拉取。
 */
const owners = ref<KdOwnerResp[]>([]);
const loaded = ref(false);
const loading = ref(false);

/** 首次调用时加载（仅管理员） */
async function ensureLoaded(): Promise<void> {
  if (loaded.value || loading.value) {
    return;
  }
  loading.value = true;
  try {
    owners.value = (await kdDataApi.owners()) ?? [];
    loaded.value = true;
  } catch {
    // 拉不到（网络异常 / 权限变化）时静默降级：下拉为空不影响主列表
  } finally {
    loading.value = false;
  }
}

/**
 * 使用数据归属人选项。
 *
 * @returns 是否管理员、下拉选项（label / value，可直接喂给 el-option）
 */
export function useKdOwners() {
  const userStore = useUserStore();
  const isAdmin = computed(() => userStore.isAdmin);

  if (isAdmin.value) {
    void ensureLoaded();
  }

  const options = computed(() =>
    owners.value.map((owner) => ({ label: owner.name, value: owner.id })),
  );

  return { isAdmin, options };
}
