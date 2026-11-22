<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { MessagePlugin, Upload as TUpload } from 'tdesign-vue-next'
import type { UploadFile } from 'tdesign-vue-next'
import { api, ApiError, errorMessage, loadImage } from '../api'

const path = '/api/admin/recharges/qr'
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const files = ref<UploadFile[]>([])
const selected = computed(() => files.value[0]?.raw)
const currentUrl = ref<string>()
const pendingUrl = ref<string>()
const preview = computed(() => pendingUrl.value ?? currentUrl.value)
let previewRequest: AbortController | undefined

watch(selected, (file, _, cleanup) => {
  const url = file ? URL.createObjectURL(file) : undefined
  pendingUrl.value = url
  if (url) cleanup(() => URL.revokeObjectURL(url))
  error.value = ''
})

function setCurrent(image?: Blob) {
  if (currentUrl.value) URL.revokeObjectURL(currentUrl.value)
  currentUrl.value = image ? URL.createObjectURL(image) : undefined
}

async function open() {
  if (visible.value) return
  visible.value = true
  loading.value = true
  error.value = ''
  const request = new AbortController()
  previewRequest = request
  try {
    const image = await loadImage(path, request.signal)
    if (!request.signal.aborted) setCurrent(image)
  } catch (failure) {
    if (!request.signal.aborted && !(failure instanceof ApiError && failure.status === 404)) {
      error.value = errorMessage(failure)
    }
  } finally {
    if (previewRequest === request) {
      previewRequest = undefined
      loading.value = false
    }
  }
}

function reset() {
  visible.value = false
  previewRequest?.abort()
  previewRequest = undefined
  loading.value = false
  files.value = []
  setCurrent()
}

function close() {
  if (!saving.value) reset()
}

async function save() {
  const file = selected.value
  if (!file || loading.value || saving.value) return
  if (file.size > 10 * 1024 * 1024) {
    error.value = '收款二维码不能超过 10 MB。'
    return
  }
  const data = new FormData()
  data.set('file', file)
  saving.value = true
  error.value = ''
  try {
    await api<void>(path, { method: 'POST', body: data })
    if (visible.value) {
      setCurrent(file)
      files.value = []
      void MessagePlugin.success('收款二维码已保存')
    }
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(reset)
</script>

<template>
  <t-button variant="outline" aria-haspopup="dialog" @click="open">收款二维码</t-button>
  <t-dialog :visible="visible" header="收款二维码" width="520px"
    :confirm-btn="{ content: '保存收款码', loading: saving, disabled: loading || !selected }" cancel-btn="关闭"
    :close-on-overlay-click="!saving" :close-on-esc-keydown="!saving"
    @confirm="save" @close="close" @cancel="close">
    <div class="admin-qr-content">
      <t-loading v-if="loading" text="正在读取收款码" />
      <figure v-else-if="preview" class="admin-qr-preview">
        <img :src="preview" :alt="pendingUrl ? '待保存的收款码' : '当前收款码'" width="176" height="176">
        <figcaption>{{ pendingUrl ? '待保存的收款码' : '当前收款码' }}</figcaption>
      </figure>
      <p v-else-if="!error" class="secondary-text">收款二维码尚未设置。</p>
      <t-upload v-model="files" :auto-upload="false" :max="1" accept=".png,.jpg,.jpeg"
        :disabled="loading || saving" tips="选一张 PNG 或 JPG，不超过 10 MB。保存后替换当前收款码。" />
      <t-alert v-if="error" theme="error" :message="error" />
    </div>
  </t-dialog>
</template>
