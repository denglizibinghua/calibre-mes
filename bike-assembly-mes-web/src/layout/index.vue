<template>
  <div class="layout-container" :class="{ 'is-sidebar-collapsed': collapsed, 'is-production-screen': route.path === '/dashboard', 'is-mobile-menu-open': mobileMenuOpen }">
    <div v-if="mobileMenuOpen" class="mobile-backdrop" @click="closeMobileMenu" />
    <aside class="layout-aside">
      <button class="brand" type="button" aria-label="返回系统首页" @click="goHome">
        <span class="brand-mark"><BrandMark :size="36" /></span>
        <span class="brand-copy">
          <strong>自行车装配 MES</strong>
          <small>CALIBRE · MANUFACTURING</small>
        </span>
      </button>

      <div class="sidebar-section-label">
        <span class="label-line" aria-hidden="true" />
        业务导航
      </div>

      <div class="sidebar-menu-scroll">
        <el-menu
          :default-active="activeMenu"
          :collapse="collapsed"
          :collapse-transition="false"
          :unique-opened="true"
          router
        >
          <SidebarMenuItem
            v-for="menu in sidebarMenus"
            :key="menu.menuId"
            :menu="menu"
          />
        </el-menu>
      </div>

      <div class="sidebar-footer">
        <span class="status-dot" />
        <span class="sidebar-footer-copy">
          <strong>系统运行正常</strong>
          <small>SYSTEM ONLINE / 服务在线</small>
        </span>
        <span class="sidebar-footer-code">V4.0</span>
      </div>
    </aside>

    <section class="layout-shell">
      <header class="layout-header">
        <div class="header-left">
          <button class="collapse-button" type="button" aria-label="切换侧边栏" @click="toggleSidebar">
            <el-icon :size="17">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </button>

          <div class="header-title-group">
            <div class="breadcrumb-line">
              <span class="breadcrumb-root">MES CONSOLE</span>
              <span class="breadcrumb-sep">/</span>
              <strong>{{ currentTitle }}</strong>
            </div>
            <span class="header-subtitle">生产制造全流程数字化管理</span>
          </div>
        </div>

        <div class="header-right">
          <div class="header-clock" aria-hidden="true">
            <strong>{{ clockTime }}</strong>
            <span>{{ clockDate }}</span>
          </div>

          <div class="header-status">
            <span class="status-dot" />
            <span>LIVE</span>
          </div>

          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user-menu-trigger" type="button">
              <span class="user-avatar">{{ avatarText }}</span>
              <span class="user-copy">
                <strong>{{ nickname }}</strong>
                <small>{{ username || '系统用户' }}</small>
              </span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="changePassword">
                  <el-icon><Lock /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item command="faceSecurity">
                  <el-icon><Lock /></el-icon>
                  人脸登录设置
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="layout-main">
        <router-view v-slot="{ Component, route: currentRoute }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="currentRoute.fullPath" />
          </transition>
        </router-view>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import BrandMark from '@/components/BrandMark.vue'
import SidebarMenuItem from './SidebarMenuItem.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const collapsed = ref(false)
const mobileMenuOpen = ref(false)
let hasInitializedResponsive = false

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => String(route.meta.title || '工作台'))
const nickname = computed(() => userStore.nickname)
const username = computed(() => userStore.username)
const sidebarMenus = computed(() => permissionStore.sidebarMenus)
const avatarText = computed(() => (nickname.value || username.value || '用').trim().slice(0, 1).toUpperCase())

const now = ref(new Date())
let clockTimer: number | undefined
const clockTime = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' }).format(now.value)
)
const clockDate = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', weekday: 'short' }).format(now.value)
)

function syncResponsiveState() {
  if (!hasInitializedResponsive) {
    collapsed.value = window.innerWidth < 1180
    hasInitializedResponsive = true
    return
  }

  if (window.innerWidth < 768) {
    collapsed.value = true
  }

  if (window.innerWidth > 640) {
    mobileMenuOpen.value = false
  }
}

