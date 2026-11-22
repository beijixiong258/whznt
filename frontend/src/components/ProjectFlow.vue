<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { LightbulbIcon, MoreIcon } from 'tdesign-icons-vue-next'
import { Card as TCard, Dropdown as TDropdown, MessagePlugin, type DropdownOption } from 'tdesign-vue-next'
import { api, ApiError, errorMessage, jsonBody, loadConversation } from '../api'
import { projectPath, running, stepLabels } from '../projects'
import type { Artifact, ProjectDetail, ProjectJob, ProjectStep, Topic } from '../projects'
import type { DraftReference } from '../subtitles'
import type { ConversationDetail } from '../types'
import WritePane from './WritePane.vue'

const props = defineProps<{ detail: ProjectDetail; active: boolean }>()
const emit = defineEmits<{
  changed: []; editState: [state: { dirty: boolean; busy: boolean }]; subtitle: [reference: DraftReference]
  backToMaterials: []
}>()
const steps = ['TOPICS', 'POSITION', 'STORY', 'TRAITS'] as const
const stepDetails = {
  TOPICS: {
    action: '帮我想选题',
    description: '想拍视频又不知道拍什么？点「帮我想选题」，AI 会给你十个题目，每条都写清讲给谁听、从哪里讲起、观众能带走什么。',
    next: '点「查看选题」看每个题目的讲法。看到一个你自己有话说的题目，就可以先试拍一条。',
  },
  POSITION: {
    action: '帮我想定位',
    description: '账号该讲什么、做给谁看？AI 会推荐一个方向，写好账号名、简介、置顶稿和首批视频标题，还会给两个备选。',
    next: '打开成果就能复制简介或置顶稿，也可以直接用这份定位想选题。',
  },
  STORY: {
    action: '帮我写故事',
    description: '想做自我介绍，或者讲一段自己的经历？AI 会把已有经历写成故事。资料少时，也能先写好一篇能直接念的开场稿。',
    next: '点「查看并编辑」读完整故事。可以改成自己的话，用来做自我介绍或拍视频。',
  },
  TRAITS: {
    action: '帮我找亮点',
    description: '觉得自己的经历很普通，想不到有什么好讲？点「帮我找亮点」，AI 会帮你找擅长的事、做过的选择和适合讲给别人听的经历。',
    next: '点「查看并编辑」，先挑一件自己最有话讲的事。可以从这件事开始写人物故事。',
  },
}
const jobLabels = { QUEUED: '排队中', RUNNING: '生成中', COMPLETED: '已完成', FAILED: '生成失败' }
const guide = computed(() => {
  if (props.detail.jobs.some(running)) {
    return { title: 'AI 正在后台帮你写', text: '写好会在这里打开正文。也可以先做别的，回来从「生成记录」查看。' }
  }
  if (props.detail.topics.length) {
    return { title: '选题有了，接下来写一条能照着念的稿', text: '在下方「口播稿」里挑一个题目，点旁边的「按题写口播」。' }
  }
  return {
    title: '第一次用，先从选题开始',
    text: '直接点下面的「帮我想选题」，空着资料也能开始。想先弄清账号讲什么，就看旁边的「IP 定位」。',
  }
})
const existing = computed(() => new Map(props.detail.artifacts.filter(row => row.step !== 'SCRIPT')
  .map(row => [row.step, row] as const)))
const scripts = computed(() => props.detail.artifacts.filter(row => row.step === 'SCRIPT'))
const importing = ref<ProjectStep | null>(null)
const importText = ref('')
type Generation = { step: ProjectStep; artifact?: Artifact; topic?: Topic; requestId: string }
const generating = ref<Generation | null>(null)
const instructions = ref('')
const duration = ref(150)
const busy = ref(false)
const dialogError = ref('')
const generationError = ref('')
const result = shallowRef<{ detail: ConversationDetail; step: ProjectStep; version?: number; fromMaterials: boolean }>()
const backLabel = computed(() => result.value?.fromMaterials ? '返回资料与版本' : '返回分步创作')
const requestedJob = ref<Pick<ProjectJob, 'id' | 'step'>>()
const readingResult = ref(false)
const resultState = reactive({ dirty: false, busy: false })
const resultLocked = computed(() => resultState.dirty || resultState.busy)
const scriptActionsDisabled = computed(() => resultLocked.value || busy.value || isGenerating('SCRIPT'))
const confirmingReturn = ref(false)
const resultRegion = ref<HTMLElement>()
const stepsRegion = ref<HTMLElement>()
const topicsRegion = ref<HTMLElement>()
let resultRequest: AbortController | undefined

