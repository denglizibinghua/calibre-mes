<template>
  <span class="brand-mark" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <svg viewBox="0 0 48 48" fill="none">
      <circle cx="24" cy="24" r="20.5" stroke="currentColor" stroke-width="1.1" opacity="0.5" />
      <circle cx="24" cy="24" r="14" stroke="currentColor" stroke-width="1.1" opacity="0.9" />
      <circle cx="24" cy="24" r="3.4" fill="currentColor" />
      <g stroke="currentColor" stroke-width="1.1" stroke-linecap="round" opacity="0.85">
        <path d="M24 3.5v6M24 38.5v6M3.5 24h6M38.5 24h6" />
        <path d="M9.5 9.5l4.2 4.2M34.3 34.3l4.2 4.2M38.5 9.5l-4.2 4.2M13.7 34.3l-4.2 4.2" />
      </g>
      <path d="M24 10a14 14 0 0 1 13.2 9.3" stroke="var(--brand-accent, #c6a96e)" stroke-width="1.6" stroke-linecap="round" />
      <circle cx="37.6" cy="21.8" r="1.6" fill="var(--brand-accent, #c6a96e)" />
    </svg>
  </span>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 40 })
</script>

<style scoped>
.brand-mark {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: currentColor;
}

.brand-mark svg {
  width: 100%;
  height: 100%;
}
</style>
