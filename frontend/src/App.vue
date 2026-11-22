<script setup lang="ts">
import { defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, ApiError, errorMessage } from './api'
import type { Account } from './types'
import AuthView from './components/AuthView.vue'

const Workspace = defineAsyncComponent(() => import('./components/Workspace.vue'))
const AdminWorkspace = defineAsyncComponent(() => import('./components/AdminWorkspace.vue'))
const adminPage = ref(/^\/admin(?:\/login)?\/?$/.test(window.location.pathname))

const account = ref<Account | null>(null)
const loading = ref(true)
const error = ref('')
const loggingOut = ref(false)

function setAccount(next: Account | null) {
  account.value = next
  if (next?.role === 'ADMIN') adminPage.value = true
  if (adminPage.value) {
    const path = next?.role === 'ADMIN' ? '/admin' : '/admin/login'
    if (window.location.pathname !== path) window.history.replaceState(null, '', path)
  }
}

async function loadAccount() {
  loading.value = true
  error.value = ''
  try {
    setAccount(await api<Account>('/api/auth/me'))
  } catch (failure) {
    if (!(failure instanceof ApiError && failure.status === 401)) {
      error.value = errorMessage(failure)
    }
    setAccount(null)
  } finally {
    loading.value = false
  }
}

function expire() {
  setAccount(null)
}

function authenticated(next: Account) {
  error.value = ''
  setAccount(next)
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  error.value = ''
  try {
    await api<void>('/api/admin/auth/logout', { method: 'POST' })
    expire()
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    loggingOut.value = false
  }
}

onMounted(() => {
  window.addEventListener('whznt:session-expired', expire)
  void loadAccount()
})

onBeforeUnmount(() => window.removeEventListener('whznt:session-expired', expire))
</script>

<template>
  <main v-if="loading" class="app-loading"><t-loading size="large" text="正在加载工作台" /></main>
  <AdminWorkspace v-else-if="account?.role === 'ADMIN'" :username="account.username"
    :error="error" :logging-out="loggingOut" @logout="logout" />
  <AuthView v-else-if="adminPage || !account" :admin="adminPage" :initial-error="error" @authenticated="authenticated" />
  <Workspace v-else :account="account" @profile="setAccount" @logout="expire" />
</template>
