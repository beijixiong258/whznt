package com.whznt.integration;

import com.whznt.exception.WorkspaceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class DocumentImport {

    private static final int MAX_CHARS = 200000;
    private static final int MAX_PASSAGE = 1000;
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R[\\t ]*\\R+");

    public record Segment(String location, String content) {
    }

    public record Imported(String filename, String contentType, byte[] original, List<Segment> segments) {
    }

    public Imported fromText(String name, String text) {
        return new Imported(filename(name) + ".txt", "text/plain", text.getBytes(StandardCharsets.UTF_8),
                checked(paragraphs(text)));
    }

    public Imported fromFile(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10L * 1024 * 1024) {
            throw WorkspaceException.badRequest("请选择不超过 10 MB 的资料文件。");
        }
        String name = filename(file.getOriginalFilename());
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            byte[] original = file.getBytes();
            return switch (extension) {
                case "docx" -> new Imported(name, "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        original, checked(word(original)));
                case "pdf" -> new Imported(name, "application/pdf", original, checked(pdf(original)));
                case "md" -> new Imported(name, "text/markdown", original, checked(paragraphs(readText(original))));
                case "txt" -> new Imported(name, "text/plain", original, checked(paragraphs(readText(original))));
                default -> throw WorkspaceException.badRequest("请上传 DOCX、TXT、MD 或有文字的 PDF。");
            };
        } catch (WorkspaceException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw WorkspaceException.badRequest("资料读取失败。请确认文件完整，且可以直接复制其中的文字。");
        }
    }

    private static String filename(String input) {
        String normal = Objects.toString(input, "资料").replace('\\', '/');
        String basename = normal.substring(normal.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}:*?\"<>|]", "_").strip();
        if (basename.isBlank()) {
            return "资料";
        }
        if (basename.length() > 235) {
            int extension = basename.lastIndexOf('.');
            return basename.substring(0, 220) + (extension > basename.length() - 12 ? basename.substring(extension) : "");
        }
        return basename;
    }

    private static String readText(byte[] bytes) throws CharacterCodingException {
        if (bytes.length >= 2
                && ((bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE)
                || (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF))) {
            return decode(bytes, StandardCharsets.UTF_16);
        }
        try {
            return decode(bytes, StandardCharsets.UTF_8).replaceFirst("^\uFEFF", "");
        } catch (CharacterCodingException exception) {
            return decode(bytes, Charset.forName("GB18030"));
        }
    }

    private static String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static List<Segment> paragraphs(String text) {
        PassageCollector passages = new PassageCollector();
        PARAGRAPH_BREAK.splitAsStream(text).forEachOrdered(passages::addParagraph);
        return passages.segments;
    }

    private static List<Segment> word(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            PassageCollector passages = new PassageCollector();
            wordParts(document.getBodyElements(), passages);
            return passages.segments;
        }
    }

    private static void wordParts(List<IBodyElement> body, PassageCollector passages) {
        for (IBodyElement element : body) {
            if (element instanceof XWPFParagraph paragraph) {
                passages.addParagraph(paragraph.getText());
            } else if (element instanceof XWPFTable table) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> wordParts(cell.getBodyElements(), passages)));
            }
        }
    }

    private static List<Segment> pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            PassageCollector passages = new PassageCollector();
            StringWriter pageText = new StringWriter();
            PDFTextStripper stripper = new PDFTextStripper() {
                private long totalLength;

                @Override
                protected void endPage(PDPage page) {
                    String text = pageText.toString();
                    totalLength += text.length();
                    requireSupportedLength(totalLength);
                    passages.add("第 " + getCurrentPageNo() + " 页", text);
                    pageText.getBuffer().setLength(0);
                }
            };
            stripper.writeText(document, pageText);
            return passages.segments;
        }
    }

    private static int passageEnd(String text, int start) {
        int end = Math.min(text.length(), start + MAX_PASSAGE);
        if (end == text.length()) {
            return end;
        }
        for (int index = end - 1; index >= start + MAX_PASSAGE / 2; index--) {
            char character = text.charAt(index);
            if (character == '\n' || character == '。') {
                return index + 1;
            }
        }
        return Character.isHighSurrogate(text.charAt(end - 1)) ? end - 1 : end;
    }

    private static List<Segment> checked(List<Segment> segments) {
        if (segments.isEmpty()) {
            throw WorkspaceException.badRequest("没有提取到文字。扫描件请先转成文字版 PDF，或粘贴原文。");
        }
        if (segments.stream().anyMatch(segment -> segment.content().contains("\u0000"))) {
            throw WorkspaceException.badRequest("资料含有无法读取的字符。");
        }
        return segments;
    }

    private static void requireSupportedLength(long length) {
        if (length > MAX_CHARS) {
            throw WorkspaceException.badRequest("单份资料最多导入 20 万字。");
        }
    }

    private static final class PassageCollector {
        private final List<Segment> segments = new ArrayList<>();
        private int paragraphNumber;
        private int length;

        private void addParagraph(String text) {
            if (!text.isBlank()) {
                add("第 " + ++paragraphNumber + " 段", text);
            }
        }

        private void add(String location, String content) {
            String text = content.strip();
            int start = 0;
            while (start < text.length()) {
                int end = passageEnd(text, start);
                String passage = text.substring(start, end).stripTrailing();
                length += passage.length();
                requireSupportedLength(length);
                segments.add(new Segment(location, passage));
                start = end;
                while (start < text.length()) {
                    int codePoint = text.codePointAt(start);
                    if (!Character.isWhitespace(codePoint)) {
                        break;
                    }
                    start += Character.charCount(codePoint);
                }
            }
        }
    }
}
