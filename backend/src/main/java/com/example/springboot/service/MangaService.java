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
    // ponytail: 导入目录列表存 user.home 单文件；要按用户隔离或换机器同步时再挪
    static final Path DEFAULT_ROOTS_FILE = Paths.get(System.getProperty("user.home"), ".komoreader-roots.txt");
    private static final Path DEFAULT_ROOT = Paths.get("D:/manga");

    private final Path rootsFile;
    private final List<Path> roots = new ArrayList<>();
    // 根目录路径 -> 最近一次扫描的读物条目数（卡片展示用，持久化在 rootsFile）
    private final Map<String, Integer> rootItemCounts = new HashMap<>();
    // id -> meta；id = hash(根目录 + 条目名)，多个根目录里有同名文件也不冲突
    private final Map<String, MangaMeta> cache = new HashMap<>();

    public MangaService() {
        this(DEFAULT_ROOTS_FILE);
    }

    public MangaService(Path rootsFile) {
        this.rootsFile = rootsFile;
        try {
            if (Files.exists(rootsFile)) {
                Files.readAllLines(rootsFile).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(s -> {
                            // 每行 "条目数\t路径"；老格式只有路径
                            int tab = s.indexOf('\t');
                            if (tab > 0 && s.substring(0, tab).matches("\\d+")) {
                                Path p = Paths.get(s.substring(tab + 1).trim());
                                roots.add(p);
                                rootItemCounts.put(p.toString(), Integer.valueOf(s.substring(0, tab)));
                            } else {
                                roots.add(Paths.get(s));
                            }
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 从没导入过时沿用老的单目录行为
        if (roots.isEmpty()) roots.add(DEFAULT_ROOT);
    }

    // 缓存条目：mtime+size 都没变就复用，不重新打开 zip
    record CacheEntry(long lastModified, long size, Integer totalPages) {}

    /** 新增导入目录（重复导入跳过），返回规范化路径并持久化列表 */
    public synchronized Path addRoot(String dir) throws IOException {
        Path p = Paths.get(dir).toAbsolutePath().normalize();
        if (!Files.isDirectory(p)) {
            throw new IOException("路径不存在或不是文件夹: " + dir);
        }
        if (roots.stream().noneMatch(r -> r.equals(p))) {
            roots.add(p);
            saveRoots();
        }
        return p;
    }

    /** 删除导入目录：列表移除并丢弃其条目，磁盘文件和各目录里的扫描缓存不动 */
    public synchronized void removeRoot(String dir) {
        Path p = Paths.get(dir).toAbsolutePath().normalize();
        if (roots.removeIf(r -> r.equals(p))) saveRoots();
        rootItemCounts.remove(p.toString());
        cache.values().removeIf(m -> Paths.get(m.getPath()).startsWith(p));
    }

    public List<Path> getRoots() {
        return roots;
    }

    /** 该目录最近一次扫描出的读物条目数，没扫过为 0 */
    public int getItemCount(Path root) {
        return rootItemCounts.getOrDefault(root.toString(), 0);
    }

    /** 扫描全部导入目录 */
    public synchronized List<MangaMeta> scanAll() throws IOException {
        List<MangaMeta> all = new ArrayList<>();
        for (Path r : roots) all.addAll(scanRoot(r));
        return all;
    }

    /** 扫描一个导入目录的直接子项，返回其中条目 */
    public synchronized List<MangaMeta> scanRoot(Path root) throws IOException {
        Map<String, CacheEntry> disk = loadCache(root);
        Map<String, CacheEntry> fresh = new HashMap<>();
        // 先清掉该目录下的旧条目（含磁盘上已删除的），扫描后重新填
        cache.values().removeIf(m -> Paths.get(m.getPath()).startsWith(root));
        List<MangaMeta> list = new ArrayList<>();

        // 扫描文件和文件夹
        try (Stream<Path> stream = Files.list(root)) {
            stream.forEach(p -> {
                try {
                    if (Files.isDirectory(p)) {
                        // 子文件夹：检查是否有图片
                        if (hasImages(p)) {
                            String folderName = p.getFileName().toString();
                            CacheEntry e = disk.get(folderName);
                            long mtime = Files.getLastModifiedTime(p).toMillis();
                            long size = getDirectorySize(p);
                            if (e == null || e.lastModified() != mtime || e.size() != size) {
                                Integer totalPages = countImagesInDirectory(p);
                                e = new CacheEntry(mtime, size, totalPages);
                            }
                            fresh.put(folderName, e);

                            MangaMeta meta = new MangaMeta();
                            meta.setId(getFixedHash(root + "/" + folderName));
                            meta.setName(folderName);
                            meta.setTotalPages(e.totalPages());
                            // 含图片的文件夹 = 图库
                            meta.setType("gallery");
                            meta.setPath(p.toString());
                            cache.put(meta.getId(), meta);
                            list.add(meta);
                        }
                    } else {
                        // 文件：检查是否支持
                        String fileName = p.getFileName().toString().toLowerCase();
                        if (fileName.endsWith(".zip") || fileName.endsWith(".cbz")
                                || fileName.endsWith(".mobi") || fileName.endsWith(".epub")
                                || fileName.endsWith(".txt") || fileName.endsWith(".pdf")
                                || disk.containsKey(fileName)
                                || isZipFile(p)) {
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
                            meta.setId(getFixedHash(root + "/" + fileName));
                            meta.setName(fileName);
                            meta.setTotalPages(e.totalPages());
                            meta.setType(type);
                            meta.setPath(p.toString());
                            cache.put(meta.getId(), meta);
                            list.add(meta);
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                } catch (NoSuchAlgorithmException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        rootItemCounts.put(root.toString(), list.size());
        saveCache(fresh, root);
        saveRoots(); // 条目数一并持久化，重启后文件夹卡片仍能显示
        return list;
    }

    // 检查目录是否有图片
    private boolean hasImages(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.matches(".*\\.(jpg|jpeg|png|webp)$");
            });
        }
    }

    // 统计目录中的图片数量
    private int countImagesInDirectory(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return (int) stream
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.matches(".*\\.(jpg|jpeg|png|webp)$");
                })
                .count();
        }
    }

    // 获取目录大小（用于缓存判断）
    private long getDirectorySize(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        }
    }

    static String typeOf(String fileName) {
        String n = fileName.toLowerCase();
        if (n.endsWith(".epub") || n.endsWith(".txt")) return "novel";
        if (n.endsWith(".pdf")) return "pdf";
        return "manga";
    }

    private void saveRoots() {
        try {
            List<String> lines = new ArrayList<>();
            for (Path r : roots) {
                lines.add(rootItemCounts.getOrDefault(r.toString(), 0) + "\t" + r);
            }
            Files.write(rootsFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 文件名 -> "mtime,size,totalPages"，totalPages 为空表示 mobi 未统计；每个根目录各自的缓存文件
    private Map<String, CacheEntry> loadCache(Path root) {
        Path cacheFile = root.resolve(".manga-cache.properties");
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

    private void saveCache(Map<String, CacheEntry> entries, Path root) {
        Path cacheFile = root.resolve(".manga-cache.properties");
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

    // 根据名字定义哈希值作为id
    public static String getFixedHash(String fileName) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fileName.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

    public MangaMeta getMeta(String id) {
        return cache.get(id);
    }

    // 条目路径直接取扫描时记录的绝对路径，天然支持多个根目录
    public Path getMangaPath(String id) {
        MangaMeta meta = cache.get(id);
        if (meta == null) {
            throw new NoSuchElementException("Unknown manga id: " + id);
        }
        return Paths.get(meta.getPath());
    }

    // List images in directory
    public List<String> listImagesInDirectory(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.matches(".*\\.(jpg|jpeg|png|webp)$");
                })
                .map(Path::getFileName)
                .map(Path::toString)
                .sorted()
                .toList();
        }
    }

    // Read image from directory
    public byte[] readImage(Path dir, String imageName) throws IOException {
        Path imagePath = dir.resolve(imageName);
        return Files.readAllBytes(imagePath);
    }

    // Get content type for image
    public String contentType(String imageName) {
        return imageName.toLowerCase().endsWith(".png") ? "image/png"
                : imageName.toLowerCase().endsWith(".webp") ? "image/webp"
                : "image/jpeg";
    }
}
