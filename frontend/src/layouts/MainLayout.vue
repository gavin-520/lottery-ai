<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <div>Lottery AI</div>
        <div class="logo-type">福彩3D</div>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#1d2b3a"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard"><span>Dashboard</span></el-menu-item>
        <el-menu-item index="/analytics"><span>数据分析</span></el-menu-item>
        <el-menu-item index="/fc3d/analytics"><span>福彩3D分析</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/predict"><span>智能预测</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/models"><span>模型对比</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/agent"><span>AI 助手</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/workflow"><span>多 Agent</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/backtest"><span>回测</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/fc3d/backtest"><span>福彩3D回测评估</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/fc3d/backtest/details"><span>福彩3D回测明细</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/fc3d/model-evaluation"><span>福彩3D模型评估</span></el-menu-item>
        <el-menu-item v-if="auth.isAnalyst" index="/fc3d/model-center"><span>福彩3D模型中心</span></el-menu-item>
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
        <div class="title-wrap">
          <span class="title">{{ pageTitle }}</span>
          <LotteryTypeBadge :code="lotteryType" :label="lotteryTypeLabel" />
        </div>
        <div class="user-area">
          <el-tag v-if="region" size="small" type="info">{{ region }}</el-tag>
          <el-tag v-if="avroEnabled" size="small" type="warning">Avro</el-tag>
          <el-tag size="small">{{ auth.role }}</el-tag>
          <span>{{ auth.nickname || auth.username }}</span>
          <el-button type="danger" link @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <div class="lottery-banner">当前彩票类型：{{ lotteryTypeLabel }}</div>
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
import LotteryTypeBadge from '@/components/LotteryTypeBadge.vue'
import { DEFAULT_LOTTERY_TYPE, DEFAULT_LOTTERY_TYPE_LABEL } from '@/constants/lottery'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const region = ref('')
const avroEnabled = ref(false)
const lotteryType = ref(DEFAULT_LOTTERY_TYPE)
const lotteryTypeLabel = ref(DEFAULT_LOTTERY_TYPE_LABEL)

onMounted(async () => {
  try {
    const info = await getPlatformInfo()
    region.value = info.region
    avroEnabled.value = info.avroEnabled
    if (info.lotteryType) lotteryType.value = info.lotteryType as typeof lotteryType.value
    if (info.lotteryTypeLabel) lotteryTypeLabel.value = info.lotteryTypeLabel
  } catch {
    region.value = 'local'
  }
})

const pageTitle = computed(() => {
  const titles: Record<string, string> = {
    history: '福彩3D 历史开奖',
    predict: '福彩3D 智能预测',
    backtest: '福彩3D 回测',
    analytics: '福彩3D 数据分析',
    'fc3d-analytics': '福彩3D 深度分析',
    'fc3d-backtest': '福彩3D 回测评估',
    'fc3d-backtest-details': '福彩3D Top50 回测明细',
    'fc3d-model-evaluation': '福彩3D 模型评估与参数优化',
    'fc3d-model-center': '福彩3D 模型中心',
    agent: '福彩3D AI 助手',
    workflow: '福彩3D 多 Agent',
    models: '福彩3D 模型对比',
    admin: '系统管理',
    events: '平台事件流',
    sla: '外部 API SLA',
    sync: '同步运维',
    ops: '运维大盘',
  }
  return titles[String(route.name)] || '福彩3D Dashboard'
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
  height: 64px; padding-top: 10px; text-align: center;
  color: #fff; font-size: 18px; font-weight: 600;
  border-bottom: 1px solid #2c3e50;
}
.logo-type {
  font-size: 12px; font-weight: 400; color: #7dcea0; margin-top: 2px;
}
.header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #ebeef5;
}
.title-wrap { display: flex; align-items: center; gap: 12px; }
.title { font-size: 18px; font-weight: 500; }
.user-area { display: flex; align-items: center; gap: 12px; }
.main { background: #f5f7fa; }
.lottery-banner {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: linear-gradient(90deg, #e8f8f0, #f5f7fa);
  border-left: 3px solid #27ae60;
  color: #1e8449;
  font-size: 14px;
  font-weight: 500;
}
</style>
