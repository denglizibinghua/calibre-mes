# 自行车组装 MES 系统

## 1. 项目简介

**自行车组装 MES**（Manufacturing Execution System）是一套面向自行车装配车间的制造执行管理系统，覆盖从基础数据管理、生产工单下发、工序流转、质量检验、仓储库存、设备维修、安灯告警、安全培训到报表分析的**全链路业务场景**。

### 核心解决的问题

| 痛点 | 解决方案 |
|------|---------|
| 生产进度不透明，工单状态靠口头传递 | 工单→工序多级**状态机**，实时追踪每道工序进度 |
| 工序操作混乱，越序生产 | 前序完工**自动触发**下道工序可开工，防止越序 |
| 返工后跳过质检，质量问题流入下道 | 状态机**强制约束**：返工完成→必须重新质检→合格才能完工 |
| 网络抖动/多次点击导致重复提交 | 客户端幂等键 + 服务端**数据库原子幂等** |
| 质检流程不规范，结果难追溯 | 检验单→检验项目→逐项录入→确认联动工序，**全链路可追溯** |
| 多端接入安全隐患 | 管理端/移动端**双通道JWT独立密钥**，密码学隔离 |
| 并发操作数据不一致 | MyBatis-Plus `@Version` **乐观锁** + 关键路径 **CAS 原子操作** |
| 密码策略薄弱 | BCrypt加密 + 复杂度校验 + 历史密码防重用 + 5次失败锁定 + 定期过期 |

### 多端架构

| 端 | 技术栈 | 用户 | 接口前缀 |
|---|--------|------|---------|
| 管理端 Web | Vue 3 + TypeScript + Element Plus + Vite | 管理员、计划员、质检主管 | `/admin/**` |
| 微信小程序 | uni-app (Vue 3) + TypeScript + Pinia | 车间工人、质检员 | `/app/**` |
| 后端 API | Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + MySQL 8.x | — | — |

---

## 2. 功能完成度

### ✅ 已完成

#### 核心生产（Web + 小程序）

| 模块 | 功能 | Web管理端 | 微信小程序 |
|------|------|:--:|:--:|
| 生产工单 | CRUD、下达、暂停、恢复、取消、关闭、指派 | ✅ | ✅ |
| 工序执行 | 开始、统一报工、前序自动触发、最后一步自动完工 | ✅ | ✅ |
| 质量检验 | 自动创建检验单、逐项录入结果、确认联动工序 | ✅ | ✅ |
| 返工闭环 | 不合格→发起返工→返工完成→强制重新质检→合格 | ✅ | ✅ |
| 设备维修 | 报修→受理→维修中→完工，设备状态联动 | ✅ | ✅ |
| 安灯告警 | 上报→受理→处理→关闭→升级，告警状态联动 | ✅ | ✅ |
| 库存管理 | 库存查询/搜索/汇总、库位管理、库存单据 | ✅ | ✅ |

#### 基础数据（Web端）

| 模块 | 功能 |
|------|------|
| 物料管理 | 产品/原料主数据 CRUD |
| BOM 管理 | 物料清单 CRUD（JSON组件明细） |
| 工序定义 | 工序编码、名称、标准工时、是否需要质检 |
| 工艺路线 | 产品→工序序列（JSON steps），编码+版本联合唯一 |
| 工作单元 | 车间/产线/工位层级树 |
| 设备台账 | 设备编码、名称、型号、所属工位、运行状态 |

#### 安全与系统管理（Web端）

| 模块 | 功能 |
|------|------|
| 用户管理 | 账号 CRUD、角色分配、密码重置、强制改密 |
| 角色管理 | RBAC 角色、数据权限范围（ALL/DEPT/SELF）、菜单分配 |
| 菜单管理 | 菜单树 CRUD、权限码维护、按钮级权限（D/C/F） |
| 系统配置 | 键值对配置管理 |
| 操作日志 | 自动记录所有 `@OperLog` 注解的操作 |
| ERP 同步日志 | 同步方向、状态、请求/响应数据记录 |

#### 报表（Web端）

| 报表 | 说明 |
|------|------|
| 工单报表 | 支持 Excel 导出 |
| 质检报表 | 合格率统计 |
| 库存报表 | 库存快照 |
| 安灯报表 | 告警统计 |
| 设备维修报表 | 维修工单统计 |

#### 基础设施

| 能力 | 说明 |
|------|------|
| 双通道JWT认证 | 管理端和移动端独立签名密钥 |
| 双Token刷新 | Access Token(30min) + Refresh Token(7天) + Token Rotation |
| RBAC权限控制 | 后端 `@RequirePermission` AOP + 前端动态路由 + `v-permission` 指令 |
| 状态机 | 工单7态 / 工序10态 / 检验3态，集中式 `StatusTransitionValidator` |
| 数据库幂等 | `pro_idempotency_record` 唯一索引原子性防重 |
| 乐观锁 | MyBatis-Plus `@Version` 通用乐观锁 |
| CAS原子更新 | 关键路径 `WHERE status=? AND version=?` 防 TOCTOU |
| 操作审计 | `@OperLog` AOP 自动记录 + 状态转换日志 |
| 全局异常处理 | 12类异常分类处理，堆栈不暴露前端 |
| Caffeine缓存 | 权限信息30分钟本地缓存，事件驱动主动驱逐 |
| Redis 缓存 | 权限/配置二级缓存 + 登录失败锁定；Redis 不可用时自动降级回源 |
| WebSocket 实时推送 | 安灯/工单/质检/维修/库存变更提交后通知；Web 六列表与小程序安灯/维修接入，心跳超时、退避重连、恢复补查；[实现与验证](docs/websocket-delivery.md) |
| 微信登录 | `wx.login` 一键登录 + 账号绑定 |

