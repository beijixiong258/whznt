<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Card as TCard, MessagePlugin } from 'tdesign-vue-next'
import type { TextareaValue } from 'tdesign-vue-next'
import { ApiError, errorMessage, loadConversation, streamGeneration, StreamInterrupted } from '../api'
import type { ChatMessage, ConversationDetail, GenerationRequest } from '../types'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{ initial: ConversationDetail | null }>()
const emit = defineEmits<{ saved: []; dirty: [value: boolean]; busy: [value: boolean] }>()

const conversationId = ref(props.initial?.conversation.id ?? null)
const messages = ref<ChatMessage[]>(props.initial?.messages ?? [])
const input = ref('')
const sending = ref(false)
const pendingPrompt = ref('')
const partial = ref('')
const error = ref('')
const bottom = ref<HTMLElement>()
const hasConversation = computed(() => messages.value.length > 0 || sending.value)
const starters = ['帮我的小店想三个短视频点子', '帮我写一条朋友圈']
let abort: AbortController | undefined
let retry: GenerationRequest | undefined

watch(input, value => emit('dirty', value.trim().length > 0))
watch(sending, value => emit('busy', value))

function scrollToBottom() {
  void nextTick(() => bottom.value?.scrollIntoView({ block: 'nearest' }))
}

function useStarter(text: string) {
  input.value = text
  document.getElementById('chat-message')?.focus()
}

async function send() {
  const message = input.value.trim()
  if (!message || sending.value) return
  const request: GenerationRequest = retry?.message === message ? retry : {
    requestId: crypto.randomUUID(), kind: 'CHAT', conversationId: conversationId.value, message,
  }
  retry = request
  sending.value = true
  pendingPrompt.value = message
  partial.value = ''
  error.value = ''
  const controller = new AbortController()
  abort = controller
  scrollToBottom()
  try {
    const result = await streamGeneration(request, {
      start: event => { conversationId.value = event.conversationId },
      delta: text => { partial.value += text },
    }, controller.signal)
    if (result.conversationId === null) throw new StreamInterrupted()
    conversationId.value = result.conversationId
    const detail = await loadConversation(result.conversationId)
    messages.value = detail.messages
    input.value = ''
    partial.value = ''
    retry = undefined
    emit('saved')
    scrollToBottom()
  } catch (failure) {
    error.value = controller.signal.aborted ? '已停止生成，可以继续编辑输入。' : errorMessage(failure)
    if (controller.signal.aborted || (failure instanceof ApiError && !(failure instanceof StreamInterrupted))) {
      retry = undefined
    }
    partial.value = ''
    if (conversationId.value !== null) emit('saved')
  } finally {
    sending.value = false
    pendingPrompt.value = ''
    abort = undefined
  }
}

function handleKey(_value: TextareaValue, { e: event }: { e: KeyboardEvent }) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void send()
  }
}

async function copy(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    void MessagePlugin.success('已复制回复')
  } catch {
    error.value = '无法访问剪贴板，可以选择文字后手动复制。'
  }
}

onBeforeUnmount(() => abort?.abort())
</script>

<template>
  <section class="chat-pane" :class="{ 'chat-home': !hasConversation }" aria-label="通用对话">
    <div v-if="!hasConversation" class="chat-intro">
      <h1>你好，今天想聊点什么？</h1>
      <p>我能帮你找选题、写文案、改稿子。有什么想法，直接跟我说。</p>
    </div>
    <t-alert v-if="error" theme="warning" :message="error" close @close="error = ''" />
    <t-card v-if="hasConversation" class="conversation-thread" aria-live="polite" :aria-busy="sending">
      <article v-for="message in messages" :key="message.id" class="chat-message" :class="message.role.toLowerCase()">
        <div class="chat-meta">{{ message.role === 'USER' ? '你' : '创作助手' }}</div>
        <MarkdownView v-if="message.role === 'ASSISTANT'" :content="message.content" />
        <p v-else class="plain-text">{{ message.content }}</p>
        <t-button v-if="message.role === 'ASSISTANT'" size="small" variant="text" @click="copy(message.content)">
          复制回复
        </t-button>
      </article>
      <template v-if="sending">
        <article class="chat-message user">
          <div class="chat-meta">你</div>
          <p class="plain-text">{{ pendingPrompt }}</p>
        </article>
        <article class="chat-message assistant">
          <div class="chat-meta">创作助手</div>
          <MarkdownView v-if="partial" :content="partial" />
          <t-loading v-else text="正在生成" />
        </article>
      </template>
      <div ref="bottom"></div>
    </t-card>
    <t-card class="chat-composer" body-class-name="chat-composer-content">
      <label class="sr-only" for="chat-message">对话内容</label>
      <t-textarea v-field-id="'chat-message'" v-model="input" placeholder="说说你想做什么"
        :autosize="{ minRows: 3, maxRows: 8 }" :readonly="sending" :maxlength="12000" @keydown="handleKey" />
      <div class="chat-send">
        <t-button v-if="sending" variant="outline" @click="abort?.abort()">停止</t-button>
        <t-button v-else :disabled="!input.trim()" @click="send">发送</t-button>
      </div>
    </t-card>
    <div v-if="!hasConversation && !input.trim()" class="chat-starters">
      <span class="secondary-text">试试跟我说</span>
      <t-button v-for="text in starters" :key="text" size="small" theme="default" variant="outline"
        @click="useStarter(text)">{{ text }}</t-button>
    </div>
  </section>
</template>
