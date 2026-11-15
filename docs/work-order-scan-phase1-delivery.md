# 一期工单扫码闭环交付说明

日期：2026-10-28（实施与首轮测试：2026-10-27）。实际项目：`D:\code\Java\bike-assembly-mes`。

## 功能与接口

管理端工单列表可获取真实工单码元数据、展示二维码并下载 PNG。小程序支持二维码和条形码，由后端解析并返回允许的动作，小程序只将动作映射到固定页面。操作员和质检员复用原有查询、报工与质检流程。

统一协议为 `BIKE-MES:1:WORK_ORDER:<正整数ID>`。条形码兼容完整工单编号，按数据库 `work_order_no = 参数` 查询；裸数字按编号查询，不猜测为主键。不支持任意 URL、页面路径、设备或物料业务类型。

| 接口 | 请求与响应 | 授权与异常 |
| --- | --- | --- |
| `GET /admin/pro-work-order/{id}/qr-code` | 返回 `workOrderId/workOrderNo/content` | 需要 `admin:work-order:view`；46003工单不存在 |
| `POST /app/scan/resolve` | 请求 `{content}`；返回 `businessType/workOrderId/workOrderNo/actions/pendingInspectionCount` | 正式App登录态，Service分别校验每个动作权限和归属 |
| `GET /app/pro-work-order/{id}` | 复用工单详情 VO | 工单查看权限＋执行人或view-all |
| `GET /app/pro-work-process/list` | 新增可选 `workOrderId`，保留scope | 工序查看权限；all额外要求工序view-all，mine按执行人限制 |
| `GET /app/qms-inspection/list` | 保留scope/workOrderId/processId，增加可选status=0/1/2 | 质检查看权限；在SQL中先过滤工单、状态与可见范围，再分页 |

动作枚举及固定路径：

| 动作 | 页面 |
| --- | --- |
| `WORK_ORDER_DETAIL` | `/pages/work-order/detail?id=工单ID` |
| `WORK_PROCESS` | `/pages/work-process/index?workOrderId=工单ID` |
| `INSPECTION_PENDING` | `/pages/inspection/index?workOrderId=工单ID&stage=pending` |

新端点沿用 `Result<T>` 的 `code/msg/data` 包装和 token 请求头，没有替换现有分页或异常返回格式。业务错误仍由 BaseException 和全局异常处理器返回。

| code | 前端展示文案 |
| --- | --- |
| 46001 | 码内容无效，请扫描工单二维码或工单编号条形码 |
| 46002 | 暂不支持此码类型或版本，请扫描一期工单码 |
| 46003 | 工单不存在或已删除 |
| 46004 | 当前工单暂无可访问的待质检任务 |
| 401 | 复用登录失效处理，清理状态并进入登录页 |
| 403 | 您无权访问该工单的任务 |

## 安全设计与兼容性

- 登录身份、微信首次绑定已有账号、JWT校验和 PermissionService 继续沿用。码内不携带凭据，扫码接口不相信客户端角色，不返回可执行地址。
- 工单、工序和质检入口独立判断；质检扫码不调用生产工单查看接口。每个目标接口仍独立鉴权，解析成功不产生授权凭证。
- 扫码、列表加载全部为读取，不自动开工、报工、领取、创建任务或确认质检。
- 质检列表和扫码计数共用SQL范围：本人领取、本人工单未领取；view-pending额外允许未领取待检池；view-all明确允许全部。非view-all用户不会因为是执行人而读到他人已领取任务。
- 相比旧实现，mine不再无条件包含所有未领取任务。这是权限边界修正，需要使用已有view-pending或view-all权限访问相应范围。
- 实际Web验收发现 `SysMenuServiceImpl.getUserPermissionCodes` 原来只返回F按钮权限，漏掉C页面查看权限，导致二维码按钮被隐藏。现返回既有角色关联中启用且非空的权限；没有给角色增加新授权，也没有放宽后端端点。
- 新增接口、可选参数、权限列表补齐属于增量契约；不改变旧业务请求字段和报工/质检状态机。
- 既有质检详情接口的历史读取语义不在本次整体重构范围。本次验证和收紧覆盖扫码计数、App质检列表及其目标页面。

## 文件与调用链

以下均相对项目根目录；完整本次文件清单另见 `.run/work-order-scan/changed-files.json`。

