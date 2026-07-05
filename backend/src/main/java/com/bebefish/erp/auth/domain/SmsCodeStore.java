package com.bebefish.erp.auth.domain;

public interface SmsCodeStore {
    void issueCode(String mobile);

    boolean verifyAndConsume(String mobile, String smsCode);
}
