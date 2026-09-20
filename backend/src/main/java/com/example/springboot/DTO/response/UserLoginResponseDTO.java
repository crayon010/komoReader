package com.example.springboot.DTO.response;

import lombok.Data;

@Data
public class UserLoginResponseDTO {
    private String token;      // 登录成功返回令牌，前端存到localStorage，后续请求带上鉴权
    private String roleType;   // 用户角色：admin/user 用来前端控制页面权限
    private String userInfo;   // 用户基础信息（可以是json字符串，也可以改成对象）
}
