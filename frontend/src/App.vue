<script setup lang="ts">
import { defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { Link as TLink } from 'tdesign-vue-next'
import { api, ApiError, errorMessage } from './api'
import type { Account } from './types'
import AuthView from './components/AuthView.vue'

const Workspace = defineAsyncComponent(() => import('./components/Workspace.vue'))
const AdminWorkspace = defineAsyncComponent(() => import('./components/AdminWorkspace.vue'))
const adminPage = /^\/admin\/?$/.test(window.location.pathname)

const account = ref<Account | null>(null)
const loading = ref(true)
const error = ref('')
const loggingOut = ref(false)

async function loadAccount() {
  loading.value = true
  error.value = ''
  try {
    account.value = await api<Account>('/api/auth/me')
  } catch (failure) {
    if (!(failure instanceof ApiError && failure.status === 401)) {
      error.value = errorMessage(failure)
    }
    account.value = null
  } finally {
    loading.value = false
  }
}

function expire() {
  account.value = null
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  error.value = ''
  try {
    await api<void>('/api/auth/logout', { method: 'POST' })
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
  <AuthView v-else-if="!account" :key="String(adminPage)" :admin="adminPage" :initial-error="error"
    @authenticated="account = $event; error = ''" />
  <Workspace v-else-if="!adminPage" :account="account" @logout="expire" />
  <AdminWorkspace v-else-if="account.role === 'ADMIN'" :error="error" :logging-out="loggingOut" @logout="logout" />
  <main v-else class="admin-shell">
    <h1>需要管理员账号</h1>
    <t-alert v-if="error" theme="error" :message="error" />
    <div class="admin-actions">
      <t-button :loading="loggingOut" @click="logout">换账号登录</t-button>
      <t-link href="/">返回工作台</t-link>
    </div>
  </main>
</template>
