# 一期工单扫码闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 管理端生成真实工单二维码，小程序扫码后按系统账号现有权限进入工单、工序或待质检任务，形成可验证的定位闭环。

**Architecture:** Controller 接收和校验参数，WorkOrderScanService/Impl 负责码协议、真实工单查询和权限判定，Mapper/XML 执行参数化 SQL。复用现有工单、工序和质检业务；小程序将后端返回的动作枚举映射到固定页面，扫码本身不产生业务写入。

**Tech Stack:** Spring Boot 3.3.5、Java 17、MyBatis-Plus 3.5.7、MySQL；uni-app + Vue 3 + TypeScript + Pinia；Vue 3 + Element Plus；JUnit 5、Vitest、Testcontainers。

**状态：用户于 2026-10-27 确认，代码及已执行验收完成；build额外自动化复验和真机验证边界见末尾。** 执行记录见 `.run/work-order-scan/progress.md`。

## 全局约束

- 项目目录和全部新增文件、日志、截图、二维码示例均位于 `D:\code\Java\bike-assembly-mes`。
- 一期只实现工单扫码，不实现设备、物料、任意 URL、微信小程序码 scene 路由。
- 保留当前所有未提交修改；尤其保留已有系统账号微信绑定、登录身份、质检工单搜索和各项筛选。
- 按用户要求在当前实际工作区增量开发，不通过新建工作树丢失未提交代码，不自动提交、重置、清理现有改动。
- Spring IoC 管理 Controller、Service、Mapper；业务代码不得手动创建这些对象。DTO/VO/Entity 分离。
- 沿用当前登录拦截器、PermissionService、权限码和 token 请求头；此次不迁移认证框架、不修改账号角色、不扩大权限。
- 沿用 `Result<T>` 的 `code/msg/data` 格式，成功码 1；新增接口不破坏现有响应格式。
- 不改数据库表结构，不执行演示数据重置。仅使用现有真实业务记录生成交付示例。
- 开发完成后分别重新编译 dev/build 微信产物，禁止手工修改 dist 文件或只更新 build。

## 一、已核查的实现和直接相关问题

1. `src/components/tab-bar/index.vue` 目前仅请求 `scanType: ['barCode']`，识别后弹窗复制。
2. 小程序现有页面为 `pages/work-order/detail?id=...`、`pages/work-process/index`、`pages/inspection/index`。工单详情没有进入工序报工入口，后两页尚未接受扫码定位参数。
3. 工单详情当前会无条件查询工序列表。只有工单查看权限而没有工序查看权限的账号可能因此无法加载详情，需要按权限加载辅助数据。
4. 质检页面已通过 QmsInspectionVO.workOrderNo 搜索和展示工单编号，不应重新依赖生产工单列表权限。
5. `AppQmsInspectionController.list` 先查询固定上限列表，再用 Stream 按 workOrderId/processId 过滤；需改为数据库过滤，避免扫码目标因截断而丢失。
6. `QmsInspectionMapper.selectPageByCondition` 的 mine 查询当前包含所有未领取待检任务，includePending 未结合 view-pending 权限。扫码和目标页面必须使用一致的、经后端校验的可见范围。
7. 工序页面目前同时按同一个 scope 请求工序和工单，两个 view-all 权限可能不同；扫码定位时应分别校验，避免权限耦合。
8. `docs/openapi.yaml` 有认证及部分工序写接口，但缺少本次链路需要的多个 `/app/**` 查询路径，需一并补齐实际契约。
9. 已找到微信开发者工具 CLI：`D:\微信小程序开发\微信web开发者工具\cli.bat`。尚未验证其服务端口、登录状态及自动化连接。

## 二、方案比较与推荐

| 方案 | 收益 | 代价 | 结论 |
| --- | --- | --- | --- |
| 后端统一码协议与解析授权，前端生成图片和固定页面跳转 | 格式单一、真实查询、质检独立授权、便于测试 | 新增两个只读业务端点 | 推荐 |
| 前端自行拼码并根据角色查询现有多个列表 | 新增端点较少 | 协议和授权分散，存在列表截断、质检依赖工单权限问题 | 不采用 |
| 服务端生成带签名或临时令牌的码、维护码记录表 | 可扩展到一次性操作凭证 | 本期只是定位，增加依赖、过期和数据库维护成本 | 本期不采用 |

