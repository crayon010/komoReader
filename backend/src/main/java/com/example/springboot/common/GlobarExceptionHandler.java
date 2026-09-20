package com.example.springboot.common;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobarExceptionHandler {
    // 参数校验异常：提取各字段校验失败的提示
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.PARAM_ERROR, "参数错误：" + message);
    }

    // 捕获所有其他异常
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        return Result.error(ResultCode.ERROR, "服务器异常：" + e.getMessage());
    }

    // 可以单独捕获特定异常，例如IO异常
    @ExceptionHandler(IOException.class)
    public Result<?> handleIoException(IOException e) {
        return Result.error(ResultCode.ERROR,"文件读取失败：" + e.getMessage());
    }
}
