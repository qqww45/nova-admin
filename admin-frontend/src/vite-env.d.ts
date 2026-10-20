/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口基地址：开发为 /api（代理），线上为后端域名（如 https://nova-api.s.ci64.com） */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<{}, {}, never>;
  export default component;
}
