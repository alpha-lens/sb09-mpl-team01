package com.codeit.mpl.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.mpl.infra.exception.MplException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

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
    void 다운로드하면_저장된_파일_내용을_응답에_그대로_쓴다() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image", "profile.png", "image/png", "test-bytes".getBytes(StandardCharsets.UTF_8)
        );
        String key = storage.put("profile-images/user-1/profile.png", file);

        MockHttpServletResponse response = new MockHttpServletResponse();
        storage.download(key, response);

        assertThat(response.getContentAsByteArray()).isEqualTo("test-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 존재하지_않는_key를_다운로드하면_예외가_발생한다() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> storage.download("no-such-key.png", response))
                .isInstanceOf(MplException.class);
    }
}
