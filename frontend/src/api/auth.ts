import http, { type ApiResult } from './http'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  nickname: string
  role: string
}

export async function login(data: LoginRequest): Promise<LoginResponse> {
  const res = await http.post<ApiResult<LoginResponse>>('/api/v1/auth/login', data)
  if (res.data.code !== 200) {
    throw new Error(res.data.message || 'Login failed')
  }
  return res.data.data
}
