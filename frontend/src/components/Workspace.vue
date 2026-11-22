<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import {
  AddIcon, ChatIcon, Edit1Icon, LogoutIcon, MenuFoldIcon, MenuUnfoldIcon, TimeIcon, UserCircleIcon, UserIcon, VideoIcon,
  ViewModuleIcon, WalletIcon,
} from 'tdesign-icons-vue-next'
import {
  Aside as TAside, Avatar as TAvatar, Content as TContent, Header as THeader, Layout as TLayout,
  Menu as TMenu, MenuItem as TMenuItem, MessagePlugin,
} from 'tdesign-vue-next'
import { api, errorMessage, jsonBody, loadConversation, loadConversations } from '../api'
import type { DraftReference } from '../subtitles'
import type { Account, ConversationAction, ConversationDetail, ConversationKind, ConversationRow } from '../types'
import type { WritingExpert } from '../writingExperts'
import ChatPane from './ChatPane.vue'
import ConversationActions from './ConversationActions.vue'
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
const emit = defineEmits<{ logout: []; profile: [account: Account] }>()
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
const historyRevision = ref(0)
const recentHistory = ref<ConversationRow[]>([])
const activeIds = reactive<{ write: number | null; chat: number | null }>({ write: null, chat: null })
let recentRequest: AbortController | undefined
const dirty = reactive({ write: false, chat: false })
const busy = reactive({ write: false, chat: false })
const subtitleState = ref({ dirty: false, busy: false })
const alert = ref('')
const opening = ref(false)
const pendingAction = ref<(() => void) | null>(null)
const historyAction = ref<{ kind: ConversationAction; row: ConversationRow } | null>(null)
const historyTitle = ref('')
const historyError = ref('')
const savingHistory = ref(false)
const editingUsername = ref(false)
const username = ref('')
const usernameError = ref('')
const savingUsername = ref(false)

const phoneLabel = computed(() => props.account.phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1 **** $2'))
const anyBusy = computed(() => busy.write || busy.chat)
const activeConversation = computed(() => pane.value === 'write' || pane.value === 'chat' ? activeIds[pane.value] : null)
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
    const result = await loadConversations({}, request.signal)
    if (!request.signal.aborted) recentHistory.value = result.items.slice(0, 6)
  } catch {
    // The history page reports request failures and offers a fresh search.
  } finally {
    if (recentRequest === request) recentRequest = undefined
  }
}

function refreshHistory() {
  historyRevision.value++
  void loadRecentHistory()
}

function onSaved(pane: 'write' | 'chat', id: number) {
  activeIds[pane] = id
  refreshHistory()
}

function whileIdle(action: () => void, subtitleBusy = false) {
  if (anyBusy.value || opening.value || savingHistory.value || subtitleBusy) {
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
    resetConversation(kind, expert)
    openPane(kind)
  }))
}

function resetConversation(kind: 'write' | 'chat', expert?: WritingExpert) {
  if (kind === 'write') {
    writing.value = null
    initialVersion.value = undefined
    initialExpert.value = expert
    writeKey.value++
  } else {
    chatting.value = null
    chatKey.value++
  }
  activeIds[kind] = null
  dirty[kind] = false
}

function openHistory(id: number, kind: ConversationKind, version?: number) {
  const target = kind === 'WRITING' ? 'write' : 'chat'
  whileIdle(() => {
    if (id === activeIds[target] && version === undefined) openPane(target)
    else confirmDiscard(target, () => void openConversation(id, version))
  })
}

function openHistoryAction(kind: ConversationAction, row: ConversationRow) {
  whileIdle(() => {
    historyAction.value = { kind, row }
    historyTitle.value = row.title
    historyError.value = ''
  })
}

function closeHistoryAction() {
  if (!savingHistory.value) historyAction.value = null
}