二维码是公开业务定位标识，不是授权凭证；修改码里的 ID 仍必须经过完整后端权限与归属校验。

## 三、先行接口契约

### 3.1 码内容

标准二维码文本：`BIKE-MES:1:WORK_ORDER:<workOrderId>`。

- 固定前缀 BIKE-MES、版本 1、业务类型 WORK_ORDER、数据库中真实存在的正整数主键。
- 严格匹配完整内容；拒绝未知版本、未知类型、额外段、负数、溢出数字和控制字符。前端页面参数校验使用正安全整数。
- 兼容普通条形码：内容必须是完整工单编号，最长 64 字符，允许字母、数字、连字符、下划线和点；按 `work_order_no = #{workOrderNo}` 精确查询，不用模糊查询或截取推断。
- 裸数字按工单编号处理，不猜测为 ID；标准二维码通过前缀明确表达主键。
- 拒绝 URL、任意页面路径、JSON、设备码、物料码；不使用扫码结果的 path 字段跳转。
- 不包含 token、openid、密码、角色、权限或服务端地址。
- 未来通过新版本/新业务类型扩展；本期遇到非工单类型明确提示不支持。

### 3.2 管理端工单码元数据

`GET /admin/pro-work-order/{id}/qr-code`

权限：`admin:work-order:view`。Controller 调用 WorkOrderScanService，查询真实工单后返回 `Result<WorkOrderQrCodeVO>`。

```ts
interface WorkOrderQrCodeVO {
  workOrderId: number
  workOrderNo: string
  content: string
}
```

Web 根据 content 使用本地二维码编码库生成 PNG，弹窗显示工单编号和二维码，支持下载。每次打开从后端获取元数据，删除的工单不得继续展示旧二维码。图片转换失败时显示明确错误并禁用下载。

### 3.3 小程序扫码解析

`POST /app/scan/resolve`

请求 DTO：`ScanResolveDTO { content: string }`，非空，最长 256 字符；客户端不传身份和权限。

响应 `Result<ScanResolveVO>`：

```ts
type ScanAction = 'WORK_ORDER_DETAIL' | 'WORK_PROCESS' | 'INSPECTION_PENDING'
interface ScanResolveVO {
  businessType: 'WORK_ORDER'
  workOrderId: number
  workOrderNo: string
  actions: ScanAction[]
  pendingInspectionCount: number
}
```

授权规则由 Service 计算，不能只依赖前端隐藏按钮：

- 必须为正式已登录系统账号，沿用 app JWT 拦截器的账号、安全状态和凭据检查。
- WORK_ORDER_DETAIL：`app:work-order:view`，并且当前用户是工单 assignee，或具有 `app:work-order:view-all`。
- WORK_PROCESS：`app:work-process:view`，并且当前用户是工单 assignee，或具有 `app:work-process:view-all`。写操作仍由原有 start/report/rework 接口独立校验。
- INSPECTION_PENDING：`app:inspection:view` 且该工单有可见 status=0 待检任务。可见范围按已有工单执行人、当前检验人、view-all、未领取且 view-pending 的规则计算，不能读取其他质检员已领取任务。
- 多个动作显示明确入口供用户选择；单个动作直接进入固定页面。仅有质检权限时直接进入质检页，绝不先请求工单详情。
- 若只有质检入口资格但没有可访问的待检任务，提示“当前工单暂无可访问的待质检任务”，不退回工单页，不自动生成或领取任务。
- 无任何可用入口和质检查询资格时返回 403；不返回该工单的响应元数据。
- 每个目标查询/写接口继续自行校验权限和数据归属，解析成功不会授予后续访问权。

业务错误沿用数字 code 和 msg，建议集中定义以下常量，前端优先展示 msg：

| code | 含义 | 展示文案 |
| --- | --- | --- |
| 46001 | 码格式无效 | 码内容无效，请扫描工单二维码或工单编号条形码 |
| 46002 | 不支持的版本/类型 | 暂不支持此码类型或版本，请扫描一期工单码 |
| 46003 | 精确查询无工单 | 工单不存在或已删除 |
| 46004 | 无可访问待检任务 | 当前工单暂无可访问的待质检任务 |
| 401 | 未登录/会话失效 | 请重新登录 |
| 403 | 权限或归属不足 | 您无权访问该工单的任务 |

