import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')

  function setAuth(data: { token: string; username: string; nickname: string }) {
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname)
  }

  function logout() {
    token.value = ''
    username.value = ''
    nickname.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
  }

  return { token, username, nickname, setAuth, logout }
})
