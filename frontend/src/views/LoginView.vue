<script setup lang="ts">
import {
  BadgeCheck,
  Eye,
  EyeOff,
  LogIn,
  LogOut,
  ShieldCheck
} from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { beginFeishuLogin, getFeishuStatus, loginWithPassword, logout } from '../services/auth';
import { clearCurrentUser, saveCurrentUser } from '../services/authSession';
import { ACCESS_TOKEN_STORAGE_KEY, type LoginResult } from '../types/auth';

type ActiveField = 'none' | 'mobile' | 'password';

const mobile = ref('');
const password = ref('');
const showPassword = ref(false);
const activeField = ref<ActiveField>('none');
const lookX = ref(0);
const lookY = ref(0);
const lookingAtEachOther = ref(false);
const peeking = ref(false);
const loading = ref(false);
const temporaryExpanded = ref(false);
const feishuAvailable = ref(false);
const feishuStatusLoading = ref(true);
const feishuRedirecting = ref(false);
const feishuStatusMessage = ref('正在检查飞书登录状态…');
const errorMessage = ref('');
const successMessage = ref('');
const currentUser = ref<LoginResult | null>(null);
const accessToken = ref(localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY) ?? '');
const router = useRouter();
const route = useRoute();

const passwordState = computed(() => `${password.value ? 'filled' : 'empty'}-${showPassword.value ? 'visible' : 'hidden'}`);
const charactersAreWatchingForm = computed(() => activeField.value !== 'none' || password.value.length > 0);
const stageMotionStyle = computed(() => ({
  '--look-x': `${lookX.value}px`,
  '--look-y': `${lookY.value}px`
}));

let lookingTimer: number | undefined;
let peekingTimer: number | undefined;

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function handleMouseMove(event: MouseEvent) {
  const viewportWidth = window.innerWidth || 1;
  const viewportHeight = window.innerHeight || 1;
  lookX.value = Math.round(clamp((event.clientX - viewportWidth / 2) / 45, -8, 8));
  lookY.value = Math.round(clamp((event.clientY - viewportHeight / 2) / 55, -6, 6));
}

function triggerLookingAtEachOther() {
  window.clearTimeout(lookingTimer);
  lookingAtEachOther.value = true;
  lookingTimer = window.setTimeout(() => {
    lookingAtEachOther.value = false;
  }, 850);
}

function triggerPeeking() {
  window.clearTimeout(peekingTimer);
  peeking.value = true;
  peekingTimer = window.setTimeout(() => {
    peeking.value = false;
  }, 1200);
}

function getPupilStyle(scale = 1) {
  let x = lookX.value * scale;
  let y = lookY.value * scale;

  if (lookingAtEachOther.value) {
    x = 4 * scale;
    y = 3 * scale;
  }

  if (password.value && showPassword.value) {
    x = peeking.value ? 4 * scale : -4 * scale;
    y = peeking.value ? 5 * scale : -4 * scale;
  }

  return {
    transform: `translate(${Math.round(x)}px, ${Math.round(y)}px)`
  };
}

function setActiveField(field: ActiveField) {
  activeField.value = field;
  triggerLookingAtEachOther();
}

function clearActiveField(field: ActiveField) {
  if (activeField.value === field) {
    activeField.value = 'none';
  }
}

function togglePasswordVisibility() {
  showPassword.value = !showPassword.value;
  setActiveField('password');
  if (showPassword.value && password.value) {
    triggerPeeking();
  } else {
    peeking.value = false;
  }
}

watch(password, (newValue, oldValue) => {
  if (newValue !== oldValue && activeField.value === 'password') {
    triggerLookingAtEachOther();
  }
  if (newValue && showPassword.value) {
    triggerPeeking();
  }
  if (!newValue) {
    peeking.value = false;
  }
});

watch(showPassword, (visible) => {
  if (visible && password.value) {
    triggerPeeking();
  }
  if (!visible) {
    peeking.value = false;
  }
});

onMounted(async () => {
  window.addEventListener('mousemove', handleMouseMove);
  temporaryExpanded.value = route.query.temporary === '1';
  try {
    const status = await getFeishuStatus();
    feishuAvailable.value = status.available;
    feishuStatusMessage.value = status.message;
  } catch (error) {
    feishuAvailable.value = false;
    feishuStatusMessage.value = error instanceof Error ? error.message : '飞书登录暂不可用';
  } finally {
    feishuStatusLoading.value = false;
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleMouseMove);
  window.clearTimeout(lookingTimer);
  window.clearTimeout(peekingTimer);
});

