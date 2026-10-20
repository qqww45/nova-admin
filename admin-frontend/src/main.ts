import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { setupIcons } from './plugins/icons';
import 'virtual:uno.css';
import './styles/index.scss';

/*
 * JS API 类组件的样式必须手动引入。
 *
 * <p>unplugin-vue-components 只为模板组件（<el-button> 等）注入样式；
 * ElMessage / ElMessageBox / ElNotification / ElLoading 是纯 JS API 调用，
 * 插件感知不到——不引入的话会出现"弹窗/提示光秃秃、位置乱飘"的现象。
 * 引 style/index.mjs（含依赖链并指向 theme-chalk SCSS 源码），
 * 使其继续经过 additionalData 注入主题令牌，与全站主题一致。
 */
import 'element-plus/es/components/message/style/index.mjs';
import 'element-plus/es/components/message-box/style/index.mjs';
import 'element-plus/es/components/notification/style/index.mjs';
import 'element-plus/es/components/loading/style/index.mjs';

/**
 * 应用入口。
 *
 * <p>启动顺序：Pinia → 路由 → 全局图标 → 挂载。
 * 图标采用显式注册（见 plugins/icons），避免按需引入造成的图标"时有时无"。
 */
const app = createApp(App);

app.use(createPinia());
app.use(router);
setupIcons(app);

app.mount('#app');
