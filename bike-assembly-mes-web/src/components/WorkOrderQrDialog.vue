<template>
  <el-dialog
    :model-value="modelValue"
    title="工单二维码"
    width="min(480px, calc(100vw - 32px))"
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="work-order-qr" :aria-busy="loading">
      <p v-if="loading" role="status">正在加载工单二维码…</p>
      <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />
      <template v-else-if="metadata && imageUrl">
        <p class="work-order-qr__number">工单号：{{ metadata.workOrderNo }}</p>
        <img :src="imageUrl" :alt="`工单 ${metadata.workOrderNo} 的二维码`" width="320" height="320" />
        <p class="work-order-qr__content">{{ metadata.content }}</p>
        <p class="work-order-qr__hint">使用 MES 小程序扫码，按账号权限进入工单任务。</p>
      </template>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button v-if="error" @click="reload">重新加载</el-button>
      <el-button type="primary" :disabled="!canDownload" @click="download">下载 PNG</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { toRef } from 'vue'
import { useWorkOrderQr } from './useWorkOrderQr'

const props = defineProps<{ modelValue: boolean; workOrderId?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { metadata, imageUrl, loading, error, canDownload, reload, download } = useWorkOrderQr(
  toRef(props, 'modelValue'), toRef(props, 'workOrderId')
)
</script>

<style scoped>
.work-order-qr {
  min-height: 160px;
  text-align: center;
}
.work-order-qr__number {
  margin: 0 0 12px;
  color: var(--el-text-color-primary);
  font-weight: 600;
  overflow-wrap: anywhere;
}
.work-order-qr img {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 0 auto;
}
.work-order-qr__content {
  margin: 12px 0;
  overflow-wrap: anywhere;
  font-family: monospace;
}
.work-order-qr__hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
