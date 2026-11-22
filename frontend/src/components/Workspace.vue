<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import {
  AddIcon, ChatIcon, Edit1Icon, LogoutIcon, MenuFoldIcon, MenuUnfoldIcon, TimeIcon, UserIcon, VideoIcon, ViewModuleIcon, WalletIcon,
} from 'tdesign-icons-vue-next'
import {
  Aside as TAside, Avatar as TAvatar, Content as TContent, Header as THeader, Layout as TLayout,
  Link as TLink, Menu as TMenu, MenuItem as TMenuItem,
} from 'tdesign-vue-next'
import { api, errorMessage, loadConversation, loadConversations } from '../api'
import type { DraftReference } from '../subtitles'
import type { Account, ConversationDetail, ConversationRow } from '../types'
import type { WritingExpert } from '../writingExperts'
import ChatPane from './ChatPane.vue'
import HistoryPane from './HistoryPane.vue'
import ProjectsPane from './ProjectsPane.vue'
import RechargePane from './RechargePane.vue'
import SubtitlePane from './SubtitlePane.vue'
import WritePane from './WritePane.vue'
import WritingExpertPicker from './WritingExpertPicker.vue'

const navigation = [
  { id: 'experts', label: '专家库', icon: ViewModuleIcon },
  { id: 'projects', label: '人物故事与定位', icon: UserIcon },
  { id: 'subtitles', label: '给视频加字幕', icon: VideoIcon },
] as const
type Pane = typeof navigation[number]['id'] | 'write' | 'chat' | 'history' | 'recharge'

const props = defineProps<{ account: Account }>()
const emit = defineEmits<{ logout: [] }>()
const pane = ref<Pane>('chat')
const railCollapsed = ref(false)
const opened = reactive(new Set<Pane>())
const writing = ref<ConversationDetail | null>(null)
const chatting = ref<ConversationDetail | null>(null)
const writeKey = ref(0)
const initialVersion = ref<number>()
const initialExpert = shallowRef<WritingExpert>()
const requestedProject = ref<{ id: number }>()
const subtitleReference = ref<DraftReference>()
const chatKey = ref(0)
const historyKey = ref(0)
const recentHistory = ref<ConversationRow[]>([])
let recentRequest: AbortController | undefined
const dirty = reactive({ write: false, chat: false })
const busy = reactive({ write: false, chat: false })
const subtitleState = ref({ dirty: false, busy: false })
const alert = ref('')
const opening = ref(false)
const pendingAction = ref<(() => void) | null>(null)

