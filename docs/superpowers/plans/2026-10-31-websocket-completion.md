# WebSocket 实时推送补齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.
>
> 状态：用户已确认，四项任务已完成；实际结果及生产/真机验证边界见 ../../websocket-delivery.md。

**Goal:** 按既有设计补齐 Web 与小程序的可靠连接、心跳、重连、退出清理和后端握手及会话管理，并验证真实业务推送链路。

**Architecture:** 延用 `/ws` 原生 WebSocket、JWT 握手、Spring 管理的处理器与通知组件。业务数据继续来自现有 Service → Mapper → MySQL；WebSocket 只通知页面通过现有 API 重新查询。保留当前 MQ 优先、未开启 MQ 时直接通知的业务路径。

**Tech Stack:** Spring Boot、Spring WebSocket、JJWT、JUnit 5、Mockito、Vue 3、TypeScript、uni-app、Vitest。

## Global Constraints

- 执行前必须获得用户对计划的确认，执行时应用 Superpowers executing-plans，收尾执行 review。
- 不覆盖工作区已有的 pom.xml、测试、学习材料及其他未提交改动。
- Controller 仅处理 HTTP 参数与响应，业务及事务保留在 Service，数据库操作保留在 Mapper。
- 会话连接集合属于传输运行时状态，不作为业务数据库使用。
- 不新建表、字段或虚构 Controller / Mapper；本次数据库迁移为无。
- 不增加 STOMP、SockJS、Redis 广播、消息持久化或通知中心；当前按单实例实时提示验收。
- 不记录 token、密码或密钥；保持现有跨域来源白名单。
- 沿用既有用户认证架构，不借本次功能迁移整个项目的安全框架。

## 1. 调查结论与设计依据

原始设计位于 `docs/06-安全与基础设施.md` 第 10.2 节：

- 端点 `/ws?token=...`，检查 access 类型及 clientType。
- 按 clientType + userId 管理连接。
- 管理端业务通知，以及上报人/处理人的移动端定向通知。
- 前端 30 秒心跳、断线指数退避重连、生产 WSS 及反向代理。

`docs/最终功能完成度.md` 与 README 描述功能已完成，但 `docs/superpowers/plans` 中没有独立 WebSocket 计划。实际已有以下实现：

| 层面 | 现有文件 | 已观察到的情况 |
| --- | --- | --- |
| 端点 | `bike-assembly-server/src/main/java/com/bikeassembly/config/WebSocketConfig.java` | `/ws` 注册、Spring IoC、来源白名单已存在 |
| 握手 | `bike-assembly-server/src/main/java/com/bikeassembly/ws/WsHandshakeInterceptor.java` | 已验 JWT，但未明确检查 userId 非空及拒绝响应状态，异常日志直接使用异常 message |
| 会话 | `bike-assembly-server/src/main/java/com/bikeassembly/ws/WsSessionManager.java` | 同用户连接增删不是同一个原子操作，存在并发移除新连接的窗口 |
| 心跳 | `bike-assembly-server/src/main/java/com/bikeassembly/ws/WsTextHandler.java` | 已回应 pong，传输异常清理需补齐 |
| 推送 | `bike-assembly-server/src/main/java/com/bikeassembly/ws/WebSocketNotifier.java` | 使用自行创建的 ObjectMapper，LocalDateTime 字符串契约未固定；单连接发送失败只记录日志 |
| Web | `bike-assembly-mes-web/src/utils/useWebSocket.ts` | 已有重连；空基地址生成相对 WS 地址，旧连接回调可影响新连接，无 pong 超时检测 |
| Web 开发代理 | `bike-assembly-mes-web/vite.config.ts` | 只有 `/admin`，没有 `/ws` upgrade 代理 |
| 小程序 | `bike-assembly-mes-miniapp/src/utils/websocket.ts` | 已有心跳，无断线退避重连，使用全局发送接口而非对应 SocketTask，单例只保留首次回调 |
| 页面 | Web 六个列表、小程序安灯页 | 已有订阅/刷新入口；小程序其他业务页面需在执行时核对服务端目标用户与现有 API 后接入 |
| 退出 | 两端 `src/stores/user.ts` | logout 未关闭 WS；Web 连接可跨账号延续 |

推荐沿现有设计补齐实现，改动集中且兼容现有业务。仅补小程序重连不能解决 Web 部署与会话问题；整体重写为 STOMP 会扩大依赖和协议变化，因此不采用。

