<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { TimeIcon } from 'tdesign-icons-vue-next'
import { Card as TCard, Upload as TUpload } from 'tdesign-vue-next'
import type { UploadFile } from 'tdesign-vue-next'
import { api, errorMessage } from '../api'
import { currency, type RechargeOrder } from '../recharge'
import RechargeRecordsTable from './RechargeRecordsTable.vue'

interface RechargeSettings {
  qrAvailable: boolean
  presets: number[]
  points: number
}

const props = defineProps<{ active: boolean }>()
const settings = ref<RechargeSettings>()
const orders = ref<RechargeOrder[]>([])
const amount = ref(200)
const customAmount = ref(false)
const note = ref('')
const receipts = ref<UploadFile[]>([])
const accepted = ref<RechargeOrder>()
const loading = ref(false)
const recordsVisible = ref(false)
const readingRecords = ref(false)
const recordsError = ref('')
const submitting = ref(false)
const error = ref('')
const requestKey = ref<string>()
const qrUrl = ref('/api/recharge/qr')

watch([() => receipts.value[0]?.raw, amount, note], () => { requestKey.value = undefined })

async function loadSettings() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    settings.value = await api<RechargeSettings>('/api/recharge/settings')
    qrUrl.value = '/api/recharge/qr?v=' + Date.now()
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    loading.value = false
  }
}

async function openRecords() {
  recordsVisible.value = true
  if (readingRecords.value) return
  void loadSettings()
  readingRecords.value = true
  recordsError.value = ''
  const previousOrders = orders.value
  try {
    const nextOrders = await api<RechargeOrder[]>('/api/recharge/orders')
    if (orders.value === previousOrders) orders.value = nextOrders
  } catch (failure) {
    recordsError.value = errorMessage(failure)
  } finally {
    readingRecords.value = false
  }
}

function selectAmount(value: number) {
  amount.value = value
  customAmount.value = false
}

function enterCustomAmount() {
  customAmount.value = true
  void nextTick(() => document.getElementById('recharge-amount')?.focus())
}

async function submit() {
  if (submitting.value || !settings.value?.qrAvailable) return
  const receipt = receipts.value[0]?.raw
  if (!receipt || !Number.isInteger(amount.value) || amount.value < 1 || amount.value > 1000000) {
    error.value = receipt ? '请输入 1 至 1,000,000 之间的整数金额。' : '请先上传付款截图。'
    return
  }
  requestKey.value ??= crypto.randomUUID()
  const body = new FormData()
  body.set('requestId', requestKey.value)
  body.set('amount', String(amount.value))
  body.set('note', note.value)
  body.set('receipt', receipt)
  error.value = ''
  accepted.value = undefined
  submitting.value = true
  try {
    const order = await api<RechargeOrder>('/api/recharge/orders', { method: 'POST', body })
    orders.value = [order, ...orders.value.filter(row => row.id !== order.id)].sort((a, b) => b.id - a.id)
    if (order.state === 'REJECTED') {
      requestKey.value = undefined
      error.value = '这笔申请已被拒绝。可在「申请记录」查看原因，更正后重新提交。'
      return
    }
    accepted.value = order
    receipts.value = []
    note.value = ''
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    submitting.value = false
  }
}

onMounted(() => void loadSettings())
watch(() => props.active, value => { if (value) void loadSettings() })
</script>

<template>
  <section class="utility-pane recharge-pane">
    <header class="page-heading recharge-heading">
      <div>
        <h1>充值申请</h1>
        <p v-if="settings" class="recharge-balance">积分余额 <strong>{{ settings.points.toLocaleString('zh-CN') }}</strong></p>
      </div>
      <t-button size="large" theme="default" variant="outline" aria-haspopup="dialog" @click="openRecords">
        <template #icon><TimeIcon /></template>
        申请记录
      </t-button>
    </header>
    <t-alert v-if="accepted" theme="success" title="申请已提交" :message="'申请编号 ' + accepted.number" />
    <t-card class="recharge-card">
      <t-loading v-if="loading && !settings" text="正在读取收款信息" />
      <t-alert v-else-if="error && !settings" theme="error" :message="error" />
      <t-form v-else-if="settings" class="recharge-form" :data="{ amount, note }" label-align="top" @submit="submit">
        <t-alert v-if="!settings.qrAvailable" theme="info" message="收款二维码尚未配置。配置后即可提交申请。" />
        <t-form-item label="选充值金额" name="amount" required-mark>
          <div class="recharge-amount">
            <div class="recharge-presets" role="group" aria-label="充值金额">
              <t-button v-for="preset in settings.presets" :key="preset" type="button" size="large"
                :variant="!customAmount && amount === preset ? 'base' : 'outline'"
                :aria-pressed="!customAmount && amount === preset" :disabled="submitting" @click="selectAmount(preset)">
                {{ preset.toLocaleString('zh-CN') }} 元
              </t-button>
              <t-button type="button" size="large" :variant="customAmount ? 'base' : 'outline'"
                :aria-pressed="customAmount" :disabled="submitting" @click="enterCustomAmount">自定义金额</t-button>
            </div>
            <div v-if="customAmount" class="recharge-custom-amount">
              <label for="recharge-amount">输入整数金额（元）</label>
              <t-input-number v-field-id="'recharge-amount'" v-model="amount" theme="normal" size="large"
                :min="1" :max="1000000" :decimal-places="0" :disabled="submitting" />
            </div>
          </div>
        </t-form-item>
        <div class="recharge-details" :class="{ 'recharge-details-with-code': settings.qrAvailable }">
          <div v-if="settings.qrAvailable" class="payment-code">
            <img :src="qrUrl" alt="收款二维码" width="160" height="160">
            <div>
              <h2>扫码支付</h2>
              <strong class="recharge-pay-amount" aria-live="polite">{{ currency.format(amount || 0) }}</strong>
              <p class="secondary-text">付款后上传截图，管理员核对后入账。</p>
            </div>
          </div>
          <div class="recharge-receipt">
            <t-form-item label="上传付款截图" required-mark>
              <t-upload v-model="receipts" :auto-upload="false" :max="1" accept=".png,.jpg,.jpeg"
                :disabled="!settings.qrAvailable || submitting" tips="PNG 或 JPG，不超过 10 MB" />
            </t-form-item>
            <t-form-item label="备注（选填）" name="note" for="recharge-note">
              <t-textarea v-field-id="'recharge-note'" v-model="note" :maxlength="300" :disabled="submitting"
                :autosize="{ minRows: 2, maxRows: 5 }" placeholder="可以填写付款备注，方便核对" />
            </t-form-item>
          </div>
        </div>
        <t-alert v-if="error" theme="error" :message="error" />
        <div class="recharge-submit">
          <t-button type="submit" size="large" :disabled="!settings.qrAvailable" :loading="submitting">提交充值申请</t-button>
        </div>
      </t-form>
    </t-card>
    <t-dialog :visible="recordsVisible" header="我的申请记录" width="min(1120px, calc(100vw - 48px))"
      :confirm-btn="null" cancel-btn="关闭" @close="recordsVisible = false" @cancel="recordsVisible = false">
      <t-alert v-if="recordsError" theme="error" :message="recordsError" />
      <RechargeRecordsTable v-else-if="recordsVisible" :rows="orders" :loading="readingRecords"
        max-height="min(460px, 58vh)" empty="暂无充值申请" />
    </t-dialog>
  </section>
</template>
