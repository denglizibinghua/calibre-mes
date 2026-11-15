# 自行车组装 MES 系统 — 前端 Web 管理端架构

> 生成日期：2026-07-25 | 关联文档：[[01-项目架构总览]]

---

## 1. 技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 (Composition API) | UI 框架 |
| TypeScript | 类型安全 |
| Vite | 构建工具 |
| Pinia | 状态管理 |
| Vue Router 4 | SPA 路由（动态路由） |
| Element Plus | UI 组件库 |
| Axios | HTTP 客户端 |
| SCSS | 样式预处理 |
| uni-app | 跨端兼容层（部分配置复用） |

---

## 2. 目录结构

```
bike-assembly-mes-web/src/
├── api/                    # 28 个 API 模块（HTTP 请求封装）
│   ├── index.ts            # 统一导出
│   ├── request.ts          # Axios 实例 + 拦截器
│   ├── auth.ts             # 登录/登出/Token刷新
│   ├── proWorkOrder.ts     # 工单 CRUD + 状态操作
│   ├── proWorkProcess.ts   # 工序报工 + 状态操作
│   ├── qmsInspection.ts    # 质检 CRUD + 确认
│   ├── baseItem.ts         # 物料管理
│   ├── baseBom.ts          # BOM 管理
│   ├── baseProcess.ts      # 工序定义
│   ├── baseRoute.ts        # 工艺路线
│   ├── baseEquipment.ts    # 设备管理
│   ├── baseWorkUnit.ts     # 工作单元
│   ├── wmsInventory.ts     # 库存管理
│   ├── wmsLocation.ts      # 库位管理
│   ├── wmsStockDoc.ts      # 库存单据
│   ├── andon.ts            # 安灯告警
│   ├── andonAlert.ts       #
│   ├── eqmMaintenance.ts   # 设备保养
│   ├── eqmRepairOrder.ts   # 设备维修
│   ├── safSafetyEvent.ts   # 安全事件
│   ├── safTraining.ts      # 安全培训
│   ├── sysUser.ts          # 用户管理
│   ├── sysRole.ts          # 角色管理
│   ├── sysMenu.ts          # 菜单管理
│   ├── sysConfig.ts        # 系统配置
│   ├── sysOperLog.ts       # 操作日志
│   ├── erpSyncLog.ts       # ERP同步日志
│   ├── permission.ts       # 权限/菜单接口
│   ├── report.ts           # 报表接口
│   └── reference.ts        # 引用数据
│
├── components/             # 共享组件
│   ├── PageContainer.vue   # 页面容器（标题+搜索+内容布局）
│   └── tab-bar/            # 底部导航栏（小程序用）
│
├── constants/              # 常量定义
│   ├── index.ts            # API_BASE_URL, STORAGE_KEYS, SUCCESS_CODE
│   └── statusMap.ts        # 状态码→中文+标签色 映射表
│
├── directives/             # 自定义指令
│   └── permission.ts       # v-permission 权限指令
│
├── layout/                 # 布局组件
│   ├── index.vue           # 主布局（侧边栏+顶栏+内容区）
│   └── SidebarMenuItem.vue # 递归菜单项组件
│
├── pages/                  # 移动端页面（uni-app 编译用）
│   ├── login/
│   ├── home/
│   ├── work-order/
│   ├── work-process/
│   ├── inspection/
│   ├── inventory/
│   ├── repair/
│   ├── andon/
│   ├── profile/
│   ├── account-bind/
│   └── security-block/
│
├── router/
│   └── index.ts            # 路由配置 + 动态路由加载
│
├── stores/                 # Pinia 状态管理
│   ├── user.ts             # 用户状态 + Token 管理
│   ├── permission.ts       # 权限 + 动态路由
│   └── tabBar.ts           # 底部导航状态
│
├── styles/
│   └── index.scss          # 全局样式
│
├── types/                  # TypeScript 类型定义
│   ├── api.ts              # 通用 API 类型 + 所有 Entity 接口
│   ├── permission.ts       # 菜单树/权限类型
│   ├── workOrder.ts        #
│   ├── workProcess.ts      #
│   ├── inspection.ts       #
│   ├── inventory.ts        #
│   ├── repair.ts           #
│   ├── andon.ts            #
│   └── reference.ts        #
│
├── utils/                  # 工具函数
│   ├── request.ts          # uni-app HTTP 请求封装
│   ├── cache.ts            # 离线缓存管理
│   ├── idempotencyKey.ts   # 幂等键生成/管理
│   ├── permission.ts       # 权限检查工具
│   └── network.ts          # 网络状态检测
│
├── views/                  # Web 管理端页面（35个 .vue 文件）
│   ├── dashboard/          # 仪表盘
│   ├── base/               # 基础数据（6个页面）
│   ├── production/         # 生产管理（7个页面）
│   ├── quality/            # 质量管理（3个页面）
│   ├── warehouse/          # 仓库管理（3个页面）
│   ├── report/             # 报表（5个页面）
│   ├── system/             # 系统管理（7个页面）
│   ├── error/              # 403/404 错误页
│   └── login/              # 登录页
│
├── App.vue                 # 根组件
├── main.ts                 # 应用入口
└── uni.scss                # uni-app 全局样式变量
```

