import http, { type ApiResult } from './http'

export interface PlatformInfo {
  region: string
  kafkaEnabled: boolean
  avroEnabled: boolean
  schemaVersion: string
  schemaRegistryUrl: string
}

export async function getPlatformInfo(): Promise<PlatformInfo> {
  const res = await http.get<ApiResult<PlatformInfo>>('/api/v1/platform/info')
  if (res.data.code !== 200) throw new Error(res.data.message)
  return res.data.data
}