| 层 | 修改/新增文件 |
| --- | --- |
| 小程序 API | `bike-assembly-mes-miniapp/src/api/scan.ts`、`api/workProcess.ts`、`api/inspection.ts` |
| 小程序类型和扫码逻辑 | `src/types/scan.ts`、`src/utils/workOrderScan.ts`、`src/components/tab-bar/index.vue` |
| 小程序目标页面 | `src/pages/work-order/detail.vue`、`pages/work-process/index.vue`、`pages/inspection/index.vue` |
| 管理端 API/类型 | `bike-assembly-mes-web/src/api/proWorkOrder.ts`、`src/types/scan.ts` |
| 管理端组件/页面 | `src/components/WorkOrderQrDialog.vue`、`src/components/useWorkOrderQr.ts`、`src/views/production/workOrder.vue` |
| DTO/VO | `bike-assembly-pojo/src/main/java/com/bikeassembly/dto/ScanResolveDTO.java`、`vo/ScanResolveVO.java`、`vo/WorkOrderQrCodeVO.java` |
| 公共协议常量 | `bike-assembly-common/src/main/java/com/bikeassembly/constant/ScanCodeConstant.java` |
| Controller | server下 `controller/app/AppScanController.java`、`AppProWorkProcessController.java`、`AppQmsInspectionController.java`；`controller/admin/ProWorkOrderController.java` |
| Service/Impl | server下 `service/WorkOrderScanService.java`、`impl/WorkOrderScanServiceImpl.java`；`ProWorkProcessService/Impl`、`QmsInspectionService/Impl`；`impl/SysMenuServiceImpl.java` |
| Mapper | server下 `mapper/ProWorkOrderMapper.java`、`ProWorkProcessMapper.java`、`QmsInspectionMapper.java` |
| Mapper XML | `bike-assembly-server/src/main/resources/mapper/ProWorkOrderMapper.xml`、`ProWorkProcessMapper.xml`、`QmsInspectionMapper.xml` |
| 文档 | `docs/openapi.yaml`、已确认计划、本文和`docs/examples/work-order-scan/` |

新增 Service 使用Spring构造器注入，原有服务保持项目既有注入方式；Controller仅处理输入、调用Service和包装响应。Mapper新增方法都有对应参数化SQL，动态条件在XML中实现，分页由MyBatis-Plus执行。扫码不需要写事务；既有业务写事务继续由原Service负责。

数据库表：`pro_work_order`、`pro_work_process`、`qms_inspection`；权限读取涉及 `sys_user/sys_role/sys_role_menu/sys_menu`。辅助名称展示复用原有基础数据查询。本次没有建表、改字段或数据迁移SQL。

新增生产依赖仅Web的 `qrcode@1.5.4`，开发类型依赖 `@types/qrcode@1.5.6`；后端复用已有依赖。二维码在浏览器本地生成，不调用第三方图像服务。

## 自动验证结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 小程序类型检查 | 通过 | `.run/work-order-scan/miniapp-type-check.log` |
| 小程序Vitest | 7文件68项通过，含扫码、API、页面权限及逆序响应 | `miniapp-test.log` |
| Web类型检查和构建 | 通过 | `web-build-final.log` |
| Web Vitest | 7文件59项通过 | `web-test-retry.log` |
| 后端编译、测试、打包 | 相关测试100项：95通过，5跳过；打包通过且包含compile | `backend-tests-final.log`、`backend-package-final.log` |
| 本机MySQL Mapper | 6项通过；独立记录事务回滚，不重建表 | `WorkOrderScanLocalDatabaseTest`测试报告 |
| Testcontainers | 5项因本机无Docker跳过，不能计为已通过 | `WorkOrderScanMapperIntegrationTest`测试报告 |
| 真实接口联调 | 14项通过，含不同身份、无效码、不存在、403及扫码无写入 | `live-api-results.json` |
| 管理端真实浏览器 | 工单40展示/下载、关闭重查、切换工单46、390px布局通过 | `web-ui/web-ui-result.json`及截图 |
| 微信dev/build产物 | 两套分别重新编译，页面注册和扫码内容检查通过 | `miniapp-dev.log`、`miniapp-build.log`、`artifact-manifest.json` |
| 微信开发者工具dev实际页面 | 9项核心检查通过，工单详情/工序/质检真实渲染、错误留在首页，无App异常 | `devtools-dev/results.json`及截图 |
| 微信开发者工具build复验 | 已导入并完成登录路由；登录后自动化页面调用停滞，未完成全流程复验，不能计为通过 | `devtools-build-ui.log` |
| 取消扫码/摄像头拒绝 | 单元测试通过；开发者工具函数模拟未取得可靠证据，真实授权与识别需真机 | `workOrderScan.test.ts`、`miniapp-test.log` |
| QR图片独立解码 | 与真实工单协议内容一致 | `qr-decoding.json` |
| OpenAPI语法与diff空白检查 | 通过 | 交付时复核 |

