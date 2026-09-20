package com.example.springboot.entity;

import lombok.Data;

@Data
public class MangaMeta {
    private String id;
    private String name;
    /** 封面图片流地址，形如 /api/manga/{id}/cover */
    private String coverUrl;
    private Integer totalPages;
    /** 后端存储该漫画的本地路径 */
    private String path;
    /** 条目类型：manga 漫画 / novel 小说（epub、txt）/ pdf */
    private String type;
}