### ⚠️ 部分完成

| 模块 | 状态 | 说明 |
|------|:--:|------|
| RabbitMQ 消息 | 🟡 | 5个队列+Exchange已配置，业务状态变更经 MQ 发布，消费者已接入真实业务消费（WebSocket 实时通知 + `event_id` 幂等留痕，重复投递只执行一次）；开发环境默认关闭（`app.rabbitmq.enabled=false`），ERP 同步消费待阶段扩展 |
| ERP 对接 | 🟡 | 已实现可配置 ERP 同步客户端（配置 `ERP_BASE_URL` 走真实 HTTP，留空走 MOCK）、工单下达/完工与库存单据执行自动发布同步、`erp_sync_log` 幂等闭环与失败重试；接入真实 ERP 系统地址与字段映射待实际对接 |
| AI 质检分析与 MES 助手 | 🟡 | Spring AI + OpenAI 兼容协议，支持质检分析、结构化 MES 问答和 WebSocket 流式输出；配置 `AI_OPENAI_API_KEY` 启用 |
| 生产看板 | 🟡 | 首页看板聚合（今日完工/今日产量/在制工单/待检质检/安灯/设备状态，真实 SQL 聚合）；SPC/OEE/工时效率分析列为后续迭代 |
| CI/CD | ✅ | Docker Compose 一键部署（MySQL/RabbitMQ/Redis/后端/Web）+ GitHub Actions 构建/测试流水线；Gitee 仓库需 Gitee Go 或镜像仓库启用 |

> 当前无 ❌ 待实现项：原待实现的 ERP 对接、AI 质检分析、CI/CD 均已落地（见上）。

---

## 3. 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编译目标版本 |
| Spring Boot | 3.3.5 | 应用框架 |
| MyBatis-Plus | 3.5.7 | ORM + 分页 + 乐观锁 |
| MySQL | 8.0+ | 关系型数据库（utf8mb4） |
| Maven | 3.8+ | 多模块项目构建 |
| JWT (jjwt) | 0.11.5 | 管理端+App端双通道认证 |
| Spring AMQP (RabbitMQ) | — | 事件驱动消息队列 |
| Caffeine | — | 本地缓存（权限信息） |
| Spring Data Redis | — | 缓存/登录锁定（Lettuce） |
| WebSocket | — | 实时推送端点 `/ws`（JWT 握手认证） |
| Spring Security Crypto | — | BCrypt 密码加密 |
| EasyExcel | 3.3.4 | 报表导出 |
| Jakarta Validation | — | Bean 参数校验 |
| Lombok | 1.18.32 | 代码简化 |
| DeepSeek API（直连） | — | 质检分析（OpenAI 兼容协议，无额外依赖） |
| Testcontainers | 1.20.4 | 集成测试（MySQL 8.0 容器） |
| JUnit 5 | — | 测试框架 |

### 管理端前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | ^3.4.0 | 组合式 API（`<script setup>`） |
| TypeScript | ^5.4.0 | 类型安全 |
| Vite | ^5.4.0 | 构建工具 |
| Element Plus | ^2.7.0 | UI 组件库 |
| Pinia | ^2.1.0 | 状态管理 |
| Vue Router | ^4.3.0 | 前端路由（动态路由 + 权限过滤） |
| Axios | ^1.7.0 | HTTP 客户端（Token 刷新队列） |
| Sass | ^1.77.0 | CSS 预处理 |
| Vitest | ^4.1.10 | 测试框架 |

### 微信小程序

| 技术 | 版本 | 说明 |
|------|------|------|
| uni-app | 3.0.0+ | 跨端开发框架 |
| Vue | ^3.4.21 | 组合式 API |
| TypeScript | ^4.9.4 | 类型安全 |
| Vite | 5.2.8 | 构建工具 |
| Pinia | 2.1.7 | 状态管理 |
| Vitest | ^4.1.10 | 测试框架 |

---

## 4. 项目架构

### 4.1 模块结构

