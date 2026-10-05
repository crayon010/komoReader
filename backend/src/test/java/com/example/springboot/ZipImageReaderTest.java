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

    @Test
    void findCoverUsesEpub3CoverImageProperty() throws Exception {
        // 正文图 images/001.jpg 字典序在 images/cover.jpg 之前，模拟"封面不是第一张图"的书
        Path epub = epub("""
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="p1" href="images/001.jpg" media-type="image/jpeg"/>
                <item id="ci" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
              </manifest>
            </package>
            """);
        assertEquals("OEBPS/images/cover.jpg", ZipImageReader.findCoverEntry(epub));
    }

    @Test
    void findCoverUsesEpub2MetaCover() throws Exception {
        // 封面放在 text/ 目录，按字典序排在 images/ 之后，只有 EPUB2 meta 声明能定位到它
        Path epub = epub("""
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>t</dc:title>
                <meta name="cover" content="cover-image-id"/>
              </metadata>
              <manifest>
                <item id="p1" href="images/001.jpg" media-type="image/jpeg"/>
                <item id="cover-image-id" href="text/cover.png" media-type="image/png"/>
              </manifest>
            </package>
            """);
        assertEquals("OEBPS/text/cover.png", ZipImageReader.findCoverEntry(epub));
    }

    @Test
    void findCoverDecodesUrlEncodedHref() throws Exception {
        Path epub = epub("""
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="ci" href="images/my%20cover.jpg" media-type="image/jpeg" properties="cover-image"/>
              </manifest>
            </package>
            """);
        assertEquals("OEBPS/images/my cover.jpg", ZipImageReader.findCoverEntry(epub));
    }

    @Test
    void findCoverReturnsNullWhenNotDeclared() throws Exception {
        Path epub = epub("""
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="p1" href="images/001.jpg" media-type="image/jpeg"/>
              </manifest>
            </package>
            """);
        assertNull(ZipImageReader.findCoverEntry(epub));
    }

    @Test
    void findCoverToleratesBrokenContainerXml() throws Exception {
        Path zip = dir.resolve("broken.epub");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write("<container><unclosed>".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("images/001.jpg"));
            zos.write(new byte[]{1});
            zos.closeEntry();
        }
        assertNull(ZipImageReader.findCoverEntry(zip));
    }

    /** 打一个最小 EPUB：container.xml 指向 content.opf，opf 内容由调用方给 */
    private Path epub(String opfContent) throws Exception {
        Path epub = dir.resolve("book-" + System.nanoTime() + ".epub");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(epub))) {
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write("""
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("OEBPS/images/001.jpg"));
            zos.write(new byte[]{1});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
            zos.write(new byte[]{2});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("OEBPS/images/my cover.jpg"));
            zos.write(new byte[]{3});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("OEBPS/text/cover.png"));
            zos.write(new byte[]{4});
            zos.closeEntry();
        }
        return epub;
    }
}