## 2. 接口及兼容约定

### 握手

- 地址：`GET /ws?token=<accessToken>`，使用 HTTP Upgrade。
- 成功：HTTP 101。
- token 缺失、过期、签名错误、refresh 类型、clientType 非法、userId 缺失：拒绝握手并返回 401。
- Origin 由现有 Spring WebSocket 白名单校验；非法来源返回 403。
- 握手 userId 与 clientType 只来自已验证 JWT，不采信 query 中其他身份字段。
- 浏览器不能读取完整握手错误体；不假定 WebSocket onerror 能区分 401 与网络错误。客户端无本地 token 时停止连接，已有 HTTP 401 流程清理登录态并停止 WS。

### 心跳及通知

客户端每 30 秒发文本 `ping`，服务端返回文本 `pong`。连接建立后才启动定时器；发送后 10 秒未收到 pong，关闭当前连接并进入重连。关闭页面、注销、没有 token 时不再重连。

通知字段保持现有契约：

```ts
export interface WsMessage {
  bizType: string
  eventType: string
  timestamp: string // ISO 本地日期时间，例如 2026-10-31T15:30:00
  data: Record<string, unknown>
}
```

业务类型沿用 `ANDON / WORK_ORDER / QUALITY / REPAIR / INVENTORY`。未知或格式不合法消息忽略，不阻断其他订阅者。时间按既有 LocalDateTime 语义输出字符串，不新增时区语义。

地址规则：未配置或空基地址使用当前页面 origin；绝对 HTTP(S) 地址保持其 origin 与部署前缀；相对前缀基于当前 origin 解析。HTTP → WS，HTTPS → WSS，最终追加 `/ws`。代理前缀须有对应路由，不凭空新增 `/api` 后端接口。

现有 `connect / disconnect / onWsMessage` 与 `connectWs / closeWs` 保留；移动端可新增按业务订阅的接口并迁移内部页面。定时器、事件回调通过连接对象或 generation 校验，旧连接不得修改新连接状态。

重连延时为 1、2、4、8、16、30 秒，之后封顶 30 秒，连接建立后归零；同一时刻最多一个重连任务。重连时读取最新 access token。

本次不改变业务 REST 字段及数据库结构。timestamp 固定字符串与现有 TS 声明一致；若旧服务端实际发送日期数组，这是对声明契约的修正，需要同步部署两端验证。

## Task 1: 后端握手、会话与通知可靠性

**Files:**
- Modify: `bike-assembly-server/src/main/java/com/bikeassembly/ws/WsHandshakeInterceptor.java`
- Modify: `bike-assembly-server/src/main/java/com/bikeassembly/ws/WsSessionManager.java`
- Modify: `bike-assembly-server/src/main/java/com/bikeassembly/ws/WsTextHandler.java`
- Modify: `bike-assembly-server/src/main/java/com/bikeassembly/ws/WebSocketNotifier.java`
- Test: `bike-assembly-server/src/test/java/com/bikeassembly/ws/WsHandshakeInterceptorTest.java`
- Test: `bike-assembly-server/src/test/java/com/bikeassembly/ws/WsSessionManagerTest.java`
- Test: `bike-assembly-server/src/test/java/com/bikeassembly/ws/WebSocketNotifierTest.java`
- Test: `bike-assembly-server/src/test/java/com/bikeassembly/ws/WsTextHandlerTest.java`

**Interfaces:** 沿用 `pushToAdmin(String, String, Map<String,Object>)`、`pushToUser(Long, String, String, Map<String,Object>)`，不改变业务调用签名。组件使用 Spring 构造器注入。

- [x] 用 JUnit 5 补握手成功与失败测试，覆盖管理员/app access、无 token、refresh、过期、签名错误及缺 userId；断言拒绝时属性不写入且状态为 401。
- [x] 对同用户多连接、单连接移除、admin/app 隔离和并发增删增加测试。将增删改为每个 key 的 compute/computeIfPresent 原子操作，避免空集合移除覆盖刚加入的连接。

```java
sessions.computeIfPresent(key(clientType, userId), (key, userSessions) -> {
    userSessions.remove(session);
    return userSessions.isEmpty() ? null : userSessions;
});
```

