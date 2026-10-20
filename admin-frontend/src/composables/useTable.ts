import { onMounted, reactive, ref, shallowRef } from 'vue';
import type { PageResult } from '@/types/api';

/**
 * 列表页数据管理组合式函数。
 *
 * <p>把"查询条件 + 分页参数 + 加载状态 + 数据"收敛到一处，
 * 页面只需提供一个 fetcher（接收合并后的参数，返回分页结果），
 * 避免每个页面复制粘贴同样的 loading / total / reload 逻辑。
 *
 * @param fetcher 数据获取函数
 * @param initialQuery 初始查询条件
 */
export function useTable<T, Q extends Record<string, unknown> = Record<string, unknown>>(
  fetcher: (params: Record<string, unknown>) => Promise<PageResult<T>>,
  initialQuery: Q = {} as Q,
) {
  const loading = ref(false);
  // 用 shallowRef：保持 T[] 的精确类型（避免深层解包导致的类型退化），列表数据整体替换即可
  const list = shallowRef<T[]>([]);
  const total = ref(0);
  const pageNum = ref(1);
  const pageSize = ref(10);

  // 保留调用方传入的字段类型（避免退化成 unknown，模板里 v-model 才能类型正确）
  const query = reactive({ ...initialQuery }) as unknown as Q;

  /** 组装请求参数（清理空值，避免把 undefined 序列化成字符串） */
  function buildParams(): Record<string, unknown> {
    const params: Record<string, unknown> = { pageNum: pageNum.value, pageSize: pageSize.value };
    for (const [key, value] of Object.entries(query)) {
      if (value !== '' && value !== null && value !== undefined) {
        params[key] = value;
      }
    }
    return params;
  }

  /** 加载数据 */
  async function load(): Promise<void> {
    loading.value = true;
    try {
      const result = await fetcher(buildParams());
      list.value = result.records ?? [];
      total.value = result.total ?? 0;
    } finally {
      loading.value = false;
    }
  }

  /** 重新查询（回到第一页） */
  function reload(): void {
    pageNum.value = 1;
    void load();
  }

  /** 重置查询条件并重新加载 */
  function reset(): void {
    const record = query as Record<string, unknown>;
    for (const key of Object.keys(record)) {
      record[key] = undefined;
    }
    reload();
  }

  /** 页码变化 */
  function onPageChange(page: number): void {
    pageNum.value = page;
    void load();
  }

  /** 每页条数变化 */
  function onSizeChange(size: number): void {
    pageSize.value = size;
    pageNum.value = 1;
    void load();
  }

  onMounted(() => {
    void load();
  });

  return {
    loading,
    list,
    total,
    pageNum,
    pageSize,
    query,
    load,
    reload,
    reset,
    onPageChange,
    onSizeChange,
  };
}