```
bike-assembly-mes/
├── bike-assembly-common/          # 公共模块：注解(@RequirePermission/@OperLog/@IdempotencyRequired)
│                                  #           常量、异常(6类)、统一响应(Result/PageResult)
│                                  #           JWT工具、ThreadLocal上下文(BaseContext)
│
├── bike-assembly-pojo/            # 数据对象：Entity(31个)、DTO(39+)、VO(40+)
│                                  #           枚举、状态机(WorkOrderStatus/WorkProcessStatus/InspectionStatus)
│
├── bike-assembly-server/          # 核心服务：Controller(29 admin + 11 app)、Service(40+)
│   ├── src/main/java/             #           Mapper(30+)、Config、Interceptor(3个)
│   │   └── com/bikeassembly/      #           Aspect(Permission/OperLog)、Listener(5个MQ)
│   └── src/main/resources/
│       ├── application.yml        # 主配置
│       ├── application-dev.yml    # 开发环境配置
│       └── mapper/                # MyBatis Mapper XML
│
├── bike-assembly-mes-web/         # Web管理端（Vue 3 + Element Plus）
│   └── src/
│       ├── api/                   # 28个API模块
│       ├── views/                 # 35个业务页面
│       ├── stores/                # Pinia 状态管理（user/permission/tabBar）
│       ├── router/                # 动态路由 + 权限守卫
│       ├── layout/                # 侧边栏布局 + 递归菜单组件
│       ├── components/            # PageContainer 通用页面容器
│       ├── directives/            # v-permission 权限指令
│       └── types/                 # TypeScript 类型定义
│
├── bike-assembly-mes-miniapp/     # 微信小程序（uni-app）
│   └── src/
│       ├── api/                   # 8个API模块
│       ├── pages/                 # 12个页面
│       ├── stores/                # Pinia 状态管理
│       ├── components/            # 自定义浮动 TabBar
│       ├── utils/                 # 请求封装、缓存管理、幂等键、网络检测
│       └── types/                 # TypeScript 类型定义
│
├── sql/                           # 数据库脚本
│   ├── init_database.sql          # ★ 全量初始化DDL + 系统种子数据
│   ├── dev_seed_full.sql          # ★ 全表演示数据
│   ├── verify_database.sql        # ★ 数据库验证脚本
│   └── README.md                  # 脚本说明与初始化步骤
│
├── docs/                          # 项目文档 ★
│   ├── 01-项目架构总览.md          # 技术栈、模块结构、分层架构、功能全景
│   ├── 02-后端调用链详解.md        # 认证、工单、工序、质检、幂等等完整调用链
│   ├── 03-前端Web管理端架构.md     # Vue 3 架构、路由、布局、HTTP层、权限指令
│   ├── 04-微信小程序架构.md        # uni-app、微信登录、页面功能、离线缓存
│   ├── 05-数据模型与状态机设计.md   # Entity ER图、状态机流转图、DTO/VO规范
│   ├── 06-安全与基础设施.md        # JWT、RBAC、密码策略、幂等、并发安全
│   └── 07-项目总结文档.md          # 综合总结：问题、技术、架构、决策、统计
│
├── scripts/                       # 运维脚本
├── .claude/                       # Claude Code 配置（CLAUDE.md 开发规范）
└── pom.xml                        # Maven 父 POM（多模块）
```

### 4.2 分层架构（苍穹外卖风格）

```
HTTP Request
  → IdempotencyInterceptor   (提取 Idempotency-Key)
  → JwtTokenInterceptor      (JWT校验 + 用户状态 + 权限加载)
  → PermissionAspect         (@RequirePermission AOP)
  → Controller               (参数校验 + 调用Service + 返回Result)
  → Service / ServiceImpl    (状态机校验 + 幂等处理 + 业务逻辑 + @Transactional)
  → Mapper                   (MyBatis-Plus BaseMapper + 自定义SQL)
  → MySQL / RabbitMQ
  → OperLogAspect            (操作日志自动记录)
  → Result<T>                (统一JSON响应)
```

### 4.3 核心数据流

```
计划员(Web)        工人(小程序)        质检员(小程序)        系统
    │                  │                    │                │
    ├─创建工单          │                    │                │
    ├─下达工单─────────▶│                    │                │
    │                  ├─开始工序            │                │
    │                  ├─报工(送检)─────────▶│                │
    │                  │                    ├─录入检验结果     │
    │                  │                    ├─确认检验         │
    │                  │                    │               ├─合格→完工→触发下道
    │                  │◀──返工通知──────────┤               ├─不合格→待返工
    │                  ├─返工开始            │                │
    │                  ├─返工完成───────────▶│                │
    │                  │                    ├─重新检验         │
    │                  │                    ├─确认合格         │
    │                  │                    │               ├─完工→触发下道
    │◀──工单完工通知───────────────────────┼───────────────┤
    ├─关闭工单          │                    │                │
```

---

## 5. 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | 编译和运行必需 |
| Maven | 3.8+ | 或使用 `./mvnw`（Maven Wrapper） |
| Node.js | ≥18 且 <23 | 管理端 + 小程序构建 |
| npm | 9+ | 随 Node.js 安装 |
| MySQL | 8.0+ | 数据库（utf8mb4） |
| Redis | 7+ | 缓存与登录失败锁定（可选，不可用时自动降级） |
| RabbitMQ | 3.x | 消息队列（可选，开发环境默认关闭） |
| 微信开发者工具 | 最新版 | 小程序预览调试 |

---

## 6. 数据库初始化

### ⚠️ 警告

