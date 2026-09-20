package com.example.springboot.controller;

import com.example.springboot.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class User {
    @PostMapping("/user")
    public Result<String> user() {
        return Result.success("1111");

    }
    @PostMapping("/post")
    public Result<String> postTest() {
        return Result.success("post");
    }
}
