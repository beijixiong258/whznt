<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Checkbox as TCheckbox, MessagePlugin, Table as TTable } from 'tdesign-vue-next'
import type { AdminPage, AdminUser, PointCredit } from '../admin'
import { api, errorMessage, jsonBody } from '../api'
import { dates } from '../recharge'

const path = '/api/admin/users'
const columns = [
  { colKey: 'phone', title: '手机号', width: 150 },
  { colKey: 'points', title: '积分余额', width: 120 },
  { colKey: 'enabled', title: '状态', width: 100 },
  { colKey: 'actions', title: '操作', minWidth: 275 },
]
const rows = ref<AdminUser[]>([])
const phone = ref('')
const search = ref('')
const page = ref(1)
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')
const dialogError = ref('')
const saving = ref(false)
const action = ref<'create' | 'edit' | 'grant' | 'history' | 'disable'>()
const selected = ref<AdminUser>()
const form = reactive({ phone: '', password: '', enabled: true, points: 0, note: '' })
const credits = ref<PointCredit[]>([])
const readingCredits = ref(false)
let grantRequestId = ''
const titles = { create: '新增用户', edit: '编辑用户', grant: '添加积分', history: '积分记录', disable: '停用账号' }
const confirm = computed(() => {
  if (action.value === 'history') return false
  if (action.value === 'disable') return { content: '停用', theme: 'danger' as const, loading: saving.value }
  return { content: action.value === 'grant' ? '确认入账' : '保存', loading: saving.value }
})

async function load(next = page.value, query = search.value) {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ phone: query, page: String(next) })
    const result = await api<AdminPage<AdminUser>>(path + '?' + params)
    rows.value = result.items
    search.value = query
    page.value = next
    hasMore.value = result.hasMore
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    loading.value = false
  }
}

function open(mode: Exclude<typeof action.value, undefined>, user?: AdminUser) {
  selected.value = user
  form.phone = user?.phone ?? ''
  form.password = ''
  form.enabled = user?.enabled ?? true
  form.points = 0
  form.note = ''
  grantRequestId = mode === 'grant' ? crypto.randomUUID() : ''
  dialogError.value = ''
  action.value = mode
}

async function openHistory(user: AdminUser) {
  open('history', user)
  credits.value = []
  readingCredits.value = true
  try {
    credits.value = await api<PointCredit[]>(path + '/' + user.id + '/credits')
  } catch (failure) {
    dialogError.value = errorMessage(failure)
  } finally {
    readingCredits.value = false
  }
}

function close() {
  if (saving.value) return
  action.value = undefined
  form.password = ''
}

