# CALIBRE — 自行车装配 MES 设计系统

> 版本 4.0 · 2026-11-14
> 本文件是前端视觉的唯一事实来源。任何页面、组件、弹窗的样式决策都必须从这里推导，不允许临场发挥。

---

## 1. Visual Theme & Atmosphere

**主题：CALIBRE / 精工序。**

这不是一个 SaaS 后台，而是一间**精密制造工坊**：黑曜石般的机加工车间、香槟金色的量具、瓷白色的工程图纸。视觉气质介于高级制表与航空仪表之间——冷静、精确、昂贵、有秩序。

- **材质**：黑曜石金属（外壳）、瓷白纸张（工作区）、香槟金（量具与刻度）、玉石绿（运行状态）。
- **密度**：中等偏松。每个页面是一张工程图纸，不是一堵数据墙。
- **几何**：几乎无圆角（2–4px）。大量 1px 发丝线、L 形校准角标、刻度尺、编号。
- **光线**：不发光、不霓虹。只用极细的金色高光和低饱和阴影塑造层次。
- **情绪关键词**：精密、克制、昂贵、可信、可测量。

**Signature（唯一记忆点）**：每张页面/面板左上角的 **L 形香槟金校准角标 + 微缩序列号（`CAL · 03`）+ 模块英文眉标**。所有页面共享同一套"校准框"，这是 CALIBRE 的身份。

---

## 2. Color Palette & Roles

### 2.1 外壳（Obsidian）

| Token | Hex | 用途 |
|---|---|---|
| `--lux-ink-950` | `#070A0F` | 页面最深底色 / 登录左屏 |
| `--lux-ink-900` | `#0B0F15` | 侧边栏、看板背景 |
| `--lux-ink-800` | `#11161F` | 深色面板、表头 |
| `--lux-ink-700` | `#1A212C` | 深色浮层、hover |
| `--lux-shell-line` | `rgba(198,169,110,.14)` | 外壳发丝线 |
| `--lux-shell-text` | `#EDE8DC` | 深色底主文字 |
| `--lux-shell-muted` | `#8B857A` | 深色底次文字 |

### 2.2 工作区（Porcelain）

| Token | Hex | 用途 |
|---|---|---|
| `--lux-porcelain-50` | `#FAF8F3` | 卡片内最亮面 |
| `--lux-porcelain-100` | `#F3EFE6` | 内容区背景 |
| `--lux-porcelain-200` | `#EAE4D8` | 软填充、hover 底 |
| `--lux-porcelain-300` | `#D9D2C2` | 强边框 |
| `--lux-surface` | `#FFFDF9` | 页面/卡片主表面 |

### 2.3 量具金（Champagne）

| Token | Hex | 用途 |
|---|---|---|
| `--lux-gold-300` | `#E2CD9E` | 高光、渐变亮端 |
| `--lux-gold-400` | `#D2B77F` | 强调文字 / 图标 |
| `--lux-gold-500` | `#C6A96E` | 主强调色、刻度、角标 |
| `--lux-gold-600` | `#A8894F` | 可访问的金色文字（浅底） |
| `--lux-gold-700` | `#866A3B` | 深金、链接 hover |

> 金色是"量具"，不是"装饰"。只用于：角标、刻度、关键数字的单位、主按钮、选中态、细分隔线。禁止大面积金色铺底。

### 2.4 状态色

| 语义 | Hex | 浅底 | 边框 |
|---|---|---|---|
| 运行 / 成功 / 在线 | `#3F8F79` | `#EDF6F1` | `#CFE6DC` |
| 警示 / 待处理 | `#B8752E` | `#FBF4E6` | `#EBD9B4` |
| 危险 / 故障 | `#A8454F` | `#FAEDEE` | `#EBCFD3` |
| 中性 / 停用 | `#6F6A5F` | `#F4F2ED` | `#DFD9CC` |

### 2.5 文字

| Token | Hex | 用途 |
|---|---|---|
| `--lux-text-strong` | `#14181F` | 标题、数字 |
| `--lux-text` | `#2A2F38` | 正文 |
| `--lux-text-muted` | `#6F6A5F` | 辅助说明 |
| `--lux-text-faint` | `#9A9487` | 占位、微标签 |
| `--lux-line` | `#E3DDD0` | 常规边框 |
| `--lux-line-strong` | `#CFC7B6` | 强调边框 |

---

## 3. Typography Rules

系统已安装字体，**禁止引入外部字体/CDN**。

| 角色 | 字体栈 | 用法 |
|---|---|---|
| Display 展示 | `"Noto Serif SC", "Source Han Serif SC", Georgia, serif` | 页面主标题、登录大标题、看板标题、弹窗标题 |
| UI 正文 | `"Noto Sans SC", "Inter", "PingFang SC", "Microsoft YaHei", sans-serif` | 表格、表单、菜单、按钮 |
| Data 数据 | `"Bahnschrift", "DIN Alternate", "Roboto Condensed", Consolas, monospace` | 大数字、ID、时间、编码、微标签 |

### 层级

