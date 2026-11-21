<script setup lang="ts">
import { ref } from 'vue'
import { api, errorMessage, jsonBody } from '../api'
import type { Account } from '../types'

const props = defineProps<{ initialError: string }>()
const emit = defineEmits<{ authenticated: [account: Account] }>()

const register = ref(false)
const phone = ref('')
const password = ref('')
const pending = ref(false)
const error = ref(props.initialError)

function switchMode() {
  register.value = !register.value
  error.value = ''
  password.value = ''
}

async function submit() {
  error.value = ''
  const number = phone.value.trim()
  if (!/^1[3-9][0-9]{9}$/.test(number)) {
    error.value = '请输入 11 位大陆手机号。'
    return
  }
  const length = [...password.value].length
  if (length < 6 || length > 20) {
    error.value = '密码须为 6-20 个字符。'
    return
  }

  pending.value = true
  try {
    if (register.value) {
      await api<void>('/api/auth/register', jsonBody({ phone: number, password: password.value }))
      register.value = false
    }
    await api<void>('/api/auth/login', {
      method: 'POST',
      body: new URLSearchParams({ phone: number, password: password.value }),
    })
    emit('authenticated', await api<Account>('/api/auth/me'))
  } catch (failure) {
    error.value = errorMessage(failure)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <main class="auth-layout">
    <aside class="auth-side">
      <div class="brand-lockup"><img class="brand-mark" src="/brand.png" alt="" width="44" height="44">网红内容生成平台</div>
      <h1>用这个的真老牧师了</h1>
    </aside>
    <section class="auth-card">
      <h2>{{ register ? '创建个人账号' : '登录工作台' }}</h2>
      <t-form class="auth-form" :data="{ phone, password }" label-align="top" @submit="submit">
        <t-form-item label="手机号" name="phone" for="phone">
          <t-input v-field-id="'phone'" v-model="phone" name="phone" type="tel" autocomplete="tel" placeholder="11 位手机号"
            :maxlength="11" size="large" />
        </t-form-item>
        <t-form-item label="密码" name="password" for="password">
          <t-input v-field-id="'password'" v-model="password" name="password" type="password" size="large" placeholder="6-20 个字符"
            :autocomplete="register ? 'new-password' : 'current-password'" />
        </t-form-item>
        <t-alert v-if="error" theme="error" :message="error" />
        <t-button block size="large" type="submit" :loading="pending">
          {{ register ? '注册并进入工作台' : '登录' }}
        </t-button>
      </t-form>
      <t-button variant="text" theme="primary" :disabled="pending" class="auth-switch" @click="switchMode">
        {{ register ? '已有账号，去登录' : '创建账号' }}
      </t-button>
    </section>
  </main>
</template>
