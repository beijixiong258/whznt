<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Card as TCard, Link as TLink, Upload as TUpload } from 'tdesign-vue-next'
import type { UploadFile } from 'tdesign-vue-next'
import { api, errorMessage } from '../api'
import { currency, dates, rechargeLabels, rechargeThemes, type RechargeOrder } from '../recharge'

interface RechargeSettings {
  qrAvailable: boolean
  presets: number[]
  points: number
}

const props = defineProps<{ active: boolean }>()
const settings = ref<RechargeSettings>()
const orders = ref<RechargeOrder[]>([])
const amount = ref(200)
const note = ref('')
const receipts = ref<UploadFile[]>([])
const accepted = ref<RechargeOrder>()
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const requestKey = ref<string>()
const qrUrl = '/api/recharge/qr'

watch(() => receipts.value[0]?.raw, () => { requestKey.value = undefined })

async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  const previousOrders = orders.value
  try {
    const [nextSettings, nextOrders] = await Promise.all([
      api<RechargeSettings>('/api/recharge/settings'),
      api<RechargeOrder[]>('/api/recharge/orders'),
    ])
    settings.value = nextSettings
    if (orders.value === previousOrders) orders.value = nextOrders
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (submitting.value || !settings.value?.qrAvailable) return
  const receipt = receipts.value[0]?.raw
  if (!receipt || !Number.isFinite(amount.value) || amount.value < 0.01 || amount.value > 1000000) {
    error.value = receipt ? '请输入有效的充值金额。' : '请选择 PNG 或 JPG 付款截图。'
    return
  }
  requestKey.value ??= crypto.randomUUID()
  const body = new FormData()
  body.set('requestId', requestKey.value)
  body.set('amount', amount.value.toFixed(2))
  body.set('note', note.value)
  body.set('receipt', receipt)
  error.value = ''
  submitting.value = true
  try {
    const order = await api<RechargeOrder>('/api/recharge/orders', { method: 'POST', body })
    accepted.value = order
    orders.value = [order, ...orders.value.filter(row => row.id !== order.id)].sort((a, b) => b.id - a.id)
    receipts.value = []
    note.value = ''
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    submitting.value = false
  }
}

onMounted(() => void load())
watch(() => props.active, value => { if (value) void load() })
</script>

<template>
  <section class="utility-pane">
    <div class="page-heading">
      <h1>充值申请</h1>
      <p v-if="settings">积分余额 {{ settings.points.toLocaleString('zh-CN') }}</p>
    </div>
    <t-alert v-if="error" theme="error" :message="error" />
    <t-alert v-if="accepted" theme="success" title="申请已提交" :message="'申请编号 ' + accepted.number" />
    <div class="utility-grid recharge-grid">
      <t-card title="提交充值申请">
        <t-loading v-if="loading && !settings" text="读取收款信息" />
        <template v-else-if="settings">
          <t-alert v-if="!settings.qrAvailable" theme="info" message="收款二维码尚未配置，配置后即可提交。" />
          <t-form class="utility-form" :data="{ amount, note }" label-align="top" @submit="submit">
            <t-form-item label="金额（元）" name="amount" for="recharge-amount" required-mark>
              <div class="amount-preset">
                <div class="utility-inline">
                  <t-button v-for="preset in settings.presets" :key="preset" :variant="amount === preset ? 'base' : 'outline'"
                    :aria-pressed="amount === preset" :disabled="submitting" @click="amount = preset">{{ preset }} 元</t-button>
                </div>
                <t-input-number v-field-id="'recharge-amount'" v-model="amount" :min="0.01" :max="1000000"
                  :decimal-places="2" :disabled="submitting" />
              </div>
            </t-form-item>
            <div v-if="settings.qrAvailable" class="payment-code">
              <img :src="qrUrl" alt="收款二维码" width="168" height="168">
              <p class="secondary-text">扫码支付 {{ currency.format(amount || 0) }} 后，上传付款截图。管理员核对后会手动入账。</p>
            </div>
            <t-form-item label="付款截图" required-mark>
              <t-upload v-model="receipts" :auto-upload="false" :max="1" accept=".png,.jpg,.jpeg"
                :disabled="!settings.qrAvailable || submitting" tips="PNG 或 JPG，不超过 10 MB" />
            </t-form-item>
            <t-form-item label="备注（选填）" name="note" for="recharge-note">
              <t-textarea v-field-id="'recharge-note'" v-model="note" :maxlength="300" :disabled="submitting"
                placeholder="补充便于核对的信息" />
            </t-form-item>
            <t-button type="submit" :disabled="!settings.qrAvailable" :loading="submitting">提交申请</t-button>
          </t-form>
        </template>
      </t-card>
      <t-card title="我的申请记录">
        <p v-if="!orders.length" class="secondary-text">暂无充值申请。</p>
        <div v-for="order in orders" :key="order.id" class="utility-row">
          <div>
            <strong>{{ currency.format(order.amount) }}</strong>
            <strong v-if="order.creditedPoints !== null"> · +{{ order.creditedPoints.toLocaleString('zh-CN') }} 积分</strong>
            <p class="secondary-text">{{ dates.format(new Date(order.submittedAt)) }}</p>
            <p class="secondary-text order-number">{{ order.number }}</p>
            <p v-if="order.note" class="secondary-text">{{ order.note }}</p>
            <p v-if="order.reviewNote" class="secondary-text">{{ order.reviewNote }}</p>
          </div>
          <t-tag :theme="rechargeThemes[order.state]" variant="light">{{ rechargeLabels[order.state] }}</t-tag>
          <t-link :href="'/api/recharge/orders/' + order.id + '/receipt'" target="_blank" theme="primary">查看截图</t-link>
        </div>
      </t-card>
    </div>
  </section>
</template>
