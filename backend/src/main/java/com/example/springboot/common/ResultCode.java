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

    // 参数相关错误
    PARAM_ERROR(400, "参数错误"),
    PARAM_MISSING(401, "缺少必要参数"),

    // 文件相关错误
    FILE_ERROR(501, "文件操作失败"),
    FILE_NOT_FOUND(502, "文件不存在");

    private final Integer code;
    private final String message;
}
