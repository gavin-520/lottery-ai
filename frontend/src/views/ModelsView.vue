<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>福彩3D 模型对比</span>
          <el-button type="primary" :loading="loading" @click="load">刷新对比</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="models" stripe>
        <el-table-column prop="modelName" label="模型" width="200" />
        <el-table-column label="预测号码" width="200">
          <template #default="{ row }">
            <el-tag type="danger" class="ball">{{ row.digit1 }}</el-tag>
            <el-tag type="danger" class="ball">{{ row.digit2 }}</el-tag>
            <el-tag type="danger" class="ball">{{ row.digit3 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sumValue" label="和值" width="80" />
        <el-table-column prop="spanValue" label="跨度" width="80" />
        <el-table-column prop="oddEvenPattern" label="奇偶" width="100" />
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">{{ (row.confidence * 100).toFixed(1) }}%</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { predictFc3dAi, predictFc3dRules, type Fc3dPredictResult } from '@/api/fc3d'

const loading = ref(false)
const models = ref<Fc3dPredictResult[]>([])

async function load() {
  loading.value = true
  try {
    const [rules, ai] = await Promise.all([predictFc3dRules(), predictFc3dAi()])
    models.value = [rules, ai]
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.ball { margin-right: 6px; min-width: 28px; text-align: center; }
</style>
