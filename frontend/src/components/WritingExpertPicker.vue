<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChatIcon, SearchIcon, StarFilledIcon, StarIcon } from 'tdesign-icons-vue-next'
import { Avatar as TAvatar, Card as TCard, Empty as TEmpty, RadioButton as TRadioButton, RadioGroup as TRadioGroup } from 'tdesign-vue-next'
import { writingExperts, type WritingExpert } from '../writingExperts'

const props = defineProps<{ accountId: number; disabled: boolean; canResume: boolean }>()
const emit = defineEmits<{ select: [expert: WritingExpert]; chat: []; resume: [] }>()
const generalAssistant = {
  name: '通用 AI',
  specialty: '通用对话',
  description: '随便聊聊、问个问题，或者让它帮你想个点子。',
  icon: ChatIcon,
}
const experts = [generalAssistant, ...writingExperts]
const query = ref('')
const filter = ref<'all' | 'favorites'>('all')
const storageKey = 'whznt:writing-expert-favorites:' + props.accountId
const favorites = ref(readFavorites())
const visibleExperts = computed(() => {
  const search = searchable(query.value)
  return experts.filter(expert => (filter.value === 'all' || favorites.value.has(expert.name))
    && searchable([expert.name, expert.specialty, expert.description].join(' ')).includes(search))
})

function searchable(text: string) {
  return text.toLocaleLowerCase().replace(/\s+/g, '')
}

function readFavorites(): Set<string> {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(storageKey) ?? '[]')
    if (Array.isArray(saved)) return new Set(experts.filter(expert => saved.includes(expert.name)).map(expert => expert.name))
  } catch {
    // The directory also works in browsers with storage disabled.
  }
  return new Set()
}

function toggleFavorite(name: string) {
  if (favorites.value.has(name)) favorites.value.delete(name)
  else favorites.value.add(name)
  try {
    localStorage.setItem(storageKey, JSON.stringify([...favorites.value]))
  } catch {
    // Keep the selection available for the current session.
  }
}

function select(expert: (typeof experts)[number]) {
  if (props.disabled) return
  if ('prompt' in expert) emit('select', expert)
  else emit('chat')
}
</script>

<template>
  <section class="expert-library" aria-labelledby="expert-library-title">
    <div class="page-heading">
      <h1 id="expert-library-title">专家库</h1>
      <p>选一个助手，告诉它你想做什么。</p>
    </div>
    <div class="expert-library-toolbar">
      <t-radio-group v-model="filter" variant="default-filled" aria-label="专家筛选">
        <t-radio-button value="all">全部专家</t-radio-button>
        <t-radio-button value="favorites">我的专家</t-radio-button>
      </t-radio-group>
      <div class="expert-search">
        <label class="sr-only" for="expert-search">搜索专家</label>
        <t-input v-field-id="'expert-search'" v-model="query" placeholder="搜索专家名称或用途" clearable>
          <template #prefix-icon><SearchIcon /></template>
        </t-input>
      </div>
      <t-button v-if="canResume" variant="outline" @click="emit('resume')">继续刚才的创作</t-button>
    </div>
    <p class="secondary-text expert-result-count" role="status">共 {{ visibleExperts.length }} 个助手</p>
    <div v-if="visibleExperts.length" class="expert-card-grid">
      <t-card v-for="expert in visibleExperts" :key="expert.name" class="expert-card" :class="{ 'expert-card-selectable': !disabled }"
        size="small" :hover-shadow="!disabled" @click="select(expert)">
        <div class="expert-card-row">
          <t-avatar size="large" aria-hidden="true">
            <template #icon><component :is="expert.icon" /></template>
          </t-avatar>
          <div class="expert-card-copy">
            <h2>
              <t-button class="expert-card-title" size="small" theme="default" variant="text"
                :disabled="disabled" :aria-label="'使用' + expert.name" @click.stop="select(expert)">
                {{ expert.name }}
              </t-button>
            </h2>
            <p class="secondary-text">{{ expert.description }}</p>
          </div>
          <t-button size="small" variant="text" :theme="favorites.has(expert.name) ? 'primary' : 'default'"
            shape="square" :aria-pressed="favorites.has(expert.name)"
            :aria-label="(favorites.has(expert.name) ? '取消收藏' : '收藏') + expert.name" @click.stop="toggleFavorite(expert.name)">
            <template #icon><StarFilledIcon v-if="favorites.has(expert.name)" /><StarIcon v-else /></template>
          </t-button>
        </div>
      </t-card>
    </div>
    <t-empty v-else :description="query.trim() ? '没有找到匹配的专家。' : '收藏的专家会显示在这里。'" />
  </section>
</template>
