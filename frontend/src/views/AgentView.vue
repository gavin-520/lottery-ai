<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>AI 分析助手</span>
          <el-button type="primary" :loading="loading" @click="run">生成分析</el-button>
        </div>
      </template>

      <el-input
        v-model="question"
        type="textarea"
        :rows="3"
        placeholder="输入分析问题，例如：近期红球热号趋势如何？"
      />

      <div v-if="result" class="result">
        <el-alert :title="result.summary" type="info" show-icon :closable="false" />
        <h4>洞察</h4>
        <ul>
          <li v-for="(item, idx) in result.insights" :key="idx">{{ item }}</li>
        </ul>
        <h4>建议</h4>
        <ul>
          <li v-for="(item, idx) in result.recommendations" :key="idx">{{ item }}</li>
        </ul>
        <p class="agent">Agent: {{ result.agentName }}</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { analyze, type AgentAnalysis } from '@/api/agent'

const loading = ref(false)
const question = ref('')
const result = ref<AgentAnalysis | null>(null)

async function run() {
  loading.value = true
  try {
    result.value = await analyze(question.value || undefined)
    ElMessage.success('分析完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '分析失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.result { margin-top: 20px; }
.result h4 { margin: 16px 0 8px; }
.agent { color: #909399; font-size: 12px; margin-top: 12px; }
</style>
