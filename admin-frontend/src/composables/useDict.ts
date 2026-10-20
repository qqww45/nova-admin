import { computed, ref } from 'vue';
import { dictApi } from '@/api/biz';
import type { DictResp } from '@/types/biz';

/**
 * 业务字典缓存（税率、币种、结算方式、交货方式等）。
 *
 * <p>为什么不在组件里各拉各的：这些字典是"全站共用且几乎不变"的数据，
 * 每个下拉都发一次请求会让列表页一打开就刷屏；这里做进程内单例缓存，
 * 首次访问拉一次，之后所有组件共享同一份。
 *
 * <p>需求明确"税率禁止硬编码"——所以下拉选项一律来自这里。
 */
const dicts = ref<DictResp[]>([]);
const loaded = ref(false);
const loading = ref(false);

/** 首次调用时加载字典 */
async function ensureLoaded(): Promise<void> {
  if (loaded.value || loading.value) {
    return;
  }
  loading.value = true;
  try {
    dicts.value = (await dictApi.all()) ?? [];
    loaded.value = true;
  } finally {
    loading.value = false;
  }
}

/**
 * 使用业务字典。
 *
 * @returns 字典列表、按编码取项的 options、以及手动刷新方法
 */
export function useDict() {
  void ensureLoaded();

  /**
   * 按字典编码取下拉选项（label / value 结构，可直接喂给 el-option）。
   *
   * @param dictCode 字典编码
   */
  function options(dictCode: string) {
    return computed(() =>
      dicts.value
        .filter((item) => item.dictCode === dictCode)
        .map((item) => ({ label: item.dictLabel, value: item.dictValue })),
    );
  }

  /**
   * 按字典编码 + 值取标签（列表里把状态码翻译成中文用）。
   *
   * @param dictCode 字典编码
   * @param value    字典值
   */
  function label(dictCode: string, value?: string | number | null): string {
    if (value === undefined || value === null || value === '') {
      return '';
    }
    const hit = dicts.value.find(
      (item) => item.dictCode === dictCode && String(item.dictValue) === String(value),
    );
    return hit?.dictLabel ?? String(value);
  }

  /** 手动刷新（字典在后台被改过后用） */
  async function refresh(): Promise<void> {
    loaded.value = false;
    await ensureLoaded();
  }

  return { dicts, options, label, refresh };
}
