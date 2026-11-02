import { getFullAuth } from '../configStore'

type Json = unknown

/**
 * 金蝶出错时不一定走非 200：可能返回 {"Result":{"ResponseStatus":{"IsSuccess":false,"Errors":[...]}}}
 * 甚至套一层数组。这里提取出错误消息，识别不到返回 null。
 */
export function extractKingdeeError(data: unknown): string | null {
  const holder = (Array.isArray(data) ? data[0] : data) as Record<string, unknown> | undefined
  const result = holder && typeof holder === 'object' && 'Result' in holder ? holder.Result : null
  if (!result || typeof result !== 'object') return null
  const rs = (result as Record<string, unknown>).ResponseStatus as
    | { IsSuccess?: boolean; ErrorCode?: number; Errors?: Array<{ Message?: string }> }
    | undefined
  if (!rs || rs.IsSuccess !== false) return null
  const msgs = Array.isArray(rs.Errors)
    ? rs.Errors.map((e) => e?.Message).filter((m): m is string => Boolean(m))
    : []
  return msgs.join('；') || (rs.ErrorCode ? `错误码 ${rs.ErrorCode}` : '') || '未知错误'
}

export class KingdeeClient {
  private cookies = new Map<string, string>()
  private base = ''

  private cookieHeader() {
    return [...this.cookies.entries()].map(([k, v]) => `${k}=${v}`).join('; ')
  }

  private absorbSetCookie(res: Response) {
    const anyHeaders = res.headers as Headers & { getSetCookie?: () => string[] }
    const list =
      typeof anyHeaders.getSetCookie === 'function'
        ? anyHeaders.getSetCookie()
        : (() => {
            const single = res.headers.get('set-cookie')
            return single ? [single] : []
          })()
    for (const raw of list) {
      const part = raw.split(';')[0]
      const eq = part.indexOf('=')
      if (eq > 0) this.cookies.set(part.slice(0, eq), part.slice(eq + 1))
    }
  }

  async login(): Promise<{ ok: boolean; message: string }> {
    const auth = getFullAuth()
    if (!auth.serverUrl || !auth.acctId || !auth.username || !auth.password) {
      return { ok: false, message: '请先完整填写服务器、账套、账号与密码' }
    }
    this.base = auth.serverUrl.replace(/\/+$/, '')
    this.cookies.clear()
    const url = `${this.base}/K3Cloud/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc`
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          parameters: [auth.acctId, auth.username, auth.password, auth.language]
        })
      })
      this.absorbSetCookie(res)
      const data = (await res.json()) as { LoginResultType?: number; Message?: string }
      if (data.LoginResultType === 1) {
        return { ok: true, message: '登录成功' }
      }
      return { ok: false, message: data.Message || `登录失败（LoginResultType=${data.LoginResultType}）` }
    } catch (e) {
      return { ok: false, message: e instanceof Error ? e.message : String(e) }
    }
  }

  async executeBillQuery(payload: {
    FormId: string
    FieldKeys: string
    FilterString?: string
    OrderString?: string
    StartRow?: number
    Limit?: number
  }): Promise<Json[][]> {
    const url = `${this.base}/K3Cloud/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc`
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: this.cookieHeader()
      },
      body: JSON.stringify({
        data: {
          FormId: payload.FormId,
          FieldKeys: payload.FieldKeys,
          FilterString: payload.FilterString || '',
          OrderString: payload.OrderString || '',
          StartRow: payload.StartRow ?? 0,
          Limit: payload.Limit ?? 2000
        }
      })
    })
    this.absorbSetCookie(res)
    const data = await res.json()
    const kdErr = extractKingdeeError(data)
    if (kdErr) {
      throw new Error(`金蝶查询失败: ${kdErr}`)
    }
    if (!Array.isArray(data)) {
      const msg =
        typeof data === 'object' && data && 'Result' in (data as object)
          ? JSON.stringify(data)
          : String(data)
      throw new Error(`查询返回异常: ${msg}`)
    }
    return data as Json[][]
  }

  /** 通用 WebAPI 服务调用：拿到单据元数据等（不同版本服务名不同，逐个试） */
  async callService(service: string, parameters: unknown[]): Promise<Json> {
    const url = `${this.base}/K3Cloud/${service}.common.kdsvc`
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: this.cookieHeader()
      },
      body: JSON.stringify({ parameters })
    })
    this.absorbSetCookie(res)
    const text = await res.text()
    let data: Json
    try {
      data = JSON.parse(text) as Json
    } catch {
      const head = text.replace(/\s+/g, ' ').slice(0, 120)
      throw new Error(`服务 ${service} 返回非 JSON（HTTP ${res.status}）：${head}`)
    }
    const kdErr = extractKingdeeError(data)
    if (kdErr) throw new Error(kdErr)
    return data
  }

  async probeOutboundSample(): Promise<{ ok: boolean; message: string }> {
    try {
      const rows = await this.executeBillQuery({
        FormId: 'SAL_OUTSTOCK',
        FieldKeys: 'FID,FBillNo,FDate',
        OrderString: 'FDate DESC',
        StartRow: 0,
        Limit: 1
      })
      if (!rows.length) {
        return { ok: true, message: '登录成功，但未查到销售出库单（请确认权限或账套数据）' }
      }
      return { ok: true, message: `连通正常，样例出库单：${rows[0]?.[1] ?? rows[0]?.[0]}` }
    } catch (e) {
      return { ok: false, message: e instanceof Error ? e.message : String(e) }
    }
  }
}

let singleton: KingdeeClient | null = null

export function getKingdee(): KingdeeClient {
  if (!singleton) singleton = new KingdeeClient()
  return singleton
}