---

## 3. 应用启动流程

```
main.ts
  │
  ├─ 1. createApp(App) → Vue 3 应用实例
  ├─ 2. app.use(createPinia()) → Pinia 状态管理
  ├─ 3. app.use(router) → Vue Router
  ├─ 4. app.use(ElementPlus, { locale: zhCn }) → UI 组件库
  ├─ 5. 全局注册 Element Plus Icons
  ├─ 6. app.directive('permission', permission) → v-permission 指令
  ├─ 7. 导入全局 SCSS 样式
  └─ 8. app.mount('#app')
```

---

## 4. 路由架构

### 4.1 路由守卫完整流程

```
router.beforeEach(to, from, next)
  │
  ├─ 1. 目标='/login'
  │     ├─ 已登录(token存在) → redirect '/' （已登录不重复登录）
  │     └─ 未登录 → next() (放行)
  │
  ├─ 2. 白名单('/login', '/403', '/404')
  │     └─ next() (放行)
  │
  ├─ 3. 无 token → redirect '/login?redirect=' + to.fullPath
  │
  ├─ 4. 安全检查
  │     ├─ userInfo.mustChangePassword || userInfo.passwordExpired
  │     └─ → 仅允许 '/change-password'，其他全部强制跳转
  │
  ├─ 5. 安全页面('/change-password') → next()
  │
  ├─ 6. 动态路由未加载
  │     ├─ permissionStore.loadPermissions()
  │     │     └─ GET /admin/user/menus → { menus, permissions }
  │     ├─ permissionStore.generateRoutes()
  │     │     └─ 菜单树 → Vue Router RouteRecordRaw[]
  │     │     └─ 使用 import.meta.glob('../views/**/*.vue') 解析组件路径
  │     │     └─ router.addRoute('Layout', eachRoute)
  │     ├─ 菜单树为空 → redirect '/403'
  │     ├─ 根路径 → redirect firstAccessibleRoute（优先 /dashboard）
  │     └─ next({ ...to, replace: true }) （重试导航）
  │
  └─ 7. 路由已加载 → next() (放行)
```

### 4.2 菜单树 → 路由转换规则

```
MenuTreeNode { menuType, path, component, children }
  │
  ├─ menuType='C' (菜单页面):
  │     └─ → 注册为 Vue Router 路由
  │           ├─ path: 解析后的路径
  │           ├─ component: import.meta.glob 解析
  │           │     └─ "views/production/workOrder.vue" → () => import('...')
  │           └─ meta: { title, icon, keepAlive, permission }
  │
  ├─ menuType='D' (目录):
  │     └─ 递归处理 children（不注册路由，仅用于侧边栏嵌套）
  │
  └─ menuType='F' (按钮):
        └─ 不显示在侧边栏，仅用于 v-permission 指令控制
```

---

## 5. 布局系统

### 5.1 主布局结构

```
Layout (layout/index.vue)
├── 侧边栏 (.layout-aside)
│   ├── Logo/品牌名（点击跳转首个可访问页面）
│   ├── "业务导航" 标签
│   ├── el-menu (router模式)
│   │   └── SidebarMenuItem (递归组件) × N
│   └── 底部状态指示器
│
├── 顶栏 (.layout-header)
│   ├── 侧边栏折叠按钮
│   ├── 面包屑/页面标题
│   └── 用户下拉菜单（修改密码 / 退出登录）
│
└── 内容区 (.layout-main)
    └── <router-view> (fade过渡)
```

