<script setup lang="ts">
import {
  BarChart3,
  Boxes,
  Eye,
  EyeOff,
  KeyRound,
  LogIn,
  LogOut,
  Package,
  Send,
  ShieldCheck,
  Smartphone,
  WalletCards
} from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { loginWithPassword, loginWithSms, logout, sendSmsCode } from '../services/auth';
import type { LoginResult } from '../types/auth';

type LoginMode = 'password' | 'sms';

const TOKEN_KEY = 'bebefish_access_token';

const mode = ref<LoginMode>('password');
const mobile = ref('');
const password = ref('');
const smsCode = ref('');
const showPassword = ref(false);
const passwordFocused = ref(false);
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
  <main class="min-h-screen bg-mist px-4 py-6 text-ink sm:px-6 lg:px-8">
    <div
      class="mx-auto grid min-h-[calc(100vh-3rem)] w-full max-w-6xl grid-cols-1 overflow-hidden border border-slate-200 bg-white shadow-sm lg:grid-cols-[minmax(0,1fr)_430px]"
    >
      <section class="order-2 flex flex-col justify-between bg-mist px-5 py-6 sm:px-8 lg:order-1 lg:px-10">
        <div>
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-md bg-pine text-white">
              <ShieldCheck class="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <p class="text-sm font-medium text-slate-500">BeBefish ERP</p>
              <h1 class="text-xl font-semibold">内部电商经营系统</h1>
            </div>
          </div>

          <div class="mt-8 max-w-2xl">
            <p class="text-sm font-medium text-pine">阶段 1：登录与权限基础</p>
            <h2 class="mt-3 text-3xl font-semibold leading-tight sm:text-4xl">
              一个入口进入库存、开单、财务和平台数据。
            </h2>
            <div class="mt-5 grid gap-3 text-sm text-slate-600 sm:grid-cols-2">
              <div class="border-l-4 border-pine bg-white px-4 py-3 shadow-sm">手机号作为主要登录标识</div>
              <div class="border-l-4 border-coral bg-white px-4 py-3 shadow-sm">密码 / 短信验证码双入口</div>
              <div class="border-l-4 border-slate-400 bg-white px-4 py-3 shadow-sm">角色权限加载菜单和操作</div>
              <div class="border-l-4 border-slate-400 bg-white px-4 py-3 shadow-sm">预留未来飞书账号绑定</div>
            </div>
          </div>

          <div
            data-testid="login-character-stage"
            :data-password-visible="String(showPassword)"
            class="relative mt-8 min-h-[360px] overflow-hidden rounded-md border border-slate-200 bg-white px-4 py-4 shadow-sm sm:min-h-[330px] sm:px-5"
          >
            <div class="relative z-10 flex items-start justify-between gap-4">
              <div>
                <p class="text-xs font-medium text-slate-500">今日经营入口</p>
                <h3 class="mt-1 text-lg font-semibold text-ink">登录后按角色加载工作台</h3>
              </div>
              <div class="shrink-0 rounded-md border border-teal-100 bg-teal-50 px-3 py-2 text-xs font-medium text-pine">
                权限在线
              </div>
            </div>

            <div class="relative z-10 mt-5 grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
              <div class="rounded-md border border-slate-200 bg-slate-50 px-3 py-3">
                <Package class="mb-2 h-4 w-4 text-pine" aria-hidden="true" />
                <p class="font-semibold text-ink">库存</p>
                <p class="mt-1 text-xs text-slate-500">采购 / 组装 / 销售扣减</p>
              </div>
              <div class="rounded-md border border-slate-200 bg-slate-50 px-3 py-3">
                <BarChart3 class="mb-2 h-4 w-4 text-coral" aria-hidden="true" />
                <p class="font-semibold text-ink">平台汇总</p>
                <p class="mt-1 text-xs text-slate-500">淘宝 / 抖音 / 京东</p>
              </div>
              <div class="rounded-md border border-slate-200 bg-slate-50 px-3 py-3">
                <WalletCards class="mb-2 h-4 w-4 text-slate-700" aria-hidden="true" />
                <p class="font-semibold text-ink">财务</p>
                <p class="mt-1 text-xs text-slate-500">应收 / 应付 / 费用</p>
              </div>
            </div>

            <div class="absolute inset-x-0 bottom-0 h-20 bg-slate-100"></div>
            <div class="absolute bottom-16 left-6 h-9 w-20 rounded-md border border-slate-300 bg-white shadow-sm"></div>
            <div class="absolute bottom-24 left-14 h-8 w-16 rounded-md border border-slate-300 bg-coral/20 shadow-sm"></div>
            <div class="absolute bottom-16 right-8 h-10 w-24 rounded-md border border-slate-300 bg-white shadow-sm"></div>

            <div class="absolute inset-x-4 bottom-4 h-44">
              <div class="absolute bottom-0 left-1 h-32 w-24 rounded-t-md border border-teal-900/10 bg-pine shadow-md sm:left-8">
                <div class="mx-auto mt-7 flex w-12 justify-between">
                  <span
                    class="block h-2.5 w-2.5 rounded-full bg-white transition-transform"
                    :class="showPassword ? 'translate-x-1' : '-translate-x-1'"
                  ></span>
                  <span
                    class="block h-2.5 w-2.5 rounded-full bg-white transition-transform"
                    :class="showPassword ? 'translate-x-1' : '-translate-x-1'"
                  ></span>
                </div>
                <div class="mx-auto mt-5 h-1.5 w-10 rounded-full bg-white/75"></div>
                <div class="absolute -right-7 top-8 h-16 w-6 rounded-md bg-pine shadow-sm"></div>
              </div>

              <div
                class="absolute bottom-0 left-1/2 z-20 h-40 w-28 -translate-x-1/2 rounded-t-md border border-amber-900/10 bg-[#F2C14E] shadow-lg transition-transform"
                :class="passwordFocused ? '-translate-y-1' : 'translate-y-0'"
              >
                <div
                  class="absolute -top-10 left-1/2 flex -translate-x-1/2 items-center gap-2 whitespace-nowrap rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-600 shadow-sm"
                >
                  <KeyRound class="h-3.5 w-3.5 text-pine" aria-hidden="true" />
                  {{ showPassword ? '密码可见' : '密码已隐藏' }}
                </div>
                <div class="mx-auto mt-8 flex w-14 justify-between">
                  <span
                    class="block h-3 w-3 rounded-full bg-ink transition-transform"
                    :class="showPassword ? 'translate-x-1' : passwordFocused ? 'translate-x-0' : '-translate-x-1'"
                  ></span>
                  <span
                    class="block h-3 w-3 rounded-full bg-ink transition-transform"
                    :class="showPassword ? 'translate-x-1' : passwordFocused ? 'translate-x-0' : '-translate-x-1'"
                  ></span>
                </div>
                <div class="mx-auto mt-6 h-1.5 w-12 rounded-full bg-ink/60"></div>
                <div
                  class="absolute left-2 top-14 h-4 w-12 rounded-md bg-pine shadow-sm transition-transform"
                  :class="showPassword ? 'translate-y-10 rotate-6' : 'translate-y-0 rotate-0'"
                ></div>
                <div
                  class="absolute right-2 top-14 h-4 w-12 rounded-md bg-pine shadow-sm transition-transform"
                  :class="showPassword ? 'translate-y-10 -rotate-6' : 'translate-y-0 rotate-0'"
                ></div>
              </div>

              <div class="absolute bottom-0 right-1 h-32 w-24 rounded-t-md border border-slate-900/10 bg-slate-800 shadow-md sm:right-8">
                <div class="mx-auto mt-7 flex w-12 justify-between">
                  <span
                    class="block h-2.5 w-2.5 rounded-full bg-white transition-transform"
                    :class="showPassword ? 'translate-x-0' : 'translate-x-1'"
                  ></span>
                  <span
                    class="block h-2.5 w-2.5 rounded-full bg-white transition-transform"
                    :class="showPassword ? 'translate-x-0' : 'translate-x-1'"
                  ></span>
                </div>
                <div class="mx-auto mt-5 h-1.5 w-10 rounded-full bg-white/70"></div>
                <div class="absolute -left-7 top-8 h-16 w-6 rounded-md bg-slate-800 shadow-sm"></div>
              </div>

              <div class="absolute bottom-2 left-1/2 z-30 flex -translate-x-1/2 items-center gap-2 whitespace-nowrap rounded-md border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-600 shadow-sm">
                <Boxes class="h-3.5 w-3.5 text-coral" aria-hidden="true" />
                单仓库存同步
              </div>
            </div>
          </div>
        </div>

        <p class="mt-6 text-xs text-slate-500">
          Dev demo: 13800138000 / Admin@123456，短信验证码默认 123456。
        </p>
      </section>

      <section class="order-1 flex items-center bg-white px-5 py-6 sm:px-8 lg:order-2 lg:border-l lg:border-slate-200">
        <div class="w-full">
          <div class="mb-6 flex items-start justify-between gap-4">
            <div>
              <p class="text-sm font-medium text-slate-500">账号登录</p>
              <h2 class="mt-1 text-2xl font-semibold">进入 ERP 工作台</h2>
            </div>
            <div class="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-medium text-slate-600">
              手机号主登录
            </div>
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
                class="h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base outline-none transition focus:border-pine focus:ring-2 focus:ring-pine/20"
                autocomplete="tel"
                inputmode="tel"
                placeholder="请输入员工手机号"
              />
            </label>

            <div v-if="mode === 'password'">
              <label class="mb-1 block text-sm font-medium text-slate-700" for="login-password">密码</label>
              <div class="relative">
                <input
                  id="login-password"
                  v-model="password"
                  data-testid="password-input"
                  class="h-11 w-full rounded-md border border-slate-300 bg-white px-3 pr-12 text-base outline-none transition focus:border-pine focus:ring-2 focus:ring-pine/20"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                  placeholder="请输入密码"
                  @focus="passwordFocused = true"
                  @blur="passwordFocused = false"
                />
                <button
                  type="button"
                  data-testid="password-visibility-toggle"
                  class="absolute right-2 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 outline-none transition hover:bg-slate-100 hover:text-pine focus:ring-2 focus:ring-pine/20"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <EyeOff v-if="showPassword" class="h-4 w-4" aria-hidden="true" />
                  <Eye v-else class="h-4 w-4" aria-hidden="true" />
                  <span class="sr-only">{{ showPassword ? '隐藏密码' : '显示密码' }}</span>
                </button>
              </div>
            </div>

            <div v-else class="grid grid-cols-[1fr_auto] gap-3">
              <label class="block">
                <span class="mb-1 block text-sm font-medium text-slate-700">短信验证码</span>
                <input
                  v-model.trim="smsCode"
                  data-testid="sms-code-input"
                  class="h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base outline-none transition focus:border-pine focus:ring-2 focus:ring-pine/20"
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
