# Dify 云端接入指南（阶段二加分项）

> 目标：在不改动本机 MES 的前提下，用 Dify Cloud 免费版做一个「知识库检索 + 大模型 + 引用回答」的可视化工作流，作为「API 接入大模型 / 工作流编排」的加分演示。
>
> 适用范围：Dify Cloud Sandbox（免费）。本机自托管方案已否决，原因见第 0 节。

---

## 0. 为什么走云端，不本机部署

2026-11-15 实测核对（Dify v1.19.0 官方 compose）：

| 项目 | 数据 |
|---|---|
| 默认常驻容器 | 15 个（api / api_websocket / worker / worker_beat / web / db_postgres / redis / sandbox / local_sandbox / plugin_daemon / agent_backend / agent_ssrf_proxy / ssrf_proxy / nginx / weaviate） |
| 硬依赖 | `api` 的 `depends_on` 写死 `agent_backend`，agent 三件套删不掉 |
| 官方最低内存 | 4 GB |
| 社区实测 | 4 GB 会 OOM；`plugin_daemon` 单容器曾吃 11.4 GB；`vmmem` 从 10 GB 涨到 20 GB（issue #14811） |
| 本机 WSL 上限 | 6 GB（`.wslconfig`），已跑 PG + Neo4j + 后端 |

结论：本机 6 GB 装不下默认栈，且会挤掉演示环境。云端免费版够用，且不需要手机号。

---

## 1. 注册（约 2 分钟）

1. 打开 https://cloud.dify.ai
2. 选 **Sign in with GitHub** 或 **邮箱验证码**（官方只提供 GitHub / Google / 邮箱三种，不需要手机号）
3. 创建工作区，名字建议 `bike-assembly-mes`（截图用）

免费额度（Sandbox）：

- 200 credits，**一次性，不按月补充**
- 1 个成员 / 5 个应用
- 知识库：50 个文档 / 50 MB 存储 / 每分钟 10 次检索
- 触发器：3000 次
- API：5000 次/月
- 日志保留 30 天

> credits 用完后换成自己的 DeepSeek Key 继续跑，不产生费用。

---

## 2. 配置模型供应商（DeepSeek）

`设置 → 模型供应商 → OpenAI-API-compatible → 添加模型`

| 字段 | 值 |
|---|---|
| 模型类型 | LLM |
| 模型名称 | `deepseek-flash`（与后端一致；`deepseek-chat` 是它的别名） |
| API Key | 用 `.env` 里的 `AI_OPENAI_API_KEY` |
| API Base URL | `https://api.deepseek.com/v1` |
| 上下文长度 | 64000 |
| 最大 token | 2048 |

**三个高频坑**

1. Base URL 必须带 `/v1`，**结尾不能有斜杠**，也**不能**写成 `.../chat/completions`（Dify 自己会拼）。
2. Base URL 留空会回落官方 OpenAI，直接 401。
3. 保存后点「测试」，必须看到模型回复才算通。

---

## 3. 建知识库

`知识库 → 创建知识库 → 导入已有文本`

建议只传这 9 个文件（检索质量比全量 34 个更好，答辩也讲得清）：

```
docs/01-项目架构总览.md
docs/02-后端调用链详解.md
docs/05-数据模型与状态机设计.md
docs/06-安全与基础设施.md
docs/07-项目总结文档.md
docs/生产状态机.md
docs/数据库全表清单.md
docs/阶段二大作业.md
docs/答辩代码导航.md
```

分段设置：

| 参数 | 值 | 理由 |
|---|---|---|
| 分段模式 | 通用 | |
| 分段标识符 | `\n\n` | Markdown 段落 |
| 最大分段长度 | 1200 | 与后端索引 `maxChars=1200` 保持一致 |
| 分段重叠 | 160 | 与后端 `overlapChars=160` 一致 |
| 索引方式 | 高质量（向量） | 免费额度内 |

> 一致性很重要：答辩时说「Dify 与本系统使用同样的 1200/160 分段参数」，比随口说更有说服力。

上传后等索引完成，在「召回测试」里搜 `返工` 或 `状态机`，能召回 3 条以上再继续。

---

## 4. 建工作流（Chatflow）

`工作室 → 创建应用 → Chatflow`，节点顺序：

```
开始
  ↓
知识检索（选择第 3 步的知识库，TopK=4，Score 阈值 0.5）
  ↓
LLM（DeepSeek flash）
  系统提示词见下
  ↓
直接回复（输出 LLM 文本）
```

系统提示词（直接复制）：

```
你是自行车装配 MES 系统的工艺与质量助手。
只根据「上下文」回答；上下文没有的信息，明确回答“知识库中没有相关内容”，不要编造。
回答必须使用 [来源1] [来源2] 的形式标注引用，编号对应上下文顺序。
回答结构：结论 → 依据 → 建议，控制在 300 字以内。
```

测试问题（三个，覆盖三种能力）：

1. `工单从创建到完工要经过哪些状态？`（知识库召回）
2. `返工完成后为什么必须重新质检？`（规则推理）
3. `MES 一共有多少张表，核心业务表是哪几张？`（结构化事实）

三个都能答且带 `[来源N]` 即达标。

---

## 5. 发布

`发布 → 发布为 Web App → 复制公开链接`。

链接形如 `https://udify.app/chat/xxxxxxxx`，答辩时直接投屏，无需登录。

---

## 6. 截图清单（交给大作业文档）

| # | 截图 | 用途 |
|---|---|---|
| 1 | 工作区首页（应用数、额度） | 证明云端部署 |
| 2 | 模型供应商配置页（Key 打码） | 证明「API 接入大模型」 |
| 3 | 知识库文档列表（9 个文件、已索引） | 证明知识库 |
| 4 | 召回测试命中结果 | 证明检索有效 |
| 5 | Chatflow 编排画布（4 个节点） | 证明「工作流编排」是加分点 |
| 6 | 问答结果（含 [来源1] 引用） | 证明端到端可用 |
| 7 | 公开 Web App 链接页面 | 证明可演示 |

截图放 `docs/screenshots/dify/`，文件名 `dify-01-*.png` … `dify-07-*.png`。

---

## 7. 答辩口径（一句话）

> Dify 是**模型无关的 AI 应用编排层**，负责流程、知识库和界面，不负责推理。
> 本系统把「检索 + 生成」用 Dify 工作流重新编排了一遍，模型仍然走 DeepSeek 云端，知识库沿用同一套分段参数，因此可以用它验证本系统 RAG 链路的设计是否合理。

讲的时候强调两点：

1. **不是替换**，是「同一套知识、两种编排」的对照实验；
2. 模型、Embedding、向量库都可以换，说明系统边界清楚——这正是加分项想看的架构意识。

---

## 8. 与现有系统的关系

| 层 | 本系统（Spring Boot） | Dify Cloud |
|---|---|---|
| 模型 | DeepSeek flash | DeepSeek flash（同一个 Key） |
| 知识库 | PostgreSQL `mes_ai` + pgvector，34 文档 / 385 向量 | Dify 内置向量库，9 文档 |
| 检索 | Spring AI `VectorStore` + 相似度阈值 0.70 | 知识检索节点 TopK=4 |
| 编排 | Java 代码（RAG / GraphRAG / 融合三种模式） | 可视化 Chatflow |
| 图能力 | Neo4j 1279 实体 / 570 关系 | 无（Dify 不支持图检索） |

云端 Dify 访问不到 `localhost:8080`，所以**不要**试图让 Dify 调本系统的 External Knowledge API。图检索能力仍由本系统独占，这正好是答辩时的差异点。