`init_database.sql` 会**删除现有表和数据**，仅适用于开发、测试和答辩演示环境。**禁止直接用于有真实数据的生产数据库**。

### 保留账号替换演示数据

先备份并暂停后端写入，仅执行 `sql/dev_seed_full.sql` 和 `sql/verify_database.sql`。这会替换基础资料及业务数据，保留账号、密码、权限、菜单、配置和登录会话。详见 [数据库重置与验证指南](docs/数据库重置与验证指南.md)。

### 全新环境初始化

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS bike_assembly_mes DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"

# 2. 初始化表结构 + 系统种子数据（角色/菜单/用户/配置）
mysql -u root -p bike_assembly_mes < sql/init_database.sql

# 3. 插入全表演示数据
mysql -u root -p bike_assembly_mes < sql/dev_seed_full.sql

# 4. 验证数据库
mysql -u root -p bike_assembly_mes < sql/verify_database.sql
```

### 数据库脚本说明

| 脚本 | 用途 |
|------|------|
| `sql/init_database.sql` | **全量初始化DDL**：删除全部表→创建32张表→外键→索引→系统种子数据 |
| `sql/dev_seed_full.sql` | **业务演示数据替换**：48张工单及关联记录，保留现有账号权限和配置 |
| `sql/verify_database.sql` | **数据库验证**：41项结构、状态、关联和库存守恒检查，不一致时报错 |
| `sql/README.md` | **脚本说明**：初始化步骤、演示账号、维护约定 |

---

## 7. 后端运行

### 环境变量

在 `bike-assembly-server/src/main/resources/application-dev.yml` 中配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bike_assembly_mes?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:your_password}
```

也可通过环境变量覆盖：
```bash
export MYSQL_USERNAME=your_user
export MYSQL_PASSWORD=your_password
```

### 密钥配置

以下密钥需通过环境变量配置（**不得在配置文件中硬编码真实值**）：

| 环境变量 | 说明 |
|---------|------|
| `JWT_ADMIN_SECRET` | 管理端 JWT 签名密钥（≥32字符） |
| `JWT_APP_SECRET` | 小程序端 JWT 签名密钥（≥32字符） |
| `WECHAT_APP_ID` | 微信小程序 AppID |
| `WECHAT_APP_SECRET` | 微信小程序 Secret |
| `AI_OPENAI_BASE_URL` | OpenAI 兼容聊天接口；Compose 本地模型默认为 `http://chat-model:8000/v1` |
| `AI_OPENAI_API_KEY` | OpenAI 兼容接口 Key；本地 vLLM 使用仅供本机服务校验的 Key |
| `AI_CHAT_MODEL` | Chat 模型名；本地默认 `Eslzzyl/Qwen3-4B-Instruct-2507-AWQ`（4-bit AWQ） |
| `AI_TEMPERATURE` | AI 温度，默认 `0.3` |
| `AI_MAX_TOKENS` | 单次 AI 最大 token 数，默认 `2048` |
| `AI_TIMEOUT_SECONDS` | AI 流式请求超时秒数，默认 `120` |
| `AI_REASONING_EFFORT` | 兼容模型的推理强度提示，默认 `medium` |
| `AI_CONTEXT_MAX_CHARS` | 单次问答最大上下文字符数，默认 `12000` |
| `AI_RAG_MIN_SIMILARITY` | RAG 最低相似度阈值，默认 `0.70` |
| `AI_EMBEDDING_MODEL` | Embedding 模型名；启用 PGVector 时必须由部署环境提供 |
| `AI_PGVECTOR_ENABLED` | PGVector 开关，默认关闭；启用后使用独立 PostgreSQL 数据源，不影响 MySQL MES |
| `AI_PGVECTOR_URL` | PGVector PostgreSQL JDBC 地址 |
| `AI_PGVECTOR_USERNAME` / `AI_PGVECTOR_PASSWORD` | PGVector 数据库账号 |
| `AI_PGVECTOR_INITIALIZE_SCHEMA` | 首次连接空库时自动创建向量表；Compose 示例默认 `true`，本地配置可设为 `false` |
| `AI_PGVECTOR_DIMENSIONS` | Embedding 向量维度，必须与实际 Embedding 模型输出一致 |
| `AI_KNOWLEDGE_PATH` | RAG 文档目录，默认 `docs` |
| `AI_PUBLIC_KNOWLEDGE_UPLOAD_PATH` | 公共知识文件的可写存储目录 |
| `AI_PRIVATE_KNOWLEDGE_PATH` | 用户私有知识文件的可写存储目录 |
| `FACE_TEMPLATE_ENCRYPTION_KEY` | AES-GCM 人脸模板加密密钥，至少 32 字节；未设置或过短时禁用人脸登录 |

配置方式取决于启动方式：

- 本地 IDE/`mvn spring-boot:run` 使用 `dev` profile 时，可在 `application-dev.yml` 中引用环境变量；该文件里的 `${AI_OPENAI_API_KEY:...}` 和 `${AI_CHAT_MODEL:}` 是占位符，不会自动提供真实值。
- 使用本仓库的 Docker Compose（包括 WSL Docker）时，Compose 默认启用 `prod` profile，且不会读取 `application-dev.yml`。请在项目根目录 `.env` 设置模型及 Key；不要把 `.env` 提交到 Git。修改 `.env` 后需重建后端容器使变量生效。

