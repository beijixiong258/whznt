<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { AddIcon, SearchIcon } from 'tdesign-icons-vue-next'
import { Card as TCard, MessagePlugin, Table as TTable } from 'tdesign-vue-next'
import type { AdminPage, AdminUser, PointCredit } from '../admin'
import { api, errorMessage, jsonBody } from '../api'
import { dates } from '../recharge'

defineProps<{ operatorName: string }>()

const path = '/api/admin/users'
const columns = [
  { colKey: 'phone', title: '用户', width: '28%' },
  { colKey: 'points', title: '积分余额', width: '16%' },
  { colKey: 'enabled', title: '账号状态', width: '17%' },
  { colKey: 'actions', title: '操作', align: 'right' as const },
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
const enabling = ref<number>()
const action = ref<'create' | 'edit' | 'grant' | 'history' | 'disable'>()
const selected = ref<AdminUser>()
const form = reactive({ phone: '', password: '', points: 0, note: '' })
const credits = ref<PointCredit[]>([])
const readingCredits = ref(false)
let grantRequestId = ''
const titles = { create: '新增用户', edit: '编辑用户', grant: '添加积分', history: '积分记录', disable: '停用账号' }
const confirm = computed(() => {
  if (action.value === 'disable') return { content: '停用', theme: 'danger' as const, loading: saving.value }
  return { content: action.value === 'grant' ? '确认入账' : '保存', loading: saving.value }
})

async function load(next = page.value, query = search.value) {
  if (loading.value || enabling.value !== undefined) return
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

async function changeStatus(userPath: string, enabled: boolean) {
  const updated = await api<AdminUser>(userPath, jsonBody({ enabled }, 'PATCH'))
  rows.value = rows.value.map(row => row.id === updated.id ? updated : row)
  void MessagePlugin.success(enabled ? '账号已启用' : '账号已停用')
}

async function enable(user: AdminUser) {
  if (loading.value || saving.value || enabling.value !== undefined) return
  enabling.value = user.id
  error.value = ''
  try {
    await changeStatus(path + '/' + user.id, true)
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    enabling.value = undefined
  }
}

async function save() {
  const user = selected.value
  const mode = action.value
  if (!mode || mode === 'history' || saving.value || enabling.value !== undefined || (mode !== 'create' && !user)) return
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
        phone: form.phone.trim(), password: form.password || undefined,
      }, 'PATCH'))
    } else if (mode === 'disable') {
      await changeStatus(userPath, false)
    } else {
      await api<PointCredit>(userPath + '/credits', jsonBody({
        requestId: grantRequestId, points: form.points, note: form.note.trim(),
      }))
    }
    action.value = undefined
    form.password = ''
    if (mode !== 'disable') {
      void MessagePlugin.success(mode === 'grant' ? '积分已入账' : '已保存')
      await load(mode === 'create' ? 1 : page.value, mode === 'create' ? '' : search.value)
    }
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
    <t-card>
      <div class="admin-toolbar">
        <div class="admin-actions">
          <label for="admin-phone-search" class="sr-only">搜索手机号</label>
          <t-input v-field-id="'admin-phone-search'" v-model="phone" clearable :maxlength="11"
            :disabled="enabling !== undefined" placeholder="搜索手机号" @enter="load(1, phone.trim())" @clear="load(1, '')">
            <template #prefix-icon><SearchIcon /></template>
          </t-input>
          <t-button theme="default" variant="outline" :disabled="loading || enabling !== undefined"
            @click="load(1, phone.trim())">搜索</t-button>
        </div>
        <t-button :disabled="enabling !== undefined" @click="open('create')">
          <template #icon><AddIcon /></template>
          新增用户
        </t-button>
      </div>
      <t-alert v-if="error" class="admin-feedback" theme="error" :message="error" />
      <div class="admin-table">
        <t-table :data="rows" :columns="columns" :loading="loading" row-key="id" hover empty="没有找到用户">
          <template #phone="{ row }">
            <strong v-if="row.username" class="admin-user-name">{{ row.username }}</strong>
            <p :class="['admin-number', row.username ? 'secondary-text' : 'admin-user-name']">{{ row.phone }}</p>
          </template>
          <template #points="{ row }"><span class="admin-number">{{ row.points.toLocaleString('zh-CN') }}</span></template>
          <template #enabled="{ row }">
            <t-tag :theme="row.enabled ? 'success' : 'default'" variant="light">{{ row.enabled ? '正常' : '已停用' }}</t-tag>
          </template>
          <template #actions="{ row }">
            <div class="admin-row-actions">
              <t-button variant="text" theme="default" :disabled="enabling !== undefined" @click="open('edit', row)">编辑</t-button>
              <t-button variant="text" theme="default" :disabled="enabling !== undefined" @click="openHistory(row)">积分记录</t-button>
              <t-button variant="text" :theme="row.enabled ? 'danger' : 'primary'" :loading="enabling === row.id"
                :disabled="loading || saving || enabling !== undefined"
                @click="row.enabled ? open('disable', row) : enable(row)">{{ row.enabled ? '停用' : '启用' }}</t-button>
              <t-button variant="outline" :disabled="enabling !== undefined" @click="open('grant', row)">添加积分</t-button>
            </div>
          </template>
        </t-table>
      </div>
      <div v-if="page > 1 || hasMore" class="admin-actions admin-pages">
        <t-button size="small" variant="outline" :disabled="loading || enabling !== undefined || page === 1"
          @click="load(page - 1)">上一页</t-button>
        <span>第 {{ page }} 页</span>
        <t-button size="small" variant="outline" :disabled="loading || enabling !== undefined || !hasMore"
          @click="load(page + 1)">下一页</t-button>
      </div>
    </t-card>
    <t-dialog :visible="!!action" :header="action ? titles[action] : ''" :confirm-btn="confirm"
      cancel-btn="取消" @confirm="save" @close="close" @cancel="close">
      <template v-if="action === 'history'" #footer>
        <t-button theme="default" variant="outline" @click="close">关闭</t-button>
      </template>
      <t-form v-if="action === 'create' || action === 'edit'" :data="form" label-align="top" @submit="save">
        <t-form-item label="手机号" for="admin-user-phone" required-mark>
          <t-input v-field-id="'admin-user-phone'" v-model="form.phone" :maxlength="11" :disabled="saving" />
        </t-form-item>
        <t-form-item :label="action === 'edit' ? '新密码（选填）' : '密码'" for="admin-user-password">
          <t-input v-field-id="'admin-user-password'" v-model="form.password" type="password" :maxlength="40"
            autocomplete="new-password" :disabled="saving" placeholder="6–20 个字符" />
        </t-form-item>
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
        <p>{{ selected?.phone }} · 余额 {{ credits[0]?.balanceAfter ?? selected?.points }} 积分</p>
        <t-loading v-if="readingCredits" text="读取积分记录" />
        <p v-else-if="!credits.length && !dialogError" class="secondary-text">还没有积分记录。</p>
        <div v-for="credit in credits" :key="credit.id" class="utility-row">
          <div>
            <strong>{{ credit.note }}</strong>
            <p class="secondary-text">{{ dates.format(new Date(credit.createdAt)) }} · 操作者 {{ operatorName }}</p>
          </div>
          <span>+{{ credit.points }} · 余额 {{ credit.balanceAfter }}</span>
        </div>
      </div>
      <p v-else-if="action === 'disable'">停用 {{ selected?.phone }} 后，该账号将退出登录。可在用户列表中重新启用。</p>
      <t-alert v-if="dialogError" class="admin-probe" theme="error" :message="dialogError" />
    </t-dialog>
  </section>
</template>
