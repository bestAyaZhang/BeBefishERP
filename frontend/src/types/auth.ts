export type LoginMethod = 'password' | 'sms' | 'feishu';

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

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
