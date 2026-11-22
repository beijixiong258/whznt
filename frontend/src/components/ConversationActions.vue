<script setup lang="ts">
import { ref, watch } from 'vue'
import { MoreIcon } from 'tdesign-icons-vue-next'
import { Button as TButton, Popup as TPopup } from 'tdesign-vue-next'
import type { ConversationAction } from '../types'

defineProps<{ title: string; disabled: boolean; draft?: boolean }>()
const emit = defineEmits<{ action: [action: ConversationAction] }>()

const visible = ref(false)
const trigger = ref<InstanceType<typeof TButton>>()
const actions = ref<HTMLElement>()

watch(actions, element => {
  if (!element) return
  requestAnimationFrame(() => {
    if (visible.value) element.querySelector('button')?.focus()
  })
})

function close() {
  visible.value = false
  trigger.value?.$el.focus()
}

function select(action: ConversationAction) {
  close()
  emit('action', action)
}
</script>

<template>
  <t-popup v-model:visible="visible" class="conversation-actions" trigger="click" placement="bottom-right">
    <t-button ref="trigger" variant="text" theme="default" size="small" shape="square"
      :aria-label="(draft ? '稿件操作，' : '会话操作，') + title" :title="draft ? '稿件操作' : '会话操作'"
      :aria-expanded="visible" :disabled="disabled"
      @keydown.esc.stop="close">
      <template #icon><MoreIcon /></template>
    </t-button>
    <template #content>
      <div v-if="visible" ref="actions" class="conversation-action-list" role="group" :aria-label="draft ? '稿件操作' : '会话操作'"
        @keydown.esc.stop.prevent="close">
        <t-button v-if="!draft" variant="text" theme="default" @click="select('rename')">重命名对话</t-button>
        <t-button variant="text" theme="danger" @click="select('delete')">{{ draft ? '移除稿件' : '删除' }}</t-button>
      </div>
    </template>
  </t-popup>
</template>
