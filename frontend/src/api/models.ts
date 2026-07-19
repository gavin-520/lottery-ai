import http, { type ApiResult } from './http'

export interface ModelPredictionItem {
  modelName: string
  redBalls: number[]
  blueBall: number
  confidence: number
}

export async function compareModels(period?: string): Promise<ModelPredictionItem[]> {
  const res = await http.get<ApiResult<{ models: ModelPredictionItem[] }>>('/api/v1/predict/models', {
    params: period ? { period } : undefined
  })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data.models
}
