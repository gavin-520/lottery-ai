import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const baseURL = import.meta.env.VITE_API_BASE_URL || ''

const http = axios.create({
  baseURL,
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push({ name: 'login' })
    }
    return Promise.reject(error)
  }
)

export default http

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}
