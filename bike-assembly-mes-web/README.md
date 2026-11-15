# 自行车装配 MES 系统 - 前端

## 项目介绍

自行车装配 MES（Manufacturing Execution System）系统前端，基于 Vue 3 + Element Plus 构建的后台管理界面。提供系统管理、基础数据、生产执行、仓储库存、质量管理等核心业务模块。

## 技术栈

| 技术         | 版本     | 说明           |
| ------------ | -------- | -------------- |
| Vue          | 3.4+     | 渐进式前端框架 |
| Vite         | 5.4+     | 前端构建工具   |
| TypeScript   | 5.4+     | 强类型 JS 超集 |
| Element Plus | 2.7+     | UI 组件库      |
| Vue Router   | 4.3+     | 前端路由       |
| Pinia        | 2.1+     | 状态管理       |
| Axios        | 1.7+     | HTTP 请求库    |
| Sass         | 1.77+    | CSS 预处理器   |

## 如何启动

### 前置条件

- Node.js >= 18
- npm >= 9

### 安装依赖

```bash
cd bike-assembly-mes-web
npm install
```

### 开发模式运行

```bash
npm run dev
```

访问地址：`http://localhost:5173`

### 生产构建

```bash
npm run build
```

构建产物在 `dist/` 目录。

## 如何配置后端地址

编辑项目根目录下的 `.env.development` 文件：

```env
VITE_API_BASE_URL=http://localhost:8080
```

修改为你的后端实际运行地址即可。例如后端部署在 `192.168.1.100:9090`：

```env
VITE_API_BASE_URL=http://192.168.1.100:9090
```

### Vite 代理（可选）

开发模式下，Vite 已配置代理，将 `/admin` 前缀的请求转发到 `http://localhost:8080`。如需修改代理目标，编辑 `vite.config.ts` 中的 `server.proxy` 配置。

## 已实现页面列表

| 路径                     | 页面名称 | 功能说明                 |
| ------------------------ | -------- | ------------------------ |
| `/dashboard`             | 首页     | 统计卡片 + 系统信息      |
| `/system/user`           | 用户管理 | 用户 CRUD                |
| `/system/config`         | 参数配置 | 系统参数配置 CRUD        |
| `/base/item`             | 物料管理 | 物料（原材料/半成品等）  |
| `/base/equipment`        | 设备台账 | 设备台账管理             |
| `/production/work-order` | 工单管理 | 生产工单 CRUD            |
| `/warehouse/inventory`   | 库存管理 | 仓储库存管理             |
| `/quality/inspection`    | 质检单   | 质检单管理（IQC/FQC 等） |

所有页面均支持：

- 表格数据展示
- 条件搜索
- 新增记录（Dialog 表单）
- 编辑记录（Dialog 表单回填）
- 删除记录（确认弹窗）

## 后端接口约定

后端统一返回格式：

```json
{
  "code": 1,
  "msg": "success",
  "data": {}
}
```

- `code === 1` 表示成功
- `code !== 1` 表示业务失败
- `data` 为业务数据

各模块 CRUD 接口路径：

```text
GET    /admin/{module}/list    # 列表查询
GET    /admin/{module}/{id}    # 单条查询
POST   /admin/{module}         # 新增
PUT    /admin/{module}/{id}    # 编辑
DELETE /admin/{module}/{id}    # 删除
```

## 常见问题

### 1. 跨域问题

开发模式下，Vite 已配置代理将 `/admin` 请求转发至后端，一般不会出现跨域问题。

如果仍遇到跨域，可在后端添加 CORS 配置：

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("*")
            .allowCredentials(true);
    }
}
```

### 2. 接口 404

- 确认后端已启动且端口正确（默认 `8080`）
- 确认接口路径是否正确：`/admin/模块名/list`
- 检查 `.env.development` 中的 `VITE_API_BASE_URL` 配置

### 3. 后端未启动

后端启动命令（在后端项目根目录执行）：

```bash
./mvnw spring-boot:run
```

### 4. 页面数据为空

- 确认数据库已创建且表结构正确
- 确认 `application.yml` 中数据库连接配置正确
- 打开浏览器开发者工具（F12）查看 Network 面板，检查接口返回

### 5. npm install 失败

- 确认 Node.js 版本 >= 18
- 尝试清除 npm 缓存：`npm cache clean --force`
- 使用国内镜像：`npm config set registry https://registry.npmmirror.com`
