<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>多 Agent 工作流</span>
          <el-button type="primary" :loading="loading" @click="run">运行工作流</el-button>
        </div>
      </template>

      <el-alert
        title="Analyst → Reviewer → Reporter 三阶段流水线"
        type="info"
        :closable="false"
        show-icon
        class="hint"
      />

      <el-input v-model="question" type="textarea" :rows="2" placeholder="可选：输入分析问题" class="question" />

      <el-timeline v-if="result" class="timeline">
        <el-timeline-item v-for="step in result.steps" :key="step.agent" :timestamp="step.role">
          <strong>{{ step.agent }}</strong>
          <p>{{ step.output }}</p>
        </el-timeline-item>
      </el-timeline>

      <el-card v-if="result" shadow="never" class="report">
        <template #header>最终报告 · {{ result.workflowName }}</template>
        <pre>{{ result.finalReport }}</pre>
      </el-card>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { runWorkflow, type AgentWorkflow } from '@/api/agent'

const loading = ref(false)
const question = ref('')
const result = ref<AgentWorkflow | null>(null)

async function run() {
  loading.value = true
  try {
    result.value = await runWorkflow(question.value || undefined)
    ElMessage.success('工作流完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '运行失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.hint { margin-bottom: 16px; }
.question { margin-bottom: 16px; }
.timeline { margin-top: 20px; }
.report { margin-top: 20px; }
.report pre { white-space: pre-wrap; font-family: inherit; line-height: 1.6; }
</style>
