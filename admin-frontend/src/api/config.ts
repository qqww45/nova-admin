import http from '@/utils/request';
import type { ConfigResp, ConfigSaveReq, LogLineResp } from '@/types/api';

/**
 * 系统配置接口。
 *
 * <p>配置项存储在数据库、读路径走服务端缓存；修改后立即生效（服务端会同步刷新缓存），
 * 无需重启应用。多实例部署时修改只会落在"处理请求的那个实例"的缓存里，
 * 需要调用 {@link refresh} 让其它实例重新加载。
 */
export const configApi = {
  /**
   * 配置列表。
   *
   * @param keyword 键名或备注关键字（可空）
   */
  list(keyword?: string): Promise<ConfigResp[]> {
    return http.get<ConfigResp[]>('/system/config/list', { keyword });
  },

  /** 单条配置 */
  get(key: string): Promise<ConfigResp> {
    return http.get<ConfigResp>(`/system/config/${encodeURIComponent(key)}`);
  },

  /** 新增配置 */
  create(data: ConfigSaveReq): Promise<ConfigResp> {
    return http.post<ConfigResp>('/system/config', data);
  },

  /** 修改配置值（内置项可改值不可删除） */
  update(data: ConfigSaveReq): Promise<void> {
    return http.put<void>('/system/config', data);
  },

  /** 删除配置（内置项不可删除） */
  remove(key: string): Promise<void> {
    return http.delete<void>(`/system/config/${encodeURIComponent(key)}`);
  },

  /**
   * 刷新缓存：从数据库全量重新加载。
   *
   * @returns 缓存中的配置条数
   */
  refresh(): Promise<number> {
    return http.post<number>('/system/config/refresh');
  },
};

/**
 * 系统日志接口（在线查看本机日志）。
 *
 * <p>定位：单机排障。多实例环境下每台机器日志独立，
 * 跨实例检索需要依赖集中式日志系统。
 */
export const logApi = {
  /**
   * 查看日志尾部。
   *
   * @param lines   行数（1~1000，默认 200）
   * @param level   级别过滤：ERROR / WARN / INFO / DEBUG
   * @param keyword 关键字过滤
   */
  tail(lines = 200, level?: string, keyword?: string): Promise<LogLineResp[]> {
    return http.get<LogLineResp[]>('/system/log/tail', { lines, level, keyword });
  },
};
