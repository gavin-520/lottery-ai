<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>福彩3D 历史开奖</span>
        <el-tag type="success">FC3D</el-tag>
      </div>
    </template>

    <el-table v-loading="loading" :data="records" stripe style="width: 100%">
      <el-table-column prop="issue" label="期号" width="120" />
      <el-table-column prop="drawDate" label="开奖日期" width="140" />
      <el-table-column label="开奖号码" width="200">
        <template #default="{ row }">
          <el-tag type="danger" class="ball">{{ row.digit1 }}</el-tag>
          <el-tag type="danger" class="ball">{{ row.digit2 }}</el-tag>
          <el-tag type="danger" class="ball">{{ row.digit3 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sumValue" label="和值" width="80" />
      <el-table-column prop="spanValue" label="跨度" width="80" />
      <el-table-column prop="oddEvenPattern" label="奇偶形态" width="120" />
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchFc3dHistory, type Fc3dDraw } from '@/api/fc3d'

const loading = ref(false)
const records = ref<Fc3dDraw[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const result = await fetchFc3dHistory(page.value, size.value)
    records.value = result.records
    total.value = result.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ball {
  margin-right: 6px;
  min-width: 28px;
  text-align: center;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