watch(resultState, () => emit('editState', { ...resultState }))
watch([() => props.active, () => props.detail.jobs, requestedJob], ([active, jobs, requested]) => {
  if (!active || requested === undefined) return
  const job = jobs.find(row => row.id === requested.id)
  if (!job || running(job)) return

  requestedJob.value = undefined
  if (job.state === 'FAILED') {
    generationError.value = job.error || '生成没有完成，请再试一次。'
    void MessagePlugin.error(generationError.value)
  } else if (job.conversationId !== null) {
    if (resultLocked.value) {
      void MessagePlugin.success('新成果已保存，可从分步创作打开')
    } else {
      void openResult(job.step, job.conversationId)
    }
  }
})
onBeforeUnmount(() => {
  resultRequest?.abort()
  emit('editState', { dirty: false, busy: false })
})

function isGenerating(step: ProjectStep) {
  return requestedJob.value?.step === step || props.detail.jobs.some(job => job.step === step && running(job))
}

function beginImport(step: ProjectStep) {
  importing.value = step
  importText.value = ''
  dialogError.value = ''
}

function beginGeneration(step: ProjectStep, artifact?: Artifact, topic?: Topic) {
  generating.value = { step, artifact, topic, requestId: crypto.randomUUID() }
  instructions.value = ''
  duration.value = 150
  dialogError.value = ''
}

function close() {
  if (busy.value) return
  importing.value = null
  generating.value = null
  dialogError.value = ''
}