- [x] 心跳回复、传输失败及连接关闭测试覆盖注册/清理；失败连接关闭并移除，其他连接继续工作。
- [x] 使用 Spring 注入 ObjectMapper，建立本组件专用副本并关闭日期数组输出；保持消息字段不变。测试捕获 TextMessage，通过 JSON 解析断言 timestamp 为字符串且可解析。
- [x] 通知测试覆盖 admin 全量、app 指定用户、null 用户跳过、坏连接清理及后续连接仍收到消息。日志不拼接 JWT 解析异常原始 message。
- [x] 先运行新增测试确认缺陷可复现，再修复并重跑：

```powershell
mvn -pl bike-assembly-server -am '-Dtest=WsHandshakeInterceptorTest,WsSessionManagerTest,WebSocketNotifierTest,WsTextHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：所有指定测试通过，父模块不因没有匹配测试而失败。此任务无数据库行为，不使用容器或数据库 mock 伪造集成验证。

## Task 2: Web 连接、部署地址与退出清理

**Files:**
- Modify: `bike-assembly-mes-web/src/utils/useWebSocket.ts`
- Modify: `bike-assembly-mes-web/src/stores/user.ts`
- Modify: `bike-assembly-mes-web/vite.config.ts`
- Test: `bike-assembly-mes-web/src/utils/__tests__/useWebSocket.test.ts`

**Interfaces:** 保留 `connect(): void`、`disconnect(): void`、`onWsMessage(bizType, handler): () => void`。`useWebSocket()` 返回值不变。

- [x] 使用 Vitest fake timers 与受控 WebSocket stub，先复现空基地址、旧 onclose 干扰新连接、手动断开后重连和心跳无回应问题。
- [x] 通过 URL 解析和当前 origin 构造绝对 ws/wss 地址；用 stubEnv 分别验证空值、HTTP、HTTPS、尾斜杠及相对前缀。
- [x] 为每次创建的连接绑定独立引用，事件入口先校验是否仍是当前连接；disconnect 先使旧连接失效，再关闭并清理全部计时器。
- [x] 补 30 秒 ping、10 秒 pong 超时、指数退避及每次重连读取 token；无 token 不新建连接。纯文本 pong 在 JSON.parse 前处理。
- [x] 解析消息时校验 bizType/eventType/timestamp/data 类型；某个订阅抛异常时其他订阅继续收到通知。
- [x] logout、切换账号前调用 disconnect；确认已有 HTTP 401 清理路径也会停止连接。登录后由现有页面订阅入口重新建立连接。
- [x] Vite 为 `/ws` 添加与 `/admin` 相同后端目标并启用 `ws: true`，生产沿用现有 nginx `/ws` upgrade 代理。

```ts
'/ws': {
  target: 'http://localhost:8080',
  changeOrigin: true,
  ws: true,
}
```

- [x] 在 Web 目录执行 `npm test -- src/utils/__tests__/useWebSocket.test.ts`，修复后执行 `npm run build`。

## Task 3: 小程序重连、业务订阅与页面闭环

**Files:**
- Modify: `bike-assembly-mes-miniapp/src/utils/websocket.ts`
- Modify: `bike-assembly-mes-miniapp/src/stores/user.ts`
- Modify: `bike-assembly-mes-miniapp/src/pages/andon/index.vue`
- Test: `bike-assembly-mes-miniapp/src/utils/__tests__/websocket.test.ts`

**Interfaces:** 保留 `connectWs(onMessage): UniApp.SocketTask` 和 `closeWs(): void`；增加 `onWsMessage(bizType: string, handler: (msg: WsMessage) => void): () => void` 以提供独立注销。原回调也纳入订阅集合，closeWs 清理全部本次页面回调，防止重复积累。

- [x] 先用 SocketTask stub 与 fake timers 测试断线重连、多个订阅、注销单个订阅、旧回调失效、token 更新、无 token 及主动关闭。
- [x] 重连采用与 Web 一致的延时、心跳和 generation 规则，通过当前 SocketTask.send 发送 ping，避免全局 sendSocketMessage 误发到其他 socket。
- [x] 保留旧 connectWs 返回类型；无 token 的页面必须在调用前返回登录流程，不创建匿名连接；若底层仍被无 token 调用，抛出明确本地认证错误，由调用页面处理并不启动重连。
- [x] 安灯页只订阅 ANDON；onShow 建立连接并刷新，onHide/onUnload 注销并关闭，避免隐藏后继续刷新或自动重连。
- [x] logout/清理认证统一调用 closeWs；重连重新读取 accessToken，兼容旧 token key。
- [x] 核对维修、工单、质检页面的服务端目标用户与 API：仅对当前已真实推送给 app 用户的业务接入订阅；页面新增接入必须同时记录其文件及 API，不宣称服务端未推送的事件已实时化。
- [x] 在小程序目录执行 `npm test -- src/utils/__tests__/websocket.test.ts`，随后执行 `npm run type-check` 和 `npm run build:mp-weixin`。

## Task 4: 真实握手与业务链路验证、文档和审查

**Files:**
- Test: `bike-assembly-server/src/test/java/com/bikeassembly/ws/WebSocketIntegrationTest.java`
- Modify: `docs/06-安全与基础设施.md`
- Modify: `docs/最终功能完成度.md`
- Modify: `README.md`
- Update: 本计划的任务状态及实际验证结果。

- [x] 增加随机端口 WebSocket 集成测试：通过 Spring IoC 注册真实配置、握手拦截器、处理器和会话组件，使用真实签名 JWT 与 JDK WebSocket 客户端验证握手、ping/pong、通知接收、身份隔离及关闭清理；测试上下文不加载数据库业务，不能把它写成数据库业务已验证。
- [x] 审查现有五类事件在 ServiceImpl / MQ Listener 的触发位置、用户路由和前端订阅字段。记录事务提交前推送风险；若验证发现回滚仍推送，先以测试复现，再在现有通知入口改为提交后调用，保持非事务调用立即发送。
- [x] 运行 `mvn -pl bike-assembly-server -am '-Dtest=Ws*Test,WebSocket*Test' -Dsurefire.failIfNoSpecifiedTests=false test` 与 `mvn -DskipTests compile`。
- [x] 运行两端完整 `npm test`；此前已成功的测试不重复执行，除非新增修改影响了它们。
- [x] 在可用的本地后端、MySQL 环境中，登录真实账号验证安灯/维修/工单/质检/库存已有 API 更新后对应 Web 列表刷新，app 仅接收目标用户事件。若依赖不可用，明确记为未执行，不以 mock 测试替代真实联调结论。
- [x] 验证 Web 开发代理、生产空基地址构建配置、HTTPS → WSS，以及前端断网恢复与退出不重连；生产 nginx/微信真机不可用时明确列出限制。
- [x] 执行 Superpowers review，按项目检查 IoC、层次、事务、Mapper SQL、参数校验、DTO、异常、敏感日志及前后端契约，修复本次范围内问题后重跑相关检查。
- [x] 文档记录接口、消息结构、单实例边界、非持久消息语义、测试结果与部署要求。断线期间消息不补发，重回页面通过原 API 查询最新状态。

## 3. 完成时交付清单

| 要求 | 本次交付 |
| --- | --- |
| 前端 API 文件 | 两端 WebSocket 工具；保留各页面原有 REST 查询文件 |
| 后端 Controller | 复用已有业务 Controller；WS 端点由 WebSocketConfig + WsTextHandler 提供，无需虚构 REST 接口 |
| Service / ServiceImpl | 复用现有业务服务；只在验证发现相关推送缺陷时修改并列明文件 |
| Mapper / XML | 复用现有业务 SQL，本次不增加数据库访问 |
| 数据库表 | 无新增、无字段变更、无迁移 SQL |
| 已验证接口 | 按实际执行逐项记录 `/ws` 握手、心跳、通知，以及完成联调的现有业务 API |
| 仍缺失或未验证 | 明确区分未实现功能、测试未执行、生产及真机验证限制 |

## 4. 已知边界

- 查询 token 方式延用原设计，反向代理访问日志应避免保存带 token 的完整 query；不打印连接 URL。
- 本次保证握手时 JWT 有效；长期连接上的 token 过期、撤销实时踢出和细粒度业务订阅权限仍需单独设计，不宣称已完成。
- 单实例连接注册表不支持跨节点广播；MQ 多实例竞争消费也不等同于 WS 广播。
- 仅发送实时刷新提示，不提供离线消息补发或消息送达保证。
- 计划编写时未运行验证；执行后 Maven 编译、60 项后端相关测试、Web 107 项、小程序 90 项及 34 项真实链路检查通过。生产与真机验证仍按交付说明列明边界。
