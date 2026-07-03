package com.codeit.mpl.infra.storage;

import org.springframework.web.multipart.MultipartFile;

public interface BinaryContentStorage {

    String put(String key, MultipartFile file);

    String getUrl(String key);
}
