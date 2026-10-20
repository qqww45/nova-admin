import http from '@/utils/request';
import type { DeviceTrustResp, KickReq, OnlineSessionResp } from '@/types/api';

/**
 * 在线用户与设备管理接口。
 *
 * <p>设计说明：在线会话是"运行时数据"（不落库），因此不做分页，
 * 一次性返回当前全部会话，页面侧自行过滤即可。
 */
export const onlineApi = {
  /**
   * 在线会话列表。
   *
   * @param username 用户名/昵称关键字（可空）
   */
  list(username?: string): Promise<OnlineSessionResp[]> {
    return http.get<OnlineSessionResp[]>('/system/online/list', { username });
  },

  /**
   * 强制下线。
   *
   * @param data 用户 ID 与设备标识（设备为空表示全部设备）
   * @returns 被下线的设备数量
   */
  kick(data: KickReq): Promise<number> {
    return http.post<number>('/system/online/kick', data);
  },

  /**
   * 某用户的可信设备列表。
   *
   * @param userId 用户 ID
   */
  devices(userId: string): Promise<DeviceTrustResp[]> {
    return http.get<DeviceTrustResp[]>(`/system/online/devices/${userId}`);
  },

  /**
   * 移除可信设备（下次登录需重新验证）。
   *
   * @param userId   用户 ID
   * @param deviceId 设备标识
   */
  removeTrust(userId: string, deviceId: string): Promise<void> {
    return http.delete<void>(`/system/online/device/${userId}/${deviceId}`);
  },
};
