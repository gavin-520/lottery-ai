<template>
  <el-tag type="success" effect="plain" class="lottery-type-badge">
    当前：{{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DEFAULT_LOTTERY_TYPE_LABEL, LOTTERY_TYPE_LABELS, type LotteryTypeCode } from '@/constants/lottery'

const props = withDefaults(
  defineProps<{
    code?: string
    label?: string
  }>(),
  {
    code: 'FC3D',
    label: '',
  }
)

const label = computed(() => {
  if (props.label) return props.label
  const code = (props.code || 'FC3D') as LotteryTypeCode
  return LOTTERY_TYPE_LABELS[code] || DEFAULT_LOTTERY_TYPE_LABEL
})
</script>

<style scoped>
.lottery-type-badge {
  font-weight: 500;
}
</style>
