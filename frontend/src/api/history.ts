import http, { type ApiResult } from './http'

export interface LotteryHistory {
  id: number
  period: string
  drawDate: string
  redBalls: string
  blueBall: string
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export async function fetchHistory(page = 1, size = 20): Promise<PageResult<LotteryHistory>> {
  const res = await http.get<ApiResult<PageResult<LotteryHistory>>>('/api/v1/history', {
    params: { page, size }
  })
  if (res.data.code !== 200) {
    throw new Error(res.data.message || 'Failed to load history')
  }
  return res.data.data
}