PGVector RAG 使用独立 PostgreSQL，不会替代 MES 的 MySQL。仓库默认使用本地
`BAAI/bge-m3` Embedding，输出维度为 `1024`。先在项目根目录 `.env` 确认
`AI_PGVECTOR_ENABLED=true`、`AI_EMBEDDING_MODEL=BAAI/bge-m3`、
`AI_PGVECTOR_DIMENSIONS=1024`，然后启动向量库和 Embedding 服务。默认使用固定的
vLLM `v0.32.0` CUDA 13.0 镜像；请确认宿主机 NVIDIA 驱动与该 CUDA 版本兼容。
如需其他已验证的运行镜像，可通过 `LOCAL_EMBEDDING_IMAGE` 覆盖：

```bash
# 默认把 BGE-M3 放在 CPU 上，为 8GB GPU 留出 Qwen 的显存。将
# Eslzzyl/Qwen3-4B-Instruct-2507-AWQ 权重放到 models/qwen3-4b-awq，
# BAAI/bge-m3 权重放到 models/bge-m3；也可在 .env 中覆盖本地目录。
# 首次载入 CPU Embedding 模型会较慢。
docker compose --profile pgvector --profile local-embedding-cpu --profile local-chat up -d pgvector embedding-cpu chat-model
docker compose --profile pgvector --profile local-embedding-cpu --profile local-chat ps

# 再启动后端和 Web
docker compose up -d --build backend web
```

CPU Embedding 容器使用 FlagEmbedding 加载本地 BGE-M3，并向 Spring AI 提供 OpenAI 兼容的
`/v1/embeddings` 接口，进程从挂载的 BGE-M3
本地权重目录启动，不会联网拉取模型。需要 GPU 加速时，可关闭 `local-embedding-cpu` profile，
改用 `local-embedding` profile，并在 `.env` 将 `AI_EMBEDDING_BASE_URL` 设为
`http://embedding:8000/v1`；CPU 与 GPU embedding profile 不要同时启用。
从 IDE 直接运行后端时，按所选服务使用 `localhost:8003`（CPU）或 `localhost:8001`（GPU）。
如果使用第三方 Embedding API，可不启动本地 profile，并在 `.env` 配置该服务可达的
`AI_EMBEDDING_BASE_URL`、`AI_EMBEDDING_MODEL`、`AI_EMBEDDING_API_KEY` 和真实输出维度。

全新 MySQL 数据卷会按 Compose 顺序自动执行 AI 数据表、权限和阶段二补丁。已有数据库不会重跑初始化脚本，
需在项目根目录手动执行一次：

```bash
mysql -u root -p bike_assembly_mes < sql/patch_ai_reliability_v1.sql
mysql -u root -p bike_assembly_mes < sql/patch_ai_permissions_v1.sql
mysql -u root -p bike_assembly_mes < sql/patch_ai_phase2.sql
```

公共上传和个人上传文件分别写入 `public-ai-files` 与 `private-ai-files` Docker 卷，
不会写入只读挂载的 `docs/`。个人文件以当前登录用户 ID 隔离；管理员只有公共库管理权限，
不会因此获得读取他人私有文件的能力。替换或删除上传文件时会清理对应向量和图谱索引。
支持 MD、TXT、PDF、DOCX、XLSX、PPTX；扫描版 PDF 不做 OCR。

启用 GraphRAG 时，在 `.env` 配置 Neo4j 密码并启用 `AI_NEO4J_ENABLED=true`：

```bash
docker compose --profile graphrag up -d neo4j
docker compose up -d --build backend web
```

实体关系由同一聊天模型抽取，服务端校验类型后使用固定参数化 Cypher 写入；模型不能生成或执行
Cypher。图谱范围按公共资料或当前用户私有资料过滤。

本地人脸服务是独立 CPU 容器，需要预先准备 YuNet、SFace 和 MiniFASNet ONNX 权重到
`models/face/`，然后启用 `local-face` profile。缺少任一模型、摄像头画面中不是单张人脸或活体未通过，
服务均会拒绝注册/登录。模板在 MySQL 中 AES-GCM 加密保存，照片只在请求内存中处理。
MiniFASNet 预处理和类别顺序依具体 checkpoint 而异，默认配置必须用真实摄像头与回放样本校准；
这只是演示级基线，不是生产级反欺骗保障。

```bash
docker compose --profile local-face up -d --build face
```

向量表使用独立名称 `vector_store_bge_m3_1024`，避免和先前使用错误维度创建的表冲突；不会删除旧表或
PGVector 数据卷。修改 Embedding 模型或维度时，应使用新的 `AI_PGVECTOR_TABLE` 名称并重新索引文档。

启动后登录管理端，在“AI 助手”页面点击“索引知识库”，或调用：

```text
POST /admin/ai/knowledge/index?maxChars=1200&overlapChars=160
GET  /admin/ai/knowledge/status
GET  /admin/ai/knowledge/tasks/{taskId}
```

