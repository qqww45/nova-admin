<script setup lang="ts">
/**
 * 根组件：只负责挂载路由视图与全局配置（Element Plus 中文语言包）。
 * 不承载任何业务逻辑。
 */
import { ElConfigProvider } from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
