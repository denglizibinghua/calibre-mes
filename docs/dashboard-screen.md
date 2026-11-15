# 生产运营大屏

入口：现有 `/dashboard` 首页。保留原业务菜单、登录及权限；该路由沿用原系统蓝白科技风，复用现有主蓝色、浅灰页面背景、白色卡片、圆角与字体。实现为 Vue 3 + TypeScript + Element Plus + 按需加载 ECharts 5。工位插图为原创 SVG，无第三方项目源码或图片复制。

## 接口闭环

| 层 | 文件 |
| --- | --- |
| 前端 API / 类型 | `bike-assembly-mes-web/src/api/dashboard.ts` |
| 页面 / 请求生命周期 | `bike-assembly-mes-web/src/views/dashboard/index.vue`、`useProductionScreen.ts` |
| 图表 / 工序 | `views/dashboard/chartOptions.ts`、`components/ScreenChart.vue`、`components/AssemblyFlow.vue` |
| Controller | `bike-assembly-server/src/main/java/com/bikeassembly/controller/admin/DashboardScreenController.java` |
| Service / Impl | `service/DashboardScreenService.java`、`service/impl/DashboardScreenServiceImpl.java` |
| Mapper / SQL | `mapper/DashboardScreenMapper.java`、`src/main/resources/mapper/DashboardScreenMapper.xml` |
| DTO / VO | `bike-assembly-pojo/src/main/java/com/bikeassembly/dto/DashboardScreenQueryDTO.java`、`vo/DashboardScreenVO.java` |

新增 `GET /admin/dashboard/screen?days=7&workUnitId=1`：

- `days`：1–30 的整数，默认 7；页面提供 7、14、30 天切换。
- `workUnitId`：可选正整数，包含所选启用单元的启用子单元。未知 ID 返回空统计；无筛选返回账号权限内数据。
- 沿用 `Result` 响应封装，返回 `generatedAt/startDate/endDate/dataScope/summary/equipment/quality/trend/processes/workOrders/alerts/defects/workUnits`。
- 登录失效 401；密码安全要求仍由原拦截器处理；参数范围错误或类型错误返回 400 和可读中文提示；数据库等未知错误沿用全局友好异常处理。
- 原 `GET /admin/dashboard/overview` 保持兼容；前端各新板块均使用新接口，没有未接入的新增接口。

## 数据表与口径

使用现有 `pro_work_order`、`pro_work_process`、`base_item`、`base_process`、`base_work_unit`、`base_equipment`、`qms_inspection`、`qms_defect`、`andon_alert`。**无需建表、改表或导入演示数据。**

- 时区：Asia/Shanghai。实际完工、初检、缺陷与告警截至请求时刻；计划完工按日期统计，含今日全天计划。
- 在制：生产中、已下达、已暂停；逾期：在制且计划结束时间早于请求时刻。
- 完工趋势：按工单实际结束日期计数，包含已完工、已关闭工单；不将同一产品在多道工序的报工数量累加为产量。
- 计划趋势：按非草稿、非取消工单的计划结束日期计数。计划曲线与实际曲线采用同一单位“工单”。
- 初检合格率：统计期内已确认、初检轮次为 1、结果 P/F 的检验单中，P 所占比例；复检、待确认记录不计入。无样本显示“—”，不显示虚假的 100%。
- 工序监控：当前在制工单的非取消工序，按工序汇总负荷。序号按最小工艺顺序排列，属于负荷示意，非物理车间布局或设备遥测。
- 工单进度：完成及跳过工序 / 非取消工序。重点工单按逾期、暂停优先，在 SQL 中限制最多 12 条；搜索和状态标签仅筛选当前列表。
- 设备可用率：启用且正常的设备 / 启用设备，不含停用设备；不是 OEE、实时运行率或设备利用率。
- 不良原因：缺陷记录数，最多 5 类，不等同于不良产品数量；未关闭告警最多展示 8 条，按等级和等待时间排序。

ALL 且存在当前身份时可见全部数据。其他范围按 SELF 处理，仅查询本人指派工单及关联生产/质量记录；安灯可见本人上报、处理或本人指派工单的记录；共享设备仅展示本人工单用到的设备。缺失身份使用不可匹配的用户 ID，避免空 ID 误放行。

## 页面行为

30 秒轮询、手动刷新、暂停及全屏。隐藏标签页暂停轮询，返回时按需要刷新；离开页面清理定时器、ResizeObserver 和图表实例。筛选请求存在竞态时只应用最新结果。

成功数据与成功筛选快照同时保存；失败保留上次数据，明确显示原生产单元和时间范围，并提示新筛选尚未生效。业务入口按准确菜单权限标识查找，无法访问时隐藏。直接访问及刷新动态路由会先加载菜单，再判断是否为 404。

## 验证

后端：9 项通过，包括真实 MySQL 查询、事务回滚夹具、三级单元筛选、跨用户隔离、初检/复检区分、未来记录排除、零分母及 HTTP 400 绑定错误。测试使用当前开发 MySQL，均自动回滚，不依赖测试顺序。

```powershell
.\mvnw.cmd -pl bike-assembly-server -am "-Dtest=DashboardScreenControllerTest,DashboardScreenIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

前端：6 项通过，覆盖请求竞态、离线保留数据、筛选失败后归属、暂停/清理和精确权限导航；TypeScript 与生产构建通过。

```powershell
cd bike-assembly-mes-web
npm test -- src/views/dashboard/__tests__
npm run build
```

Edge/Playwright 使用文档中的开发账号验证真实 API、7/14/30 天筛选、生产单元、初检无数据、工序选择、工单搜索与详情、刷新暂停、失败重试、全屏及 390px 手机布局；无 JavaScript 运行错误、无页面横向溢出。截图及 `verification.json` 位于本次 Codex 可视化目录的 `production-screen` 文件夹。

范围说明：本次接入生产及质量统计、当前台账设备状态；班次分析、OEE 与真实 3D 车间需要相应班次/采集/空间数据，本次未虚构这些数据。
