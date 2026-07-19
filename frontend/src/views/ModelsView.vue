<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>模型对比</span>
          <el-button type="primary" :loading="loading" @click="load">刷新对比</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="models" stripe>
        <el-table-column prop="modelName" label="模型" width="160" />
        <el-table-column label="红球">
          <template #default="{ row }">
            <el-tag v-for="b in row.redBalls" :key="b" type="danger" class="ball">{{ b }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="蓝球" width="80">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.blueBall }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">{{ (row.confidence * 100).toFixed(1) }}%</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { compareModels, type ModelPredictionItem } from '@/api/models'

const loading = ref(false)
const models = ref<ModelPredictionItem[]>([])

async function load() {
  loading.value = true
  try {
    models.value = await compareModels()
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
.ball { margin-right: 4px; }
</style>
