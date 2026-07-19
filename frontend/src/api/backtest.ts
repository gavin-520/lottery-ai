import http, { type ApiResult } from './http'

export interface BacktestRequest {
  name?: string
  minHistory?: number
  topRed?: number
}

export interface BacktestResult {
  reportId: number
  name: string
  totalPeriods: number
  totalRedHits: number
  maxRedHits: number
  avgRedHits: number
  redHitRate: number
  blueHitRate: number
  summary: Record<string, unknown>
}

export async function runBacktest(payload: BacktestRequest = {}): Promise<BacktestResult> {
  const res = await http.post<ApiResult<BacktestResult>>('/api/v1/backtest/run', payload)
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
