<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi, validateUsername, validatePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { getErrorText } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const tab = ref('login') // login | register
const submitting = ref(false)
const form = reactive({ username: '', password: '', nickname: '' })
const errors = reactive({})
const serverError = ref('')

function switchTab(t) {
  tab.value = t
  Object.keys(errors).forEach(k => delete errors[k])
  serverError.value = ''
}

function clientValidate() {
  errors.username = tab.value === 'login' ? (form.username ? '' : '请输入用户名') : validateUsername(form.username)
  errors.password = tab.value === 'login' ? (form.password ? '' : '请输入密码') : validatePassword(form.password)
  return !errors.username && !errors.password
}

async function submit() {
  serverError.value = ''
  if (!clientValidate() || submitting.value) return
  submitting.value = true
  try {
    const data =
      tab.value === 'login'
        ? await authApi.login({ username: form.username, password: form.password })
        : await authApi.register({
            username: form.username,
            password: form.password,
            nickname: form.nickname || undefined,
          })
    store.setAuth(data)
    router.push(route.query.redirect || '/')
  } catch (e) {
    serverError.value = getErrorText(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="max-w-sm mx-auto pt-12">
    <div class="bg-surface rounded-2xl shadow-sm border border-line p-6">
      <h1 class="text-lg font-semibold text-center mb-1">欢迎来到 Scenary</h1>
      <p class="text-center text-xs text-ink-soft mb-5">分享此刻的山与海</p>

      <!-- 双 Tab -->
      <div class="grid grid-cols-2 mb-5 rounded-full bg-mute p-1 text-sm select-none">
        <button
          data-testid="tab-login"
          class="h-8 rounded-full transition"
          :class="tab === 'login' ? 'bg-surface shadow font-medium text-brand-600' : 'text-ink-soft'"
          @click="switchTab('login')"
        >
          登录
        </button>
        <button
          data-testid="tab-register"
          class="h-8 rounded-full transition"
          :class="tab === 'register' ? 'bg-surface shadow font-medium text-brand-600' : 'text-ink-soft'"
          @click="switchTab('register')"
        >
          注册
        </button>
      </div>

      <form class="space-y-3" @submit.prevent="submit">
        <div>
          <label for="login-username" class="sr-only">用户名</label>
          <input
            id="login-username"
            v-model.trim="form.username"
            data-testid="input-username"
            placeholder="用户名（4~20 位字母/数字/下划线）"
            class="w-full h-11 px-3.5 rounded-xl border border-line focus:border-brand-300 focus:ring-2 focus:ring-brand-100 dark:focus:ring-brand-900 outline-none text-sm placeholder:text-neutral-400 dark:placeholder:text-neutral-500 bg-transparent"
            autocomplete="username"
            :aria-invalid="Boolean(errors.username)"
            :aria-describedby="errors.username ? 'username-error' : undefined"
          />
          <p v-if="errors.username" id="username-error" role="alert" class="mt-1 text-xs text-red-500">{{ errors.username }}</p>
        </div>

        <div>
          <label for="login-password" class="sr-only">密码</label>
          <input
            id="login-password"
            v-model="form.password"
            data-testid="input-password"
            type="password"
            :placeholder="tab === 'register' ? '密码（8 位以上，含字母和数字）' : '密码'"
            class="w-full h-11 px-3.5 rounded-xl border border-line focus:border-brand-300 focus:ring-2 focus:ring-brand-100 dark:focus:ring-brand-900 outline-none text-sm placeholder:text-neutral-400 dark:placeholder:text-neutral-500 bg-transparent"
            autocomplete="current-password"
            :aria-invalid="Boolean(errors.password)"
            :aria-describedby="errors.password ? 'password-error' : undefined"
          />
          <p v-if="errors.password" id="password-error" role="alert" class="mt-1 text-xs text-red-500">{{ errors.password }}</p>
        </div>

        <label v-if="tab === 'register'" for="register-nickname" class="sr-only">昵称</label>
        <input
          v-if="tab === 'register'"
          id="register-nickname"
          v-model.trim="form.nickname"
          data-testid="input-nickname"
          placeholder="昵称（可选，默认同用户名）"
          class="w-full h-11 px-3.5 rounded-xl border border-line focus:border-brand-300 focus:ring-2 focus:ring-brand-100 dark:focus:ring-brand-900 outline-none text-sm placeholder:text-neutral-400 dark:placeholder:text-neutral-500 bg-transparent"
        />

        <p v-if="serverError" data-testid="server-error" role="alert" class="text-xs text-red-500">{{ serverError }}</p>

        <button
          data-testid="btn-submit"
          type="submit"
          :disabled="submitting"
          class="w-full h-11 rounded-xl bg-brand-500 hover:bg-brand-600 active:scale-[0.99] disabled:opacity-60 transition text-white font-medium"
        >
          {{ submitting ? '处理中…' : tab === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
    </div>
  </section>
</template>
