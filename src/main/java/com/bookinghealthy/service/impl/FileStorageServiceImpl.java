package com.bookinghealthy.service.impl;

import com.bookinghealthy.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private static final List<String> IMAGE_EXT = List.of("jpg", "jpeg", "png", "webp", "gif");
    private static final List<String> CV_EXT = List.of("pdf", "doc", "docx");

    private final Path uploadRoot;
    private final Path privateRoot;

    public FileStorageServiceImpl(@Value("${app.upload-dir}") String uploadDir,
                                  @Value("${app.private-dir}") String privateDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.privateRoot = Paths.get(privateDir).toAbsolutePath().normalize();
        log.info("[Upload] Thư mục công khai: {}", uploadRoot);
        log.info("[Upload] Thư mục riêng tư : {}", privateRoot);
    }

    @Override
    public Path uploadRoot() {
        return uploadRoot;
    }

    @Override
    public Path privateRoot() {
        return privateRoot;
    }

    @Override
    public String storeImage(MultipartFile file, String subdir) {
        return store(file, resolveDir(uploadRoot, subdir), IMAGE_EXT, false);
    }

    @Override
    public String storeCv(MultipartFile file) {
        return store(file, resolveDir(privateRoot, "cv"), CV_EXT, true);
    }

    @Override
    public Path resolveCv(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            return null;
        }
        String safe = Paths.get(storedFileName).getFileName().toString();
        Path candidate = privateRoot.resolve("cv").resolve(safe).normalize();
        if (!candidate.startsWith(privateRoot) || !Files.isRegularFile(candidate)) {
            return null;
        }
        return candidate;
    }

    private String store(MultipartFile file, Path dir, List<String> allowedExt, boolean keepOriginalName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp tải lên đang trống.");
        }
        String original = StringUtils.getFilename(file.getOriginalFilename());
        String ext = extensionOf(original);
        if (ext == null || !allowedExt.contains(ext)) {
            throw new IllegalArgumentException(
                    "Định dạng tệp không được chấp nhận. Chỉ nhận: " + String.join(", ", allowedExt) + ".");
        }

        String base = keepOriginalName ? slugify(StringUtils.stripFilenameExtension(original)) : null;
        String fileName = System.currentTimeMillis()
                + "_" + ThreadLocalRandom.current().nextInt(1000, 10000)
                + (base == null || base.isBlank() ? "" : "_" + base)
                + "." + ext;

        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("Tên tệp không hợp lệ.");
            }
            file.transferTo(target);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được tệp " + fileName, e);
        }
    }

    private Path resolveDir(Path root, String subdir) {
        if (!StringUtils.hasText(subdir)) {
            return root;
        }
        Path dir = root.resolve(subdir).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("Thư mục con không hợp lệ: " + subdir);
        }
        return dir;
    }

    private static String extensionOf(String fileName) {
        String ext = StringUtils.getFilenameExtension(fileName);
        return ext == null ? null : ext.toLowerCase(Locale.ROOT);
    }

    private static String slugify(String raw) {
        if (raw == null) {
            return "";
        }
        String noAccent = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        String slug = noAccent.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }
}
