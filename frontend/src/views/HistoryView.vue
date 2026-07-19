<template>
  <el-card shadow="never">
    <el-table v-loading="loading" :data="records" stripe style="width: 100%">
      <el-table-column prop="period" label="期号" width="120" />
      <el-table-column prop="drawDate" label="开奖日期" width="140" />
      <el-table-column label="红球">
        <template #default="{ row }">
          <el-tag v-for="ball in row.redBalls.split(',')" :key="ball" type="danger" class="ball">
            {{ ball }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="蓝球" width="80">
        <template #default="{ row }">
          <el-tag type="primary">{{ row.blueBall }}</el-tag>
        </template>
      </el-table-column>
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
import { fetchHistory, type LotteryHistory } from '@/api/history'

const loading = ref(false)
const records = ref<LotteryHistory[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const result = await fetchHistory(page.value, size.value)
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
.ball {
  margin-right: 4px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