async function saveHistoryAction() {
  const action = historyAction.value
  if (!action || savingHistory.value) return
  const title = historyTitle.value.trim()
  if (action.kind === 'rename' && !title) {
    historyError.value = '请输入标题。'
    return
  }
  savingHistory.value = true
  historyError.value = ''
  try {
    const path = '/api/conversations/' + action.row.id
    if (action.kind === 'rename') {
      await api<ConversationRow>(path, jsonBody({ title }, 'PATCH'))
      void MessagePlugin.success('已重命名对话')
    } else {
      await api<void>(path, { method: 'DELETE' })
      const target = action.row.kind === 'WRITING' ? 'write' : 'chat'
      if (activeIds[target] === action.row.id) {
        resetConversation(target)
        if (pane.value === target) openPane('chat')
      }
      void MessagePlugin.success('已删除对话')
    }
    historyAction.value = null
    refreshHistory()
  } catch (failure) {
    historyError.value = errorMessage(failure)
  } finally {
    savingHistory.value = false
  }
}

function openProjects(id: number) {
  requestedProject.value = { id }
  openPane('projects')
}

function projectDeleted(id: number) {
  if (requestedProject.value?.id === id) requestedProject.value = undefined
  if (writing.value?.conversation.projectId === id) resetConversation('write')
  refreshHistory()
}

function editUsername() {
  username.value = props.account.username
  usernameError.value = ''
  editingUsername.value = true
}

function closeUsername() {
  if (!savingUsername.value) editingUsername.value = false
}