async function save() {
  const user = selected.value
  const mode = action.value
  if (!mode || mode === 'history' || saving.value || (mode !== 'create' && !user)) return
  if (mode === 'grant' && (!Number.isInteger(form.points) || form.points <= 0 || !form.note.trim())) {
    dialogError.value = '请填写正整数积分和入账原因。'
    return
  }
  saving.value = true
  dialogError.value = ''
  try {
    const userPath = path + '/' + user?.id
    if (mode === 'create') {
      await api<AdminUser>(path, jsonBody({ phone: form.phone.trim(), password: form.password }))
    } else if (mode === 'edit') {
      await api<AdminUser>(userPath, jsonBody({
        phone: form.phone.trim(), password: form.password || undefined, enabled: form.enabled,
      }, 'PATCH'))
    } else if (mode === 'disable') {
      await api<void>(userPath, { method: 'DELETE' })
    } else {
      await api<PointCredit>(userPath + '/credits', jsonBody({
        requestId: grantRequestId, points: form.points, note: form.note.trim(),
      }))
    }
    action.value = undefined
    form.password = ''
    void MessagePlugin.success(mode === 'grant' ? '积分已入账' : mode === 'disable' ? '账号已停用' : '已保存')
    await load(mode === 'create' ? 1 : page.value, mode === 'create' ? '' : search.value)
    if (mode === 'create') phone.value = ''
  } catch (failure) {
    dialogError.value = errorMessage(failure)
  } finally {
    saving.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="admin-panel" aria-label="用户管理">
    <div class="admin-toolbar">
      <div class="admin-actions">
        <label for="admin-phone-search" class="sr-only">搜索手机号</label>
        <t-input v-field-id="'admin-phone-search'" v-model="phone" clearable :maxlength="11"
          placeholder="输入手机号搜索" @enter="load(1, phone.trim())" />
        <t-button theme="default" variant="outline" :disabled="loading" @click="load(1, phone.trim())">搜索</t-button>
      </div>
      <t-button @click="open('create')">新增用户</t-button>
    </div>
    <t-alert v-if="error" theme="error" :message="error" />
    <div class="admin-table">
      <t-table :data="rows" :columns="columns" :loading="loading" row-key="id" empty="没有找到用户">
        <template #enabled="{ row }">
          <t-tag :theme="row.enabled ? 'success' : 'default'" variant="light">{{ row.enabled ? '正常' : '已停用' }}</t-tag>
        </template>
        <template #actions="{ row }">
          <div class="admin-actions">
            <t-button size="small" variant="text" @click="open('edit', row)">编辑</t-button>
            <t-button size="small" variant="text" @click="open('grant', row)">添加积分</t-button>
            <t-button size="small" variant="text" @click="openHistory(row)">积分记录</t-button>
            <t-button v-if="row.enabled" size="small" theme="danger" variant="text" @click="open('disable', row)">停用</t-button>
          </div>
        </template>
      </t-table>
    </div>
    <div v-if="page > 1 || hasMore" class="admin-actions admin-pages">
      <t-button size="small" variant="outline" :disabled="loading || page === 1" @click="load(page - 1)">上一页</t-button>
      <span>第 {{ page }} 页</span>
      <t-button size="small" variant="outline" :disabled="loading || !hasMore" @click="load(page + 1)">下一页</t-button>
    </div>
    <t-dialog :visible="!!action" :header="action ? titles[action] : ''" :confirm-btn="confirm"
      :cancel-btn="action === 'history' ? '关闭' : '取消'" @confirm="save" @close="close" @cancel="close">
      <t-form v-if="action === 'create' || action === 'edit'" :data="form" label-align="top" @submit="save">
        <t-form-item label="手机号" for="admin-user-phone" required-mark>
          <t-input v-field-id="'admin-user-phone'" v-model="form.phone" :maxlength="11" :disabled="saving" />
        </t-form-item>
        <t-form-item :label="action === 'edit' ? '新密码（选填）' : '密码'" for="admin-user-password">
          <t-input v-field-id="'admin-user-password'" v-model="form.password" type="password" :maxlength="40"
            autocomplete="new-password" :disabled="saving" placeholder="6–20 个字符" />
        </t-form-item>
        <t-checkbox v-if="action === 'edit'" v-model="form.enabled" :disabled="saving">允许登录</t-checkbox>
      </t-form>
      <t-form v-else-if="action === 'grant'" :data="form" label-align="top" @submit="save">
        <p class="admin-user-label">{{ selected?.phone }} · 余额 {{ selected?.points }} 积分</p>
        <t-form-item label="添加积分" for="admin-grant-points" required-mark>
          <t-input-number v-field-id="'admin-grant-points'" v-model="form.points" :min="1" :max="2147483647"
            :decimal-places="0" :disabled="saving" />
        </t-form-item>
        <t-form-item label="入账原因" for="admin-grant-note" required-mark>
          <t-input v-field-id="'admin-grant-note'" v-model="form.note" :maxlength="200" :disabled="saving" />
        </t-form-item>
      </t-form>
      <div v-else-if="action === 'history'" class="admin-ledger">
        <p>{{ selected?.phone }} · 余额 {{ selected?.points }} 积分</p>
        <t-loading v-if="readingCredits" text="读取积分记录" />
        <p v-else-if="!credits.length && !dialogError" class="secondary-text">还没有积分记录。</p>
        <div v-for="credit in credits" :key="credit.id" class="utility-row">
          <div>
            <strong>{{ credit.note }}</strong>
            <p class="secondary-text">{{ dates.format(new Date(credit.createdAt)) }} · 操作者 {{ credit.operatorId }}</p>
          </div>
          <span>+{{ credit.points }} · 余额 {{ credit.balanceAfter }}</span>
        </div>
      </div>
      <p v-else-if="action === 'disable'">停用 {{ selected?.phone }} 后，该账号将退出登录。编辑账号可恢复登录。</p>
      <t-alert v-if="dialogError" class="admin-probe" theme="error" :message="dialogError" />
    </t-dialog>
  </section>
</template>
