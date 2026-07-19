<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>智能预测</span>
          <el-button type="primary" :loading="loading" @click="runPredict">混合预测</el-button>
        </div>
      </template>

      <el-space wrap>
        <el-button :loading="loadingRules" @click="runRules">规则引擎</el-button>
        <el-button :loading="loadingAi" @click="runAi">AI 模型</el-button>
      </el-space>

      <div v-if="result" class="result">
        <p>期号：{{ result.period }}</p>
        <p>
          红球：
          <el-tag v-for="ball in result.redBalls" :key="ball" type="danger" class="ball">{{ ball }}</el-tag>
        </p>
        <p>蓝球：<el-tag type="primary">{{ result.blueBall }}</el-tag></p>
        <p>模型：{{ result.modelName }} · 置信度 {{ (result.confidence * 100).toFixed(1) }}% · 来源 {{ result.source }}</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { predictHybrid, predictRules, predictAi, type PredictResult } from '@/api/predict'

const loading = ref(false)
const loadingRules = ref(false)
const loadingAi = ref(false)
const result = ref<PredictResult | null>(null)

async function runPredict() {
  loading.value = true
  try {
    result.value = await predictHybrid()
    ElMessage.success('混合预测完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '预测失败')
  } finally {
    loading.value = false
  }
}

async function runRules() {
  loadingRules.value = true
  try {
    result.value = await predictRules()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '预测失败')
  } finally {
    loadingRules.value = false
  }
}

async function runAi() {
  loadingAi.value = true
  try {
    result.value = await predictAi()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '预测失败')
  } finally {
    loadingAi.value = false
  }
}
</script>

<style scoped>
.page {
  padding: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result {
  margin-top: 20px;
  line-height: 2;
}

.ball {
  margin-right: 4px;
}
</style>