| 层级 | 字号 | 字重 | 字距 | 行高 | 字体 |
|---|---|---|---|---|---|
| 登录主标题 | clamp(40px, 4.4vw, 64px) | 300 | .02em | 1.22 | Display |
| 页面标题 h1 | clamp(24px, 2vw, 30px) | 500 | .01em | 1.25 | Display |
| 看板标题 | clamp(24px, 2.4vw, 34px) | 400 | .06em | 1.25 | Display |
| 区块标题 h2 | 16px | 600 | .01em | 1.4 | UI |
| 正文 | 13px | 400 | .01em | 1.65 | UI |
| 辅助说明 | 12px | 400 | .01em | 1.6 | UI |
| 微标签 / 眉标 | 9–10px | 600 | .18em | 1.4 | Data, uppercase |
| 大数字 | 32–56px | 400 | -.02em | 1.1 | Data, tabular-nums |

**规则**
- 中文标题用 Noto Serif SC，字重 400–500；不要用 700 以上，避免"宋体公文感"。
- 英文眉标一律大写 + 宽字距 + 9–10px，颜色 `--lux-gold-600` 或 `--lux-text-faint`。
- 所有数字列（ID、数量、金额、时间）使用 `font-family: var(--lux-font-data); font-variant-numeric: tabular-nums`。
- 禁止使用 `font-weight: 650` 这类非标准字重；用 500/600。

---

## 4. Component Stylings

### 4.1 页面容器 `.page-container`

- 表面 `--lux-surface`，1px `--lux-line`，圆角 4px，内边距 26px 28px 30px。
- 左上角 + 右下角各一组 **L 形校准角标**（14px，1px 金线）。
- 顶部 1px 金色渐隐线：从左 32% 宽的金色渐变到透明。
- 阴影：`0 1px 2px rgba(35,28,15,.04), 0 24px 48px -36px rgba(35,28,15,.28)`。
- 页面标题上方是 `.page-meta`：`CAL · 03`（金）+ `QUALITY / 质量管控`（灰）+ 发丝分隔。

### 4.2 按钮

- **Primary**：香槟金渐变 `linear-gradient(135deg,#E2CD9E 0%,#C6A96E 55%,#B08F55 100%)`，文字 `#17130B`，1px `#A8894F`，顶部内高光，hover 上移 1px 并提亮。高度 34px，圆角 2px。
- **Default**：`#FFFDF9` 底，1px `#D8D1C2`，文字 `#2A2F38`；hover 金色边框 + 暖白底。
- **Danger**：白底 + 酒红文字/边框；hover 酒红底白字。
- **Text/Link**：`--lux-gold-700`，hover 更深；不加背景。
- 图标与文字间距 6px；按钮组间距 8px。

### 4.3 输入 / 选择

- 高度 36px（大号 44px），圆角 2px，底色 `#FFFDF9`，1px `#D8D1C2`。
- hover 边框 `#BFB5A2`；focus 金色 1px + `0 0 0 3px rgba(198,169,110,.16)`。
- placeholder `--lux-text-faint`；标签 12px `--lux-text-muted`。
- 下拉浮层：白底、1px `#E3DDD0`、圆角 3px、暖阴影；选项 hover `#F8F3E8`，选中金色文字。

### 4.4 表格

- 外框 1px `#E3DDD0`，圆角 3px，白底。
- **表头：深墨 `#14181F` 底，文字 `#C9C0AD`，9–10px 大写、字距 .14em，底部 1px `#A8894F`。** 这是数据页的视觉锚点。
- 行高 48px；正文 13px `#3C414A`；行分隔 `#EFE9DD`。
- 斑马行 `#FBF8F1`；hover `#F7F2E6`；固定列阴影暖色。
- 操作列按钮用 link 样式，间距 2px；危险操作酒红。
- 空态：`el-empty` 描述 12px 灰。

### 4.5 标签 / 状态

- 圆角 2px，高 22px，字号 11px，1px 边框。
- 成功：玉石绿文字/浅底/边框；警示：琥珀；危险：酒红；中性：暖灰。
- 状态圆点 5px，配 1px 同色环；在线状态加 2s 呼吸动画（reduced-motion 关闭）。

### 4.6 弹窗 / 抽屉

- 遮罩 `rgba(7,10,15,.58)` + blur 3px。
- 白底，1px `#D8D1C2`，圆角 4px，阴影 `0 30px 90px rgba(7,10,15,.35)`。
- 标题：Noto Serif SC 18px 500；头部底边 1px 金调发丝线。
- 内容 22px；底部按钮右对齐。
- 弹窗内表单标签宽度按内容自适应，禁止超过 120px。

### 4.7 搜索栏与工具条

- `.search-bar`：暖白底 `#F8F5EE`，1px `#EAE4D8`，**左侧 2px 金色竖轨**，圆角 3px，内边距 14px 16px。
- 输入宽 200px；按钮组右对齐（`margin-left:auto`）。
- `.table-toolbar`：右侧操作，左侧 `.table-toolbar-copy` 用 Data 字体 11px、字距 .08em、色 `#8B857A`。
- `.pagination-bar`：右对齐，顶部 1px `#EFE9DD`。

