package com.codeit.mpl.infra.storage;

import com.codeit.mpl.infra.exception.storage.StorageUploadFailedException;
import com.codeit.mpl.infra.exception.storage.StorageUrlGenerationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3BinaryContentStorageTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3BinaryContentStorage storage;

    private static final StorageProperties.S3 PROPERTIES =
            new StorageProperties.S3("access-key", "secret-key", "ap-northeast-2", "test-bucket", 600);

    @BeforeEach
    void setUp() {
        storage = new S3BinaryContentStorage(PROPERTIES, s3Client, s3Presigner);
    }

    @Test
    void 업로드하면_지정한_bucket_key로_putObject를_호출하고_key를_반환한다() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "profile.png", "image/png", "test-bytes".getBytes(StandardCharsets.UTF_8)
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = storage.put("profile-images/user-1/profile.png", file);

        assertThat(key).isEqualTo("profile-images/user-1/profile.png");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("profile-images/user-1/profile.png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void 업로드_중_SdkException이_발생하면_StorageUploadFailedException이_발생한다() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "profile.png", "image/png", "test-bytes".getBytes(StandardCharsets.UTF_8)
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("네트워크 오류"));

        assertThatThrownBy(() -> storage.put("profile-images/user-1/profile.png", file))
                .isInstanceOf(StorageUploadFailedException.class)
                .hasMessage("파일 업로드에 실패했습니다.");
    }

    @Test
    void getUrl은_presigner가_생성한_presigned_url_문자열을_그대로_반환한다() throws Exception {
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://test-bucket.s3.amazonaws.com/key?signature=abc").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        String url = storage.getUrl("profile-images/user-1/profile.png");

        assertThat(url).isEqualTo("https://test-bucket.s3.amazonaws.com/key?signature=abc");
    }

    @Test
    void getUrl_중_SdkException이_발생하면_StorageUrlGenerationFailedException이_발생한다() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("네트워크 오류"));

        assertThatThrownBy(() -> storage.getUrl("profile-images/user-1/profile.png"))
                .isInstanceOf(StorageUrlGenerationFailedException.class)
                .hasMessage("파일 URL 생성에 실패했습니다.");
    }

    @Test
    void 삭제하면_지정한_bucket_key로_deleteObject를_호출한다() {
        storage.delete("profile-images/user-1/profile.png");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("profile-images/user-1/profile.png");
    }
}
