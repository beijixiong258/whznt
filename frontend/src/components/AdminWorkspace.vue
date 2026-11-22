<script setup lang="ts">
import { ref } from 'vue'
import { Link as TLink, TabPanel as TTabPanel, Tabs as TTabs } from 'tdesign-vue-next'
import AdminRecharge from './AdminRecharge.vue'
import AdminUpstream from './AdminUpstream.vue'
import AdminUsers from './AdminUsers.vue'

defineProps<{ error: string; loggingOut: boolean }>()
const emit = defineEmits<{ logout: [] }>()
const tab = ref('recharges')
</script>

<template>
  <main class="admin-shell">
    <header class="admin-heading">
      <h1>管理后台</h1>
      <div class="admin-actions">
        <t-link href="/" theme="default">返回用户工作台</t-link>
        <t-button theme="default" variant="outline" :loading="loggingOut" @click="emit('logout')">退出登录</t-button>
      </div>
    </header>
    <t-alert v-if="error" theme="error" :message="error" />
    <t-tabs v-model="tab">
      <t-tab-panel value="recharges" label="充值记录"><AdminRecharge /></t-tab-panel>
      <t-tab-panel value="users" label="用户管理"><AdminUsers /></t-tab-panel>
      <t-tab-panel value="upstream" label="AI 上游"><AdminUpstream /></t-tab-panel>
    </t-tabs>
  </main>
</template>