索引接口异步返回 `taskId`，任务状态持久化在 MySQL；Redis 可用时使用分布式锁，避免多
实例重复索引。索引器读取 `AI_KNOWLEDGE_PATH` 下的 `.md`/`.txt`/`.pdf`/`.docx`/`.xlsx`/`.pptx` 文件，按文件 SHA-256
增量更新，忽略 `target`、`node_modules`、`dist`、`build` 和 `.git` 目录；文档变化时会
先删除同一 source 的旧 chunk，再写入新向量，删除文件时也会清理旧向量。服务重启后会从
`ai_knowledge_document` 清单恢复状态，不依赖内存 Map。

`GET /admin/ai/knowledge/status` 返回文档统计、最近任务、失败原因和 `vectorStoreStatus`。
没有 Embedding 模型、维度或 PGVector 配置时状态为 `NOT_CONFIGURED`，只做文档清单扫描，
不会在索引阶段隐式写入失败。问答返回的 `sources` 会包含文件、chunk 和更新时间，模型
回答被要求使用 `[来源1]` 形式引用，服务端会清理未提供的引用。PGVector 使用独立数据源，
避免把 MySQL 主数据源用于向量表。Docker 镜像会默认携带仓库根目录 `docs/`；额外工艺
文档可通过 `AI_KNOWLEDGE_PATH` 指向挂载目录。

索引只依赖 Embedding 服务；要让问答基于检索到的文档生成回答，还需配置可用的
`AI_OPENAI_API_KEY` 和 `AI_CHAT_MODEL`。

“已索引文档/chunk”统计的是 MySQL 中的知识库文件清单和文本分块，不代表已写入向量。
当 `AI_PGVECTOR_ENABLED=false` 时这些文档不会参与语义向量检索，因此界面同时显示
“未配置向量库”是预期状态。启用前必须先验证 Embedding API 可用，并确认返回向量维度；
Chat 模型不能代替 Embedding 模型。仓库的 BGE-M3 配置为 `1024` 维。

AI 查询和排产草稿统一使用当前用户的数据范围：

- `ALL`：可查询全部工单、质量、设备、安灯和库存数据；
- `DEPT`：查询本人生产单元及其下属车间、产线、工位；
- `SELF`：只查询本人负责、上报或处理的数据；库存没有人员归属，因此不会返回库存全量；
- 未登录、数据范围缺失或部门未绑定生产单元时返回 `DENIED`，不会退化成全量查询。

排产接口只生成可审计草稿，不自动修改业务数据：

```text
POST /admin/ai/query
POST /admin/ai/stream
GET  /admin/ai/stream/{requestId}
DELETE /admin/ai/stream/{requestId}
POST /admin/ai/plans
POST /admin/ai/plans/{planId}/confirm
```

流式状态按 `requestId + seq` 持久化，支持重复 delta 去重、断线恢复、超时和取消。排产
草稿会保存影响工单、建议顺序、设备/工位约束、物料约束、风险依据、数据时间范围、来源、
模型和输入摘要；确认接口要求 `Idempotency-Key`，只把草稿状态改为已确认，不执行工单、
库存或设备变更。

### 已有数据库升级

如果数据库已经存在，Docker 的 `init_database.sql` 不会再次执行。请在停写并备份后按顺序执行：

```bash
mysql -u root -p bike_assembly_mes < sql/patch_ai_reliability_v1.sql
mysql -u root -p bike_assembly_mes < sql/patch_ai_permissions_v1.sql
```

这两个 patch 会增加 `sys_user.work_unit_id`、AI 审计表、知识库清单/任务表、排产草稿字段
和新的 `admin:ai:knowledge-manage`、`admin:ai:plan-confirm` 权限。给 DEPT 用户配置
`sys_user.work_unit_id` 后，数据范围才会包含部门及下属生产单元。

开发环境 JWT 已内置 dev 占位密钥（见 `application-dev.yml`），无需额外配置；
微信真实凭据与生产 JWT 密钥必须通过环境变量或 `application-local.yml`（已被 gitignore）提供。
生产环境启动时 `JwtSecretValidator` 会校验密钥安全性。
完整变量清单见仓库根目录 `.env.example`。

### 启动

```bash
# 依赖服务（可选：Redis 用于缓存与登录锁定，不可用时自动降级）
docker run -d --name mes-redis -p 6379:6379 redis:7-alpine

# 编译
./mvnw clean compile

# 运行测试
./mvnw test

# 启动（默认 http://localhost:8080）
./mvnw spring-boot:run
```

验证：
```bash
curl http://localhost:8080/admin/health
```

### RabbitMQ（可选）

```yaml
# application-dev.yml 中默认关闭
app:
  rabbitmq:
    enabled: false

# 需要时改为 true，并配置连接信息
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### Docker Compose 一键部署

仓库根目录提供 `docker-compose.yml`，一条命令拉起 MySQL 8、RabbitMQ、Redis、
PGVector、本地 CPU BGE-M3 Embedding、本地 Qwen 聊天模型、后端和 Web 管理端。
本地 Qwen 聊天模型需要兼容的 NVIDIA GPU 与驱动；BGE-M3 默认走 CPU：

```bash
# 可选：复制 .env.example 为 .env 并按需修改（默认值可直接演示）
cp .env.example .env