执行要点：本机使用 `E:\JDK17` 和现有 Maven 3.9.14。Node 24高于项目声明的18至22范围，本机上述前端检查已通过；没有为此修改项目Node版本声明。

后端测试命令：

```powershell
$env:JAVA_HOME='E:\JDK17'
$env:MES_SCAN_LOCAL_DB_TEST='true'
& 'C:\Users\e7407\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -o -pl bike-assembly-server -am '-Dtest=WorkOrderScan*Test,ScopeSecurityTest,InspectionWorkOrderNumberTest,WechatBindingFlowTest,WechatConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`MES_SCAN_LOCAL_DB_TEST=true` 仅用于显式启用本机事务回滚验证；标准容器用例通过 `@DynamicPropertySource` 注入配置。未执行整个历史后端测试库，也未将跳过用例算作通过。

## 已验证与缺失接口清单

已验证：`GET /admin/pro-work-order/{id}/qr-code`、`POST /app/scan/resolve`、`GET /app/pro-work-order/list`、`GET /app/pro-work-order/{id}`、`GET /app/pro-work-process/list`、`GET /app/qms-inspection/list`。工序/质检页面名称参考查询继续复用原接口。

一期所需前后端接口缺失清单：无。真实相机、授权弹窗与真实微信绑定会话属于设备验收项，不是缺失的后端接口。

## 自查与审查

1. 总体评价：扫码生成、解析、授权和目标页面形成真实数据库闭环；已有微信身份绑定流程保留。
2. 严重问题：审查发现的他人已领取质检任务可见范围问题已修复，真实SQL权限矩阵通过。
3. 一般问题：两目标页旧响应覆盖问题已加请求版本保护；页面查看权限聚合遗漏已补齐。
4. 优化建议：本期不增加码记录表、签名凭据或额外业务模块。
5. 测试补充：已补扫码协议、角色路径、Mapper、Controller、权限聚合和页面竞态；Docker和真机边界如前述。
6. 建议修改方案：本次审查已完成必要修复，无未解决的代码阻断项，保留当前工作区供用户审阅，不自动提交。

## 微信开发者工具验收边界与剩余操作

本次实际页面验收在dev产物执行：通过WXML已绑定的输入/点击事件触发真实登录和扫码流程，模拟的仅是wx.scanCode识别结果及多入口选择；业务请求连接实际后端和MySQL。已查看工单40详情、该工单8道工序和质检单QC-20261024-0115的待检页面截图。未点击开工、报工、录入或确认。

build产物已重新生成并导入，自动化记录到真实登录后的首页路由；后续页面调用在多个独立自动化连接中停滞，未获取全流程通过结果。保留此限制，不用构建成功替代页面验收。开发者工具已重新打开通过验收的dev项目，便于继续使用。

仍需手工复核build项目：导入dist/build/mp-weixin，使用已分配的操作员和质检员账号，扫描工单40，分别确认详情、工序定位和待检定位；验证后再用于发布。真机继续检查实际相机识别、取消/授权拒绝与重新授权、微信已有账号绑定会话、HTTPS合法域名和打印扫码质量。

## 保留现有修改与风险边界

执行前保存了29个已有修改/新增文件的基线副本和哈希。仅在本次必要的质检页面、质检Service、OpenAPI及关联回归测试上增量修改；其他基线文件哈希保持一致，包括微信绑定、登录、认证类型和请求封装。没有回滚无关代码、提交Git或改动系统账号角色。

二维码是可复制的业务标识，访问能力始终取决于后端当前权限和归属。不同环境应生成自己数据库的码；本例工单状态或待检任务变化后，页面入口会随实际数据变化。

操作步骤、二维码及真机验收清单见 [示例说明](examples/work-order-scan/README.md)。开发者工具的模拟识别不能代替真实摄像头、授权弹窗、微信已有账号绑定会话、实际HTTPS域名和打印扫码质量的真机验证。
