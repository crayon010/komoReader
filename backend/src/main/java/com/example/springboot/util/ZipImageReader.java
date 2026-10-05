package com.example.springboot.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class ZipImageReader {

    /** 图片扩展名判断，文件夹扫描和 zip 内条目共用 */
    public static boolean isImageName(String name) {
        return name.toLowerCase().matches(".*\\.(jpg|jpeg|png|webp)$");
    }

    public static List<String> listImages(Path zipPath) throws IOException {
        try (ZipFile zip = ZipFile.builder().setFile(zipPath.toFile()).get()) {
            return zip.stream()
                    .map(ZipArchiveEntry::getName)
                    .filter(ZipImageReader::isImageName)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * EPUB 封面按包文档声明取：EPUB3 用 manifest item 的 properties="cover-image"，
     * EPUB2 用 metadata 里 meta[name=cover] 指到的 item id。声明缺失或解析失败返回 null，
     * 调用方回退到第一张图。
     */
    public static String findCoverEntry(Path zipPath) throws IOException {
        try (ZipFile zip = ZipFile.builder().setFile(zipPath.toFile()).get()) {
            ZipArchiveEntry container = zip.getEntry("META-INF/container.xml");
            if (container == null) return null;
            Document containerDoc = parseQuiet(zip.getInputStream(container));
            if (containerDoc == null) return null;

            // container.xml 里第一个 rootfile 的 full-path 指向包文档（.opf）
            String opfPath = null;
            for (Element rootfile : elementsByLocalName(containerDoc.getDocumentElement(), "rootfile")) {
                opfPath = rootfile.getAttribute("full-path");
                break;
            }
            if (opfPath == null || opfPath.isEmpty()) return null;
            ZipArchiveEntry opf = zip.getEntry(opfPath);
            if (opf == null) return null;
            Document opfDoc = parseQuiet(zip.getInputStream(opf));
            if (opfDoc == null) return null;

            // EPUB3：item[properties~=cover-image]
            for (Element item : elementsByLocalName(opfDoc.getDocumentElement(), "item")) {
                for (String prop : item.getAttribute("properties").split("\\s+")) {
                    if (prop.equals("cover-image")) {
                        String entry = resolveEntry(zip, opfPath, item.getAttribute("href"));
                        if (entry != null) return entry;
                    }
                }
            }
            // EPUB2：meta[name=cover] 的 content 是 manifest item 的 id
            for (Element meta : elementsByLocalName(opfDoc.getDocumentElement(), "meta")) {
                if (!"cover".equals(meta.getAttribute("name"))) continue;
                String id = meta.getAttribute("content");
                for (Element item : elementsByLocalName(opfDoc.getDocumentElement(), "item")) {
                    if (item.getAttribute("id").equals(id)) {
                        String entry = resolveEntry(zip, opfPath, item.getAttribute("href"));
                        if (entry != null) return entry;
                    }
                }
            }
            return null;
        }
    }

    /** 把 manifest href 按包文档所在目录解析成 zip 内路径，zip 里存在该条目才返回 */
    private static String resolveEntry(ZipFile zip, String opfPath, String href) {
        if (href == null || href.isEmpty()) return null;
        String base = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
        String raw = href.startsWith("/") ? href.substring(1) : base + href;
        for (String candidate : new String[]{decodePath(raw), raw}) {
            if (candidate == null) continue;
            Deque<String> segments = new ArrayDeque<>();
            for (String seg : candidate.split("/")) {
                if (seg.isEmpty() || seg.equals(".")) continue;
                if (seg.equals("..")) { if (!segments.isEmpty()) segments.pollLast(); }
                else segments.addLast(seg);
            }
            String entryName = String.join("/", segments);
            if (zip.getEntry(entryName) != null) return entryName;
        }
        return null;
    }

    private static String decodePath(String path) {
        try {
            return URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null; // 非法百分号编码就只试原始名字
        }
    }

    private static Document parseQuiet(InputStream in) throws IOException {
        try (InputStream is = in) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // EPUB 数据不可信：禁止外部 DTD/实体访问
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return f.newDocumentBuilder().parse(is);
        } catch (SAXException | ParserConfigurationException e) {
            return null;
        }
    }

    /** 兼容带命名空间前缀和不带的写法，按去掉前缀的标签名找后代元素 */
    private static List<Element> elementsByLocalName(Element root, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList all = root.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            String name = n.getNodeName();
            int colon = name.indexOf(':');
            if ((colon >= 0 ? name.substring(colon + 1) : name).equals(localName)) out.add((Element) n);
        }
        return out;
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
                : imageName.toLowerCase().endsWith(".gif") ? "image/gif"
                : imageName.toLowerCase().endsWith(".svg") ? "image/svg+xml"
                : "image/jpeg";
    }
}