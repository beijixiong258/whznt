package com.whznt.integration;

import com.whznt.exception.WorkspaceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;

@Component
public class PaymentImages {

    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final Path qrPath;

    public PaymentImages(@Value("${whznt.recharge.qr-file:}") String filename) {
        this.qrPath = filename.isBlank() ? null : Path.of(filename).toAbsolutePath().normalize();
    }

    public boolean qrAvailable() {
        return qr() != null;
    }

    public ImageView requireQr() {
        ImageView qr = qr();
        if (qr == null) {
            throw WorkspaceException.notFound("收款二维码尚未配置。");
        }
        return qr;
    }

    public Receipt readReceipt(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw WorkspaceException.badRequest("请选择不超过 10 MB 的 PNG 或 JPG 付款截图。");
        }
        try {
            byte[] bytes = file.getBytes();
            return new Receipt(bytes, mediaType(bytes), sha256(bytes));
        } catch (IOException exception) {
            throw WorkspaceException.badRequest("付款截图无法读取，请重新选择 PNG 或 JPG。");
        }
    }

    private ImageView qr() {
        if (qrPath == null || !Files.isRegularFile(qrPath)) return null;
        try {
            if (Files.size(qrPath) > MAX_BYTES) return null;
            byte[] bytes = Files.readAllBytes(qrPath);
            return new ImageView(new ByteArrayResource(bytes), mediaType(bytes));
        } catch (IOException | WorkspaceException exception) {
            return null;
        }
    }

    private static MediaType mediaType(byte[] bytes) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > 40_000_000) throw invalidImage();
                MediaType type = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> MediaType.IMAGE_PNG;
                    case "jpeg" -> MediaType.IMAGE_JPEG;
                    default -> throw invalidImage();
                };
                reader.read(0);
                return type;
            } finally {
                reader.dispose();
            }
        }
    }

    private static WorkspaceException invalidImage() {
        return WorkspaceException.badRequest("请上传有效的 PNG 或 JPG 图片。");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    public record Receipt(byte[] bytes, MediaType type, String sha256) {
    }

    public record ImageView(Resource resource, MediaType type) {
    }
}
