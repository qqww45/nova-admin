/**
 * 后端统一响应与业务模型类型定义。
 *
 * <p>与后端 R<T> / DTO 一一对应，禁止在组件里写 any，
 * 保证接口字段变更时 TypeScript 能在编译期发现问题。
 */

/** 统一响应包装 */
export interface R<T> {
  code: number;
  msg: string;
  data: T;
  timestamp?: number;
}

/** 分页数据 */
export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

/** 登录请求 */
export interface LoginReq {
  username: string;
  password: string;
  // 图形验证码字段已移除：登录主流程是风险分层验证（无风险直接放行、有风险则下发票据做验证），
  // 这两个字段服务端从未读取，留着会让人误以为存在验证码防线
  /** 设备标识（前端生成并持久化） */
  deviceId?: string;
  /** 浏览器指纹哈希 */
  fingerprint?: string;
  /** 是否信任此设备（默认 true，写入信任后该设备免验证码） */
  trustDevice?: boolean;
}

/** 登录响应 */
export interface LoginResp {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  username: string;
  nickname: string;
  /** 服务端确认的设备标识（前端应持久化，保持设备稳定识别） */
  deviceId: string;
  /** 该设备是否已纳入可信列表 */
  trusted: boolean;
}

/** 在线会话（一条 = 一个用户的一台设备） */
export interface OnlineSessionResp {
  userId: string;
  username: string;
  nickname: string;
  deptName: string;
  ip: string;
  browser: string;
  os: string;
  deviceId: string;
  /** 该设备是否已纳入可信列表 */
  trusted: boolean;
  loginTime: string;
  lastAccessTime: string;
}

/** 可信设备 */
export interface DeviceTrustResp {
  deviceId: string;
  trustTime: string;
  expireTime: string;
  online: boolean;
}

/** 强制下线请求（deviceId 为空表示该用户所有设备） */
export interface KickReq {
  userId: string;
  deviceId?: string;
}

/** 人机验证挑战（两阶段登录的中间态：登录返回 1014/1015 时携带） */
export interface ChallengeResp {
  /** 一次性票据（验证时原样回传，用后即焚） */
  ticketId: string;
  /** 验证强度：LIGHT=滑块拼图 / STRONG=一次性码 */
  level: 'LIGHT' | 'STRONG';
  /** 缺口目标 x（像素），由服务端下发，前端据此渲染 */
  targetX?: number;
  /** 票据有效期（秒） */
  expireSeconds: number;
  /** 面向用户的说明 */
  hint: string;
}

/** 完成验证的请求 */
export interface ChallengeVerifyReq {
  ticketId: string;
  username: string;
  deviceId?: string;
  /** 滑块落点 x（像素），轻验证用 */
  slideX?: number;
  /** 拖动轨迹采样（按时间有序的 x 序列），轻验证用 */
  track?: number[];
  /** 从按下到松开的总时长（毫秒），轻验证用 */
  durationMs?: number;
  /** 从按下到首次移动的停顿（毫秒），轻验证用 */
  startDelayMs?: number;
  /** 挑战下发到提交的总耗时（毫秒），轻验证用 */
  elapsedMs?: number;
  /** 一次性验证码，强验证用 */
  code?: string;
  trustDevice?: boolean;
}

/** 请求下发一次性验证码 */
export interface OneTimeCodeReq {
  ticketId: string;
  username: string;
  deviceId?: string;
}

/** 一次性验证码下发结果 */
export interface OneTimeCodeResp {
  /** 票据 ID（轻验证降级换发时以新票据为准） */
  ticketId: string;
  expireSeconds: number;
  /** 发送目标（脱敏） */
  target: string;
  /** 开发模式回传的验证码（生产为空） */
  devCode?: string;
}

/** 用户信息（个人中心 / getInfo） */
export interface UserInfo {
  id: string;
  username: string;
  nickname: string;
  avatar: string;
  email: string;
  phone: string;
  sex: number;
  status: number;
  theme: string;
  deptName: string;
  loginIp: string;
  loginDate: string;
  createTime: string;
  remark: string;
}

/** getInfo 响应 */
export interface GetInfoResp {
  user: UserInfo;
  roles: string[];
  permissions: string[];
}

