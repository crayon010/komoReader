package com.example.springboot.controller;

import com.example.springboot.common.Result;
import com.example.springboot.service.MangaService;
import com.example.springboot.util.ZipImageReader;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.springboot.entity.MangaMeta;

@RestController
@RequestMapping("/api")
public class MangaList {
    @Autowired
    private MangaService mangaService;

    @GetMapping("/manga")
    public Result<List<MangaMeta>> manga() throws IOException {
        return Result.success(mangaService.scan(null));
    }

    // 导入文件夹，请求体 {path}，path 缺省扫默认目录；裸对象返回
    public record ImportReq(String path) {}

    @PostMapping("/manga/import")
    public Map<String, Object> importManga(@RequestBody(required = false) ImportReq req) {
        String dir = req == null ? null : req.path();
        try {
            int n = mangaService.scan(dir).size();
            return Map.of("success", true, "message", "共导入 " + n + " 部");
        } catch (IOException | InvalidPathException e) {
            return Map.of("success", false, "message", "路径不存在");
        }
    }

    @GetMapping("/manga/{id}/cover")
    public void mangaCover(@PathVariable String id, HttpServletResponse res) throws IOException {
        Path zip = mangaService.getZipPath(id);

        List<String> images = ZipImageReader.listImages(zip);
        if (images != null && !images.isEmpty()) {
            byte[] data = ZipImageReader.readImage(zip, images.getFirst());

            res.setContentType(ZipImageReader.contentType(images.getFirst()));
            res.getOutputStream().write(data);
        }
    }

    // 书籍原始文件流（txt/epub/pdf 共用）：前端 BookView 用它做 iframe/fetch/epub.js 渲染
    @GetMapping("/manga/{id}/file")
    public void mangaFile(@PathVariable String id, HttpServletResponse res) throws IOException {
        Path file = mangaService.getZipPath(id);
        String name = file.getFileName().toString().toLowerCase();
        res.setContentType(name.endsWith(".pdf") ? "application/pdf"
                : name.endsWith(".txt") ? "text/plain; charset=utf-8"
                : "application/epub+zip");
        res.setContentLengthLong(Files.size(file));
        Files.copy(file, res.getOutputStream());
    }

    // 页目录：裸数组 [{"index":0},...]
    @GetMapping("/manga/{id}/pages")
    public List<Map<String, Integer>> mangaPages(@PathVariable String id) throws IOException {
        Path zip = mangaService.getZipPath(id);
        List<Map<String, Integer>> pages = new ArrayList<>();
        for (int i = 0; i < ZipImageReader.listImages(zip).size(); i++) {
            pages.add(Map.of("index", i));
        }
        return pages;
    }

    // 单页图片流
    @GetMapping("/manga/{id}/page")
    public void mangaPage(        @PathVariable String id,
                                            @RequestParam int index,
                                            HttpServletResponse res) throws IOException {
        Path zip = mangaService.getZipPath(id);
        List<String> images = ZipImageReader.listImages(zip);
        byte[] data = ZipImageReader.readImage(zip, images.get(index));
        res.setContentType(ZipImageReader.contentType(images.get(index)));
        res.getOutputStream().write(data);
    }
}

