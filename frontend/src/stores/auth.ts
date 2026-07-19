import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')
  const role = ref(localStorage.getItem('role') || '')

  const isAdmin = computed(() => role.value === 'ADMIN')
  const isAnalyst = computed(() => role.value === 'ADMIN' || role.value === 'ANALYST')

  function setAuth(data: { token: string; username: string; nickname: string; role: string }) {
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname
    role.value = data.role
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname)
    localStorage.setItem('role', data.role)
  }

  function logout() {
    token.value = ''
    username.value = ''
    nickname.value = ''
    role.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
    localStorage.removeItem('role')
  }

  function hasRole(...roles: string[]) {
    return roles.includes(role.value)
  }

  return { token, username, nickname, role, isAdmin, isAnalyst, setAuth, logout, hasRole }
})
