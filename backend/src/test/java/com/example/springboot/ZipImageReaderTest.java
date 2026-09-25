package com.example.springboot;

import com.example.springboot.util.ZipImageReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipImageReaderTest {

    @TempDir
    Path dir;

    @Test
    void listImagesMatchesUpperCaseExtensionButKeepsEntryName() throws Exception {
        Path zip = dir.resolve("b.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("P1.JPG"));
            zos.write(new byte[]{1, 2, 3});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("notes.txt"));
            zos.write(new byte[]{4});
            zos.closeEntry();
        }

        List<String> images = ZipImageReader.listImages(zip);
        assertEquals(List.of("P1.JPG"), images);
        assertArrayEquals(new byte[]{1, 2, 3}, ZipImageReader.readImage(zip, "P1.JPG"));
    }
}
