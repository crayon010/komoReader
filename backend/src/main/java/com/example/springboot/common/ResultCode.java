package com.example.springboot.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 请求状态码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    PARAM_ERROR(400, "参数错误");

    private final Integer code;
    private final String message;
}
