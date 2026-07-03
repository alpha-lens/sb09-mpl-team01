package com.codeit.mpl.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    void getUrl은_uploads_경로_아래로_key를_그대로_붙인_경로를_반환한다() {
        String url = storage.getUrl("profile-images/user-1/profile.png");

        assertThat(url).isEqualTo("/uploads/profile-images/user-1/profile.png");
    }
}
