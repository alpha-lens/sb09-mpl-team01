package com.codeit.mpl.infra.storage;

import com.codeit.mpl.infra.exception.storage.StorageInvalidKeyException;
import com.codeit.mpl.infra.exception.storage.StorageUploadFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "mpl.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalBinaryContentStorage implements BinaryContentStorage {

    private static final String PUBLIC_PATH_PREFIX = "/uploads/";

    private final Path rootPath;

    public LocalBinaryContentStorage(StorageProperties storageProperties) {
        this.rootPath = Path.of(storageProperties.local().rootPath()).toAbsolutePath().normalize();
    }

    @Override
    public String put(String key, MultipartFile file) {
        Path target = resolveWithinRoot(key)
                .orElseThrow(StorageInvalidKeyException::new);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return key;
        } catch (IOException e) {
            throw new StorageUploadFailedException();
        }
    }

    @Override
    public String getUrl(String key) {
        return PUBLIC_PATH_PREFIX + key;
    }

    @Override
    public void delete(String key) {
        Optional<Path> target = resolveWithinRoot(key);
        if (target.isEmpty()) {
            log.warn("루트 디렉터리를 벗어나는 key라 삭제를 건너뜀: {}", key);
            return;
        }
        try {
            Files.deleteIfExists(target.get());
        } catch (IOException e) {
            log.warn("로컬 스토리지 파일 삭제 실패: {}", key, e);
        }
    }

    // rootPath 하위로 정규화됐는지 확인해 "../"를 통한 디렉터리 탈출을 막는다.
    private Optional<Path> resolveWithinRoot(String key) {
        Path target = rootPath.resolve(key).normalize();
        return target.startsWith(rootPath) ? Optional.of(target) : Optional.empty();
    }
}
