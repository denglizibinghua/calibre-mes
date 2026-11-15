# WebSocket 实时推送交付说明

日期：2026-10-31。依据 `06-安全与基础设施.md` 第 10.2 节及已确认的 `superpowers/plans/2026-10-31-websocket-completion.md` 实现。

## 已完成行为

- `/ws?token=<accessToken>` 校验签名、有效期、access 类型、客户端身份及非空正数 userId，并复核客户端对应的签名密钥。无效凭证返回 401，非法来源由现有白名单返回 403。
- 会话按客户端与用户隔离，原子增删同用户多连接。发送失败、传输错误、关闭连接时清理注册表，单连接失败不影响其他接收者。
- 消息沿用 `bizType / eventType / timestamp / data`，timestamp 固定 ISO 本地日期时间字符串；HTTP 原有日期格式保持兼容。
- 五类业务通知在 Service 的事务提交后发送，回滚不发送。MQ 可用时由原消费者推送，MQ 未开启或发布失败时直接通知。
- Web 与小程序均每 30 秒发 ping，10 秒未收到 pong 重新连接；重连按 1、2、4、8、16、30 秒退避并封顶，每次读取当前 token。
- 旧连接回调不能干扰新连接。退出、切换账号及认证最终失效时停止连接；正常自动刷新 token 保留活跃页订阅。
- Web 六个列表、小程序安灯与维修页收到对应通知及连接恢复时调用原有 API。请求代际防止迟到旧数据覆盖新结果，页面退出后响应失效。
- Web 空基地址采用页面同源地址，HTTPS 自动使用 WSS；开发 Vite 已增加 `/ws` upgrade 代理，生产构建默认使用同源反向代理。

## 安全设计、依赖与协议

复用 Spring Boot WebSocket、JJWT、Jackson、JUnit 5 和两端现有 Vitest，无新增生产依赖。Spring 管理端点、处理器、通知组件、业务服务与 Mapper，事务边界仍在 Service。

```json
{
  "bizType": "ANDON",
  "eventType": "CREATE",
  "timestamp": "2026-10-31T14:11:44.38",
  "data": { "alertId": 12, "status": "0" }
}
```

心跳使用纯文本 `ping` / `pong`。无效通知忽略，各订阅者的同步异常及异步拒绝互相隔离。

握手错误不是新增业务异常：浏览器无法从 WebSocket onerror 读取详细 HTTP 响应，不把网络错误误判为登录失效。小程序无本地 token 时提示“登录已失效，请重新登录”；底层同步连接失败提示“实时连接暂不可用，将自动重试”。已有 REST 业务错误继续由原接口返回明确原因，401 最终失效通过原认证流程提示“登录已失效，请重新登录”。

## 文件与数据库闭环

前端传输文件：

- Web：`bike-assembly-mes-web/src/utils/useWebSocket.ts`。
- 小程序：`bike-assembly-mes-miniapp/src/utils/websocket.ts`。
- 两端 `src/stores/user.ts`、请求拦截器及相关页面完成认证、生命周期和查询衔接。

后端传输与事务文件：

- `bike-assembly-server/src/main/java/com/bikeassembly/config/WebSocketConfig.java`（复用端点注册）。
- 同模块 `ws/WsHandshakeInterceptor.java`、`WsTextHandler.java`、`WsSessionManager.java`、`WebSocketNotifier.java`。
- `bike-assembly-common/src/main/java/com/bikeassembly/utils/AfterCommitNotification.java`。
- 以下五个 ServiceImpl 的通知入口改为提交后执行，仍使用原有 Service 接口与 Mapper SQL。

下表中的 Java 类位于 `bike-assembly-server/src/main/java/com/bikeassembly/`，XML 位于 `bike-assembly-server/src/main/resources/mapper/`，Web API 位于 `bike-assembly-mes-web/src/api/`：

| 业务 | 前端 API 文件 | 原 Controller | Service / ServiceImpl | 原 Mapper / XML | 原表 |
| --- | --- | --- | --- | --- | --- |
| 安灯 | `andonAlert.ts`；移动端 `src/api/andon.ts` | `controller/admin/AndonAlertController.java`、`controller/app/AppAndonAlertController.java` | `AndonAlertService / AndonAlertServiceImpl` | `AndonAlertMapper.java / AndonAlertMapper.xml` | `andon_alert` |
| 维修 | `eqmRepairOrder.ts`；移动端 `src/api/repair.ts` | `controller/admin/EqmRepairOrderController.java`、`controller/app/AppEqmRepairOrderController.java` | `EqmRepairOrderService / EqmRepairOrderServiceImpl` | `EqmRepairOrderMapper.java / EqmRepairOrderMapper.xml` | `eqm_repair_order` |
| 工单 | `proWorkOrder.ts` | `controller/admin/ProWorkOrderController.java` | `ProWorkOrderService / ProWorkOrderServiceImpl` | `ProWorkOrderMapper.java / ProWorkOrderMapper.xml` | `pro_work_order`、`pro_work_process` |
| 质检 | `qmsInspection.ts` | `controller/admin/QmsInspectionController.java`、`controller/app/AppQmsInspectionController.java` | `QmsInspectionService / QmsInspectionServiceImpl` | `QmsInspectionMapper.java / QmsInspectionMapper.xml` | `qms_inspection`、`pro_work_process` |
| 库存单据 | `wmsStockDoc.ts` | `controller/admin/WmsStockDocController.java` | `WmsStockDocService / WmsStockDocServiceImpl` | `WmsStockDocMapper.java / WmsStockDocMapper.xml` | `wms_stock_doc`、`wms_inventory`、`wms_stock_flow` |
| 库存台账刷新 | `wmsInventory.ts` | `controller/admin/WmsInventoryController.java` | `WmsInventoryService / WmsInventoryServiceImpl` | `WmsInventoryMapper.java / WmsInventoryMapper.xml` | `wms_inventory` |

