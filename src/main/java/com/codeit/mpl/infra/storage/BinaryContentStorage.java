package com.codeit.mpl.infra.storage;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BinaryContentStorage {

    String put(String key, MultipartFile file);

    void download(String key, HttpServletResponse response);
}