### 4.8 卡片 / 统计

- `.section-card`：白底、1px、圆角 3px、内边距 22px。
- `.stat-card`：白底、1px、左上角 8px 金色短刻度；数值 Data 字体 30px；标签 10px 大写字距。
- 禁止大圆角、彩色渐变卡片、发光边框。

### 4.9 微标签 / 序列号

```html
<span class="lux-kicker">CAL · 03</span>
<span class="lux-kicker lux-kicker--muted">QUALITY / 质量管控</span>
```

---

## 5. Layout Principles

- 外壳：侧边栏 258px（收起 78px）+ 顶栏 64px；内容区内边距 26px 28px。
- 页面最大宽度不限制，但内容保持 24px 栅格节奏。
- 间距刻度：4 / 8 / 12 / 16 / 20 / 24 / 32 / 48。
- 内容区背景：`#F3EFE6` + 40px 极淡网格（3% 墨色）+ 顶部金色径向微光。
- 侧边栏右缘有 6px 宽的**刻度尺**（每 8px 一个 1px 刻度），这是外壳的签名细节。
- 登录页：左 58% 黑曜石视觉，右 42% 瓷白登录卡。
- 看板：全幅黑曜石指挥中心，独立于浅色内容区。

---

## 6. Depth & Elevation

| 层级 | 用法 | 值 |
|---|---|---|
| 0 | 页面底 | 无阴影 |
| 1 | 页面容器 / 卡片 | `0 1px 2px rgba(35,28,15,.04), 0 24px 48px -36px rgba(35,28,15,.28)` |
| 2 | 搜索栏 / 表格工具条 | 无阴影，仅边框 |
| 3 | 下拉 / 浮层 | `0 16px 40px rgba(20,16,8,.14)` |
| 4 | 弹窗 | `0 30px 90px rgba(7,10,15,.35)` |

深色外壳不使用彩色发光，只用 `rgba(198,169,110,.12)` 发丝线与极弱径向光。

---

## 7. Design Do's and Don'ts

### Do

- 用 1px 发丝线、刻度、序列号建立"精密感"。
- 每页只保留一个视觉重点（标题区或主数据区）。
- 金色只做"量具"：角标、单位、选中态、主按钮。
- 用 Noto Serif SC 做标题，Noto Sans SC 做正文，Bahnschrift 做数字。
- 表格表头用深墨底 + 金色底线，形成统一的数据页身份。
- 空态写"下一步做什么"，不写"暂无数据"四个字就结束。
- 动效 160–240ms，ease-out；页面切换 6px 上移淡入。

### Don't

- 禁止蓝紫渐变、霓虹发光、玻璃拟态大圆角、彩色阴影。
- 禁止大面积金色背景、金色正文、金色表格行。
- 禁止 `border-radius > 6px`（状态胶囊除外）。
- 禁止 Inter/Roboto 以外再引入新字体；禁止 CDN 字体。
- 禁止 emoji 作为品牌图标；统一用 `BrandMark` SVG。
- 禁止在业务页面写死颜色；必须使用 `--lux-*` / `--mes-*` token。
- 禁止改动业务逻辑、API 调用、权限指令、表单校验。

---

## 8. Responsive Behavior

| 断点 | 行为 |
|---|---|
| ≥ 1440px | 完整外壳；页面内边距 26–32px |
| 1180–1439px | 侧边栏自动收起为 78px |
| 900–1179px | 顶栏隐藏副标题；内容区 20px；表格横向滚动 |
| 640–899px | 页面容器 16px；页面头部纵向排列；搜索栏换行 |
| < 640px | 搜索栏单列、输入 100%；表格操作列固定；弹窗 `calc(100vw - 24px)`；登录视觉区隐藏 |

触控目标 ≥ 40×40px。键盘 focus 必须可见：2px 金色 outline + 2px offset。`prefers-reduced-motion` 下关闭所有动画。

---

## 9. Agent Prompt Guide

给页面改造 Agent 的固定指令：

> 你正在改造 CALIBRE 制造执行系统的一个页面。先读项目根 `DESIGN.md`。只改模板结构、class 和 scoped 样式，**不改任何业务逻辑、API、权限、校验、事件**。保留现有 PageContainer、search-bar、table-toolbar、el-table、el-dialog 结构；用 `PageContainer` 自动提供的校准框与序列号，不要重复实现。所有颜色必须来自 `--lux-*` / `--mes-*` token。表格必须保留 `border stripe` 与 `fixed="right"` 操作列。完成后运行 `npm run build`，确保 0 类型错误。

**快速色板**

```
ink #070A0F / #0B0F15 / #11161F
porcelain #F3EFE6 / #FFFDF9
gold #C6A96E / #A8894F
jade #3F8F79
ember #B8752E
wine #A8454F
text #14181F / #6F6A5F
line #E3DDD0
```

**语气**

界面文案用动词开头、句子式大写（中文不适用）、不堆术语。按钮说"保存更改"而不是"提交"；空态说"创建第一张工单"而不是"暂无数据"。
