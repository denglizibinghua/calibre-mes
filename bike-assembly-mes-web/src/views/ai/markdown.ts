import DOMPurify from 'dompurify'
import { marked } from 'marked'

const CITATION_MARKER = /\[来源\s*(\d+)\]/g
const TOKEN_TAG = /(<[^>]*>)/g
const TABLE_BLOCK = /<table(?:\s[^>]*)?>[\s\S]*?<\/table>/gi
const ANCHOR_OPEN = /<a\b([^>]*)>/gi

/**
 * 渲染 AI 回答 Markdown。
 * - GFM + 换行；
 * - DOMPurify 清理后，把纯文本 [来源N] 转为可点击 chip；
 * - 链接补 target/rel；表格包一层横向滚动容器。
 */
export function renderMarkdown(content: string): string {
  if (!content || !content.trim()) return ''

  const rendered = marked(content, { async: false, breaks: true, gfm: true })
  const sanitized = DOMPurify.sanitize(rendered)
  return enhanceHtml(sanitized)
}

function enhanceHtml(html: string): string {
  return withExternalLinks(withCitationChips(wrapTables(html)))
}

function wrapTables(html: string): string {
  return html.replace(TABLE_BLOCK, match => `<div class="table-scroll">${match}</div>`)
}

function withCitationChips(html: string): string {
  const parts = html.split(TOKEN_TAG)
  let codeDepth = 0

  return parts
    .map(part => {
      if (part.startsWith('<') && part.endsWith('>')) {
        const tag = part.toLowerCase()
        if (/^<(code|pre)(\s|>)/.test(tag)) codeDepth += 1
        else if (/^<\/(code|pre)>/.test(tag)) codeDepth = Math.max(0, codeDepth - 1)
        return part
      }
      if (codeDepth > 0) return part
      return part.replace(CITATION_MARKER, (_marker, index: string) => {
        return `<button type="button" class="citation-chip" data-citation-index="${index}" aria-label="查看来源 ${index}">来源 ${index}</button>`
      })
    })
    .join('')
}

function withExternalLinks(html: string): string {
  return html.replace(ANCHOR_OPEN, (match, attributes: string) => {
    let addition = ''
    if (!/\starget\s*=/i.test(attributes)) addition += ' target="_blank"'
    if (!/\srel\s*=/i.test(attributes)) addition += ' rel="noopener noreferrer"'
    return addition ? `<a${attributes}${addition}>` : match
  })
}