onMounted(() => {
  syncResponsiveState()
  window.addEventListener('resize', syncResponsiveState)
  clockTimer = window.setInterval(() => { now.value = new Date() }, 1000)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncResponsiveState)
  if (clockTimer) window.clearInterval(clockTimer)
})

function toggleSidebar() {
  if (window.innerWidth <= 640) {
    mobileMenuOpen.value = !mobileMenuOpen.value
    if (mobileMenuOpen.value) collapsed.value = false
    return
  }
  collapsed.value = !collapsed.value
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

watch(() => route.path, () => { mobileMenuOpen.value = false })

function goHome() {
  const firstRoute = permissionStore.getFirstAccessibleRoute()
  router.push(firstRoute || '/')
}

function handleUserCommand(command: string) {
  if (command === 'faceSecurity') {
    router.push('/account/security')
    return
  }
  if (command === 'changePassword') {
    router.push('/change-password')
    return
  }

  if (command === 'logout') {
    handleLogout()
  }
}

/**
 * 本地退出立即生效；远端使用捕获的凭据尽力撤销会话。
 */
async function handleLogout() {
  const token = localStorage.getItem('token')
  userStore.logout()
  permissionStore.resetPermission()
  ElMessage.success('已退出登录')
  router.replace('/login')

  try {
    const axios = (await import('axios')).default
    const url = new URL(import.meta.env.VITE_API_BASE_URL || '/', window.location.origin + '/')
    url.pathname = url.pathname.replace(/\/+$/, '') + '/admin/user/logout'
    url.search = ''
    url.hash = ''
    await axios.post(url.toString(), null, {
      headers: token ? { token } : {},
      timeout: 10000
    })
  } catch {
    /* 后端登出失败或超时不影响已经完成的本地退出 */
  }
}
</script>

<style scoped lang="scss">
.layout-container {
  display: flex;
  width: 100%;
  height: 100vh;
  min-width: 0;
  overflow: hidden;
  background: var(--lux-ink-950);
}

/* ---------------------------------------------------------------------
   Sidebar
   --------------------------------------------------------------------- */
.layout-aside {
  position: relative;
  z-index: 20;
  display: flex;
  flex: 0 0 var(--mes-sidebar-width);
  flex-direction: column;
  width: var(--mes-sidebar-width);
  min-width: 0;
  overflow: hidden;
  color: var(--lux-shell-text);
  background:
    radial-gradient(120% 55% at 0 0, rgb(198 169 110 / 10%), transparent 58%),
    linear-gradient(180deg, #0b0f15 0%, #070a0f 100%);
  border-right: 1px solid var(--lux-shell-line);
  transition: width var(--mes-transition), flex-basis var(--mes-transition);
}

.layout-aside::before {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgb(198 169 110 / 3.5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(198 169 110 / 3.5%) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(120% 70% at 0 0, #000 0%, transparent 72%);
  content: '';
  pointer-events: none;
}

.layout-aside::after {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 6px;
  background-image: repeating-linear-gradient(
    to bottom,
    rgb(198 169 110 / 30%) 0,
    rgb(198 169 110 / 30%) 1px,
    transparent 1px,
    transparent 8px
  );
  mask-image: linear-gradient(to bottom, transparent, #000 12%, #000 88%, transparent);
  content: '';
  pointer-events: none;
}

.brand {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  width: 100%;
  height: 82px;
  padding: 0 22px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--lux-shell-line);
  cursor: pointer;
}

.brand-mark {
  display: inline-flex;
  flex: 0 0 38px;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: #ded7c6;
  --brand-accent: var(--lux-gold-500);
}

.brand-copy {
  display: flex;
  min-width: 0;
  margin-left: 12px;
  overflow: hidden;
  flex-direction: column;
  white-space: nowrap;
  transition: opacity var(--mes-transition), width var(--mes-transition), margin var(--mes-transition);

  strong {
    overflow: hidden;
    color: #f4f0e5;
    font-family: var(--lux-font-display);
    font-size: 15px;
    font-weight: 500;
    letter-spacing: 0.04em;
    text-overflow: ellipsis;
  }

  small {
    margin-top: 4px;
    color: #6e6a60;
    font-family: var(--lux-font-data);
    font-size: 8px;
    letter-spacing: 0.2em;
  }
}

.sidebar-section-label {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 22px 24px 8px;
  color: #6e6a60;
  font-family: var(--lux-font-data);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  white-space: nowrap;
  transition: opacity var(--mes-transition);
}

.label-line {
  width: 18px;
  height: 1px;
  background: var(--lux-gold-600);
}

.sidebar-menu-scroll {
  position: relative;
  z-index: 2;
  flex: 1;
  min-height: 0;
  padding: 4px 12px 18px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgb(198 169 110 / 24%) transparent;
}

.sidebar-menu-scroll::-webkit-scrollbar {
  width: 4px;
}

.sidebar-menu-scroll::-webkit-scrollbar-thumb {
  background: rgb(198 169 110 / 24%);
}

:deep(.el-menu) {
  width: 100%;
  background: transparent;
  border-right: 0;
}

:deep(.el-menu--collapse) {
  width: 100%;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  position: relative;
  height: 44px;
  margin: 2px 0;
  padding-right: 14px;
  color: #a9a395;
  font-size: 13.5px;
  letter-spacing: 0.02em;
  border-radius: var(--lux-radius-xs);
  transition:
    color var(--mes-transition-fast),
    background-color var(--mes-transition-fast);
}

:deep(.el-menu-item .el-icon),
:deep(.el-sub-menu__title .el-icon) {
  width: 18px;
  margin-right: 11px;
  color: #7e7a70;
  font-size: 17px;
  transition: color var(--mes-transition-fast);
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  color: #f1ecdf;
  background: rgb(255 255 255 / 4.5%);
}

:deep(.el-menu-item:hover .el-icon),
:deep(.el-sub-menu__title:hover .el-icon) {
  color: #c6a96e;
}

:deep(.el-menu-item.is-active) {
  color: #f7f2e6;
  font-weight: 500;
  background: linear-gradient(90deg, rgb(198 169 110 / 18%), rgb(198 169 110 / 2%));
  box-shadow: inset 2px 0 0 var(--lux-gold-500);
}

:deep(.el-menu-item.is-active::after) {
  position: absolute;
  top: 50%;
  right: 14px;
  width: 4px;
  height: 4px;
  background: var(--lux-gold-500);
  content: '';
  transform: translateY(-50%) rotate(45deg);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: var(--lux-gold-400);
}

:deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  height: 40px;
  padding-left: 46px !important;
  color: #8f8a7f;
  font-size: 13px;
  background: transparent;
}

:deep(.el-sub-menu .el-menu-item.is-active) {
  color: #f7f2e6;
  background: linear-gradient(90deg, rgb(198 169 110 / 16%), transparent);
}

:deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  color: #e7e1d5;
}

.sidebar-footer {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  min-height: 62px;
  margin: 0 14px 16px;
  padding: 12px 14px;
  background: rgb(255 255 255 / 2.5%);
  border: 1px solid var(--lux-shell-line);
  border-radius: var(--lux-radius-xs);
  white-space: nowrap;
  transition: padding var(--mes-transition), margin var(--mes-transition);
}

.status-dot {
  display: inline-block;
  flex: 0 0 7px;
  width: 7px;
  height: 7px;
  background: var(--lux-jade-400);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgb(63 143 121 / 14%);
  animation: lux-pulse 2.4s ease-in-out infinite;
}

.sidebar-footer-copy {
  display: flex;
  min-width: 0;
  margin-left: 11px;
  overflow: hidden;
  flex-direction: column;
  transition: opacity var(--mes-transition), width var(--mes-transition), margin var(--mes-transition);

  strong {
    color: #dcd6c8;
    font-size: 11.5px;
    font-weight: 500;
  }

  small {
    margin-top: 3px;
    color: #6e6a60;
    font-family: var(--lux-font-data);
    font-size: 8px;
    letter-spacing: 0.14em;
  }
}

.sidebar-footer-code {
  margin-left: auto;
  padding-left: 8px;
  color: var(--lux-gold-600);
  font-family: var(--lux-font-data);
  font-size: 9px;
  letter-spacing: 0.14em;
}

/* ---------------------------------------------------------------------
   Shell
   --------------------------------------------------------------------- */
.layout-shell {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.layout-header {
  position: relative;
  z-index: 10;
  display: flex;
  flex: 0 0 var(--mes-header-height);
  align-items: center;
  justify-content: space-between;
  height: var(--mes-header-height);
  padding: 0 26px;
  background: rgb(7 10 15 / 94%);
  border-bottom: 1px solid var(--lux-shell-line);
  backdrop-filter: blur(14px);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  min-width: 0;
}

.collapse-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-right: 16px;
  color: #a9a395;
  background: transparent;
  border: 1px solid var(--lux-shell-line);
  border-radius: var(--lux-radius-xs);
  transition: color var(--mes-transition-fast), border-color var(--mes-transition-fast), background-color var(--mes-transition-fast);
}

.collapse-button:hover {
  color: var(--lux-gold-400);
  background: rgb(255 255 255 / 3%);
  border-color: var(--lux-shell-line-strong);
}

.header-title-group {
  min-width: 0;
}

.breadcrumb-line {
  display: flex;
  align-items: center;
  min-width: 0;
  white-space: nowrap;

  .breadcrumb-root {
    color: #6e6a60;
    font-family: var(--lux-font-data);
    font-size: 9px;
    letter-spacing: 0.2em;
  }

  .breadcrumb-sep {
    margin: 0 8px;
    color: #4f4b43;
    font-family: var(--lux-font-data);
    font-size: 9px;
  }

  strong {
    overflow: hidden;
    color: #ede8dc;
    font-family: var(--lux-font-display);
    font-size: 15px;
    font-weight: 500;
    letter-spacing: 0.03em;
    text-overflow: ellipsis;
  }
}

.header-subtitle {
  display: block;
  margin-top: 4px;
  color: #7e7a70;
  font-size: 10px;
  letter-spacing: 0.06em;
}

.header-right {
  gap: 14px;
  margin-left: 18px;
}

.header-clock {
  display: flex;
  flex-direction: column;
  text-align: right;

  strong {
    color: #dcd6c8;
    font-family: var(--lux-font-data);
    font-size: 14px;
    font-weight: 400;
    letter-spacing: 0.1em;
    line-height: 1.2;
  }

  span {
    margin-top: 2px;
    color: #6e6a60;
    font-family: var(--lux-font-data);
    font-size: 9px;
    letter-spacing: 0.12em;
  }
}

.header-status {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  color: #7fbfa9;
  font-family: var(--lux-font-data);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.18em;
  background: rgb(63 143 121 / 8%);
  border: 1px solid rgb(63 143 121 / 18%);
  border-radius: var(--lux-radius-xs);
}

.header-status .status-dot {
  animation: none;
  box-shadow: none;
}

.user-menu-trigger {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 4px 6px 4px 4px;
  color: inherit;
  background: transparent;
  border: 0;
  border-radius: var(--lux-radius-xs);
  cursor: pointer;
  transition: background-color var(--mes-transition-fast);
}

.user-menu-trigger:hover {
  background: rgb(255 255 255 / 4%);
}

.user-avatar {
  display: inline-flex;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--lux-gold-300);
  font-family: var(--lux-font-data);
  font-size: 13px;
  font-weight: 600;
  background: linear-gradient(145deg, #1b1710, #0f0c08);
  border: 1px solid rgb(198 169 110 / 45%);
  border-radius: 3px;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 4%);
}

.user-copy {
  display: flex;
  min-width: 0;
  margin: 0 10px;
  flex-direction: column;
  text-align: left;

  strong {
    max-width: 120px;
    overflow: hidden;
    color: #ede8dc;
    font-size: 12.5px;
    font-weight: 500;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    max-width: 120px;
    margin-top: 2px;
    overflow: hidden;
    color: #7e7a70;
    font-family: var(--lux-font-data);
    font-size: 9px;
    letter-spacing: 0.08em;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.user-arrow {
  color: #6e6a60;
  font-size: 11px;
}

.layout-main {
  flex: 1;
  min-width: 0;
  padding: 26px 28px;
  overflow-x: hidden;
  overflow-y: auto;
  background-color: var(--lux-porcelain-100);
  background-image:
    radial-gradient(120% 46% at 50% 0%, rgb(198 169 110 / 8%), transparent 62%),
    linear-gradient(rgb(20 24 31 / 2.6%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(20 24 31 / 2.6%) 1px, transparent 1px);
  background-size: auto, 40px 40px, 40px 40px;
}

.is-production-screen {
  .layout-main {
    padding: 0;
    background-color: var(--lux-ink-950);
    background-image: none;
  }
}

/* ---------------------------------------------------------------------
   Collapsed
   --------------------------------------------------------------------- */
.is-sidebar-collapsed {
  .layout-aside {
    flex-basis: var(--mes-sidebar-collapsed-width);
    width: var(--mes-sidebar-collapsed-width);
  }

  .brand {
    justify-content: center;
    padding: 0;
  }

  .brand-copy,
  .sidebar-section-label,
  .sidebar-footer-copy,
  .sidebar-footer-code {
    width: 0;
    margin: 0;
    opacity: 0;
  }

  .sidebar-menu-scroll {
    padding-right: 10px;
    padding-left: 10px;
  }

  .sidebar-footer {
    justify-content: center;
    min-height: 42px;
    margin-right: 10px;
    margin-left: 10px;
    padding: 10px;
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    justify-content: center;
    padding: 0 !important;
  }

  :deep(.el-menu-item .el-icon),
  :deep(.el-sub-menu__title .el-icon) {
    margin: 0;
  }

  :deep(.el-menu-item.is-active::after) {
    display: none;
  }
}

/* ---------------------------------------------------------------------
   Page transition
   --------------------------------------------------------------------- */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

/* ---------------------------------------------------------------------
   Responsive
   --------------------------------------------------------------------- */
@media (max-width: 1100px) {
  .header-clock {
    display: none;
  }
}

@media (max-width: 900px) {
  .layout-header {
    padding: 0 18px;
  }

  .layout-main {
    padding: 18px;
  }

  .header-status,
  .header-subtitle {
    display: none;
  }
}

@media (max-width: 640px) {
  .layout-aside {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 40;
    width: var(--mes-sidebar-width);
    flex-basis: var(--mes-sidebar-width);
    box-shadow: 18px 0 50px rgb(0 0 0 / 38%);
    transform: translateX(-100%);
    transition: transform var(--mes-transition);
  }

  .is-mobile-menu-open .layout-aside {
    transform: translateX(0);
  }

  .mobile-backdrop {
    position: fixed;
    inset: 0;
    z-index: 35;
    background: rgb(3 5 8 / 58%);
    backdrop-filter: blur(2px);
  }

  .layout-main {
    padding: 12px;
  }

  .layout-header {
    padding: 0 12px;
  }

  .header-title-group {
    display: none;
  }

  .header-right {
    margin-left: auto;
  }

  .collapse-button {
    margin-right: 0;
  }

  .user-copy {
    display: none;
  }

  .user-arrow {
    margin-left: 4px;
  }
}
</style>
