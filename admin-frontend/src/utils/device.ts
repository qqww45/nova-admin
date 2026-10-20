/**
 * 设备标识与浏览器指纹。
 *
 * <p>用途：支撑「可信设备免验证码」——同一台设备、同一个浏览器应得到稳定不变的 deviceId，
 * 后端据此判断"这台设备以前验证过吗"。
 *
 * <p>设计取舍：
 * <ul>
 *     <li><b>不用完整 IP</b>：家庭宽带重拨、办公网 DHCP 都会换 IP，用 IP 会导致频繁误判为陌生设备；</li>
 *     <li><b>不用纯 UA</b>：UA 易被伪造，且同机不同浏览器 UA 不同；</li>
 *     <li><b>指纹取自稳定特征</b>：屏幕、色深、时区、语言、画布渲染特征（canvas 在不同 GPU/驱动下结果不同，
 *         可区分设备），不含任何账号信息，属于合规做法；</li>
 *     <li><b>deviceId 持久化在 localStorage</b>：指纹可能因浏览器升级而变化，
 *         已生成的 deviceId 保持稳定才能避免"升级后又要重新验证"。</li>
 * </ul>
 */

/** 本地存储键 */
const DEVICE_ID_KEY = 'admin-device-id';

/**
 * 简单字符串哈希（FNV-1a 变体，32 位十六进制）。
 *
 * <p>说明：这里只需要"稳定 + 分散"，不需要抗碰撞强度，
 * 真正的随机性由后端 SHA-256 兜底，前端避免引入额外依赖。
 *
 * @param input 输入字符串
 */
function hash(input: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

/**
 * 采集浏览器稳定特征，生成指纹哈希。
 *
 * <p>canvas 指纹原理：同样的绘图指令在不同 GPU / 驱动 / 字体渲染下会产生细微差异，
 * 因此可作为设备特征；这里只取像素数据的摘要，不保存图像本身。
 *
 * @returns 指纹字符串（多段哈希拼接，降低单一特征变化的影响）
 */
/** 指纹缓存：canvas 渲染有开销，且同一次会话内结果稳定 */
let cachedFingerprint: string | null = null;

function fingerprint(): string {
  if (cachedFingerprint) {
    return cachedFingerprint;
  }
  const nav = window.navigator;
  const screen = window.screen;
  const parts: string[] = [
    nav.userAgent,
    nav.language,
    String(nav.hardwareConcurrency ?? 0),
    `${screen.width}x${screen.height}x${screen.colorDepth}`,
    String(new Date().getTimezoneOffset()),
  ];

  try {
    const canvas = document.createElement('canvas');
    canvas.width = 200;
    canvas.height = 40;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.textBaseline = 'top';
      ctx.font = '14px Arial';
      ctx.fillStyle = '#f60';
      ctx.fillRect(0, 0, 100, 30);
      ctx.fillStyle = '#06f';
      ctx.fillText('admin-device', 4, 8);
      parts.push(hash(canvas.toDataURL()));
    }
  } catch {
    // 部分浏览器（隐私模式 / 禁用 canvas）可能抛异常，忽略即可，其余特征仍可用
  }

  cachedFingerprint = parts.map(hash).join('');
  return cachedFingerprint;
}

/**
 * 获取（或首次生成并持久化）设备标识。
 *
 * @returns 设备标识
 */
/** localStorage 不可用时（隐私模式）的进程内兜底值 */
let memoryDeviceId: string | null = null;

export function getDeviceId(): string {
  if (memoryDeviceId) {
    return memoryDeviceId;
  }
  try {
    let deviceId = localStorage.getItem(DEVICE_ID_KEY);
    if (!deviceId) {
      deviceId = hash(fingerprint()) + hash(String(Date.now()) + Math.random().toString(36));
      localStorage.setItem(DEVICE_ID_KEY, deviceId);
    }
    memoryDeviceId = deviceId;
    return deviceId;
  } catch {
    // Safari 隐私模式、禁用第三方存储、磁盘配额耗尽时读写 localStorage 会抛 SecurityError。
    // 每个请求都会调用本方法（request.ts 里统一注入设备标识），这里必须兜底，
    // 否则一次抛错会让全站请求崩溃、登录页白屏。退化成"仅本次会话有效"的内存值即可。
    memoryDeviceId = hash(fingerprint()) + hash(String(Date.now()) + Math.random().toString(36));
    return memoryDeviceId;
  }
}

/**
 * 获取浏览器指纹（随登录请求上报，供后端合成 / 校验设备标识）。
 *
 * @returns 指纹哈希
 */
export function getFingerprint(): string {
  return fingerprint();
}

/**
 * 清除本地设备标识（如用户主动"忘记此设备"）。
 */
export function clearDeviceId(): void {
  localStorage.removeItem(DEVICE_ID_KEY);
}
