package com.codeit.mpl.infra.storage;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "mpl.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalBinaryContentStorage implements BinaryContentStorage {

    private static final String PUBLIC_PATH_PREFIX = "/uploads/";

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
    public String getUrl(String key) {
        return PUBLIC_PATH_PREFIX + key;
    }
}
