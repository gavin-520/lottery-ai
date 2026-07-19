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
        <el-menu-item index="/dashboard">
          <span>Dashboard</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <span>历史开奖</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="title">{{ pageTitle }}</span>
        <div class="user-area">
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const pageTitle = computed(() => {
  if (route.name === 'history') return '历史开奖数据'
  return 'Dashboard'
})

function handleLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #1d2b3a;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  border-bottom: 1px solid #2c3e50;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.title {
  font-size: 18px;
  font-weight: 500;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.main {
  background: #f5f7fa;
}
</style>
