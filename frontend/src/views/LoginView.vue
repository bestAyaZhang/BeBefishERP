<script setup lang="ts">
import { KeyRound, LogIn, LogOut, Send, ShieldCheck, Smartphone } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { loginWithPassword, loginWithSms, logout, sendSmsCode } from '../services/auth';
import type { LoginResult } from '../types/auth';

type LoginMode = 'password' | 'sms';

const TOKEN_KEY = 'bebefish_access_token';

const mode = ref<LoginMode>('password');
const mobile = ref('');
const password = ref('');
const smsCode = ref('');
const loading = ref(false);
const sendingCode = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const currentUser = ref<LoginResult | null>(null);
const accessToken = ref(localStorage.getItem(TOKEN_KEY) ?? '');

const primaryButtonText = computed(() => (mode.value === 'password' ? '密码登录' : '验证码登录'));

async function submitLogin() {
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const result =
      mode.value === 'password'
        ? await loginWithPassword({ mobile: mobile.value, password: password.value })
        : await loginWithSms({ mobile: mobile.value, smsCode: smsCode.value });
    currentUser.value = result;
    accessToken.value = result.accessToken;
    localStorage.setItem(TOKEN_KEY, result.accessToken);
    successMessage.value = '登录成功，已加载当前用户权限。';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    loading.value = false;
  }
}

async function handleSendCode() {
  sendingCode.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await sendSmsCode(mobile.value);
    successMessage.value = '验证码已发送，有效期 5 分钟。';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '验证码发送失败';
  } finally {
    sendingCode.value = false;
  }
}

async function handleLogout() {
  if (accessToken.value) {
    await logout(accessToken.value).catch(() => undefined);
  }
  accessToken.value = '';
  currentUser.value = null;
  localStorage.removeItem(TOKEN_KEY);
  successMessage.value = '已退出登录。';
}
</script>