460xx 使用项目已有 BaseException(code, message) 业务错误机制。取消扫码属于本地取消，不请求解析接口、不弹报错。摄像头权限拒绝提示在微信设置中开启，提供设置入口；网络错误显示网络提示。

### 3.4 复用查询接口的增量参数

- `GET /app/pro-work-process/list` 新增可选 `workOrderId`；Mapper XML 在现有归属条件内追加工单条件。
- `GET /app/qms-inspection/list` 保留 scope/workOrderId/processId，新增可选 status，用于扫码待检筛选；全部条件在 SQL 中生效。
- 质检列表读取授权与扫码查询共用 Service 权限范围判定；mine 只有具有 view-pending 时才包含未领取任务。
- 保留原 Service 公共签名或提供兼容重载，管理端及现有测试调用不被无关改变。
- 不强制把全项目列表改成分页；现有分页依旧由 MyBatis-Plus IPage 执行真实 LIMIT/OFFSET。扫码必须先 SQL 精确限定工单，不能从全量列表中寻找目标。
- 本次 API 是新增端点、增加可选查询参数；修正 mine 未领取任务权限属于可见范围收紧，需在交付中说明。

## 四、实施任务与修改文件

以下 Java 路径均位于对应模块的 `src/main/java/com/bikeassembly/`；SQL XML 位于 server 的 `src/main/resources/mapper/`。

### Task 1：协议、DTO/VO 和后端解析链路

**新增文件：**

- pojo：`dto/ScanResolveDTO.java`、`vo/ScanResolveVO.java`、`vo/WorkOrderQrCodeVO.java`。
- common：`constant/ScanCodeConstant.java`，统一版本、业务类型、动作和业务错误码。
- server：`controller/app/AppScanController.java`、`service/WorkOrderScanService.java`、`service/impl/WorkOrderScanServiceImpl.java`。
- server tests：`scan/WorkOrderScanServiceTest.java`、`scan/WorkOrderScanControllerTest.java`。

**修改文件：** `controller/admin/ProWorkOrderController.java`、`mapper/ProWorkOrderMapper.java`、`ProWorkOrderMapper.xml`。

- [x] 保存执行前 git diff 和待修改文件哈希到 D 盘 `.run/work-order-scan/`，作为保留既有修改的核对基线，不将敏感配置写入证据。
- [x] 先将第三节契约补充到 OpenAPI，再定义 Java 和前端类型，固定字段和枚举。
- [x] 编写 JUnit 5 参数化测试：标准码、完整编号、非法 URL、未知类型/版本、超长输入、数字边界、查无工单。
- [x] 实现 Spring Service、精确查询和入口权限计算，Controller 仅校验参数、调用 Service、包装 Result。
- [x] 编写操作员本人/他人工单、查看全部、无权限、纯质检、权限组合测试，断言纯质检路径不调用生产工单查看 Service。
- [x] 验证只调用查询 Mapper，不修改工单、工序、检验状态或领取人。

### Task 2：目标查询的数据库过滤与归属闭环

**修改文件：**

- server：`controller/app/AppProWorkProcessController.java`、`service/ProWorkProcessService.java`、`service/impl/ProWorkProcessServiceImpl.java`、`mapper/ProWorkProcessMapper.java`、`ProWorkProcessMapper.xml`。
- server：`controller/app/AppQmsInspectionController.java`、`service/QmsInspectionService.java`、`service/impl/QmsInspectionServiceImpl.java`、`mapper/QmsInspectionMapper.java`、`QmsInspectionMapper.xml`。
- tests：新增 `scan/WorkOrderScanMapperIntegrationTest.java` 和查询授权回归测试；核对现有 `security/ScopeSecurityTest.java`、`review/InspectionWorkOrderNumberTest.java`。

- [x] 编写数据库集成用例，以隔离的 MySQL Testcontainer 执行真实 Mapper/XML；使用 @DynamicPropertySource，不 mock 数据库。
- [x] 插入两名操作员、两名质检员和不同工单/任务测试记录，覆盖工单精确过滤、状态过滤、mine/pending/all、本人、未领取、他人领取和无任务。
- [x] 移除 Controller 的 Stream 业务过滤，Service 验证可选参数和查询范围，Mapper 使用动态 SQL 和参数绑定。
- [x] 为新查询复用统一可见范围条件，消除 mine 无 view-pending 却可读待检池的问题；不增加逐条查询造成 N+1。
- [x] 回归原有质检编号补充、搜索、状态/确认/结果筛选与写接口归属控制。
- [x] 若容器运行时不可用，记录原因；另执行独立且事务回滚的本地 MySQL Mapper 集成验证，不在实际业务库运行清库脚本，也不把未执行的 Testcontainers 用例计为通过。

