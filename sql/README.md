# 数据库脚本说明（PostgreSQL 16）

更新：2026-11-15。支持全新环境初始化，以及保留现有身份配置的业务演示数据替换。演示数据是合成场景，不是真实生产记录。

| 文件 | 用途 |
| --- | --- |
| `init_database.sql` | 全量重建 32 张应用表及系统种子数据；会重置用户权限，只用于新环境 |
| `dev_seed_full.sql` | 保留七张身份配置表，事务清理基础与业务资料后重建一致性演示数据（生成文件） |
| `verify_database.sql` | 41 项结构与业务关系校验；非零违规数会触发异常（生成文件） |
| `demo-seed-manifest.json` | 生成后的逐表数据量、保留范围及校验数量 |
| `patch_ai_*.sql` | AI 相关增量补丁，可重复执行 |
| `pg-migration-notes.md` | MySQL 8 → PostgreSQL 16 类型映射与迁移说明 |
| `test/` | 独立测试场景脚本，不属于正常业务初始化链 |

## 执行命令（psql / ON_ERROR_STOP）

```bash
# 容器内执行（示例：xiaoxueqi-pgvector-1 / bike_assembly_mes）
docker cp sql/init_database.sql xiaoxueqi-pgvector-1:/tmp/init_database.sql
docker exec xiaoxueqi-pgvector-1 psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f /tmp/init_database.sql

# 补丁（可重复执行）
docker exec xiaoxueqi-pgvector-1 psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f /tmp/patch_ai_reliability_v1.sql
docker exec xiaoxueqi-pgvector-1 psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f /tmp/patch_ai_permissions_v1.sql
docker exec xiaoxueqi-pgvector-1 psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f /tmp/patch_ai_phase2.sql

# 演示数据 + 校验
docker exec xiaoxueqi-pgvector-1 psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f /tmp/dev_seed_full.sql
docker exec xiaoxueqi-pgvector-1 psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f /tmp/verify_database.sql

# 本机 psql 等价写法
psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f sql/init_database.sql
```

执行顺序固定为：`init_database.sql` → 三个 `patch_ai_*.sql` → `dev_seed_full.sql` → `verify_database.sql`。不要使用 `psql` 的 `--no-error-exit` 之类忽略错误；数据事务失败须回滚。

## 已有环境保留账号替换

先备份并停止后端写入，执行 `dev_seed_full.sql` → `verify_database.sql` 后恢复后端。不要运行 `init_database.sql`。数据事务失败会整体回滚。

保留 `sys_user`、`sys_role`、`sys_menu`、`sys_role_menu`、`sys_config`、`sys_user_password_history`、`user_refresh_token`；账号查找依据启用角色，不依赖固定用户 ID，不重置密码。后台任务、MQ、ERP 和旧幂等记录不预造成功数据。

## 全新环境

先创建数据库（UTF8），再依次执行 `init_database.sql` → 三个补丁 → `dev_seed_full.sql` → `verify_database.sql`。默认开发账号仍由初始化文件创建，既有环境的替换不会覆盖这些账号的自定义信息。

## 维护与测试

使用 `node scripts/data/generate-demo-data.cjs` 重新生成业务种子 SQL、校验 SQL 和数据量清单；生成器在 JavaScript 中计算全部时间，输出字面量时间戳，生成文件不含 SQL 日期函数与会话变量。修改后运行 `DemoSeedIntegrationTest`。测试默认使用 Testcontainers；测试脚本见 `test/reset_test_database.sql` 与 `test/seed_production_flow.sql`。完整命令见 [重置与验证指南](../docs/数据库重置与验证指南.md)，场景入口见 [演示数据说明](../docs/演示数据说明.md)。