<template>
  <main class="min-h-screen bg-mist text-ink">
    <div class="mx-auto grid min-h-screen w-full max-w-6xl grid-cols-1 lg:grid-cols-[1fr_420px]">
      <section class="flex flex-col justify-between px-6 py-8 sm:px-10 lg:px-12">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-md bg-pine text-white">
            <ShieldCheck class="h-5 w-5" aria-hidden="true" />
          </div>
          <div>
            <p class="text-sm font-medium text-slate-500">BeBefish ERP</p>
            <h1 class="text-xl font-semibold">内部电商经营系统</h1>
          </div>
        </div>

        <div class="my-12 max-w-2xl">
          <p class="text-sm font-medium text-pine">阶段 1：登录与权限基础</p>
          <h2 class="mt-4 text-4xl font-semibold leading-tight sm:text-5xl">
            库存、开单、财务和平台数据从同一套账号权限进入。
          </h2>
          <div class="mt-8 grid gap-3 text-sm text-slate-600 sm:grid-cols-2">
            <div class="border-l-4 border-pine bg-white px-4 py-3 shadow-sm">手机号作为主要登录标识</div>
            <div class="border-l-4 border-coral bg-white px-4 py-3 shadow-sm">密码 / 短信验证码双入口</div>
            <div class="border-l-4 border-slate-400 bg-white px-4 py-3 shadow-sm">角色权限加载菜单和操作</div>
            <div class="border-l-4 border-slate-400 bg-white px-4 py-3 shadow-sm">预留未来飞书账号绑定</div>
          </div>
        </div>

        <p class="text-xs text-slate-500">
          Dev demo: 13800138000 / Admin@123456，短信验证码默认 123456。
        </p>
      </section>

      <section class="flex items-center border-l border-slate-200 bg-white px-6 py-8 sm:px-10">
        <div class="w-full">
          <div class="mb-6">
            <p class="text-sm font-medium text-slate-500">账号登录</p>
            <h2 class="mt-1 text-2xl font-semibold">进入 ERP 工作台</h2>
          </div>

          <div class="mb-6 grid grid-cols-2 rounded-md bg-slate-100 p-1">
            <button
              type="button"
              class="flex h-10 items-center justify-center gap-2 rounded px-3 text-sm font-medium transition"
              :class="mode === 'password' ? 'bg-white text-pine shadow-sm' : 'text-slate-500'"
              @click="mode = 'password'"
            >
              <KeyRound class="h-4 w-4" aria-hidden="true" />
              密码
            </button>
            <button
              type="button"
              class="flex h-10 items-center justify-center gap-2 rounded px-3 text-sm font-medium transition"
              :class="mode === 'sms' ? 'bg-white text-pine shadow-sm' : 'text-slate-500'"
              @click="mode = 'sms'"
            >
              <Smartphone class="h-4 w-4" aria-hidden="true" />
              验证码
            </button>
          </div>

          <form class="space-y-4" @submit.prevent="submitLogin">
            <label class="block">
              <span class="mb-1 block text-sm font-medium text-slate-700">手机号</span>
              <input
                v-model.trim="mobile"
                data-testid="mobile-input"
                class="h-11 w-full rounded-md border border-slate-300 px-3 text-base outline-none transition focus:border-pine focus:ring-2 focus:ring-pine/20"
                autocomplete="tel"
                inputmode="tel"
                placeholder="请输入员工手机号"
              />
            </label>

            <label v-if="mode === 'password'" class="block">
              <span class="mb-1 block text-sm font-medium text-slate-700">密码</span>
              <input
                v-model="password"
                data-testid="password-input"
                class="h-11 w-full rounded-md border border-slate-300 px-3 text-base outline-none transition focus:border-pine focus:ring-2 focus:ring-pine/20"
                type="password"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
            </label>

            <div v-else class="grid grid-cols-[1fr_auto] gap-3">
              <label class="block">
                <span class="mb-1 block text-sm font-medium text-slate-700">短信验证码</span>
                <input
                  v-model.trim="smsCode"
                  data-testid="sms-code-input"
                  class="h-11 w-full rounded-md border border-slate-300 px-3 text-base outline-none transition focus:border-pine focus:ring-2 focus:ring-pine/20"
                  inputmode="numeric"
                  placeholder="6 位验证码"
                />
              </label>
              <button
                type="button"
                data-testid="send-code-button"
                class="mt-6 inline-flex h-11 min-w-28 items-center justify-center gap-2 rounded-md border border-pine px-3 text-sm font-medium text-pine transition hover:bg-pine hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="sendingCode || !mobile"
                @click="handleSendCode"
              >
                <Send class="h-4 w-4" aria-hidden="true" />
                {{ sendingCode ? '发送中' : '发送' }}
              </button>
            </div>

            <p v-if="errorMessage" class="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {{ errorMessage }}
            </p>
            <p v-if="successMessage" class="rounded-md border border-teal-200 bg-teal-50 px-3 py-2 text-sm text-teal-800">
              {{ successMessage }}
            </p>

            <button
              type="submit"
              data-testid="login-button"
              class="inline-flex h-11 w-full items-center justify-center gap-2 rounded-md bg-pine px-4 text-sm font-semibold text-white transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="loading || !mobile"
            >
              <LogIn class="h-4 w-4" aria-hidden="true" />
              {{ loading ? '登录中' : primaryButtonText }}
            </button>
          </form>

          <div v-if="currentUser" class="mt-6 border-t border-slate-200 pt-5 text-sm text-slate-600">
            <div class="flex items-center justify-between">
              <div>
                <p class="font-medium text-ink">{{ currentUser.mobile }}</p>
                <p class="mt-1">角色：{{ currentUser.roles.join(', ') }}</p>
              </div>
              <button
                type="button"
                class="inline-flex h-9 items-center justify-center gap-2 rounded-md border border-slate-300 px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
                @click="handleLogout"
              >
                <LogOut class="h-4 w-4" aria-hidden="true" />
                退出
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
