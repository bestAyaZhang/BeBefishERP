<script setup lang="ts">
import { CheckCircle2, CircleAlert, LoaderCircle } from 'lucide-vue-next';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { exchangeFeishuTicket } from '../services/auth';
import { saveCurrentUser } from '../services/authSession';
import { ACCESS_TOKEN_STORAGE_KEY } from '../types/auth';

type ResultState = 'working' | 'success' | 'failure';

const route = useRoute();
const router = useRouter();
const state = ref<ResultState>('working');
const step = ref(0);
const errorMessage = ref('');
const warningMessage = ref('');
const steps = ['正在验证企业身份', '正在同步员工资料与角色', '登录成功'];
const title = computed(() => state.value === 'failure' ? '飞书登录未完成' : steps[Math.min(step.value, 2)]);

onMounted(async () => {
  const callbackError = typeof route.query.error === 'string' ? route.query.error : '';
  const ticket = typeof route.query.ticket === 'string' ? route.query.ticket : '';
  window.history.replaceState(window.history.state, '', route.path);
  if (callbackError || !ticket) {
    state.value = 'failure';
    errorMessage.value = messageFor(callbackError || 'FEISHU_CALLBACK_EXPIRED');
    return;
  }

  try {
    step.value = 1;
    const login = await exchangeFeishuTicket(ticket);
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, login.accessToken);
    saveCurrentUser(login);
    if (login.warnings?.includes('FEISHU_ROLE_SYNC_DEGRADED')) {
      warningMessage.value = '业务角色同步暂时不可用，本次已按基础权限登录。';
    }
    step.value = 2;
    state.value = 'success';
    const destination = sessionStorage.getItem('bebefish_post_login_redirect') || '/workbench';
    sessionStorage.removeItem('bebefish_post_login_redirect');
    window.setTimeout(() => void router.replace(destination), 600);
  } catch (error) {
    state.value = 'failure';
    errorMessage.value = error instanceof Error ? error.message : '登录结果不存在或已过期，请重新扫码。';
  }
});

function messageFor(code: string) {
  const messages: Record<string, string> = {
    FEISHU_STATE_INVALID: '登录状态已失效，请重新扫码。',
    FEISHU_TENANT_NOT_ALLOWED: '当前飞书企业不允许登录本系统。',
    FEISHU_IDENTITY_CONFLICT: '无法安全匹配员工账号，请联系管理员。',
    USER_DISABLED: '用户或员工已停用，请联系管理员。',
    FEISHU_CALLBACK_EXPIRED: '登录结果不存在或已过期，请重新扫码。'
  };
  return messages[code] ?? '飞书登录暂时不可用，请稍后重试。';
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-slate-50 p-6 text-[#25314d]">
    <section class="w-full max-w-lg rounded-xl border border-slate-200 bg-white p-8 shadow-sm" data-testid="feishu-result">
      <div class="flex items-center gap-3">
        <LoaderCircle v-if="state === 'working'" class="h-7 w-7 animate-spin text-[#536dff]" aria-hidden="true" />
        <CheckCircle2 v-else-if="state === 'success'" class="h-7 w-7 text-emerald-600" aria-hidden="true" />
        <CircleAlert v-else class="h-7 w-7 text-rose-600" aria-hidden="true" />
        <div>
          <p class="text-sm text-slate-500">BeBefish ERP</p>
          <h1 class="text-xl font-semibold">{{ title }}</h1>
        </div>
      </div>

      <ol v-if="state !== 'failure'" class="mt-8 space-y-3">
        <li v-for="(item, index) in steps" :key="item" class="flex items-center gap-3 text-sm">
          <span class="flex h-6 w-6 items-center justify-center rounded-full" :class="index <= step ? 'bg-[#536dff] text-white' : 'bg-slate-100 text-slate-400'">{{ index + 1 }}</span>
          <span :class="index <= step ? 'text-slate-800' : 'text-slate-400'">{{ item }}</span>
        </li>
      </ol>

      <p v-if="warningMessage" class="mt-6 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">{{ warningMessage }}</p>
      <div v-if="state === 'failure'" class="mt-6">
        <p class="rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{{ errorMessage }}</p>
        <div class="mt-5 flex flex-wrap gap-3">
          <a href="/api/auth/feishu/authorize" class="rounded-md bg-[#536dff] px-4 py-2 text-sm font-medium text-white">重新扫码</a>
          <RouterLink :to="{ name: 'login', query: { temporary: '1' } }" class="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium">返回临时员工登录</RouterLink>
        </div>
      </div>
    </section>
  </main>
</template>
