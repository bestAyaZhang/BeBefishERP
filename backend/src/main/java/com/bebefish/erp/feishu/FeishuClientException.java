package com.bebefish.erp.feishu;

public class FeishuClientException extends RuntimeException {
    public FeishuClientException(String message) {
        super(message);
    }

    public FeishuClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
