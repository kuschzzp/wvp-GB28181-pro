package com.genersoft.iot.vmp.auth.apikey.dto;

public record ApiKeyQuery(int page, int count) {

    public ApiKeyQuery {
        if (page < 1 || count < 1 || count > 200) {
            throw new IllegalArgumentException("分页参数不合法");
        }
    }
}
