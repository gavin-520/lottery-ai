<template>
  <div class="page">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>CSV 数据导入</template>
          <p class="hint">格式：period,draw_date,red_balls,blue_ball 或 period,draw_date,r1,r2,r3,r4,r5,r6,blue</p>
          <el-upload drag :auto-upload="false" :limit="1" :on-change="handleFile">
            <div class="el-upload__text">拖拽 CSV 到此处，或 <em>点击上传</em></div>
          </el-upload>
          <el-button type="primary" class="upload-btn" :loading="loading" :disabled="!file" @click="submit">
            开始导入
          </el-button>
          <el-descriptions v-if="result" :column="1" border class="result">
            <el-descriptions-item label="文件">{{ result.filename }}</el-descriptions-item>
            <el-descriptions-item label="总行数">{{ result.totalRows }}</el-descriptions-item>
            <el-descriptions-item label="成功">{{ result.successRows }}</el-descriptions-item>
            <el-descriptions-item label="失败">{{ result.failedRows }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ result.status }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>新建用户</template>
          <el-form :model="userForm" label-width="80px">
            <el-form-item label="用户名"><el-input v-model="userForm.username" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
            <el-form-item label="昵称"><el-input v-model="userForm.nickname" /></el-form-item>
            <el-form-item label="角色">
              <el-select v-model="userForm.role" style="width: 100%">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="分析师" value="ANALYST" />
                <el-option label="普通用户" value="USER" />
              </el-select>
            </el-form-item>
            <el-button type="primary" :loading="userLoading" @click="createUser">创建</el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="sync-row">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>实时数据同步</template>
          <el-button type="primary" :loading="syncLoading" @click="doSync">立即同步（Mock Feed）</el-button>
          <el-descriptions v-if="syncResult" :column="2" border class="result">
            <el-descriptions-item label="来源">{{ syncResult.source }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ syncResult.status }}</el-descriptions-item>
            <el-descriptions-item label="抓取">{{ syncResult.fetchedCount }}</el-descriptions-item>
            <el-descriptions-item label="新增">{{ syncResult.newCount }}</el-descriptions-item>
            <el-descriptions-item label="Cron">{{ syncResult.cron }}</el-descriptions-item>
            <el-descriptions-item label="消息">{{ syncResult.message }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="user-card">
      <template #header>用户列表</template>
      <el-table :data="users" stripe>
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column prop="status" label="状态" width="80" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { createUser as createUserApi, importCsv, listUsers, type ImportResult, type UserSummary } from '@/api/admin'
import { triggerSync, type SyncStatus } from '@/api/sync'

const loading = ref(false)
const userLoading = ref(false)
const syncLoading = ref(false)
const file = ref<File | null>(null)
const result = ref<ImportResult | null>(null)
const syncResult = ref<SyncStatus | null>(null)
const users = ref<UserSummary[]>([])
const userForm = reactive({ username: '', password: '', nickname: '', role: 'USER' })

function handleFile(uploadFile: UploadFile) {
  file.value = uploadFile.raw || null
}

async function submit() {
  if (!file.value) return
  loading.value = true
  try {
    result.value = await importCsv(file.value)
    ElMessage.success('导入完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '导入失败')
  } finally {
    loading.value = false
  }
}

async function doSync() {
  syncLoading.value = true
  try {
    syncResult.value = await triggerSync()
    ElMessage.success('同步完成')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '同步失败')
  } finally {
    syncLoading.value = false
  }
}

async function loadUsers() {
  users.value = await listUsers()
}

async function createUser() {
  userLoading.value = true
  try {
    await createUserApi(userForm)
    ElMessage.success('用户已创建')
    userForm.username = ''
    userForm.password = ''
    userForm.nickname = ''
    await loadUsers()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    userLoading.value = false
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.page { padding: 4px; }
.hint { color: #909399; font-size: 12px; margin-bottom: 12px; }
.upload-btn { margin-top: 12px; }
.result { margin-top: 16px; }
.sync-row { margin-top: 20px; }
.user-card { margin-top: 20px; }
</style>
