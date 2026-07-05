package com.codeit.mpl.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mpl.storage")
public record StorageProperties(
        String type,
        Local local,
        S3 s3
) {
    public record Local(String rootPath) {
    }

    public record S3(
            String accessKey,
            String secretKey,
            String region,
            String bucket,
            long presignedUrlExpiration
    ) {
    }
}
