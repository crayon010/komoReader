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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.springboot.entity.MangaMeta;

@RestController
@RequestMapping("/api")
public class MangaList {
    @Autowired
    private MangaService mangaService;

    // root 缺省扫全部导入目录；传 root 只扫该目录（阅读库进入文件夹时用）
    @GetMapping("/manga")
    public Result<List<MangaMeta>> manga(@RequestParam(required = false) String root) throws IOException {
        List<MangaMeta> list = root == null || root.isBlank()
                ? mangaService.scanAll()
                : mangaService.scanRoot(Paths.get(root));
        return Result.success(list);
    }

    // 导入 = 新增一个根目录并扫描，请求体 {path}；裸对象返回
    public record ImportReq(String path) {}

    @PostMapping("/manga/import")
    public Map<String, Object> importManga(@RequestBody(required = false) ImportReq req) {
        if (req == null || req.path() == null || req.path().isBlank()) {
            return Map.of("success", false, "message", "请填写文件夹路径");
        }
        try {
            Path dir = mangaService.addRoot(req.path());
            int n = mangaService.scanRoot(dir).size();
            return Map.of("success", true, "message", "共导入 " + n + " 部");
        } catch (IOException | InvalidPathException e) {
            return Map.of("success", false, "message", "路径不存在或不是文件夹");
        }
    }

    // 导入的根目录列表（阅读库第一级），itemCount 为该目录最近一次扫描的读物条目数
    public record RootInfo(String name, String path, long itemCount) {}

    @GetMapping("/roots")
    public Result<List<RootInfo>> roots() {
        return Result.success(mangaService.getRoots().stream()
                .map(r -> new RootInfo(
                        r.getFileName() != null ? r.getFileName().toString() : r.toString(),
                        r.toString(),
                        mangaService.getItemCount(r)))
                .toList());
    }

    // 删除导入目录：不能再访问其内容，磁盘文件不删
    @DeleteMapping("/roots")
    public Map<String, Object> removeRoot(@RequestParam String path) {
        mangaService.removeRoot(path);
        return Map.of("success", true, "message", "已删除");
    }

    @GetMapping("/manga/{id}/cover")
    public void mangaCover(@PathVariable String id, HttpServletResponse res) throws IOException {
        Path mangaPath = mangaService.getMangaPath(id);
        if (Files.isDirectory(mangaPath)) {
            // 文件夹：返回第一张图片作为封面
            List<String> images = mangaService.listImagesInDirectory(mangaPath);
            if (!images.isEmpty()) {
                byte[] data = mangaService.readImage(mangaPath, images.getFirst());
                res.setContentType(mangaService.contentType(images.getFirst()));
                res.getOutputStream().write(data);
            }
        } else {
            // 文件：原逻辑
            Path zip = mangaService.getMangaPath(id);
            List<String> images = ZipImageReader.listImages(zip);
            if (images != null && !images.isEmpty()) {
                byte[] data = ZipImageReader.readImage(zip, images.getFirst());
                res.setContentType(ZipImageReader.contentType(images.getFirst()));
                res.getOutputStream().write(data);
            }
        }
    }

    // 书籍原始文件流（txt/epub/pdf 共用）：前端 BookView 用它做 iframe/fetch/epub.js 渲染
    @GetMapping("/manga/{id}/file")
    public void mangaFile(@PathVariable String id, HttpServletResponse res) throws IOException {
        Path mangaPath = mangaService.getMangaPath(id);
        if (Files.isDirectory(mangaPath)) {
            // 文件夹：返回第一张图片
            List<String> images = mangaService.listImagesInDirectory(mangaPath);
            if (images.isEmpty()) {
                res.sendError(404, "No images found in directory");
                return;
            }
            byte[] data = mangaService.readImage(mangaPath, images.getFirst());
            res.setContentType(mangaService.contentType(images.getFirst()));
            res.setContentLengthLong(data.length);
            res.getOutputStream().write(data);
        } else {
            // 文件：原逻辑
            Path file = mangaService.getMangaPath(id);
            String name = file.getFileName().toString().toLowerCase();
            res.setContentType(name.endsWith(".pdf") ? "application/pdf"
                    : name.endsWith(".txt") ? "text/plain; charset=utf-8"
                    : "application/epub+zip");
            res.setContentLengthLong(Files.size(file));
            Files.copy(file, res.getOutputStream());
        }
    }

    // 页目录：裸数组 [{"index":0},...]
    @GetMapping("/manga/{id}/pages")
    public List<Map<String, Integer>> mangaPages(@PathVariable String id) throws IOException {
        Path mangaPath = mangaService.getMangaPath(id);
        List<Map<String, Integer>> pages = new ArrayList<>();
        if (Files.isDirectory(mangaPath)) {
            // 文件夹模式
            List<String> images = mangaService.listImagesInDirectory(mangaPath);
            for (int i = 0; i < images.size(); i++) {
                pages.add(Map.of("index", i));
            }
        } else {
            // 文件模式
            Path zip = mangaService.getMangaPath(id);
            List<String> images = ZipImageReader.listImages(zip);
            for (int i = 0; i < images.size(); i++) {
                pages.add(Map.of("index", i));
            }
        }
        return pages;
    }

    // 单页图片流
    @GetMapping("/manga/{id}/page")
    public void mangaPage(@PathVariable String id,
                          @RequestParam int index,
                          HttpServletResponse res) throws IOException {
        Path mangaPath = mangaService.getMangaPath(id);
        List<String> images;
        if (Files.isDirectory(mangaPath)) {
            // 文件夹模式
            images = mangaService.listImagesInDirectory(mangaPath);
        } else {
            // 文件模式
            Path zip = mangaService.getMangaPath(id);
            images = ZipImageReader.listImages(zip);
        }
        byte[] data;
        String contentType;
        if (Files.isDirectory(mangaPath)) {
            // 文件夹模式
            data = mangaService.readImage(mangaPath, images.get(index));
            contentType = mangaService.contentType(images.get(index));
        } else {
            // 文件模式
            data = ZipImageReader.readImage(mangaPath, images.get(index));
            contentType = ZipImageReader.contentType(images.get(index));
        }
        res.setContentType(contentType);
        res.getOutputStream().write(data);
    }
}

