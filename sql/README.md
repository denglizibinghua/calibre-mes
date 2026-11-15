# 数据库脚本说明

更新：2026-10-24。支持全新环境初始化，以及保留现有身份配置的业务演示数据替换。演示数据是合成场景，不是真实生产记录。

| 文件 | 用途 |
| --- | --- |
| `init_database.sql` | 全量重建 32 张应用表及系统种子数据；会重置用户权限，只用于新环境 |
| `dev_seed_full.sql` | 保留七张身份配置表，事务清理基础与业务资料后重建一致性演示数据 |
| `verify_database.sql` | 41 项结构与业务关系校验；非零违规数会抛 SQL 错误 |
| `demo-seed-manifest.json` | 生成后的逐表数据量、保留范围及校验数量 |
| `test/` | 独立测试场景脚本，不属于正常业务初始化链 |

## 已有环境保留账号替换

先备份并停止后端写入，执行 `dev_seed_full.sql` → `verify_database.sql` 后恢复后端。不要运行 `init_database.sql`，也不要使用 mysql 的 `--force` 忽略失败。数据事务失败须回滚；待检时间字段的结构调整在数据事务前执行。

保留 `sys_user`、`sys_role`、`sys_menu`、`sys_role_menu`、`sys_config`、`sys_user_password_history`、`user_refresh_token`；账号查找依据启用角色，不依赖固定用户 ID，不重置密码。后台任务、MQ、ERP 和旧幂等记录不预造成功数据。

## 全新环境

先创建数据库，再依次执行 `init_database.sql` → `dev_seed_full.sql` → `verify_database.sql`。默认开发账号仍由初始化文件创建，既有环境的替换不会覆盖这些账号的自定义信息。

## 维护与测试

使用 `node scripts/data/generate-demo-data.cjs` 重新生成业务种子 SQL、校验 SQL 和数据量清单，修改后运行 `DemoSeedIntegrationTest`。测试默认使用 Testcontainers；本地 MySQL 回退方案自动创建、销毁独立测试库。完整命令见 [重置与验证指南](../docs/数据库重置与验证指南.md)，场景入口见 [演示数据说明](../docs/演示数据说明.md)。
