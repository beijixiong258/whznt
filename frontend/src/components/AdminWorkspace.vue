<script setup lang="ts">
import { reactive, ref } from 'vue'
import { LogoutIcon, SettingIcon, UserIcon, WalletIcon } from 'tdesign-icons-vue-next'
import { Avatar as TAvatar } from 'tdesign-vue-next'
import AdminRecharge from './AdminRecharge.vue'
import AdminUpstream from './AdminUpstream.vue'
import AdminUsers from './AdminUsers.vue'

defineProps<{ username: string; error: string; loggingOut: boolean }>()
const emit = defineEmits<{ logout: [] }>()
const pages = {
  recharges: { title: '充值管理', icon: WalletIcon },
  users: { title: '用户管理', icon: UserIcon },
  upstream: { title: 'AI 提供商配置', icon: SettingIcon },
} as const
type Page = keyof typeof pages
const current = ref<Page>('recharges')
const opened = reactive(new Set<Page>([current.value]))

function open(page: Page) {
  opened.add(page)
  current.value = page
  window.scrollTo(0, 0)
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="brand-lockup">
        <t-avatar image="/brand.png" size="36px" aria-hidden="true" />
        <span>管理后台</span>
      </div>
      <nav class="admin-navigation" aria-label="后台导航">
        <t-button v-for="(page, id) in pages" :key="id" class="admin-nav-item" variant="text" block
          :theme="current === id ? 'primary' : 'default'" :aria-current="current === id ? 'page' : undefined"
          @click="open(id)">
          <template #icon><component :is="page.icon" /></template>
          {{ page.title }}
        </t-button>
      </nav>
      <div class="admin-session">
        <span>{{ username }}</span>
        <t-button theme="default" variant="text" shape="square" aria-label="退出登录" title="退出登录"
          :loading="loggingOut" @click="emit('logout')">
          <template #icon><LogoutIcon /></template>
        </t-button>
      </div>
    </aside>
    <main class="admin-content">
      <header class="admin-heading"><h1>{{ pages[current].title }}</h1></header>
      <t-alert v-if="error" class="page-alert" theme="error" :message="error" />
      <AdminRecharge v-if="opened.has('recharges')" v-show="current === 'recharges'" />
      <AdminUsers v-if="opened.has('users')" v-show="current === 'users'" :operator-name="username" />
      <AdminUpstream v-if="opened.has('upstream')" v-show="current === 'upstream'" />
    </main>
  </div>
</template>
