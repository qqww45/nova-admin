/**
 * 剪贴板表格解析（从 Excel / WPS / 网页表格复制的内容）。
 *
 * <p>要处理的真实情况：
 * <ul>
 *     <li>列分隔符是制表符 \t，行分隔符是 \r\n；</li>
 *     <li>单元格里有换行或制表符时，Excel 会用双引号包起来，内部的双引号写成两个（""）；</li>
 *     <li>复制区域末尾常带一个空行，需要丢掉；</li>
 *     <li>数字常被复制成 "1,234.5"（千分位）或 "12.00"，交给后端 ValueParser 处理，
 *         这里只负责原样还原文本，不做类型转换。</li>
 * </ul>
 *
 * <p>只做纯文本还原，不猜测类型 —— 类型判断是后端 ValueParser 的职责，
 * 前端擅自猜类型会把 "0012" 变成 12。
 *
 * @param text 剪贴板纯文本
 * @returns 二维单元格数组（已去掉末尾空行）
 */
export function parseClipboardTable(text: string): string[][] {
  if (!text) {
    return [];
  }
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;

  const pushField = (): void => {
    row.push(field);
    field = '';
  };
  const pushRow = (): void => {
    pushField();
    rows.push(row);
    row = [];
  };

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (inQuotes) {
      if (char === '"') {
        // 两个连续双引号 = 转义后的一个引号；否则为字段结束
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += char;
      }
      continue;
    }
    if (char === '"') {
      inQuotes = true;
    } else if (char === '\t') {
      pushField();
    } else if (char === '\r') {
      // \r\n 视为一个换行；单独的 \r 也按换行处理
      if (text[i + 1] === '\n') {
        i++;
      }
      pushRow();
    } else if (char === '\n') {
      pushRow();
    } else {
      field += char;
    }
  }
  // 最后一行（文本不以换行结尾时）
  if (field !== '' || row.length > 0) {
    pushRow();
  }

  // 去掉末尾的全空行（Excel 复制区域的常见尾巴）
  while (rows.length > 0 && rows[rows.length - 1].every((cell) => cell.trim() === '')) {
    rows.pop();
  }
  return rows;
}

/**
 * 判断一段文本是否"像"从表格里复制出来的（用于决定粘贴时是填表还是普通输入）。
 *
 * @param text 剪贴板文本
 * @returns 含制表符或换行即判定为表格
 */
export function looksLikeTable(text: string): boolean {
  return text.includes('\t') || text.includes('\n') || text.includes('\r');
}