async function saveUsername() {
  if (savingUsername.value) return
  const value = username.value.trim()
  const width = [...value].reduce((size, char) => size + (char.charCodeAt(0) < 128 ? 1 : 2), 0)
  if (width > 20) {
    usernameError.value = '最多填写 10 个汉字或 20 个英文字母。'
    return
  }
  savingUsername.value = true
  usernameError.value = ''
  try {
    const account = await api<Account>('/api/auth/me', jsonBody({ username: value }, 'PATCH'))
    emit('profile', account)
    editingUsername.value = false
    void MessagePlugin.success('用户名已保存')
  } catch (failure) {
    usernameError.value = errorMessage(failure)
  } finally {
    savingUsername.value = false
  }
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
      activeIds.write = id
      initialVersion.value = version
      initialExpert.value = undefined
      writeKey.value++
      dirty.write = false
      openPane('write')
    } else {
      chatting.value = detail
      activeIds.chat = id
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
      <section class="rail-history" aria-label="最近对话">
        <t-button class="rail-history-link" size="small" variant="text" block :theme="pane === 'history' ? 'primary' : 'default'"
          aria-label="历史对话" title="历史对话" :aria-current="pane === 'history' ? 'page' : undefined" @click="openPane('history')">
          <span class="rail-label">历史对话</span>
          <TimeIcon />
        </t-button>
        <div v-if="recentHistory.length" class="rail-recent">
          <div v-for="row in recentHistory" :key="row.id" class="rail-recent-row"
            :class="{ 'rail-recent-active': activeConversation === row.id }">
            <t-button class="rail-recent-item" variant="text" theme="default" size="small" block
              :disabled="opening" :title="row.title" :aria-current="activeConversation === row.id ? 'true' : undefined"
              @click="openHistory(row.id, row.kind)">
              <template #icon><Edit1Icon v-if="row.kind === 'WRITING'" /><ChatIcon v-else /></template>
              <span>{{ row.title }}</span>
            </t-button>
            <ConversationActions :title="row.title" :disabled="opening" @action="openHistoryAction($event, row)" />
          </div>
        </div>
      </section>
      <div class="rail-footer">
        <t-button class="rail-recharge" variant="outline" block :theme="pane === 'recharge' ? 'primary' : 'default'"
          aria-label="充值申请" title="充值申请" :aria-current="pane === 'recharge' ? 'page' : undefined" @click="openPane('recharge')">
          <template #icon><WalletIcon /></template>
          <span class="rail-label">充值申请</span>
        </t-button>
        <div class="rail-account">
          <t-button class="account-profile" variant="text" theme="default" aria-label="设置用户名" title="设置用户名"
            @click="editUsername">
            <template #icon><UserCircleIcon /></template>
            <span class="account-label rail-label">{{ account.username || phoneLabel }}</span>
          </t-button>
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
      </t-header>

      <t-content class="page-content">
        <t-alert v-if="alert" class="page-alert" theme="warning" :message="alert" close
          @close="alert = ''" />
        <WritingExpertPicker v-show="pane === 'experts'" :account-id="account.id" :disabled="anyBusy || opening"
          :can-resume="dirty.write || busy.write" @select="startNew('write', $event)"
          @chat="startNew('chat')" @resume="openPane('write')" />
        <WritePane v-show="pane === 'write'" :key="writeKey" :initial="writing" :initial-version="initialVersion"
          :initial-expert="initialExpert" @experts="openPane('experts')"
          @saved="onSaved('write', $event)" @dirty="dirty.write = $event" @busy="busy.write = $event"
          @project="openProjects" @upstream="(id, version) => openHistory(id, 'WRITING', version)" @subtitle="openSubtitles" />
        <ChatPane v-show="pane === 'chat'" :key="chatKey" :initial="chatting" :username="account.username"
          @saved="onSaved('chat', $event)" @dirty="dirty.chat = $event" @busy="busy.chat = $event" />
        <HistoryPane v-if="opened.has('history')" v-show="pane === 'history'" :revision="historyRevision" :opening="opening"
          @open="openHistory" @action="openHistoryAction" @back="openPane('chat')" @new-chat="startNew('chat')" />
        <ProjectsPane v-if="opened.has('projects')" v-show="pane === 'projects'" :active="pane === 'projects'"
          :requested-project="requestedProject" @open="(id, version) => openHistory(id, 'WRITING', version)"
          @deleted="projectDeleted" @history-changed="refreshHistory" />
        <SubtitlePane v-if="opened.has('subtitles')" v-show="pane === 'subtitles'" :active="pane === 'subtitles'"
          :requested-reference="subtitleReference" @edit-state="subtitleState = $event" />
        <RechargePane v-if="opened.has('recharge')" v-show="pane === 'recharge'" :active="pane === 'recharge'" />
      </t-content>
    </t-layout>

    <t-dialog :visible="pendingAction !== null" header="离开当前内容？"
      body="当前还有未保存的内容。离开后会丢失这部分修改。" :confirm-btn="{ content: '仍然离开', theme: 'danger' }"
      cancel-btn="继续编辑" @close="pendingAction = null" @cancel="pendingAction = null" @confirm="discard" />
    <t-dialog :visible="editingUsername" header="设置用户名" :confirm-btn="{ content: '保存', loading: savingUsername }"
      @confirm="saveUsername" @cancel="closeUsername" @close="closeUsername">
      <label class="sr-only" for="workspace-username">用户名</label>
      <t-input v-field-id="'workspace-username'" v-model="username" clearable :maxlength="20"
        :disabled="savingUsername" placeholder="输入用户名" @enter="saveUsername" />
      <p class="secondary-text username-help">最多 10 个汉字或 20 个英文字母。混用时，一个汉字占两位。</p>
      <t-alert v-if="usernameError" theme="error" :message="usernameError" />
    </t-dialog>
    <t-dialog :visible="historyAction !== null" :header="historyAction?.kind === 'rename' ? '重命名对话' : '删除对话'"
      :confirm-btn="{
        content: historyAction?.kind === 'rename' ? '保存' : '删除',
        theme: historyAction?.kind === 'rename' ? 'primary' : 'danger',
        loading: savingHistory,
      }"
      :close-on-overlay-click="!savingHistory" :close-on-esc-keydown="!savingHistory"
      @cancel="closeHistoryAction" @close="closeHistoryAction" @confirm="saveHistoryAction">
      <template v-if="historyAction?.kind === 'rename'">
        <label for="history-title">标题</label>
        <t-input v-field-id="'history-title'" v-model="historyTitle" class="dialog-input" :maxlength="120"
          :disabled="savingHistory" @enter="saveHistoryAction" />
      </template>
      <template v-else-if="historyAction">
        <p class="history-delete-title">删除「{{ historyAction.row.title }}」？</p>
        <p>{{ historyAction.row.projectId
          ? '这条记录会从历史对话移除，稿件仍可从人物项目打开。'
          : '这条对话和其中未保存的内容会从工作台移除。' }}</p>
      </template>
      <t-alert v-if="historyError" theme="error" :message="historyError" />
    </t-dialog>
  </t-layout>
</template>