# 构建并启动本地 RAG/聊天全栈（首次启动会自动执行初始化 SQL）
docker compose --profile pgvector --profile local-embedding-cpu --profile local-chat up -d --build

# 查看状态
docker compose ps
```

启动后访问：

| 入口 | 地址 |
|------|------|
| Web 管理端 | http://localhost:8081 |
| 后端 API | http://localhost:8080 |
| 健康检查 | http://localhost:8080/admin/health |

说明：

- 默认使用 `prod` profile，JWT 密钥通过 `JWT_ADMIN_SECRET` / `JWT_APP_SECRET` 注入（`.env` 中默认值仅供演示，上线前必须替换）；
- nginx 将 `/admin`、`/app`、`/ws` 反代到后端，Web 端走同源请求，无跨域问题；
- MySQL 数据持久化在 `mysql-data` 卷，删除卷后重新 `up` 会再次执行初始化脚本；
- Neo4j 与人脸服务按需启用 `graphrag`、`local-face` profile；人脸服务需要先准备 ONNX 权重；
- CI 流水线见 `.github/workflows/ci.yml`（后端 `mvn test` + Web 构建/测试 + 小程序 type-check/构建 + Docker 镜像构建）。

---

## 8. Web 管理端运行

```bash
cd bike-assembly-mes-web

# 安装依赖
npm install

# 开发模式启动（http://localhost:5173）
npm run dev

# 生产构建（含类型检查）
npm run build

# 运行测试
npm run test
```

---

## 9. 微信小程序运行

```bash
cd bike-assembly-mes-miniapp

# 安装依赖
npm install

# 开发模式启动
npm run dev:mp-weixin

# 生产构建
npm run build:mp-weixin

# 类型检查
npm run type-check