async function saveImported() {
  if (importing.value === null || busy.value) return
  if (!importText.value.trim()) {
    dialogError.value = '请粘贴已有成果。'
    return
  }
  busy.value = true
  try {
    await api<Artifact>(projectPath(props.detail.project.id) + '/artifacts',
      jsonBody({ step: importing.value, content: importText.value }))
    importing.value = null
    emit('changed')
    void MessagePlugin.success('已录入成果')
  } catch (failure) {
    dialogError.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}

async function queue(selected: Generation, customized = false) {
  if (busy.value || isGenerating(selected.step)) return
  busy.value = true
  dialogError.value = ''
  generationError.value = ''
  try {
    const job = await api<ProjectJob>(projectPath(props.detail.project.id) + '/jobs', jsonBody({
      requestId: selected.requestId,
      step: selected.step,
      artifactId: selected.artifact?.id,
      baseVersionId: selected.artifact?.currentVersion.id,
      topicVersionId: selected.topic?.versionId,
      topicIndex: selected.topic?.index,
      durationSeconds: customized && selected.step === 'SCRIPT' ? duration.value : undefined,
      instructions: customized ? instructions.value : undefined,
    }))
    requestedJob.value = { id: job.id, step: job.step }
    generating.value = null
    emit('changed')
    void MessagePlugin.success('已开始后台生成')
  } catch (failure) {
    if (customized) dialogError.value = errorMessage(failure)
    else generationError.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}

function generate(step: ProjectStep, artifact?: Artifact, topic?: Topic) {
  void queue({ step, artifact, topic, requestId: crypto.randomUUID() })
}

function menuOptions(artifact?: Artifact): DropdownOption[] {
  return [
    { value: 'settings', content: '生成设置' },
    artifact ? { value: 'regenerate', content: '重做这一步' } : { value: 'import', content: '粘贴已有成果' },
  ]
}

function chooseAction(option: DropdownOption, step: ProjectStep) {
  const artifact = existing.value.get(step)
  if (option.value === 'regenerate') generate(step, artifact)
  else if (option.value === 'settings') beginGeneration(step, artifact)
  else if (option.value === 'import') beginImport(step)
}

function openArtifact(row: Artifact | undefined) {
  if (row) void openResult(row.step, row.conversationId)
}

async function openResult(step: ProjectStep, conversationId: number, version?: number,
                          fromMaterials = result.value?.fromMaterials ?? false) {
  resultRequest?.abort()
  const request = new AbortController()
  resultRequest = request
  readingResult.value = true
  generationError.value = ''
  try {
    const detail = await loadConversation(conversationId, request.signal)
    if (request.signal.aborted) return
    if (!detail.draft) throw new ApiError(404, '这份成果还没有可打开的正文。')
    if (resultLocked.value) return
    result.value = { detail, step, version, fromMaterials }
    if (props.active) void nextTick(() => resultRegion.value?.scrollIntoView({ block: 'start' }))
  } catch (failure) {
    if (!request.signal.aborted) generationError.value = errorMessage(failure)
  } finally {
    if (resultRequest === request) {
      resultRequest = undefined
      readingResult.value = false
    }
  }
}

function openSavedResult(artifact: Artifact) {
  return openResult(artifact.step, artifact.conversationId, undefined, true)
}

defineExpose({ openSavedResult })

function closeResult() {
  const fromMaterials = result.value?.fromMaterials
  resultRequest?.abort()
  result.value = undefined
  confirmingReturn.value = false
  generationError.value = ''
  if (fromMaterials) emit('backToMaterials')
  else void nextTick(() => stepsRegion.value?.scrollIntoView({ block: 'start' }))
}

function scrollToTopics() {
  topicsRegion.value?.scrollIntoView({ block: 'start' })
}

function back() {
  if (resultState.busy) return
  if (resultState.dirty) confirmingReturn.value = true
  else closeResult()
}

function discardAndBack() {
  resultState.dirty = false
  closeResult()
}
</script>

<template>
  <section class="project-flow">
    <t-alert v-if="generationError" theme="error" :message="generationError" />
    <t-loading v-if="readingResult" text="正在打开成果" />
    <div v-if="result" ref="resultRegion" class="project-result">
      <WritePane :key="`${result.detail.draft?.currentVersion.version.id}:${result.version ?? 'current'}`"
        :initial="result.detail" :initial-version="result.version" :show-subtitles="result.step === 'SCRIPT'"
        :back-label="backLabel" inline
        @back="back" @saved="emit('changed')" @dirty="resultState.dirty = $event" @busy="resultState.busy = $event"
        @subtitle="emit('subtitle', $event)">
        <template #next="{ disabled }">
          <t-button v-if="result.step === 'POSITION'" :loading="isGenerating('TOPICS')"
            :disabled="disabled || busy || readingResult || isGenerating('TOPICS')"
            @click="generate('TOPICS', existing.get('TOPICS'))">
            {{ existing.has('TOPICS') ? '用当前定位更新选题' : '用当前定位想选题' }}
          </t-button>
          <t-button v-if="result.step === 'TOPICS' && detail.topics.length" :disabled="disabled"
            @click="scrollToTopics">选一条写口播</t-button>
        </template>
      </WritePane>
    </div>
    <template v-else>
      <div ref="stepsRegion" class="project-guide" aria-live="polite">
        <LightbulbIcon class="project-guide-icon" aria-hidden="true" />
        <div>
          <p class="project-guide-title">{{ guide.title }}</p>
          <p class="secondary-text">{{ guide.text }}</p>
        </div>
      </div>
      <div class="steps-grid">
        <t-card v-for="step in steps" :key="step" :title="stepLabels[step]" class="project-step"
          body-class-name="project-step-body"
          :class="{ 'project-step-featured': step === 'TOPICS' && !existing.has(step) }">
          <template #actions>
            <t-tag v-if="isGenerating(step)" theme="primary" variant="light">生成中</t-tag>
            <t-tag v-else-if="existing.has(step)" theme="success" variant="light">已保存</t-tag>
          </template>
          <div class="step-description">
            <p v-if="existing.has(step)" class="step-result">{{ existing.get(step)?.currentVersion.title }}</p>
            <p class="secondary-text">{{ existing.has(step) ? stepDetails[step].next : stepDetails[step].description }}</p>
          </div>
          <div class="project-actions project-step-actions">
            <t-button v-if="existing.has(step)" variant="outline"
              @click="openArtifact(existing.get(step))">{{ step === 'TOPICS' ? '查看选题' : '查看并编辑' }}</t-button>
            <t-button v-else :variant="step === 'TOPICS' ? 'base' : 'outline'" :disabled="busy || isGenerating(step)"
              @click="generate(step)">{{ stepDetails[step].action }}</t-button>
            <t-dropdown :options="menuOptions(existing.get(step))" trigger="click" placement="bottom-right"
              :max-column-width="160" :disabled="busy || isGenerating(step)" @click="chooseAction($event, step)">
              <t-button variant="text" theme="default" shape="square" :aria-label="stepLabels[step] + '的更多操作'"
                :disabled="busy || isGenerating(step)">
                <template #icon><MoreIcon /></template>
              </t-button>
            </t-dropdown>
          </div>
        </t-card>
      </div>
    </template>
    <section v-if="(!result || result.step === 'TOPICS') && (detail.topics.length || scripts.length)"
      ref="topicsRegion" class="project-topics">
      <t-card title="口播稿">
        <template #actions>
          <t-tag v-if="isGenerating('SCRIPT')" theme="primary" variant="light">正在写口播稿</t-tag>
        </template>
        <div v-if="detail.topics.length">
          <p class="secondary-text">挑一个你想拍的题目，点「按题写口播」。AI 写好后会打开可以照着念的稿子。</p>
          <div v-for="topic in detail.topics" :key="topic.versionId + ':' + topic.index" class="project-row">
            <span>{{ topic.title }}</span>
            <div class="project-actions">
              <t-button size="small" variant="outline" :disabled="scriptActionsDisabled"
                @click="generate('SCRIPT', undefined, topic)">按题写口播</t-button>
              <t-button size="small" variant="text" theme="default" :disabled="scriptActionsDisabled"
                @click="beginGeneration('SCRIPT', undefined, topic)">生成设置</t-button>
            </div>
          </div>
        </div>
        <div v-if="scripts.length" class="scripts-list">
          <h3>已保存口播稿</h3>
          <div v-for="script in scripts" :key="script.id" class="project-row">
            <span>{{ script.topic }}</span>
            <div class="project-actions">
              <t-button size="small" variant="outline" :disabled="resultLocked"
                @click="openArtifact(script)">打开稿件</t-button>
              <t-button size="small" variant="outline" :disabled="scriptActionsDisabled"
                @click="generate('SCRIPT', script)">重做这篇</t-button>
              <t-button size="small" variant="text" theme="default" :disabled="scriptActionsDisabled"
                @click="beginGeneration('SCRIPT', script)">生成设置</t-button>
            </div>
          </div>
        </div>
      </t-card>
    </section>
    <t-card v-if="!result && detail.jobs.length" title="生成记录">
      <div>
        <div v-for="job in detail.jobs" :key="job.id" class="project-row">
          <div>
            <b>{{ stepLabels[job.step] }}</b>
            <p v-if="job.error" class="secondary-text">{{ job.error }}</p>
          </div>
          <t-tag :theme="job.state === 'FAILED' ? 'danger' : running(job) ? 'primary' : 'success'"
            variant="light">{{ jobLabels[job.state] }}</t-tag>
          <t-button v-if="job.conversationId !== null" size="small" variant="text"
            @click="openResult(job.step, job.conversationId, job.versionId ?? undefined)">查看结果</t-button>
        </div>
      </div>
    </t-card>

    <t-dialog :visible="confirmingReturn" :header="backLabel + '？'" body="还有未保存的修改。"
      :confirm-btn="{ content: '放弃修改并返回', theme: 'danger' }" cancel-btn="继续编辑"
      @confirm="discardAndBack" @close="confirmingReturn = false" @cancel="confirmingReturn = false" />

    <t-dialog :visible="importing !== null"
      :header="importing ? '录入已有' + stepLabels[importing] : '录入已有成果'"
      :confirm-btn="{ content: '保存', loading: busy }" @confirm="saveImported" @cancel="close" @close="close">
      <t-form :data="{ content: importText }" label-align="top">
        <t-form-item label="成果正文" name="content" for="artifact-content">
          <t-textarea v-field-id="'artifact-content'" v-model="importText" :maxlength="30000"
            :autosize="{ minRows: 6, maxRows: 12 }"
            :placeholder="importing === 'TOPICS' ? '每行一个选题标题，也可以用 Markdown 二级标题' : '粘贴现有正文'" />
        </t-form-item>
      </t-form>
      <t-alert v-if="dialogError" theme="error" :message="dialogError" />
    </t-dialog>
    <t-dialog :visible="generating !== null" :header="generating ? '生成' + stepLabels[generating.step] : '生成成果'"
      :confirm-btn="{ content: '开始生成', loading: busy }"
      @confirm="generating && queue(generating, true)" @cancel="close" @close="close">
      <p v-if="generating?.topic || generating?.artifact?.topic" class="selected-topic">
        {{ generating?.topic?.title ?? generating?.artifact?.topic }}
      </p>
      <t-form :data="{ instructions, duration }" label-align="top">
        <t-form-item v-if="generating?.step === 'SCRIPT'" label="口播时长（秒）" name="duration" for="script-duration">
          <t-input-number v-field-id="'script-duration'" v-model="duration" :min="15" :max="600" :step="15" />
        </t-form-item>
        <t-form-item label="补充要求" name="instructions" for="generation-instructions">
          <t-textarea v-field-id="'generation-instructions'" v-model="instructions" :maxlength="1500"
            :autosize="{ minRows: 3, maxRows: 7 }"
            placeholder="选填，例如突出一次实际转折" />
        </t-form-item>
      </t-form>
      <t-alert v-if="dialogError" theme="error" :message="dialogError" />
    </t-dialog>
  </section>
</template>
