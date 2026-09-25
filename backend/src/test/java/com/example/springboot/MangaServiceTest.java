package com.example.springboot;

import com.example.springboot.entity.MangaMeta;
import com.example.springboot.service.MangaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/** 多根目录导入/删除的核心逻辑 */
class MangaServiceTest {

    @TempDir
    Path rootA;

    @TempDir
    Path rootB;

    @TempDir
    Path confDir;

    @Test
    void multiRootScanAndRemove() throws Exception {
        // 两个根目录各放一个同名图片文件夹，id 必须不同
        makeImageFolder(rootA, "lib");
        makeImageFolder(rootB, "lib");

        MangaService svc = new MangaService(confDir.resolve("roots.txt"));
        svc.addRoot(rootA.toString());
        svc.addRoot(rootB.toString());
        // 重复导入不生效
        svc.addRoot(rootA.toString());

        List<MangaMeta> a = svc.scanRoot(rootA);
        List<MangaMeta> b = svc.scanRoot(rootB);
        assertEquals(1, a.size());
        assertEquals(1, b.size());
        assertNotEquals(a.get(0).getId(), b.get(0).getId());
        assertEquals("gallery", a.get(0).getType());

        // 删除 rootA 后其条目不可访问，rootB 不受影响
        svc.removeRoot(rootA.toString());
        assertThrows(NoSuchElementException.class, () -> svc.getMangaPath(a.get(0).getId()));
        assertEquals(b.get(0).getId(), svc.scanRoot(rootB).get(0).getId());
        // 非法路径被拒绝
        assertThrows(Exception.class, () -> svc.addRoot(rootA.resolve("nope").toString()));

        // 条目数 = 扫描列表长度，并随 roots 文件持久化（模拟重启后重建 service）
        assertEquals(1, svc.getItemCount(rootB));
        MangaService reloaded = new MangaService(confDir.resolve("roots.txt"));
        assertEquals(1, reloaded.getItemCount(rootB));
        assertEquals(0, reloaded.getItemCount(rootA));
    }

    private static void makeImageFolder(Path root, String name) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.write(dir.resolve("p1.jpg"), new byte[] {1});
    }
}