### Task 3：管理端二维码展示和下载

**修改/新增文件：**

- `bike-assembly-mes-web/src/api/proWorkOrder.ts`。
- `bike-assembly-mes-web/src/types/scan.ts`。
- `bike-assembly-mes-web/src/views/production/workOrder.vue`。
- `bike-assembly-mes-web/src/components/WorkOrderQrDialog.vue`。
- `bike-assembly-mes-web/package.json`、锁文件，按实际需要增加 qrcode 及类型声明。
- 对应二维码组件/工具 Vitest 用例。

- [x] 在工单列表增加“工单二维码”操作，权限沿用工单查看权限。
- [x] 每次打开查询真实码元数据，组件显示编号、可读码内容、二维码和“下载 PNG”。
- [x] PNG 使用实际工单编号命名，清理文件名非法字符，保证二维码留白和对比度。
- [x] 覆盖接口失败、渲染失败、连续切换工单、关闭后重新打开，避免显示上一个工单的二维码。
- [x] 执行管理端测试、类型检查和构建。

### Task 4：小程序扫码与三条业务进入路径

**修改/新增文件：**

- `bike-assembly-mes-miniapp/src/types/scan.ts`、`src/api/scan.ts`、`src/utils/workOrderScan.ts`。
- `src/components/tab-bar/index.vue`。
- `src/pages/work-order/detail.vue`、`src/pages/work-process/index.vue`、`src/pages/inspection/index.vue`。
- `src/api/workProcess.ts`、`src/api/inspection.ts`。
- 新增 `src/utils/__tests__/workOrderScan.test.ts`，按测试边界补充 API/跳转用例。

- [x] 先测试 scanner 成功/取消/权限拒绝/其他失败、解析错误、重复扫码保护和固定动作映射。
- [x] 使用 `scanType: ['barCode', 'qrCode']`；处理完整 result，不使用 path 跳转；通过封装 http 调用解析端点。
- [x] 保持防重锁直到解析和入口选择完成；取消选择/扫码失败/接口失败均释放防重锁。
- [x] 工作入口由 actions 决定：工单详情 `/pages/work-order/detail?id=<id>`；工序 `/pages/work-process/index?workOrderId=<id>`；质检 `/pages/inspection/index?workOrderId=<id>&stage=pending`。
- [x] 工单详情增加按权限展示的“进入工序报工”，辅助工序数据按权限加载，只有工单查看权限的账号仍可读详情。
- [x] 工序页按扫码工单 ID 向后端查询；工序和工单的 scope 分别结合自身权限；不因缺少生产工单查看权限阻断合法的工序入口。
- [x] 质检页解析 onLoad 参数，首次和刷新都向后端传工单条件，预设待检筛选；没有匹配任务保持本工单筛选并显示空态，不自动清空后展示其他工单。
- [x] 保留手工搜索工单、待检/已检、确认状态和结果筛选；明确提供清除扫码定位的操作以返回常规列表。
- [x] 错误文案优先使用后端 msg；401 复用登录失效处理，403 明确无权限；扫码和页面加载不触发任何业务写接口。

### Task 5：构建、真实联调、开发者工具和交付

**产物目录：** `.run/work-order-scan/`、`docs/examples/work-order-scan/`。

