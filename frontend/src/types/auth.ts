export type LoginMethod = 'password' | 'feishu';

export const ACCESS_TOKEN_STORAGE_KEY = 'bebefish_access_token';

export interface LoginResult {
  accessToken: string;
  employeeId?: number;
  mobile: string | null;
  displayName?: string;
  avatarUrl?: string | null;
  roles: string[];
  permissions: string[];
  loginMethod: LoginMethod;
  warnings?: string[];
}

export interface PasswordLoginPayload {
  mobile: string;
  password: string;
}

export interface FeishuLoginStatus {
  available: boolean;
  errorCode: string | null;
  message: string;
}
