package com.codeit.mpl.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.codeit.mpl.infra.exception.storage.StorageInvalidKeyException;
import com.codeit.mpl.infra.exception.storage.StorageUploadFailedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class LocalBinaryContentStorageTest {

    @TempDir
    Path rootPath;

    private LocalBinaryContentStorage storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "local",
                new StorageProperties.Local(rootPath.toString()),
                null
        );
        storage = new LocalBinaryContentStorage(properties);
    }

    @Test
    void 업로드하면_root_path에_파일이_저장된다() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "profile.png", "image/png", "test-bytes".getBytes(StandardCharsets.UTF_8)
        );

        String storedKey = storage.put("profile-images/user-1/profile.png", file);

        assertThat(storedKey).isEqualTo("profile-images/user-1/profile.png");
        Path saved = rootPath.resolve(storedKey);
        assertThat(Files.exists(saved)).isTrue();
        assertThat(Files.readString(saved)).isEqualTo("test-bytes");
    }

    @Test
    void getUrl은_uploads_경로_아래로_key를_그대로_붙인_경로를_반환한다() {
        String url = storage.getUrl("profile-images/user-1/profile.png");

        assertThat(url).isEqualTo("/uploads/profile-images/user-1/profile.png");
    }

    @Test
    void 삭제하면_저장된_파일이_제거된다() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "profile.png", "image/png", "test-bytes".getBytes(StandardCharsets.UTF_8)
        );
        String key = storage.put("profile-images/user-1/profile.png", file);

        storage.delete(key);

        assertThat(Files.exists(rootPath.resolve(key))).isFalse();
    }

    @Test
    void 존재하지_않는_key를_삭제해도_예외가_발생하지_않는다() {
        storage.delete("no-such-key.png");
    }

    @Test
    void 루트_경로를_벗어나는_key로_업로드하면_StorageInvalidKeyException이_발생한다() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "profile.png", "image/png", "test-bytes".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> storage.put("../outside.png", file))
                .isInstanceOf(StorageInvalidKeyException.class)
                .hasMessage("허용되지 않는 파일 경로입니다.");
    }

    @Test
    void 업로드_중_IOException이_발생하면_StorageUploadFailedException이_발생한다() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        doThrow(new IOException("디스크 오류")).when(file).transferTo(any(Path.class));

        assertThatThrownBy(() -> storage.put("profile-images/user-1/profile.png", file))
                .isInstanceOf(StorageUploadFailedException.class)
                .hasMessage("파일 업로드에 실패했습니다.");
    }
}
