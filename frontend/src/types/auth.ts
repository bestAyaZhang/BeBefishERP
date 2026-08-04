export type LoginMethod = 'password' | 'sms' | 'feishu';

export const ACCESS_TOKEN_STORAGE_KEY = 'bebefish_access_token';

export interface LoginResult {
  accessToken: string;
  mobile: string;
  roles: string[];
  permissions: string[];
  loginMethod: LoginMethod;
}

export interface PasswordLoginPayload {
  mobile: string;
  password: string;
}

export interface SmsLoginPayload {
  mobile: string;
  smsCode: string;
}
