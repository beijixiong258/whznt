<script setup lang="ts">
import { defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, ApiError, errorMessage } from './api'
import type { Account } from './types'
import AuthView from './components/AuthView.vue'

const Workspace = defineAsyncComponent(() => import('./components/Workspace.vue'))

const account = ref<Account | null>(null)
const loading = ref(true)
const error = ref('')

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

onMounted(() => {
  window.addEventListener('whznt:session-expired', expire)
  void loadAccount()
})

onBeforeUnmount(() => window.removeEventListener('whznt:session-expired', expire))
</script>

<template>
  <main v-if="loading" class="app-loading"><t-loading size="large" text="正在加载工作台" /></main>
  <Workspace v-else-if="account" :account="account" @logout="expire" />
  <AuthView v-else :initial-error="error" @authenticated="account = $event" />
</template>
