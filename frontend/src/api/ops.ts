import http, { type ApiResult } from './http'
import type { RegionStats } from './sync'

export interface OpsOverview {
  regions: RegionStats[]
  breaches24h: number
  failedSyncs24h: number
  cacheEnabled: boolean
}

export async function getOpsOverview(hours = 24): Promise<OpsOverview> {
  const res = await http.get<ApiResult<OpsOverview>>('/api/v1/admin/ops/overview', { params: { hours } })
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
