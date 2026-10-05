package com.example.springboot.common;

import lombok.Data;

/**
 * 统一接口返回结果
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return of(ResultCode.SUCCESS, data);
    }

    public static <T> Result<T> error(ResultCode resultCode, T data) {
        return of(resultCode, data);
    }

    private static <T> Result<T> of(ResultCode resultCode, T data) {
        Result<T> r = new Result<>();
        r.code = resultCode.getCode();
        r.message = resultCode.getMessage();
        r.data = data;
        return r;
    }
}