async function submitLogin() {
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const result = await loginWithPassword({ mobile: mobile.value, password: password.value });
    currentUser.value = result;
    accessToken.value = result.accessToken;
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, result.accessToken);
    saveCurrentUser(result);
    successMessage.value = '登录成功，已加载当前用户权限。';
    await router.push({ name: 'workbench' });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    loading.value = false;
  }
}

function handleFeishuLogin() {
  if (feishuRedirecting.value || feishuStatusLoading.value || !feishuAvailable.value) return;
  feishuRedirecting.value = true;
  const destination = typeof route.query.redirect === 'string' ? route.query.redirect : '/workbench';
  sessionStorage.setItem('bebefish_post_login_redirect', destination);
  beginFeishuLogin();
}

async function handleLogout() {
  if (accessToken.value) {
    await logout(accessToken.value).catch(() => undefined);
  }
  accessToken.value = '';
  currentUser.value = null;
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  clearCurrentUser();
  successMessage.value = '已退出登录。';
  await router.push({ name: 'login' });
}
</script>

<template>
  <main class="min-h-screen bg-white text-ink">
    <div
      data-testid="template-login-shell"
      data-color-scheme="black-white"
      class="grid min-h-screen grid-cols-1 sm:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)]"
    >
      <section
        data-testid="animated-characters-panel"
        class="relative flex min-h-[420px] flex-col justify-between overflow-hidden bg-black p-6 text-white sm:min-h-screen lg:p-12"
      >
        <div class="relative z-20 flex items-center gap-2 text-lg font-semibold">
          <div class="flex min-w-0 items-center gap-4">
            <!-- Display the original artwork in white and blend its background into the dark panel. -->
            <img src="/brand/bebefish-horizontal.svg" alt="BeBefish" width="176" height="46" class="h-auto w-44 mix-blend-screen [filter:grayscale(1)_invert(1)_contrast(3)]" />
            <span class="shrink-0 border-l border-white/20 pl-4 text-xs font-medium tracking-[0.18em] text-white/60" aria-label="BeBefish ERP">ERP</span>
          </div>
        </div>

        <div class="relative z-20 flex flex-1 items-end justify-center py-8">
          <div
            data-testid="login-character-stage"
            :data-active-field="activeField"
            :data-look-x="lookX"
            :data-look-y="lookY"
            :data-looking-at-each-other="String(lookingAtEachOther)"
            :data-peeking="String(peeking)"
            :data-password-state="passwordState"
            :data-password-visible="String(showPassword)"
            class="relative h-[360px] w-[520px] origin-bottom scale-[0.62] sm:scale-[0.58] md:scale-[0.72] lg:scale-100"
            :style="stageMotionStyle"
          >
            <div
              data-testid="animated-character-purple"
              class="login-character-blink login-character-float absolute bottom-0 left-[70px] z-10 w-[180px] rounded-t-[10px] bg-[#6C3FF5] shadow-2xl transition-all duration-700 ease-in-out"
              :class="[
                password && !showPassword
                  ? 'h-[440px] translate-x-10 -skew-x-12'
                  : charactersAreWatchingForm
                    ? 'h-[430px] translate-x-6 -skew-x-6'
                    : 'h-[400px] translate-x-0 skew-x-0',
                { 'login-character-peek': peeking }
              ]"
            >
              <div
                class="absolute flex gap-8 transition-all duration-700 ease-in-out"
                :class="
                  password && showPassword
                    ? 'left-5 top-9'
                    : charactersAreWatchingForm
                      ? 'left-14 top-16'
                      : 'left-12 top-10'
                "
              >
                <span class="login-eye flex h-[18px] w-[18px] items-center justify-center rounded-full bg-white">
                  <span class="login-pupil block h-[7px] w-[7px] rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.9)"></span>
                </span>
                <span class="login-eye flex h-[18px] w-[18px] items-center justify-center rounded-full bg-white">
                  <span class="login-pupil block h-[7px] w-[7px] rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.9)"></span>
                </span>
              </div>
            </div>

            <div
              data-testid="animated-character-black"
              class="absolute bottom-0 left-[240px] z-20 h-[310px] w-[120px] rounded-t-md bg-[#2D2D2D] shadow-2xl transition-all duration-700 ease-in-out"
              :class="
                password && !showPassword
                  ? 'skew-x-3'
                  : charactersAreWatchingForm
                    ? 'translate-x-5 skew-x-6'
                    : 'translate-x-0 skew-x-0'
              "
            >
              <div
                class="absolute flex gap-6 transition-all duration-700 ease-in-out"
                :class="
                  password && showPassword
                    ? 'left-3 top-7'
                    : charactersAreWatchingForm
                      ? 'left-8 top-4'
                      : 'left-7 top-8'
                "
              >
                <span class="login-eye flex h-4 w-4 items-center justify-center rounded-full bg-white">
                  <span class="login-pupil block h-1.5 w-1.5 rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.75)"></span>
                </span>
                <span class="login-eye flex h-4 w-4 items-center justify-center rounded-full bg-white">
                  <span class="login-pupil block h-1.5 w-1.5 rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.75)"></span>
                </span>
              </div>
            </div>

            <div
              data-testid="animated-character-orange"
              class="absolute bottom-0 left-0 z-30 h-[200px] w-[240px] rounded-t-full bg-[#FF9B6B] shadow-2xl transition-all duration-700 ease-in-out"
              :class="password && showPassword ? 'skew-x-0' : charactersAreWatchingForm ? '-skew-x-3' : 'skew-x-0'"
            >
              <div
                class="absolute flex gap-8 transition-all duration-300 ease-out"
                :class="password && showPassword ? 'left-12 top-[85px]' : charactersAreWatchingForm ? 'left-24 top-[92px]' : 'left-20 top-[90px]'"
              >
                <span class="login-pupil block h-3 w-3 rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.65)"></span>
                <span class="login-pupil block h-3 w-3 rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.65)"></span>
              </div>
            </div>

            <div
              data-testid="animated-character-yellow"
              class="absolute bottom-0 left-[310px] z-40 h-[230px] w-[140px] rounded-t-full bg-[#E8D754] shadow-2xl transition-all duration-700 ease-in-out"
              :class="password && showPassword ? 'skew-x-0' : charactersAreWatchingForm ? 'skew-x-3' : 'skew-x-0'"
            >
              <div
                class="absolute flex gap-6 transition-all duration-300 ease-out"
                :class="password && showPassword ? 'left-5 top-9' : charactersAreWatchingForm ? 'left-14 top-10' : 'left-[52px] top-10'"
              >
                <span class="login-pupil block h-3 w-3 rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.65)"></span>
                <span class="login-pupil block h-3 w-3 rounded-full bg-[#2D2D2D]" :style="getPupilStyle(0.65)"></span>
              </div>
              <div
                class="absolute h-1 w-20 rounded-full bg-[#2D2D2D] transition-all duration-300"
                :class="password && showPassword ? 'left-3 top-[88px]' : charactersAreWatchingForm ? 'left-10 top-[88px]' : 'left-10 top-[88px]'"
              ></div>
            </div>

            <div
              data-testid="login-stage-ground"
              class="pointer-events-none absolute bottom-0 left-1/2 z-0 h-6 w-36 -translate-x-1/2 rounded-t-md bg-white/10 blur-[1px] transition-all duration-700"
              :class="password && !showPassword ? 'translate-y-3 opacity-60' : 'translate-y-5 opacity-40'"
            ></div>
          </div>
        </div>

        <div class="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.08)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.08)_1px,transparent_1px)] bg-[size:22px_22px]"></div>
        <div class="absolute left-10 top-24 h-36 w-36 rounded-full bg-white/10 blur-3xl"></div>
        <div class="absolute bottom-24 right-12 h-48 w-48 rounded-full bg-white/5 blur-3xl"></div>
      </section>

      <section
        data-testid="login-form-panel"
        class="flex items-center justify-center bg-white px-6 py-10 sm:px-8 lg:px-12"
      >
        <div class="w-full max-w-[420px]">
          <div class="mb-10 text-center">
            <div class="mx-auto mb-5 flex h-11 w-11 items-center justify-center rounded-md bg-pine/10 text-pine sm:hidden">
              <ShieldCheck class="h-5 w-5" aria-hidden="true" />
            </div>
            <p class="text-sm font-medium text-slate-500">内部电商经营系统</p>
            <h1 class="mt-2 text-3xl font-bold tracking-tight">欢迎回来</h1>
            <p class="mt-2 text-sm text-slate-500">正式员工使用飞书扫码，临时员工使用本地密码</p>
          </div>

          <button
            type="button"
            data-testid="feishu-login"
            class="inline-flex h-12 w-full items-center justify-center gap-2 rounded-md bg-[#3370ff] px-4 text-base font-semibold text-white transition hover:bg-[#2862e5] disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="feishuStatusLoading || !feishuAvailable || feishuRedirecting"
            @click="handleFeishuLogin"
          >
            <BadgeCheck class="h-5 w-5" aria-hidden="true" />
            {{ feishuRedirecting ? '正在前往飞书…' : feishuStatusLoading ? '正在检查飞书…' : '飞书扫码登录' }}
          </button>
          <p class="mt-2 text-center text-xs" :class="feishuAvailable ? 'text-slate-500' : 'text-amber-700'">
            {{ feishuStatusMessage }}
          </p>

          <div class="my-6 flex items-center gap-3 text-xs text-slate-400">
            <span class="h-px flex-1 bg-slate-200"></span><span>仅临时员工</span><span class="h-px flex-1 bg-slate-200"></span>
          </div>
          <button
            type="button"
            data-testid="temporary-login-toggle"
            class="mb-4 inline-flex h-10 w-full items-center justify-center rounded-md border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50"
            @click="temporaryExpanded = !temporaryExpanded"
          >
            {{ temporaryExpanded ? '收起临时员工登录' : '使用临时员工账号登录' }}
          </button>

          <form v-show="temporaryExpanded" data-testid="temporary-login-form" class="space-y-5" @submit.prevent="submitLogin">
            <label class="block space-y-2">
              <span data-testid="login-mobile-label" class="block text-sm font-medium uppercase text-slate-700">MOBILE</span>
              <input
                v-model.trim="mobile"
                data-testid="mobile-input"
                class="h-12 w-full rounded-md border border-slate-300 bg-white px-3 text-base outline-none transition placeholder:text-slate-400 focus:border-pine focus:ring-2 focus:ring-pine/20"
                autocomplete="tel"
                inputmode="tel"
                placeholder="请输入员工手机号"
                @focus="setActiveField('mobile')"
                @input="setActiveField('mobile')"
                @blur="clearActiveField('mobile')"
              />
            </label>

            <div data-testid="login-credential-fields" class="space-y-2">
              <label data-testid="login-password-label" class="block text-sm font-medium uppercase text-slate-700" for="login-password">
                PASSWORD
              </label>
              <div class="relative">
                <input
                  id="login-password"
                  v-model="password"
                  data-testid="password-input"
                  class="h-12 w-full rounded-md border border-slate-300 bg-white px-3 pr-12 text-base outline-none transition placeholder:text-slate-400 focus:border-pine focus:ring-2 focus:ring-pine/20"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                  placeholder="请输入密码"
                  @focus="setActiveField('password')"
                  @input="setActiveField('password')"
                  @blur="clearActiveField('password')"
                />
                <button
                  type="button"
                  data-testid="password-visibility-toggle"
                  class="absolute right-2 top-1/2 inline-flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 outline-none transition hover:bg-slate-100 hover:text-pine focus:ring-2 focus:ring-pine/20"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="togglePasswordVisibility"
                >
                  <EyeOff v-if="showPassword" class="h-5 w-5" aria-hidden="true" />
                  <Eye v-else class="h-5 w-5" aria-hidden="true" />
                  <span class="sr-only">{{ showPassword ? '隐藏密码' : '显示密码' }}</span>
                </button>
              </div>
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
              class="inline-flex h-12 w-full items-center justify-center gap-2 rounded-md bg-pine px-4 text-base font-semibold text-white transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="loading || !mobile"
            >
              <LogIn class="h-4 w-4" aria-hidden="true" />
              {{ loading ? '登录中' : '临时员工登录' }}
            </button>
          </form>

          <div v-if="currentUser" class="mt-6 border-t border-slate-200 pt-5 text-sm text-slate-600">
            <div class="flex items-center justify-between">
              <div>
                <p class="font-medium text-ink">{{ currentUser.displayName || currentUser.mobile || '飞书员工' }}</p>
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
