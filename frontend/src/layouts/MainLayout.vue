<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">Lottery AI</div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#1d2b3a"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard"><span>Dashboard</span></el-menu-item>
        <el-menu-item index="/analytics"><span>数据分析</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/predict"><span>智能预测</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/models"><span>模型对比</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/agent"><span>AI 助手</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/workflow"><span>多 Agent</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/backtest"><span>回测</span></el-menu-item>
        <el-menu-item index="/history"><span>历史开奖</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin"><span>系统管理</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/events"><span>事件流</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/sla"><span>API SLA</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/sync"><span>同步运维</span></el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/ops"><span>运维大盘</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="title">{{ pageTitle }}</span>
        <div class="user-area">
          <el-tag v-if="region" size="small" type="info">{{ region }}</el-tag>
          <el-tag v-if="avroEnabled" size="small" type="warning">Avro</el-tag>
          <el-tag size="small">{{ auth.role }}</el-tag>
          <span>{{ auth.nickname || auth.username }}</span>
          <el-button type="danger" link @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getPlatformInfo } from '@/api/platform'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const region = ref('')
const avroEnabled = ref(false)

onMounted(async () => {
  try {
    const info = await getPlatformInfo()
    region.value = info.region
    avroEnabled.value = info.avroEnabled
  } catch {
    region.value = 'local'
  }
})

const pageTitle = computed(() => {
  const titles: Record<string, string> = {
    history: '历史开奖数据',
    predict: '智能预测',
    backtest: '回测分析',
    analytics: '数据分析',
    agent: 'AI 分析助手',
    workflow: '多 Agent 工作流',
    models: '模型对比',
    admin: '系统管理',
    events: '平台事件流',
    sla: '外部 API SLA',
    sync: '同步运维',
    ops: '运维大盘',
  }
  return titles[String(route.name)] || 'Dashboard'
})

function handleLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.aside { background: #1d2b3a; }
.logo {
  height: 60px; line-height: 60px; text-align: center;
  color: #fff; font-size: 18px; font-weight: 600;
  border-bottom: 1px solid #2c3e50;
}
.header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #ebeef5;
}
.title { font-size: 18px; font-weight: 500; }
.user-area { display: flex; align-items: center; gap: 12px; }
.main { background: #f5f7fa; }
</style>
