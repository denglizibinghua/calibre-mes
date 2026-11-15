/**
 * 修复历史数据中的 UTF-8 → Windows-1252 误解码乱码。
 *
 * 背景：早期菜单/昵称数据以 UTF-8 写入后，被按 Windows-1252 解码，
 * 例如「系统管理」会变成「ç³»ç»Ÿç®¡ç†」。这里只在字符串不含 CJK、
 * 且能无损还原出 CJK 时才替换，正常中文与非中文文本原样返回。
 */
const CP1252_BYTES: Record<string, number> = {
  '€': 0x80, '‚': 0x82, 'ƒ': 0x83, '„': 0x84, '…': 0x85, '†': 0x86, '‡': 0x87,
  'ˆ': 0x88, '‰': 0x89, 'Š': 0x8a, '‹': 0x8b, 'Œ': 0x8c, 'Ž': 0x8e, '‘': 0x91,
  '’': 0x92, '“': 0x93, '”': 0x94, '•': 0x95, '–': 0x96, '—': 0x97, '˜': 0x98,
  '™': 0x99, 'š': 0x9a, '›': 0x9b, 'œ': 0x9c, 'ž': 0x9e, 'Ÿ': 0x9f
}

const CJK = /[\u3400-\u9fff]/

export function repairTextEncoding(value?: string | null): string {
  if (!value) return value ?? ''
  if (CJK.test(value)) return value

  const bytes: number[] = []
  for (const char of value) {
    const codePoint = char.codePointAt(0)
    if (codePoint == null) return value
    if (codePoint <= 0xff) {
      bytes.push(codePoint)
      continue
    }
    const mapped = CP1252_BYTES[char]
    if (mapped == null) return value
    bytes.push(mapped)
  }

  try {
    const decoded = new TextDecoder('utf-8', { fatal: true }).decode(new Uint8Array(bytes))
    return CJK.test(decoded) ? decoded : value
  } catch {
    return value
  }
}

/**
 * 递归修复接口返回中的文本字段，用于兼容历史数据里的编码乱码。
 * 只替换能无损还原出 CJK 的字符串，其余原样返回；不修改原对象。
 */
export function repairTextFields<T>(value: T): T {
  if (typeof value === 'string') return repairTextEncoding(value) as T
  if (Array.isArray(value)) return value.map(item => repairTextFields(item)) as T
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
      result[key] = repairTextFields(child)
    }
    return result as T
  }
  return value
}
