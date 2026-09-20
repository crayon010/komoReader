package com.example.springboot.service;

import com.example.springboot.entity.MangaMeta;
import com.example.springboot.util.ZipImageReader;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

@Service
public class MangaService {
    // ponytail: 单一当前目录，多用户同时扫不同目录会互相覆盖；需要时改成扫描结果带各自 root
    private Path root = Paths.get("D:/manga");
    private Path cacheFile = root.resolve(".manga-cache.properties");
    private final Map<String, MangaMeta> cache = new HashMap<>();

    // 缓存条目：mtime+size 都没变就复用，不重新打开 zip
    record CacheEntry(long lastModified, long size, Integer totalPages) {}

    /** dir 为空则沿用上次（默认 D:/manga） */
    public synchronized List<MangaMeta> scan(String dir) throws IOException {
        if (dir != null && !dir.isBlank()) {
            root = Paths.get(dir);
            cacheFile = root.resolve(".manga-cache.properties");
        }
        Map<String, CacheEntry> disk = loadCache();
        Map<String, CacheEntry> fresh = new HashMap<>();
        cache.clear();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> {
                    String fileName = p.getFileName().toString().toLowerCase();
                    return fileName.endsWith(".zip") || fileName.endsWith(".cbz")
                            || fileName.endsWith(".mobi") || fileName.endsWith(".epub")
                            || fileName.endsWith(".txt") || fileName.endsWith(".pdf")
                            || disk.containsKey(p.getFileName().toString())
                            || isZipFile(p);
                })
                .forEach(p -> {
                    try {
                        String fileName = p.getFileName().toString();
                        String type = typeOf(fileName);
                        CacheEntry e = disk.get(fileName);
                        long mtime = Files.getLastModifiedTime(p).toMillis();
                        long size = Files.size(p);
                        if (e == null || e.lastModified() != mtime || e.size() != size) {
                            Integer totalPages = null;
                            // 只有漫画数图片；mobi/epub/txt/pdf 不拆包统计
                            if ("manga".equals(type) && !fileName.toLowerCase().endsWith(".mobi")) {
                                totalPages = ZipImageReader.listImages(p).size();
                            }
                            e = new CacheEntry(mtime, size, totalPages);
                        }
                        fresh.put(fileName, e);

                        MangaMeta meta = new MangaMeta();
                        meta.setId(getFixedHash(fileName));
                        meta.setName(fileName);
                        meta.setTotalPages(e.totalPages());
                        meta.setType(type);
                        meta.setPath(p.toString());
                        cache.put(meta.getId(), meta);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (NoSuchAlgorithmException ex) {
                        throw new RuntimeException(ex);
                    }
                });

        }
        saveCache(fresh);
        return new ArrayList<>(cache.values());
    }

    static String typeOf(String fileName) {
        String n = fileName.toLowerCase();
        if (n.endsWith(".epub") || n.endsWith(".txt")) return "novel";
        if (n.endsWith(".pdf")) return "pdf";
        return "manga";
    }

    // 文件名 -> "mtime,size,totalPages"，totalPages 为空表示 mobi 未统计
    private Map<String, CacheEntry> loadCache() {
        Properties props = new Properties();
        if (Files.exists(cacheFile)) {
            try (InputStream in = Files.newInputStream(cacheFile)) {
                props.load(in);
            } catch (IOException e) {
                return Map.of(); // 缓存损坏就全量重扫
            }
        }
        Map<String, CacheEntry> m = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            try {
                String[] parts = props.getProperty(name).split(",");
                Integer pages = parts.length > 2 && !parts[2].isEmpty()
                        ? Integer.parseInt(parts[2]) : null;
                m.put(name, new CacheEntry(Long.parseLong(parts[0]),
                        Long.parseLong(parts[1]), pages));
            } catch (RuntimeException ignored) {
                // 单行坏了就当没有，重扫该文件
            }
        }
        return m;
    }

    private void saveCache(Map<String, CacheEntry> entries) {
        Properties props = new Properties();
        entries.forEach((name, e) -> props.setProperty(name,
                e.lastModified() + "," + e.size() + "," +
                        (e.totalPages() == null ? "" : e.totalPages())));
        try (OutputStream out = Files.newOutputStream(cacheFile)) {
            props.store(out, "manga scan cache");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isZipFile(Path p) {
        try (ZipFile zf = ZipFile.builder().setFile(p.toFile()).get()) {
            // 能成功打开即视为 ZIP
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 根据文件夹名字定义哈希值作为id
    public static String getFixedHash(String fileName) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fileName.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

    public MangaMeta getMeta(String id) {
        return cache.get(id);
    }

    public Path getZipPath(String id) {
        MangaMeta meta = cache.get(id);
        if (meta == null) {
            throw new NoSuchElementException("Unknown manga id: " + id);
        }
        return root.resolve(meta.getName());
    }
}
