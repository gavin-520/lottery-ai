import http, { type ApiResult } from './http'

export interface PredictResult {
  period: string
  redBalls: number[]
  blueBall: number
  modelName: string
  confidence: number
  source: string
}

export async function predictHybrid(period?: string): Promise<PredictResult> {
  const res = await http.post<ApiResult<PredictResult>>('/api/v1/predict', null, {
    params: period ? { period } : undefined
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function predictRules(period?: string): Promise<PredictResult> {
  const res = await http.get<ApiResult<PredictResult>>('/api/v1/predict/rules', {
    params: period ? { period } : undefined
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}

export async function predictAi(period?: string): Promise<PredictResult> {
  const res = await http.get<ApiResult<PredictResult>>('/api/v1/predict/ai', {
    params: period ? { period } : undefined
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
