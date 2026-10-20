<script setup lang="ts">
/**
 * 系统日志：在线查看本机日志尾部（单机排障用）。
 *
 * <p>设计边界（重要）：
 * <ul>
 *     <li>只读本机日志文件，**不能替代集中式日志系统**：
 *         多实例部署时每台机器日志独立，跨实例检索必须依赖 ELK / Loki 这类方案；</li>
 *     <li>返回的是原始日志行而不是结构化字段：日志格式由 logback pattern 决定，
 *         强行解析会在调整 pattern 时静默失效；保留原文最稳，也便于复制排障；</li>
 *     <li>日志可能包含 IP、UA 等个人信息，因此仅授予超级管理员查看权限。</li>
 * </ul>
 */
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { logApi } from '@/api/config';
import type { LogLineResp } from '@/types/api';

const loading = ref(false);
const lines = ref<LogLineResp[]>([]);
/** 拉取行数（服务端上限 1000） */
const size = ref(200);
const level = ref('');
const keyword = ref('');

/** 加载日志尾部 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    lines.value = await logApi.tail(size.value, level.value || undefined, keyword.value || undefined);
    if (lines.value.length === 0) {
      ElMessage.info('没有匹配的日志（或日志文件尚不存在）');
    }
  } finally {
    loading.value = false;
  }
}

/** 日志级别配色（用于左侧竖条，避免整行染色影响阅读） */
function levelClass(content: string): string {
  if (content.includes(' ERROR ')) {
    return 'error';
  }
  if (content.includes(' WARN ')) {
    return 'warn';
  }
  if (content.includes(' DEBUG ')) {
    return 'debug';
  }
  return 'info';
}

onMounted(load);
</script>

<template>
  <!-- 用 .page-container 与其它页面对齐；搜索区样式与 ProTable 保持一致（16px 间距） -->
  <div class="page-container">
    <!-- 搜索区：与 ProTable 的 pro-search 结构同构，视觉上和其它列表页在同一套规范下 -->
    <el-card shadow="never" class="log__search">
      <el-form :inline="true" class="log__form" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            placeholder="过滤日志内容"
            clearable
            style="width: 220px"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="level" placeholder="全部" clearable style="width: 130px">
            <el-option label="ERROR" value="ERROR" />
            <el-option label="WARN" value="WARN" />
            <el-option label="INFO" value="INFO" />
            <el-option label="DEBUG" value="DEBUG" />
          </el-select>
        </el-form-item>
        <el-form-item label="行数">
          <el-select v-model="size" style="width: 130px">
            <el-option :value="100" label="100 行" />
            <el-option :value="200" label="200 行" />
            <el-option :value="500" label="500 行" />
            <el-option :value="1000" label="1000 行" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="load">查看</el-button>
          <el-button :icon="'Refresh'" :loading="loading" @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 日志正文：等宽终端面板，与上方筛选卡片之间留 16px -->
    <el-card shadow="never" class="log__body-card">
      <template #header>
        <span class="log__card-title">日志输出</span>
        <span class="log__count">共 {{ lines.length }} 行</span>
      </template>
      <div v-loading="loading" class="log__body">
        <p v-for="(line, index) in lines" :key="index" class="log__line" :class="levelClass(line.content)">
          {{ line.content }}
        </p>
        <p v-if="!loading && lines.length === 0" class="log__empty">
          没有匹配的日志（或日志文件尚不存在）
        </p>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.log {
  /* 筛选区卡片与其它列表页的搜索卡片保持 16px 分隔 */
  &__search {
    margin-bottom: 16px;
  }

  /* 内联表单：间距由 styles/element/global.scss 统一，此处不重复定义 */

  /* 等宽字体 + 按粒度换行：日志要能一眼扫出结构 */
  &__body {
    max-height: 620px;
    padding: 12px;
    overflow: auto;
    border: 1px solid var(--admin-border-color, #dcdfe6);
    border-radius: 4px;
    background-color: var(--admin-bg-color, #f5f7fa);
  }

  &__line {
    margin: 0;
    padding: 2px 0 2px 10px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12px;
    line-height: 1.7;
    color: var(--admin-text-regular);
    white-space: pre-wrap;
    word-break: break-all;
    border-left: 3px solid transparent;

    &.error {
      color: var(--el-color-danger);
      border-left-color: var(--el-color-danger);
    }

    &.warn {
      color: var(--el-color-warning);
      border-left-color: var(--el-color-warning);
    }

    &.debug {
      color: var(--admin-text-secondary, #909399);
    }
  }

  /* 日志正文卡片标题：左侧标题、右侧行数，左右分两端的弹性布局 */
  &__card-title {
    font-weight: 600;
  }

  &__count {
    margin-left: auto;
    font-size: 13px;
    font-variant-numeric: tabular-nums;
    color: var(--admin-text-regular);
  }

  &__empty {
    padding: 24px 0;
    text-align: center;
    color: var(--admin-text-secondary, #909399);
  }
}
</style>
