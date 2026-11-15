<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

onLaunch(() => {
  console.log('App Launch')
})

onShow(() => {
  console.log('App Show')
})

onHide(() => {
  console.log('App Hide')
})
</script>

<style>
/* =====================================================================
   CALIBRE · Design Tokens（WXSS 无 :root，page 即根；App.vue 未加 scoped，
   编译进 app.wxss 后由全部页面继承）
   黑曜石 / 瓷白 / 香槟金 —— 精密制造美学
   ===================================================================== */
page {
  /* ---------- Obsidian shell ---------- */
  --lux-ink-950: #070A0F;
  --lux-ink-900: #14181F;
  --lux-ink-800: #11161F;
  --lux-ink-700: #1A212C;
  --lux-shell-text: #EDE8DC;
  --lux-shell-muted: #8B857A;
  --lux-shell-line: rgba(198, 169, 110, 0.14);
  --lux-shell-line-strong: rgba(198, 169, 110, 0.26);

  /* ---------- Porcelain workspace ---------- */
  --lux-porcelain-50: #FAF8F3;
  --lux-porcelain-100: #F3EFE6;
  --lux-porcelain-200: #EDE7D9;
  --lux-porcelain-300: #D9D2C2;
  --lux-surface: #FFFDF9;
  --lux-surface-soft: #FAF8F3;

  /* ---------- Champagne gold ---------- */
  --lux-gold-50: #F7F1E4;
  --lux-gold-300: #E2CD9E;
  --lux-gold-400: #D2B77F;
  --lux-gold-500: #C6A96E;
  --lux-gold-600: #A8894F;
  --lux-gold-700: #866A3B;
  /* 全 App 唯一强调渐变（金色量具） */
  --lux-gold-gradient: linear-gradient(135deg, #E2CD9E 0%, #C6A96E 55%, #B08F55 100%);
  /* 黑曜石页头基底（非强调渐变） */
  --lux-hero-gradient: linear-gradient(160deg, #070A0F 0%, #14181F 100%);

  /* ---------- Status ---------- */
  --lux-jade: #3F8F79;
  --lux-jade-600: #2F6F5D;
  --lux-jade-400: #55A98F;
  --lux-jade-50: #EDF6F1;
  --lux-jade-line: #CFE6DC;
  --lux-ember: #B8752E;
  --lux-ember-600: #8F5A20;
  --lux-ember-50: #FBF4E6;
  --lux-ember-line: #EBD9B4;
  --lux-wine: #A8454F;
  --lux-wine-600: #85333C;
  --lux-wine-50: #FAEDEE;
  --lux-wine-line: #EBCFD3;
  --lux-neutral: #6F6A5F;
  --lux-neutral-600: #565148;
  --lux-neutral-50: #F4F2ED;
  --lux-neutral-line: #DFD9CC;

  /* ---------- Text & lines ---------- */
  --lux-text-strong: #14181F;
  --lux-text: #2A2F38;
  --lux-text-soft: #3C414A;
  --lux-text-muted: #6F6A5F;
  --lux-text-faint: #6F6A5F;
  --lux-line: #E4DDCB;
  --lux-line-soft: #EFE9DD;
  --lux-line-strong: #CFC7B6;

  /* ---------- Type ---------- */
  --lux-font-ui: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  --lux-font-data: 'Bahnschrift', 'DIN Alternate', 'Roboto Condensed', Consolas, monospace;

  /* ---------- Geometry（rpx；CALIBRE 圆角上限 6px = 12rpx） ---------- */
  --lux-radius-xs: 4rpx;
  --lux-radius-sm: 8rpx;
  --lux-radius-md: 12rpx;
  --lux-radius-round: 50%;
  --lux-space-xs: 8rpx;
  --lux-space-sm: 12rpx;
  --lux-space-md: 16rpx;
  --lux-space-lg: 24rpx;
  --lux-space-xl: 32rpx;
  --lux-space-2xl: 48rpx;

  /* ---------- Elevation（暖墨阴影，无彩色光） ---------- */
  --lux-shadow-card: 0 2rpx 4rpx rgba(35, 28, 15, 0.04), 0 24rpx 48rpx -36rpx rgba(35, 28, 15, 0.18);
  --lux-shadow-pop: 0 16rpx 40rpx rgba(20, 16, 8, 0.14);
  --lux-shadow-bar: 0 -8rpx 24rpx rgba(20, 16, 8, 0.06);

  /* ---------- Page base ---------- */
  font-family: var(--lux-font-ui);
  font-size: 28rpx;
  line-height: 1.5;
  color: var(--lux-text);
  background: var(--lux-porcelain-100);
  -webkit-font-smoothing: antialiased;
}

view,
text,
button,
input,
textarea,
image,
scroll-view {
  box-sizing: border-box;
}

button {
  padding: 0;
  margin: 0;
  line-height: inherit;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  color: inherit;
}

button::after { border: none; }

input,
textarea {
  font-family: inherit;
  font-size: inherit;
  color: inherit;
}

image { display: block; }

.page-container {
  min-height: 100vh;
  background: var(--lux-porcelain-100);
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

.app-card {
  background: var(--lux-surface);
  border: 1rpx solid var(--lux-line);
  border-radius: var(--lux-radius-md);
  box-shadow: var(--lux-shadow-card);
}

.app-empty,
.app-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 112rpx 40rpx;
}

.app-empty-icon {
  width: 108rpx;
  height: 108rpx;
  margin-bottom: 26rpx;
  opacity: 0.42;
}

.app-empty-title {
  margin-bottom: 10rpx;
  font-size: 30rpx;
  font-weight: 500;
  letter-spacing: 1rpx;
  color: var(--lux-ink-900);
}

.app-empty-desc,
.app-loading-text {
  font-size: 24rpx;
  line-height: 1.65;
  color: var(--lux-text-faint);
  text-align: center;
}

.app-loading-text { margin-top: 20rpx; }

.safe-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

.status-tag {
  display: inline-flex;
  align-items: center;
  min-height: 40rpx;
  padding: 4rpx 14rpx;
  border: 1rpx solid var(--lux-neutral-line);
  border-radius: var(--lux-radius-xs);
  background: var(--lux-neutral-50);
  color: var(--lux-neutral-600);
  font-size: 22rpx;
  font-weight: 500;
  letter-spacing: 1rpx;
}

.filter-tag {
  display: inline-flex;
  align-items: center;
  height: 58rpx;
  padding: 0 24rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
  border: 1rpx solid var(--lux-line);
  border-radius: var(--lux-radius-xs);
  background: var(--lux-surface);
  color: var(--lux-text-muted);
  font-size: 25rpx;
  letter-spacing: 1rpx;
  transition: all 0.2s ease;
}

.filter-tag--active {
  border-color: var(--lux-ink-900);
  background: var(--lux-ink-900);
  color: var(--lux-porcelain-50);
  box-shadow: inset 0 -4rpx 0 var(--lux-gold-500);
}

.bottom-fixed-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 18rpx 28rpx;
  padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid var(--lux-line);
  background: var(--lux-surface);
  box-shadow: var(--lux-shadow-bar);
}

.modal-mask {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(7, 10, 15, 0.58);
}

.modal-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: 88vh;
  padding-bottom: env(safe-area-inset-bottom);
  overflow: hidden;
  border: 1rpx solid var(--lux-line);
  border-bottom: none;
  border-radius: var(--lux-radius-md) var(--lux-radius-md) 0 0;
  background: var(--lux-surface);
  box-shadow: var(--lux-shadow-pop);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-bottom: 1rpx solid var(--lux-line-soft);
  flex-shrink: 0;
}

