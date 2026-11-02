/**
 * M0 连通性探针（不依赖 Electron）
 * 用法：
 *   set HZ_SERVER=https://xxx.kingdee.com
 *   set HZ_ACCT=数据中心内码
 *   set HZ_USER=账号
 *   set HZ_PASS=密码
 *   npm run m0
 */
const server = (process.env.HZ_SERVER || '').replace(/\/+$/, '')
const acct = process.env.HZ_ACCT || ''
const user = process.env.HZ_USER || ''
const pass = process.env.HZ_PASS || ''

if (!server || !acct || !user || !pass) {
  console.error('缺少环境变量 HZ_SERVER / HZ_ACCT / HZ_USER / HZ_PASS')
  process.exit(1)
}

const cookies = new Map()

function cookieHeader() {
  return [...cookies.entries()].map(([k, v]) => `${k}=${v}`).join('; ')
}

function absorb(res) {
  const list =
    typeof res.headers.getSetCookie === 'function'
      ? res.headers.getSetCookie()
      : res.headers.get('set-cookie')
        ? [res.headers.get('set-cookie')]
        : []
  for (const raw of list) {
    const part = String(raw).split(';')[0]
    const eq = part.indexOf('=')
    if (eq > 0) cookies.set(part.slice(0, eq), part.slice(eq + 1))
  }
}

async function post(path, body) {
  const res = await fetch(`${server}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookieHeader()
    },
    body: JSON.stringify(body)
  })
  absorb(res)
  return res.json()
}

const login = await post(
  '/K3Cloud/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc',
  { parameters: [acct, user, pass, 2052] }
)
console.log('login:', login.LoginResultType, login.Message || '')
if (login.LoginResultType !== 1) process.exit(2)

async function query(formId, fieldKeys) {
  return post(
    '/K3Cloud/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc',
    {
      data: {
        FormId: formId,
        FieldKeys: fieldKeys,
        FilterString: '',
        OrderString: '',
        StartRow: 0,
        Limit: 3
      }
    }
  )
}

for (const [name, formId, keys] of [
  ['customer', 'BD_Customer', 'FCUSTID,FNumber,FName'],
  ['sale_order', 'SAL_SaleOrder', 'FID,FBillNo,FDate'],
  ['outbound', 'SAL_OUTSTOCK', 'FID,FBillNo,FDate']
]) {
  try {
    const rows = await query(formId, keys)
    console.log(name, Array.isArray(rows) ? `rows=${rows.length}` : rows)
    if (Array.isArray(rows) && rows[0]) console.log(' sample', rows[0])
  } catch (e) {
    console.error(name, 'error', e)
  }
}

// 出库分录字段逐组探测，锁定数量/单价/金额/单位/源单号的真实字段名
const lineCandidates = [
  'FEntryID,FMaterialId.FNumber,FMaterialId.FName,FUnitID.FName,FRealQty,FTaxPrice,FAllAmount,FSrcBillNo,FEntryNote',
  'FEntryID,FMaterialId.FNumber,FMaterialId.FName,FUnitID.FName,FQty,FTaxPrice,FAllAmount,FSrcBillNo,FEntryNote',
  'FEntryID,FMaterialId.FNumber,FMaterialId.FName,FQty,FPrice,FAmount'
]
for (const keys of lineCandidates) {
  try {
    const rows = await query('SAL_OUTSTOCK', keys)
    console.log('\nOUT_LINES OK →', keys)
    if (Array.isArray(rows) && rows[0]) console.log(' sample', rows[0])
    break
  } catch (e) {
    console.log('\nOUT_LINES FAIL →', keys)
    console.log('  reason:', String(e && e.message ? e.message : e).slice(0, 300))
  }
}

console.log('\n请对照金蝶界面锁定含税字段与源单/PO，写入 resources/mappings/outstock.json')
console.log('客户采购订单号字段确认后，填到 outstock.json 的 customerPoField（如 F_PO_No）')