### 5.2 递归菜单组件

```
SidebarMenuItem.vue (递归)
  │
  ├─ item.menuType='D' (目录)
  │     └─ 渲染 <el-sub-menu>
  │           └─ <SidebarMenuItem v-for="child in item.children">
  │
  ├─ item.menuType='C' 或 'M' (页面)
  │     └─ 渲染 <el-menu-item index="fullPath">
  │           └─ router 模式自动匹配激活
  │
  └─ item.menuType='F' (按钮)
        └─ 过滤掉（不显示在侧边栏）
```

---

## 6. HTTP 通信层

### 6.1 Axios 封装

```typescript
// api/request.ts
const service = axios.create({
  baseURL: VITE_API_BASE_URL,  // 默认 http://127.0.0.1:8080
  timeout: 15000,               // 15秒超时
})

// 请求拦截器
service.interceptors.request.use(config => {
  if (token) config.headers.token = token  // 附加 JWT
  return config
})

// 响应拦截器 (成功)
service.interceptors.response.use(response => {
  const { code, msg, data } = response.data  // ApiResult<T>
  if (code === 1) return data                // 成功, 解包返回
  ElMessage.error(msg)                       // 业务错误, 提示
  return Promise.reject(new Error(msg))
})

// 响应拦截器 (HTTP错误)
service.interceptors.response.use(undefined, error => {
  if (error.response.status === 401) {
    // 自动刷新 Token（并发请求队列）
    return refreshTokenQueue(enqueue)
  }
  if (error.response.status === 403) {
    // 安全检查 / 无权限
  }
  // 400/404/500 统一错误提示
})
```

### 6.2 Token 刷新队列（防并发重复刷新）

```
第一个 401 到达:
  ├─ isRefreshing = true
  ├─ 调用 POST /admin/user/refresh-token
  └─ 成功后：
       ├─ 更新 localStorage 中的 token
       ├─ 遍历 pendingRequests 队列 → resolve
       └─ isRefreshing = false

后续 401（在刷新期间到达）:
  └─ 加入 pendingRequests 队列 → 等待第一个刷新完成
```

---

## 7. 权限系统

### 7.1 三层权限架构

| 层级 | 组件 | 职责 |
|------|------|------|
| **路由层** | `router.beforeEach` + 动态路由 | 控制页面级访问 |
| **组件层** | `v-permission` 指令 | 控制按钮/元素显隐 |
| **API层** | `@RequirePermission` (后端) | 最终安全边界 |

### 7.2 权限指令实现

```typescript
// directives/permission.ts
app.directive('permission', {
  mounted(el, binding) {
    const required = binding.value  // 'admin:user:add' 或 ['perm1', 'perm2']
    const has = permissionStore.hasAnyPermission(required)
    if (!has) {
      el.parentNode?.removeChild(el)  // 从 DOM 中完全移除
    }
  }
})
```

使用示例：
```html
<el-button v-permission="'admin:user:add'">新增用户</el-button>
<el-button v-permission="['admin:user:edit', 'admin:user:update']">编辑</el-button>
```

### 7.3 权限码格式

```
格式: {domain}:{module}:{action}

admin 端:
  admin:user:view          # 查看用户
  admin:user:manage        # 管理用户
  admin:work-order:release # 下达工单
  admin:work-order:pause   # 暂停工单
  admin:inspection:confirm # 确认检验

app 端:
  app:work-order:view      # 查看工单
  app:work-process:start   # 开始工序
  app:work-process:report  # 工序报工

通配符:
  *:*:*                    # 管理员全部权限
```

---

## 8. 页面组件模式

### 8.1 标准 CRUD 页面结构

以 `views/production/workOrder.vue` 为例：

