<template>
  <div class="login-page">
    <el-card class="login-card" shadow="hover">
      <h2>Lottery AI Platform</h2>
      <p class="subtitle">企业级 AI 数据分析平台</p>

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <p class="hint">admin/admin123 · analyst/analyst123 · user/user123</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123'
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login(form)
    auth.setAuth({
      token: data.token,
      username: data.username,
      nickname: data.nickname,
      role: data.role
    })
    ElMessage.success('登录成功')
    router.push({ name: 'dashboard' })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d2b3a 0%, #2c5364 100%);
}

.login-card {
  width: 400px;
  padding: 20px;
}

h2 {
  text-align: center;
  margin-bottom: 4px;
}

.subtitle {
  text-align: center;
  color: #909399;
  margin-bottom: 24px;
  font-size: 14px;
}

.submit-btn {
  width: 100%;
}

.hint {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}
</style>