.modal-title {
  font-size: 32rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--lux-ink-900);
}

.modal-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54rpx;
  height: 54rpx;
  border-radius: var(--lux-radius-xs);
  background: var(--lux-porcelain-200);
  color: var(--lux-text-muted);
  font-size: 28rpx;
}

.modal-body {
  flex: 1;
  padding: 30rpx 32rpx;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  gap: 16rpx;
  padding: 22rpx 32rpx 26rpx;
  border-top: 1rpx solid var(--lux-line-soft);
  flex-shrink: 0;
}

.modal-btn {
  flex: 1;
  height: 88rpx;
  border: 1rpx solid transparent;
  border-radius: var(--lux-radius-sm);
  font-size: 28rpx;
  font-weight: 500;
  line-height: 88rpx;
  text-align: center;
}

.modal-btn--cancel {
  border-color: var(--lux-line);
  background: var(--lux-porcelain-200);
  color: var(--lux-text);
}

.modal-btn--confirm {
  border-color: var(--lux-gold-600);
  background: var(--lux-gold-gradient);
  color: #17130B;
  font-weight: 600;
  box-shadow: 0 1rpx 0 rgba(255, 255, 255, 0.35) inset, 0 8rpx 18rpx rgba(120, 94, 44, 0.18);
}

.modal-btn--danger {
  border-color: var(--lux-wine-600);
  background: var(--lux-wine);
  color: var(--lux-porcelain-50);
}

.modal-btn--confirm[disabled],
.modal-btn--danger[disabled] { opacity: 0.5; }
</style>