移动端 API 路径基于 `bike-assembly-mes-miniapp/`。**无新增业务 REST 接口、表或字段，无需执行数据库迁移 SQL。** WebSocket 运行时连接集合不存放业务数据。

## 已验证接口清单

真实网络 JUnit 测试通过：`GET /ws` upgrade、access 握手、refresh/无效凭证拒绝、Origin 拒绝、ping/pong、admin/app 用户隔离、连接关闭清理。

完整后端使用独立随机 `mes_ws_*` 测试库、临时 Redis 和 Vite 验证以下实际请求及事件，共 34 项断言：

| 实际请求 | 对应验证 |
| --- | --- |
| `POST /admin/user/login`、`POST /app/user/login` | 从真实登录接口取 token，凭证只在内存中使用 |
| `POST /admin/andon-alert` | ANDON CREATE，管理端与上报人 app 连接收取 |
| `POST /admin/eqm-repair-order`、`PUT /admin/eqm-repair-order/{id}/accept` | REPAIR ACCEPT，管理端与目标 app 收取 |
| `PUT /admin/pro-work-order/{id}/release` | WORK_ORDER RELEASE，提交后原详情接口读到下达状态 |
| `PUT /app/qms-inspection/{id}/confirm` | QUALITY CONFIRM，工序联动成功且原详情读到确认状态 |
| `POST /admin/wms-stock-doc`、`PUT /admin/wms-stock-doc/{id}/execute` | INVENTORY EXECUTE 与真实库存写入 |
| 六个 Web 列表 GET | `/admin/andon-alert/list`、`/admin/eqm-repair-order/list`、`/admin/pro-work-order/list`、`/admin/qms-inspection/list`、`/admin/wms-stock-doc/list`、`/admin/wms-inventory/list` |
| 两个 app 列表 GET | `/app/andon-alert/list`、`/app/eqm-repair-order/list` |
| Vite `/ws` | 真实 upgrade 代理后收到 pong |

测试只在自己的临时库内装载演示夹具、补齐工单指派人与质检检验人等业务前置条件，测试后删除该库并关闭临时服务。未修改现有业务库。

## 测试与自查结果

| 检查 | 结果 |
| --- | --- |
| Maven compile | 通过，三个子模块均成功 |
| 后端 WS / 事务通知 / 并发维修相关测试 | 42 项通过，含真实随机端口网络测试 |
| 既有真实 MySQL 回归 `ReviewFixIntegrationTest` | 18 项通过，独立 `mes_review_*` 库自动清理 |
| Web 完整 Vitest | 107 项通过，12 个文件 |
| Web 类型与生产 build | 通过 |
| 小程序完整 Vitest | 90 项通过，10 个文件 |
| 小程序 type-check / build:mp-weixin | 均通过 |
| 完整后端 + MySQL + WS + Vite 联调 | 34 项检查通过 |
| git diff --check | 通过 |

回归测试先复现握手状态、会话并发丢失、日期格式、提交前通知、连接竞态及页面响应乱序，再验证修复。独立代码审查发现的问题已修复；原有未提交的 POM 与其他工作区材料保留，未提交或推送本次改动。

自查：Spring IoC、原有包结构、Controller 轻量、Service 事务、Mapper 真实 SQL、DTO/VO 契约及统一 REST 返回格式保持一致；本次未加入未完成方法、占位业务或内存业务数据库。

## 仍缺失接口与验证边界

本次计划范围内无缺失接口。app 实时推送只覆盖后端已经定向发送的 ANDON / REPAIR（上报人或处理人）；工单、质检、库存目前仅通知管理端。

- 微信真机断网、后台切换、合法 WSS 域名与生产 TLS/nginx 未实测；实际浏览器 UI 完整业务流程未自动操作。页面实际 SFC 的订阅、API 补查、乱序与退出行为由测试验证。
- 本次真实业务联调使用 MQ 关闭模式；MQ 发布成功/失败与提交顺序由回归测试验证，没有声称已跑真实 RabbitMQ 消费验收。
- 会话表为单实例，未实现跨节点广播；通知不持久化，断线期间不补发事件，通过恢复后 API 查询得到最新状态。
- JWT 在握手时校验；现存长连接的实时撤销、到期踢出和更细业务订阅权限没有在本次扩展。
- 生产反向代理应使用 WSS 并避免把 `/ws` 查询参数写入访问日志，凭证仍沿用原设计的 query 传输。

实施计划及本文件为交付依据。可复现执行记录、无凭证联调结果位于 `.run/websocket-20261031/`。