```vue
<template>
  <PageContainer title="工单管理" description="管理生产工单">
    <!-- 顶部操作栏 -->
    <template #header>
      <el-button v-permission="'admin:work-order:create'" @click="openCreate">
        新增工单
      </el-button>
    </template>

    <!-- 搜索栏 -->
    <template #search>
      <el-form :model="queryParams" inline>
        <el-form-item label="工单号">
          <el-input v-model="queryParams.workOrderNo" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" />
        </el-form-item>
        <el-form-item>
          <el-button @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </template>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="workOrderNo" label="工单号" />
      <el-table-column prop="itemName" label="产品名称" />
      <el-table-column label="状态">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 操作列（按钮受 v-permission 控制） -->
      <el-table-column label="操作">
        <el-button v-permission="'admin:work-order:release'"
                   @click="handleRelease(row)">下达</el-button>
        <el-button v-permission="'admin:work-order:pause'"
                   @click="handlePause(row)">暂停</el-button>
        <!-- ... -->
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="queryParams.page"
      v-model:page-size="queryParams.size"
      :total="total"
      :page-sizes="[15, 30, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" title="工单信息">
      <el-form ref="formRef" :model="form" :rules="rules">
        <!-- 表单项 -->
      </el-form>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
// 标准 Composition API 模式
const loading = ref(false)
const tableData = ref<ProWorkOrder[]>([])
const queryParams = reactive({ page: 1, size: 15, ... })
const total = ref(0)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getProWorkOrderList(queryParams)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
</script>
```

---

## 9. 状态映射表（前后端共用）

### 9.1 状态码标准化

```typescript
// constants/statusMap.ts

// 工单状态
export const WORK_ORDER_STATUS_MAP = {
  '0': { label: '已创建', tagType: 'info' },
  '1': { label: '生产中', tagType: '' },
  '2': { label: '已完工', tagType: 'success' },
  '3': { label: '已关闭', tagType: 'info' },
  '4': { label: '已下达', tagType: 'warning' },
  '5': { label: '已暂停', tagType: 'danger' },
  '6': { label: '已取消', tagType: 'info' },
}

// 工序状态 (10个状态)
export const WORK_PROCESS_STATUS_MAP = {
  '0': { label: '待开始', tagType: 'info' },
  '1': { label: '进行中', tagType: '' },
  '2': { label: '已完成', tagType: 'success' },
  '3': { label: '待检', tagType: 'warning' },
  '4': { label: '检验合格', tagType: 'success' },
  '5': { label: '检验不合格', tagType: 'danger' },
  '6': { label: '返工中', tagType: 'danger' },
  '7': { label: '已跳过', tagType: 'info' },
  '8': { label: '已取消', tagType: 'info' },
  '9': { label: '可开工', tagType: '' },
}

// QC结果
export const QC_RESULT_MAP = {
  '0': { label: '待检', tagType: 'info' },
  '1': { label: '合格', tagType: 'success' },
  '2': { label: '不合格', tagType: 'danger' },
  '3': { label: '待复检', tagType: 'warning' },
}
```

---

## 10. 数据流向总图

```
用户操作
  │
  ├─ 登录
  │   └─ POST /admin/user/login
  │       ├─ 成功 → localStorage[token, userInfo]
  │       ├─ userStore.login() → state
  │       └─ router.push('/') → 触发 beforeEach 守卫
  │
  ├─ 首次加载
  │   └─ router.beforeEach
  │       ├─ permissionStore.loadPermissions()
  │       │   └─ GET /admin/user/menus → { menus, permissions }
  │       ├─ permissionStore.generateRoutes()
  │       │   └─ 菜单树 → 动态路由注册
  │       └─ redirect → 首页
  │
  ├─ 页面渲染
  │   └─ Layout → SidebarMenuItem(递归) → 侧边栏
  │       └─ router-view → PageContainer → el-table/el-dialog
  │
  ├─ CRUD 操作
  │   └─ API function → axios → /admin/xxx
  │       ├─ 请求拦截器：附加 token header
  │       ├─ 后端：JWT校验 → 权限校验 → Service → Mapper → DB
  │       └─ 响应拦截器：解包 ApiResult → 成功/失败处理
  │
  ├─ 状态操作（幂等保护）
  │   └─ idempotencyKey = getOrCreate(actionKey)
  │       └─ API call + Idempotency-Key header
  │           ├─ 成功 → clear(actionKey)
  │           └─ 失败 → 保留 key 供重试
  │
  └─ Token 过期
      └─ 401 响应 → 自动刷新队列
          ├─ 成功 → 重放失败请求
          └─ 失败 → 清除状态 → redirect /login
```
