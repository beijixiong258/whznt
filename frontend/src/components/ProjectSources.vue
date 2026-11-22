<script setup lang="ts">
import { ref, watch } from 'vue'
import { Card as TCard } from 'tdesign-vue-next'
import { api, errorMessage } from '../api'
import { materialLabels, projectPath, stepLabels } from '../projects'
import type { MaterialPassage, Sources } from '../projects'

const props = defineProps<{ projectId: number; versionId: number }>()
const emit = defineEmits<{ open: [conversationId: number, versionId: number]; loaded: [ids: number[]] }>()
const sources = ref<Sources | null>(null)
const focused = ref<MaterialPassage | null>(null)
const loading = ref(false)
const error = ref('')

watch(() => [props.projectId, props.versionId] as const, async ([projectId, versionId], _, invalidate) => {
  let stale = false
  invalidate(() => { stale = true })
  focused.value = null
  sources.value = null
  loading.value = true
  error.value = ''
  emit('loaded', [])
  try {
    const result = await api<Sources>(projectPath(projectId) + '/versions/' + versionId + '/sources')
    if (!stale) {
      sources.value = result
      emit('loaded', result.passages.filter(part => part.kind !== 'STYLE').map(part => part.id))
    }
  } catch (failure) {
    if (!stale) error.value = errorMessage(failure)
  } finally {
    if (!stale) loading.value = false
  }
}, { immediate: true })

function openCitation(id: number) {
  focused.value = sources.value?.passages.find(part => part.id === id) ?? null
}

defineExpose({ openCitation })
</script>

<template>
  <t-card title="这个版本的来源">
    <t-loading v-if="loading" text="正在读取来源" />
    <t-alert v-else-if="error" theme="error" :message="error" />
    <template v-else-if="sources">
      <p v-if="!sources.upstream.length && !sources.passages.length" class="secondary-text">此版本由手动录入。</p>
      <div v-for="upstream in sources.upstream" :key="upstream.versionId" class="project-row">
        <span>{{ stepLabels[upstream.step] }} · {{ upstream.title }}</span>
        <t-button size="small" variant="text" @click="emit('open', upstream.conversationId, upstream.versionId)">
          查看所用版本
        </t-button>
      </div>
      <div v-for="part in sources.passages" :key="part.id" class="project-row">
        <div>
          <span>{{ part.material }} · {{ part.location }}</span>
          <p class="secondary-text">{{ part.kind === 'STYLE' ? '风格参考' : '资料' + part.id }} · 资料版本 {{ part.versionId }}</p>
        </div>
        <t-button size="small" variant="text" :aria-label="'查看原文 ' + part.material + ' ' + part.location"
          @click="openCitation(part.id)">查看原文</t-button>
      </div>
    </template>
    <t-dialog :visible="focused !== null" :header="focused?.material ?? '引用原文'" :footer="false"
      width="min(620px, 92vw)" @close="focused = null">
      <div v-if="focused" class="source-text">
        <h3>{{ focused.location }} · {{ materialLabels[focused.kind] }} · 资料版本 {{ focused.versionId }}</h3>
        <p>{{ focused.content }}</p>
      </div>
    </t-dialog>
  </t-card>
</template>
