package com.codeit.mpl.infra.storage;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "mpl.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalBinaryContentStorage implements BinaryContentStorage {

    private final Path rootPath;

    public LocalBinaryContentStorage(StorageProperties storageProperties) {
        this.rootPath = Path.of(storageProperties.local().rootPath());
    }

    @Override
    public String put(String key, MultipartFile file) {
        try {
            Path target = rootPath.resolve(key);
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return key;
        } catch (IOException e) {
            throw new MplException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public void download(String key, HttpServletResponse response) {
        Path target = rootPath.resolve(key);
        if (!Files.exists(target)) {
            throw new MplException(ErrorCode.STORAGE_FILE_NOT_FOUND);
        }
        try {
            String contentType = Files.probeContentType(target);
            response.setContentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            try (InputStream in = Files.newInputStream(target); OutputStream out = response.getOutputStream()) {
                StreamUtils.copy(in, out);
            }
        } catch (IOException e) {
            throw new MplException(ErrorCode.STORAGE_DOWNLOAD_FAILED);
        }
    }
}
