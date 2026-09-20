package com.example.springboot.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.nio.file.Path;
import java.util.stream.Collectors;
// import java.util.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

public class ZipImageReader {

    public static List<String> listImages(Path zipPath) throws IOException {
        try (ZipFile zip = ZipFile.builder().setFile(zipPath.toFile()).get()) {
            return zip.stream()
                    .map(ZipArchiveEntry::getName)
                    .filter(name -> name.matches(".*\\.(jpg|jpeg|png|webp)$"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static byte[] readImage(Path zipPath, String imageName) throws IOException {
        try (ZipFile zip = ZipFile.builder().setFile(zipPath.toFile()).get()) {
            ZipArchiveEntry entry = zip.getEntry(imageName);
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    public static String contentType(String imageName) {
        return imageName.toLowerCase().endsWith(".png") ? "image/png"
                : imageName.toLowerCase().endsWith(".webp") ? "image/webp"
                : "image/jpeg";
    }
}