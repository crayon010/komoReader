package com.example.springboot.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IOException.class)
    public Result<?> handleIoException(IOException e) {
        return Result.error(ResultCode.ERROR, "文件读取失败：" + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        return Result.error(ResultCode.ERROR, "服务器异常：" + e.getMessage());
    }
}