/** 菜单视图对象（同时用于动态路由） */
export interface MenuVo {
  id: string;
  parentId: string;
  menuName: string;
  /** 菜单类型：1 目录 2 菜单 3 按钮（与后端一致，用整数编码） */
  menuType: number;
  path: string;
  component: string;
  /**
   * 权限标识，如 system:user:list。
   *
   * <p>字段名必须与后端保持一致：后端 MenuVo / MenuSaveReq 均为 permission，
   * 曾误写为 perms，导致响应解析后恒为 undefined、保存时该字段被后端忽略。
   */
  permission: string;
  icon: string;
  sort: number;
  visible: number;
  status: number;
  children: MenuVo[];
}

/** 用户列表项 */
export interface UserResp {
  id: string;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  sex: number;
  avatar: string;
  status: number;
  deptId: string;
  deptName: string;
  roleIds: string[];
  roleNames: string[];
  loginIp: string;
  loginDate: string;
  createTime: string;
  remark: string;
}

/** 用户保存请求 */
export interface UserSaveReq {
  id?: string;
  username: string;
  nickname: string;
  password?: string;
  email: string;
  phone: string;
  sex: number;
  status: number;
  deptId?: string;
  roleIds: string[];
  remark: string;
}

/** 用户查询条件 */
export interface UserQuery {
  username?: string;
  phone?: string;
  status?: number;
  deptId?: string;
  pageNum: number;
  pageSize: number;
}

/** 角色列表项 */
export interface RoleResp {
  id: string;
  roleName: string;
  roleKey: string;
  sort: number;
  status: number;
  remark: string;
  createTime: string;
  /**
   * 已分配的菜单 ID（后端 RoleResp 始终返回；分配权限抽屉据此回填勾选）。
   */
  menuIds: string[];
}

/** 角色保存请求 */
export interface RoleSaveReq {
  id?: string;
  roleName: string;
  roleKey: string;
  sort: number;
  status: number;
  remark: string;
}

/** 角色下拉选项 */
export interface RoleOptionResp {
  id: string;
  roleName: string;
  roleKey: string;
}

/** 部门树节点 */
export interface DeptVo {
  id: string;
  parentId: string;
  deptName: string;
  sort: number;
  leader: string;
  phone: string;
  email: string;
  status: number;
  children: DeptVo[];
}

/** 登录日志 */
export interface LoginLogResp {
  id: string;
  username: string;
  ip: string;
  location: string;
  browser: string;
  os: string;
  status: number;
  msg: string;
  loginTime: string;
}

/** 仪表盘汇总 */
export interface DashboardSummary {
  userCount: number;
  roleCount: number;
  menuCount: number;
  deptCount: number;
  todayLoginCount: number;
  todayFailCount: number;
  loginTrend: TrendPoint[];
}

/** 趋势点 */
export interface TrendPoint {
  date: string;
  value: number;
}

/** 系统配置项 */
export interface ConfigResp {
  id: string;
  configKey: string;
  configValue: string;
  /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
  configType: string;
  /** true 表示内置项，不可删除（可改值） */
  builtin: boolean;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 配置新增/修改请求 */
export interface ConfigSaveReq {
  configKey: string;
  configValue?: string;
  configType?: string;
  remark?: string;
}

/** 日志行（在线查看，返回原始行） */
export interface LogLineResp {
  content: string;
}

/** 系统信息（仪表盘"系统参数"卡片） */
export interface SystemInfoResp {
  appName: string;
  version: string;
  activeProfile: string;
  startTime: string;
  uptime: string;
  switches: Record<string, string>;
  javaVersion: string;
  jvmHeap: MemoryUsage;
  jvmNonHeap: MemoryUsage;
  availableProcessors: number;
  os: string;
  arch: string;
  workDir: string;
  databaseType: string;
  driverVersion: string;
  connectionPool: PoolStats;
  userCount: number;
  roleCount: number;
  deptCount: number;
  todayLoginLogCount: number;
  onlineSessionCount: number;
}

/** 内存用量（MB） */
export interface MemoryUsage {
  usedMb: number;
  maxMb: number;
  initMb: number;
}

/** 连接池用量 */
export interface PoolStats {
  active: number;
  idle: number;
  total: number;
  max: number;
}