- [x] 使用已有环境启动必要本地服务和更新后的后端，不回显密钥/凭据；通过真实业务查询选取已存在工单及其归属、待检任务，记录非敏感元数据。
- [x] 执行 `npm run type-check`、`npm test -- --run`（miniapp），以及 `npm run build`、`npm test -- --run`（web）。
- [x] 执行 `mvnw.cmd -pl bike-assembly-server -am -DskipTests compile`；使用 `-Dtest=WorkOrderScan*Test,WorkOrderScanMapperIntegrationTest,ScopeSecurityTest,InspectionWorkOrderNumberTest -Dsurefire.failIfNoSpecifiedTests=false test` 执行相关后端测试。实际 Maven/JDK 路径在执行前按本机安装核实。
- [x] 分别运行 miniapp 的 `npm run build:mp-weixin` 和 `npm run dev:mp-weixin`，等待开发编译成功后再停止 watch；记录两套产物的编译时间和相关页面代码哈希。
- [x] 对两套产物检查 app.json、页面注册、扫码 qrCode/barCode 代码及无丢失依赖；不通过复制 build 到 dev 代替开发构建。
- [x] 生成真实工单 PNG 二维码和工单编号条形码示例；使用独立解码方式核对图片内容，示例文档注明查询时工单状态、预期身份和路径。测试中不存在工单编号只用于失败用例，不冒充真实业务示例。
- [ ] 在微信开发者工具分别导入实际项目 `dist/dev/mp-weixin`、`dist/build/mp-weixin`；通过可用 CLI/uni-automator 连接检查首页、点击扫码、识别结果后页面跳转、工序筛选和质检待检筛选，保存截图与结果。
- [x] 开发者工具可注入 scanCode 结果验证页面逻辑，但明确标为模拟识别；真机摄像头识别、首次摄像头授权、拒绝后重授权、真实微信绑定会话单列真机验收，不将模拟结果称为真实相机识别。
- [x] 核对扫码前后工单/工序/质检状态和领取人未改变，复测账号绑定与既有筛选不被破坏。
- [x] 按 Superpowers review 流程自查分层、SQL、权限、DTO/VO、异常和测试，修复本次问题后再次执行相关验证。
- [x] 交付修改文件、API/Controller/Service/Mapper 对照、数据库表、已验证和仍缺失接口清单、执行命令及通过/失败/未执行结果、二维码和操作步骤。

## 五、验收矩阵

| 输入/身份 | 期望结果 |
| --- | --- |
| 标准二维码＋工单执行人 | 真实工单详情/本工单工序入口；扫码无写入 |
| 完整编号条形码＋执行人 | 与同工单标准二维码定位一致 |
| 纯质检账号＋有权待检任务 | 直接进入对应工单待检列表，不请求生产工单详情/列表 |
| 同时拥有操作和质检权限 | 只展示后端批准的动作，选择后定位同一工单 |
| 无工单权限但有合法工序查看权限 | 可进入允许的本工单工序页，不强依赖工单列表 |
| 他人指派工单且无 view-all | 403；不出现详情内容 |
| 无扫码业务权限 | 403；不出现业务入口 |
| 他人已领取的质检任务＋无 view-all | 不可见、不误显示为可操作任务 |
| 工单存在但无可访问待检任务 | 明确空态或 46004，不展示其他工单任务 |
| 无效/URL/设备/物料/未知版本码 | 明确无效或不支持；无任意地址跳转 |
| 工单不存在/已删除 | 46003；可重新扫码 |
| 取消扫描/取消入口选择 | 静默返回；可以再次扫码 |
| 摄像头权限拒绝 | 授权提示和设置入口，不误报业务异常 |
| 网络异常/401/403 | 分别展示网络错误、登录失效、无权限 |
| dev 和 build 导入 | 首页正常渲染，三条路径工作，无旧 dev 白屏 |

## 六、确认点与已知验证边界

确认后按本计划在当前工作区继续实施，不扩展设备/物料扫码。确认覆盖协议、两个新增接口、按动作进入页面，以及质检 mine 查询收紧到既有权限边界。

开发者工具安装路径已经找到，但服务、账号与自动化端口尚未连通验证；容器运行时和实际本地服务可用性也尚未验证。实施阶段会尝试完成真实联调和开发者工具检查；若环境限制使某项不能执行，将给出具体原因和已完成证据，不把构建成功当作页面验收通过。

项目 `AGENTS.md` 明确要求：“生成详细的实施计划（Plan），并等待我确认计划无误”。因此在用户确认前不修改业务代码。


## 执行结果（2026-10-28）

实现及相关单元/数据库/真实API/WebUI/dev微信UI验收完成。dev/build分别编译，原有改动基线核对通过；当前分支保留未提交。build已导入并到达登录后首页路由，但额外跨页自动化调用停滞，未计为通过，故上方对应验收项保留未完成。真机相机/授权/微信真实绑定会话，以及发布前build手工复核详见交付说明。Docker缺失的5项容器用例未计为通过，本机真实MySQL6项已事务回滚通过。