const phoneLabel = computed(() => props.account.phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1 **** $2'))
const anyBusy = computed(() => busy.write || busy.chat)
const hasUnsavedChanges = computed(() => dirty.write || dirty.chat || subtitleState.value.dirty)
const selectedMenu = computed(() => {
  if (pane.value !== 'write') return pane.value
  return writing.value?.conversation.projectId ? 'projects' : 'experts'
})

function openPane(next: Pane) {
  opened.add(next)
  pane.value = next
  window.scrollTo(0, 0)
}

async function loadRecentHistory() {
  recentRequest?.abort()
  const request = new AbortController()
  recentRequest = request
  try {
    const result = await loadConversations('', 1, request.signal)
    if (!request.signal.aborted) recentHistory.value = result.items.slice(0, 6)
  } catch {
    // The history page reports request failures and offers a fresh search.
  } finally {
    if (recentRequest === request) recentRequest = undefined
  }
}

function refreshHistory() {
  historyKey.value++
  void loadRecentHistory()
}

function whileIdle(action: () => void, subtitleBusy = false) {
  if (anyBusy.value || opening.value || subtitleBusy) {
    alert.value = '请先等待当前操作完成。'
    return
  }
  alert.value = ''
  action()
}

function confirmDiscard(mode: 'write' | 'chat' | 'all', action: () => void) {
  const hasChanges = mode === 'all' ? hasUnsavedChanges.value : dirty[mode]
  if (hasChanges) {
    pendingAction.value = action
  } else {
    action()
  }
}

function discard() {
  const action = pendingAction.value
  pendingAction.value = null
  action?.()
}

function startNew(kind: 'write' | 'chat', expert?: WritingExpert) {
  whileIdle(() => confirmDiscard(kind, () => {
    if (kind === 'write') {
      writing.value = null
      initialVersion.value = undefined
      initialExpert.value = expert
      writeKey.value++
    } else {
      chatting.value = null
      chatKey.value++
    }
    dirty[kind] = false
    openPane(kind)
  }))
}

function openHistory(id: number, kind: 'WRITING' | 'CHAT', version?: number) {
  const target = kind === 'WRITING' ? 'write' : 'chat'
  whileIdle(() => confirmDiscard(target, () => void openConversation(id, version)))
}

function openProjects(id: number) {
  requestedProject.value = { id }
  openPane('projects')
}

function openSubtitles(reference: DraftReference) {
  subtitleReference.value = reference
  openPane('subtitles')
}

async function openConversation(id: number, version?: number) {
  opening.value = true
  try {
    const detail = await loadConversation(id)
    if (detail.conversation.kind === 'WRITING') {
      writing.value = detail
      initialVersion.value = version
      initialExpert.value = undefined
      writeKey.value++
      dirty.write = false
      openPane('write')
    } else {
      chatting.value = detail
      chatKey.value++
      dirty.chat = false
      openPane('chat')
    }
  } catch (failure) {
    alert.value = errorMessage(failure)
  } finally {
    opening.value = false
  }
}

function logout() {
  whileIdle(() => confirmDiscard('all', () => {
    void api<void>('/api/auth/logout', { method: 'POST' })
      .then(() => emit('logout'))
      .catch(failure => { alert.value = errorMessage(failure) })
  }), subtitleState.value.busy)
}

function warnBeforeUnload(event: BeforeUnloadEvent) {
  event.preventDefault()
  event.returnValue = true
}

watch(hasUnsavedChanges, hasChanges => {
  if (hasChanges) window.addEventListener('beforeunload', warnBeforeUnload)
  else window.removeEventListener('beforeunload', warnBeforeUnload)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', warnBeforeUnload)
  recentRequest?.abort()
})
onMounted(() => void loadRecentHistory())
</script>

<template>
  <t-layout class="shell" :class="{ 'rail-collapsed': railCollapsed }">
    <t-aside class="rail" width="var(--workspace-rail-width)">
      <div class="brand-lockup">
        <t-avatar image="/brand.png" size="36px" aria-hidden="true" />
        <span class="rail-label">网红内容生成平台</span>
      </div>
      <t-button class="rail-new" block aria-label="新对话" title="新对话" @click="startNew('chat')">
        <template #icon><AddIcon /></template>
        <span class="rail-label">新对话</span>
      </t-button>
      <nav id="workspace-navigation" class="rail-navigation" aria-label="工作台">
        <t-menu class="rail-nav" :value="selectedMenu" :collapsed="railCollapsed" :width="['100%', '100%']">
          <t-menu-item v-for="item in navigation" :key="item.id" :value="item.id" :aria-label="item.label"
            :aria-current="selectedMenu === item.id ? 'page' : undefined" @click="openPane(item.id)">
            <template #icon><component :is="item.icon" /></template>
            {{ item.label }}
          </t-menu-item>
        </t-menu>
      </nav>
      <section class="rail-history" aria-label="最近历史">
        <t-button class="rail-history-link" size="small" variant="text" block :theme="pane === 'history' ? 'primary' : 'default'"
          aria-label="历史记录" title="历史记录" :aria-current="pane === 'history' ? 'page' : undefined" @click="openPane('history')">
          <template #icon><TimeIcon /></template>
          <span class="rail-label">历史记录</span>
        </t-button>
        <div v-if="recentHistory.length" class="rail-recent">
          <t-button v-for="row in recentHistory" :key="row.id" class="rail-recent-item" variant="text" theme="default"
            size="small" block :disabled="opening" :title="row.title" @click="openHistory(row.id, row.kind)">
            <template #icon><Edit1Icon v-if="row.kind === 'WRITING'" /><ChatIcon v-else /></template>
            <span>{{ row.title }}</span>
          </t-button>
        </div>
      </section>
      <div class="rail-footer">
        <t-button class="rail-recharge" variant="outline" block :theme="pane === 'recharge' ? 'primary' : 'default'"
          aria-label="充值申请" title="充值申请" :aria-current="pane === 'recharge' ? 'page' : undefined" @click="openPane('recharge')">
          <template #icon><WalletIcon /></template>
          <span class="rail-label">充值申请</span>
        </t-button>
        <div class="rail-account">
          <span class="account-label rail-label">{{ phoneLabel }}</span>
          <t-button variant="text" theme="default" shape="square" aria-label="退出登录" title="退出登录" @click="logout">
            <template #icon><LogoutIcon /></template>
          </t-button>
        </div>
      </div>
    </t-aside>

    <t-layout class="work-area">
      <t-header class="top-bar" height="52px">
        <t-button variant="text" theme="default" shape="square" aria-controls="workspace-navigation"
          :aria-expanded="!railCollapsed" :aria-label="railCollapsed ? '展开侧边栏' : '收起侧边栏'"
          :title="railCollapsed ? '展开侧边栏' : '收起侧边栏'" @click="railCollapsed = !railCollapsed">
          <template #icon><MenuUnfoldIcon v-if="railCollapsed" /><MenuFoldIcon v-else /></template>
        </t-button>
        <t-link v-if="account.role === 'ADMIN'" class="admin-link" href="/admin" theme="primary">管理后台</t-link>
      </t-header>

      <t-content class="page-content" :class="{ 'expert-page-content': pane === 'experts' }">
        <t-alert v-if="alert" class="page-alert" theme="warning" :message="alert" close
          @close="alert = ''" />
        <WritingExpertPicker v-show="pane === 'experts'" :account-id="account.id" :disabled="anyBusy || opening"
          :can-resume="dirty.write || busy.write" @select="startNew('write', $event)"
          @chat="startNew('chat')" @resume="openPane('write')" />
        <WritePane v-show="pane === 'write'" :key="writeKey" :initial="writing" :initial-version="initialVersion"
          :initial-expert="initialExpert" @experts="openPane('experts')"
          @saved="refreshHistory" @dirty="dirty.write = $event" @busy="busy.write = $event"
          @project="openProjects" @upstream="(id, version) => openHistory(id, 'WRITING', version)" @subtitle="openSubtitles" />
        <ChatPane v-show="pane === 'chat'" :key="chatKey" :initial="chatting"
          @saved="refreshHistory" @dirty="dirty.chat = $event" @busy="busy.chat = $event" />
        <HistoryPane v-if="pane === 'history'" :key="historyKey" :opening="opening" @open="openHistory"
          @renamed="loadRecentHistory" />
        <ProjectsPane v-if="opened.has('projects')" v-show="pane === 'projects'" :active="pane === 'projects'"
          :requested-project="requestedProject" @open="(id, version) => openHistory(id, 'WRITING', version)" />
        <SubtitlePane v-if="opened.has('subtitles')" v-show="pane === 'subtitles'" :active="pane === 'subtitles'"
          :requested-reference="subtitleReference" @edit-state="subtitleState = $event" />
        <RechargePane v-if="opened.has('recharge')" v-show="pane === 'recharge'" :active="pane === 'recharge'" />
      </t-content>
    </t-layout>

    <t-dialog :visible="pendingAction !== null" header="离开当前内容？"
      body="当前还有未保存的内容。离开后会丢失这部分修改。" :confirm-btn="{ content: '仍然离开', theme: 'danger' }"
      cancel-btn="继续编辑" @close="pendingAction = null" @cancel="pendingAction = null" @confirm="discard" />
  </t-layout>
</template>
