import axios, { type AxiosRequestConfig, type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';
import type { LoginResp, R } from '@/types/api';
import { clearTokens, getRefreshToken, getToken, setToken } from './auth';
import { getDeviceId, getFingerprint } from './device';
import router from '@/router';

/**
 * HTTP 客户端封装。
 *
 * <p>职责：
 * <ul>
 *     <li>统一携带 Bearer 令牌；</li>
 *     <li>统一拆包：业务层直接拿到 data，无需层层 response.data.data；</li>
 *     <li>无感刷新：access 令牌过期（401）时用 refresh 令牌换发新令牌并重放原请求，
 *         并发请求共享同一个刷新 Promise（避免重复刷新导致旧 refresh 令牌失效）；</li>
 *     <li>统一错误提示：业务异常提示 msg，网络异常提示状态码。</li>
 * </ul>
 */
/**
 * 接口基地址。
 *
 * <p>构建期由 VITE_API_BASE_URL 注入，目的只有一个：让"开发代理"与"线上直连"共用同一份代码。
 * <ul>
 *     <li>开发：`/api`，由 Vite devServer 转发到 8080 并剥离前缀；</li>
 *     <li>线上：`https://nova-api.s.ci64.com`（后端未设置 context-path，故不带 /api 前缀）。</li>
 * </ul>
 * 末尾斜杠统一去掉，避免拼出 `//refresh` 这类路径。
 */
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '');

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
});

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // 设备标识与指纹：服务端据此做"可信设备"判定与按设备维度的令牌管理
  config.headers['X-Device-Id'] = getDeviceId();
  config.headers['X-Device-Fingerprint'] = getFingerprint();
  return config;
});

/** 正在进行的刷新 Promise（并发去重） */
let refreshPromise: Promise<string> | null = null;

/**
 * 刷新访问令牌。
 *
 * <p>此处直接用 axios（而非 service 实例）发起请求，
 * 一是避免再次进入响应拦截器形成递归，二是避免与业务请求共用令牌头逻辑。
 *
 * @returns 新的访问令牌
 */
function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${API_BASE_URL}/refresh`, { refreshToken: getRefreshToken(), deviceId: getDeviceId() })
      .then((response) => {
        const body = response.data as R<LoginResp>;
        if (body.code !== 200) {
          throw new Error(body.msg || '令牌刷新失败');
        }
        setToken(body.data.accessToken, body.data.refreshToken);
        return body.data.accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

/** 重试标记：防止同一请求无限重试 */
type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean };

/** 请求配置：允许调用方声明"错误静默"（如退出登录这类尽力而为的通知型请求） */
export type RequestConfig = AxiosRequestConfig & { silentError?: boolean };

service.interceptors.response.use(
  (response) => {
    // 二进制下载（导入模板 / 导出 / 错误清单 / 对账单导出）：responseType=blob 时
    // 响应体是文件流，没有 R<T> 外壳。原先无条件按 R<T> 解析，Blob 上取不到 code，
    // 于是每次下载都被判为"业务失败"——弹"请求失败"且文件拿不到，下载全挂。
    // 这里直接放行 Blob；若服务端把业务错误包成了 JSON（如"该批次没有失败行"），
    // 读出 msg 弹提示并拒绝，避免把错误 JSON 当成 xlsx 存到磁盘
    if (response.config.responseType === 'blob') {
      const blob = response.data as Blob;
      if (blob.type && blob.type.includes('json')) {
        return blob.text().then((text) => {
          let msg = '下载失败';
          try {
            msg = (JSON.parse(text) as R<unknown>).msg || msg;
          } catch {
            // 非 R 结构的 JSON：保留默认文案
          }
          ElMessage.error(msg);
          return Promise.reject(new Error(msg));
        });
      }
      return blob as never;
    }
    const body = response.data as R<unknown>;
    if (body.code === 200) {
      return body.data as never;
    }
    // 1013 = 陌生设备需二次校验，属于"可预期分支"：交由登录页自行展示验证码，
    // 这里不弹错误提示，避免把正常的安全流程表现成系统报错
    if (body.code !== 1013 && !(response.config as RequestConfig).silentError) {
      ElMessage.error(body.msg || '请求失败');
    }
    // 带上业务错误码与数据，便于调用方（如登录页）做差异化处理：
    // 例如"需要人机验证"这类中间态，后续步骤所需的票据就放在 data 里
    const failure = new Error(body.msg || '请求失败') as Error & { code?: number; data?: unknown };
    failure.code = body.code;
    failure.data = body.data;
    return Promise.reject(failure);
  },
  async (error: AxiosError<R<unknown>>) => {
    const status = error.response?.status;
    const original = error.config as RetryConfig;

    // 请求被取消 / 调用方声明静默 → 不弹任何提示（登出等"尽力而为"的请求走这里）
    if (error.code === 'ERR_CANCELED' || (original as RequestConfig | undefined)?.silentError) {
      return Promise.reject(error);
    }

    // 401 且尚未重试过 → 尝试无感刷新
    if (status === 401 && original && !original._retry && getRefreshToken()) {
      original._retry = true;
      try {
        const token = await refreshAccessToken();
        original.headers.Authorization = `Bearer ${token}`;
        return service.request(original);
      } catch {
        // 刷新失败：清空令牌并要求重新登录
        clearTokens();
        ElMessage.error('登录状态已失效，请重新登录');
        void router.replace('/login');
        return Promise.reject(error);
      }
    }

    // 无 response：区分"被取消"与真正的网络故障，避免笼统的"网络异常（未知）"
    // 二进制下载失败时响应体是 Blob（服务端 JSON 错误被按二进制收了），先读出文本再取 msg
    let message: string | undefined;
    const errorData = error.response?.data as unknown;
    if (errorData instanceof Blob) {
      try {
        message = (JSON.parse(await errorData.text()) as R<unknown>).msg;
      } catch {
        message = undefined;
      }
    } else {
      message = (errorData as R<unknown> | undefined)?.msg;
    }
    const finalMessage =
      message ??
      (status === 403
        ? '没有权限访问该资源'
        : status === 404
          ? '接口不存在'
          : status
            ? `网络异常（${status}）`
            : '网络连接异常，请检查网络后重试');
    ElMessage.error(finalMessage);
    return Promise.reject(error);
  },
);

/**
 * 语义化方法：返回类型即业务数据类型。
 */
/**
 * 说明：响应拦截器已经把 AxiosResponse 拆开、只返回 data，
 * 但 Axios 的静态类型无法感知这一点，故此处做一次断言，
 * 让调用方拿到的是纯业务数据类型。
 */
const http = {
  get<T>(url: string, params?: unknown, config?: RequestConfig): Promise<T> {
    return service.get<unknown, T>(url, { params, ...config }) as Promise<T>;
  },
  post<T>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return service.post<unknown, T>(url, data, config) as Promise<T>;
  },
  put<T>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return service.put<unknown, T>(url, data, config) as Promise<T>;
  },
  delete<T>(url: string, params?: unknown, config?: RequestConfig): Promise<T> {
    return service.delete<unknown, T>(url, { params, ...config }) as Promise<T>;
  },
};

export default http;
