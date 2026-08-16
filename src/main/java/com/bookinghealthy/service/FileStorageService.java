package com.bookinghealthy.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    Path uploadRoot();

    Path privateRoot();

    String storeImage(MultipartFile file, String subdir);

    String storeCv(MultipartFile file);

    Path resolveCv(String storedFileName);
}
