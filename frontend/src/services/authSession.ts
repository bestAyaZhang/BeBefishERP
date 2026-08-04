import { ref } from 'vue';
import type { LoginResult } from '../types/auth';

export const CURRENT_USER_STORAGE_KEY = 'bebefish_current_user';

function isLoginResult(value: unknown): value is LoginResult {
  if (!value || typeof value !== 'object') return false;
  const user = value as Partial<LoginResult>;
  return typeof user.accessToken === 'string'
    && typeof user.mobile === 'string'
    && Array.isArray(user.roles)
    && Array.isArray(user.permissions)
    && (user.loginMethod === 'password' || user.loginMethod === 'sms' || user.loginMethod === 'feishu');
}

function readStoredCurrentUser(): LoginResult | null {
  const serializedUser = localStorage.getItem(CURRENT_USER_STORAGE_KEY);
  if (!serializedUser) return null;

  try {
    const user = JSON.parse(serializedUser) as unknown;
    return isLoginResult(user) ? user : null;
  } catch {
    return null;
  }
}

export const currentUser = ref<LoginResult | null>(readStoredCurrentUser());

export function restoreCurrentUser() {
  currentUser.value = readStoredCurrentUser();
  return currentUser.value;
}

export function saveCurrentUser(user: LoginResult) {
  currentUser.value = {
    ...user,
    roles: [...user.roles],
    permissions: [...user.permissions]
  };
  localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify(currentUser.value));
}

export function clearCurrentUser() {
  currentUser.value = null;
  localStorage.removeItem(CURRENT_USER_STORAGE_KEY);
}
