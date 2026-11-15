# 代码审查问题修复记录

日期：2026-10-24。范围：库存单据执行与编辑、生产/工单/质检幂等响应、失败重试与超时接管。

## 修复结果

| 原问题 | 最终行为 |
| --- | --- |
| 同一库存单据并发执行重复增减库存 | 执行、编辑、删除在 Service 事务中锁定同一单据行；后续请求读取提交后的状态，拒绝重复执行 |
| 普通 CRUD 可以伪造已执行状态或重置已执行单据 | 创建和编辑只接受草稿/已提交；已执行和已关闭单据禁止修改或删除 |
| LocalDateTime 导致幂等响应序列化失败 | 工序、工单、质检服务注入 Spring ObjectMapper；缓存保存失败抛业务异常并回滚 |
| 超时记录接管后仍立即过期，多请求同时接管 | 按 updated_at 计算租期；条件 SQL 原子接管；执行事务持有幂等记录行锁，防止活跃事务被超时接管 |

幂等成功快照与业务数据处于同一事务；回滚后独立记录失败，支持同键重试。服务端记录 ID 与客户端幂等键分开保存，包含 `::` 的合法键也可重放。内部 QC 回调参与父质检事务，避免覆盖父请求的结果快照。

## 涉及文件与接口

| 模块 | 前端 API | Controller | Service / Mapper | 数据表 |
| --- | --- | --- | --- | --- |
| 库存单据 | `src/api/wmsStockDoc.ts` | `WmsStockDocController` | `WmsStockDocServiceImpl`、`WmsStockDocMapper` / XML | `wms_stock_doc`、`wms_inventory`、`wms_stock_flow` |
| 生产执行 | `src/api/proWorkProcess.ts` | 原工序 Controller | `ProWorkProcessServiceImpl`、`IdempotencyServiceImpl`、`ProIdempotencyRecordMapper` / XML | `pro_work_process`、`pro_idempotency_record` |
| 工单 | `src/api/proWorkOrder.ts` | 原工单 Controller | `ProWorkOrderServiceImpl`、共享幂等服务 | `pro_work_order`、`pro_idempotency_record` |
| 质检 | `src/api/qmsInspection.ts` | 原质检 Controller | `QmsInspectionServiceImpl`、共享幂等服务 | `qms_inspection`、`pro_work_process`、`pro_idempotency_record` |

路径、请求/响应字段及前端调用不变，无数据库结构迁移。本次无新增或待补接口。CRUD 错误沿用现有 Result 及异常处理，新增明确提示“已执行或已关闭的库存单据不可修改或删除”“普通编辑仅允许草稿或已提交状态，请使用执行操作完成单据”；幂等结果保存失败提示“无法保存请求结果，请稍后重试”。

## 验证

最终 Maven 构建成功，**91 项测试通过，0 失败、0 错误、0 跳过**。其中新增真实 MySQL 回归 18 项，其他为已有生产流程、权限范围和状态机测试。

新增回归覆盖：入库/出库/移库各 6 请求并发只执行一次；已执行单据修改、重置、删除保护；正常草稿编辑删除；编辑与执行竞争；失败明细整体回滚；续租依据更新时间；过期和失败记录并发仅一个接管者；首次并发占位；成功快照随业务回滚；业务持锁期间禁止接管；旧版 hash 接口；部分报工重放及序列化失败回滚重试；质检确认内部回调及工单暂停重放；ThreadLocal 清理。

```powershell
.\mvnw.cmd -o -pl bike-assembly-server -am "-Dtest=ReviewFixIntegrationTest,Stage6ProductionFlowTest,ScopeSecurityTest,StatusTransitionValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

测试默认使用 Testcontainers MySQL 8；本机无 Docker，实际验证使用本地 MySQL 上随机命名的 `mes_review_<UUID>` 独立库，测试结束自动删除。复现本地方式时设置 `MES_REVIEW_MYSQL_URL=jdbc:mysql://localhost:3306/`、`MES_REVIEW_MYSQL_USER`、`MES_REVIEW_MYSQL_PASSWORD`。URL 必须指向服务器根路径，测试不会将现有业务库作为目标。

本次验证针对真实 Service/Mapper/事务边界，通知依赖被 mock；未执行 HTTP 端到端、MQ、ERP 或完整项目测试。`git diff --check` 通过。