# 运行测试
npm run test
```

编译输出在 `dist/dev/mp-weixin/`（开发）或 `dist/build/mp-weixin/`（生产），用微信开发者工具导入该目录预览。

开发时可在微信开发者工具中选择 **"不校验合法域名"**。

---

## 10. 演示账号

**所有演示账号统一开发密码：`Demo@123456`**

| 账号 | 角色 | 主要权限 | 适用端 | 小程序openid |
|------|------|---------|:--:|------|
| `admin` | 系统管理员 | 全部权限（通配符 `*:*:*`） | Web | — |
| `planner` | 生产计划员 | 工单管理、查看全部数据 | Web + 小程序 | — |
| `operator01` | 生产操作员 | 工序开始/报工/返工、设备报修 | 小程序 | `demo_openid_operator01` |
| `inspector01` | 质量检验员 | 检验结果录入/确认 | 小程序 | `demo_openid_inspector01` |
| `maintainer01` | 设备维修员 | 维修受理、完成维修 | 小程序 | `demo_openid_maintainer01` |
| `viewer01` | 普通查看用户 | 所有模块只读 | 小程序 | `demo_openid_viewer01` |

> ⚠️ **安全警告**：这些账号仅用于开发演示。生产部署时必须删除或修改所有演示账号和密码！

---

## 11. 推荐演示流程

### 第一步：管理员初始化（Web端）
1. 登录 `admin`
2. 检查基础资料：物料、设备、工序、BOM、工艺路线
3. 进入工单管理 → 查看已有演示工单
4. 对"已创建"状态工单执行"下达" → 观察工序记录自动生成

### 第二步：操作员执行工序（小程序）
1. 登录 `operator01`
2. 查看我的工单 → 进入工单详情
3. 开始第一道工序 → 完成 → 报工
4. 依次完成各工序（前序完工自动触发下道可开工）
5. 需要质检的工序报工后自动触发质检任务

### 第三步：质检员检验（小程序）
1. 登录 `inspector01`
2. 查看待检验列表 → 逐项录入检验结果
3. 合格场景：确认 → 工序自动完工 → 触发下道工序
4. 返工场景（WO-DEMO-202607-003）：不合格→发起返工→返工完成→重新检验→合格→解锁

### 第四步：维修员处理设备故障（小程序）
1. 登录 `maintainer01`
2. 查看待维修单 → 受理 → 状态变为"维修中"
3. 完成维修 → 填写维修结果
4. 观察设备 `running_status` 恢复为"可运行"

### 第五步：最终验证（Web端）
1. 最后一道工序完成 → 工单自动完工
2. 质检记录完整可追溯（含返工复检链）
3. 设备运行状态与维修单联动
4. 操作日志完整记录所有操作

---

## 12. 测试

### 后端测试

| 测试类型 | 测试类 | 说明 |
|---------|--------|------|
| 认证安全 | `AuthenticationIntegrationTest`、`AuthenticationSecurityTest` | JWT 校验、Token 刷新、密码安全 |
| 权限控制 | `ScopeSecurityTest` | RBAC 权限校验 |
| 生产流程 | `ProductionFlowIntegrationTest`、`QcPassMainFlowIntegrationTest` | 工单→工序→质检完整流程 |
| 状态机 | `WorkflowStateMachineIntegrationTest`、`WorkflowStateMachineVerificationTest`、`StatusTransitionValidatorTest` | 状态转换合法性 |
| 幂等性 | `IdempotencyIntegrationTest` | 重复提交防护 |
| 并发 | `Stage5ConcurrencyTest` | 乐观锁 + CAS |

```bash
./mvnw test    # 运行全部测试
```

> 说明：
> - 集成测试使用 Testcontainers 启动 MySQL 8 容器，需本机可用 Docker；
> - Docker 29+（最低 API 1.40）需在 `~/.testcontainers.properties` 配置 `api.version=1.44` 兼容旧客户端；
> - `ProductionFlowIntegrationTest` / `QcPassMainFlowIntegrationTest` 依赖测试种子数据：
>   `mysql -u root -p123456 bike_assembly_mes < sql/test/seed_production_flow.sql`

### 前端测试

```bash
cd bike-assembly-mes-web && npm run test    # Web端
cd bike-assembly-mes-miniapp && npm run test # 小程序
```

---

## 13. 项目统计

| 维度 | 数据 |
|------|------|
| 后端 Controller | 29 管理端 + 11 移动端 = 40 |
| 后端 Service | 40+ |
| 后端 Mapper | 30+ |
| 数据库表 | 30 |
| Entity 实体 | 31 |
| DTO/VO | 80+ |
| 权限码 | 38 |
| Web 管理端页面 | 35 |
| 微信小程序页面 | 12 |
| RabbitMQ 队列 | 5 |
| 总代码文件 | 420+ |
| 总代码行数 | ~72,000+ |
| 后端测试类 | 15 |

---

## 14. 开发规范

本项目严格遵循 **苍穹外卖** 风格分层架构（详见 `.claude/CLAUDE.md`）：

1. **分层架构**：Controller → Service(接口) → ServiceImpl → Mapper → MySQL
2. **Spring IoC**：所有组件由 Spring 容器管理，禁止手动 new
3. **Controller 职责**：仅做参数接收、参数校验、调用 Service、返回 `Result<T>`
4. **Service 职责**：业务逻辑、事务管理（`@Transactional`）、状态机校验
5. **Mapper 职责**：MyBatis-Plus BaseMapper + 自定义 SQL（注解或 XML）
6. **DTO/VO/Entity 分离**：入参 DTO + Jakarta Validation，出参 VO，持久化 Entity
7. **权限控制**：`@RequirePermission` + AOP 方法级权限校验（后端为安全边界）
8. **操作审计**：`@OperLog` + AOP 自动记录操作日志
9. **幂等保护**：`@IdempotencyRequired` + 数据库唯一索引防重复提交
10. **异常处理**：12类异常分类 → `GlobalExceptionHandler` 统一转换，堆栈不暴露前端

> 📖 详细的架构设计、调用链、数据模型等请参阅 [`docs/`](./docs/) 目录下的项目文档。

---

## 15. 已知限制

| 限制 | 说明 |
|------|------|
| RabbitMQ | 5队列+Exchange已配置，Producer/Listener已实现，消费者已接入消费留痕落库；真实通知/ERP 消费待扩展 |
| Redis | 缓存与登录锁定依赖本地 Redis（默认 localhost:6379），不可用时自动降级并告警 |
| WebSocket | `/ws` 使用 access JWT 握手；单实例实时提示，断线不补发，恢复后查 API；生产 WSS/真机需环境验证，详见[交付说明](docs/websocket-delivery.md) |
| ERP 真实接口 | `erp_sync_log` 表已创建，同步接口已定义，待对接 |
| DeepSeek API（直连） | AI 质检分析已实现（质检不合格原因/建议/风险等级） |
| 演示数据 | 所有演示数据均为虚构，不包含任何真实信息 |

---

## 16. 相关文档

| 文档 | 说明 |
|------|------|
| [01-项目架构总览](./docs/01-项目架构总览.md) | 技术栈、模块结构、分层架构、功能全景图 |
| [02-后端调用链详解](./docs/02-后端调用链详解.md) | 认证、工单、工序、质检、幂等等完整调用链 |
| [03-前端Web管理端架构](./docs/03-前端Web管理端架构.md) | Vue 3 架构、路由、布局、HTTP层、权限指令 |
| [04-微信小程序架构](./docs/04-微信小程序架构.md) | uni-app、微信登录、页面功能、离线缓存 |
| [05-数据模型与状态机设计](./docs/05-数据模型与状态机设计.md) | Entity ER图、状态机流转图、DTO/VO规范 |
| [06-安全与基础设施](./docs/06-安全与基础设施.md) | JWT、RBAC、密码策略、幂等、并发安全 |
| [07-项目总结文档](./docs/07-项目总结文档.md) | 综合总结：问题、技术、架构、决策、统计 |
| [数据库脚本说明](./sql/README.md) | 初始化步骤、演示账号与脚本维护约定 |

---

**V4.0 · 2026-07-25**

🤖 Generated with [Claude Code](https://claude.com/claude-code)
