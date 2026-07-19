import http, { type ApiResult } from './http'

export interface BallFrequencyItem {
  ball: number
  count: number
  type: string
}

export interface AnalyticsSummary {
  redFrequency: BallFrequencyItem[]
  blueFrequency: BallFrequencyItem[]
  totalPeriods: number
}

export async function fetchFrequency(): Promise<AnalyticsSummary> {
  const res = await http.get<ApiResult<AnalyticsSummary>>('/api/v1/analytics/frequency')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
