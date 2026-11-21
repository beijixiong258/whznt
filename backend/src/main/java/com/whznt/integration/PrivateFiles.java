package com.whznt.integration;

import com.whznt.exception.WorkspaceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@Slf4j
public class PrivateFiles {

    private final Path root;
    private final Path previousOriginals;

    public PrivateFiles(@Value("${whznt.storage-dir}") String directory,
                        @Value("${whznt.legacy-originals:}") String previousOriginals) {
        root = Path.of(directory).toAbsolutePath().normalize();
        this.previousOriginals = previousOriginals.isBlank() ? null : Path.of(previousOriginals).toAbsolutePath().normalize();
    }

    public String saveOriginal(byte[] bytes) {
        return saveOriginal(new ByteArrayInputStream(bytes));
    }

    public String saveOriginal(MultipartFile file) {
        try {
            return saveOriginal(file.getInputStream());
        } catch (IOException exception) {
            throw WorkspaceException.unavailable("无法读取上传的文件，请重新选择。");
        }
    }

    private String saveOriginal(InputStream source) {
        String key = UUID.randomUUID().toString();
        Path path = original(key);
        try (source) {
            Files.createDirectories(path.getParent());
            Files.copy(source, path);
        } catch (IOException exception) {
            discard(path);
            throw WorkspaceException.unavailable("无法保存文件，请检查存储空间后重试。");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    discard(path);
                }
            }
        });
        return key;
    }

    public Resource download(String key) {
        return new FileSystemResource(requireOriginal(key));
    }

    public Path requireOriginal(String key) {
        Path path = original(key);
        if (!Files.isRegularFile(path) && previousOriginals != null) {
            path = previousOriginals.resolve(path.getFileName());
        }
        if (!Files.isRegularFile(path)) {
            throw WorkspaceException.notFound("原件已不可用。");
        }
        return path;
    }

    public Path qmdDirectory() {
        return root.resolve("qmd");
    }

    public Path subtitleDirectory(long taskId) {
        return root.resolve("subtitles").resolve(Long.toString(taskId));
    }

    private Path original(String key) {
        return root.resolve("originals").resolve(UUID.fromString(key).toString());
    }

    private static void discard(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Could not remove a rolled-back upload");
        }
    }
}
