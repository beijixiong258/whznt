<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Card as TCard, Link as TLink, MessagePlugin } from 'tdesign-vue-next'
import { api, errorMessage, jsonBody } from '../api'
import { pending, subtitlePath } from '../subtitles'
import type { Cue, SubtitleDetail } from '../subtitles'

const props = defineProps<{ detail: SubtitleDetail }>()
const emit = defineEmits<{ changed: [detail: SubtitleDetail]; editState: [state: { dirty: boolean; busy: boolean }] }>()
const player = ref<HTMLVideoElement>()
const time = ref(0)
const cues = ref<Cue[]>([])
const saving = ref(false)
const error = ref('')
const playbackError = ref('')
const source = computed(() => subtitlePath(props.detail.task.id))
const versionQuery = computed(() => '?version=' + props.detail.version)
const editing = computed(() => !saving.value && !pending(props.detail.task.exportState))
const changed = computed(() => JSON.stringify(cues.value) !== JSON.stringify(props.detail.cues))
const activeIndex = computed(() => cues.value.findIndex(cue => cue.startMs <= time.value && time.value < cue.endMs))
const caption = computed(() => cues.value[activeIndex.value]?.text)

watch(() => props.detail.version, () => {
  cues.value = props.detail.cues.map(cue => ({ ...cue }))
}, { immediate: true })
watch([changed, saving], ([dirty, busy]) => emit('editState', { dirty, busy }), { immediate: true })

function seek(ms: number) {
  if (!player.value) return
  player.value.currentTime = Math.min(ms, props.detail.durationMs) / 1000
  time.value = ms
}

function setTime(cue: Cue, field: 'startMs' | 'endMs', seconds: unknown) {
  if (typeof seconds === 'number' && Number.isFinite(seconds)) cue[field] = Math.round(seconds * 1000)
}

function addCue() {
  const start = cues.value.at(-1)?.endMs ?? 0
  if (start >= props.detail.durationMs) {
    error.value = '请先调短末条字幕，留出新字幕的时间。'
    return
  }
  cues.value.push({ startMs: start, endMs: Math.min(start + 1500, props.detail.durationMs), text: '' })
  error.value = ''
}

async function send(action: 'save' | 'export') {
  if (!editing.value) return
  saving.value = true
  error.value = ''
  try {
    const body = action === 'save'
      ? { version: props.detail.version, cues: cues.value }
      : { version: props.detail.version }
    const next = await api<SubtitleDetail>(source.value + (action === 'save' ? '/timeline' : '/export'),
      jsonBody(body, action === 'save' ? 'PUT' : 'POST'))
    cues.value = next.cues.map(cue => ({ ...cue }))
    emit('changed', next)
    void MessagePlugin.success(action === 'save' ? '时间轴已保存' : '视频开始后台导出')
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <t-card :title="detail.task.name">
    <p v-if="detail.referenceTitle" class="secondary-text subtitle-reference">逐字稿 · {{ detail.referenceTitle }}</p>
    <div class="subtitle-preview">
      <video ref="player" :src="source + '/video'" controls playsinline preload="metadata"
        aria-label="字幕校对视频" @timeupdate="time = Math.round((player?.currentTime ?? 0) * 1000)"
        @error="playbackError = '浏览器无法播放这个视频，请检查视频编码。'" />
      <span v-if="caption" class="subtitle-caption">{{ caption }}</span>
    </div>
    <t-alert v-if="playbackError" theme="warning" :message="playbackError" />
    <div class="utility-inline subtitle-toolbar">
      <h2>校对字幕</h2>
      <t-button variant="text" size="small" :disabled="!editing" @click="addCue">添加一行</t-button>
    </div>
    <div class="subtitle-cues">
      <div v-for="(cue, index) in cues" :key="index" class="subtitle-cue"
        :class="{ 'subtitle-cue-active': index === activeIndex }">
        <div class="utility-inline cue-times">
          <t-button variant="text" size="small" :aria-label="'跳到第 ' + (index + 1) + ' 条字幕'"
            @click="seek(cue.startMs)">第 {{ index + 1 }} 条</t-button>
          <label :for="'cue-start-' + index">开始（秒）</label>
          <t-input-number v-field-id="'cue-start-' + index" :model-value="cue.startMs / 1000"
            :decimal-places="3" :step="0.1" :min="0" :max="detail.durationMs / 1000" :disabled="!editing"
            size="small" @change="setTime(cue, 'startMs', $event)" />
          <label :for="'cue-end-' + index">结束（秒）</label>
          <t-input-number v-field-id="'cue-end-' + index" :model-value="cue.endMs / 1000"
            :decimal-places="3" :step="0.1" :min="0" :max="detail.durationMs / 1000" :disabled="!editing"
            size="small" @change="setTime(cue, 'endMs', $event)" />
          <t-button theme="danger" variant="text" size="small" :disabled="!editing"
            :aria-label="'移除第 ' + (index + 1) + ' 条字幕'" @click="cues.splice(index, 1)">移除</t-button>
        </div>
        <label class="sr-only" :for="'cue-text-' + index">第 {{ index + 1 }} 条字幕正文</label>
        <t-textarea v-field-id="'cue-text-' + index" v-model="cue.text" :maxlength="300"
          :autosize="{ minRows: 1, maxRows: 4 }" :disabled="!editing" />
      </div>
    </div>
    <t-alert v-if="error || detail.exportError" theme="error" :message="error || detail.exportError" />
    <div class="utility-inline subtitle-toolbar">
      <t-button :disabled="!editing || !changed || !cues.length" :loading="saving" @click="send('save')">保存时间轴</t-button>
      <t-link v-if="!changed" :href="source + '/subtitle.srt' + versionQuery" theme="primary">下载 SRT</t-link>
      <t-link v-if="!changed && detail.task.exportState === 'READY'" :href="source + '/export.mp4' + versionQuery" theme="primary">
        下载带字幕的 MP4
      </t-link>
      <t-button v-else variant="outline" :disabled="!editing || changed"
        :loading="pending(detail.task.exportState)" @click="send('export')">
        {{ pending(detail.task.exportState) ? '正在导出' : '导出带字幕的 MP4' }}
      </t-button>
    </div>
  </t-card>
</template>
