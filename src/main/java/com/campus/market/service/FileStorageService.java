package com.campus.market.service;

import com.campus.market.common.BusinessException;
import com.campus.market.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储。
 * <p>
 * 安全要点：
 * 1. 白名单校验扩展名，不接受 .jsp / .sh 之类的可执行文件；
 * 2. 文件名完全由服务端生成（日期目录 + UUID），不使用用户传来的文件名，
 *    从根本上杜绝 ../../ 这类路径穿越；
 * 3. 落盘前再校验一次规范化后的路径仍位于上传根目录之内，做双重保险。
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppProperties properties;
    private final Path root;

    public FileStorageService(AppProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.upload().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("上传目录已就绪: {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 jpg / jpeg / png / gif / webp / bmp 格式的图片");
        }

        String relative = LocalDate.now().format(DATE_DIR) + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + "." + extension.toLowerCase(Locale.ROOT);

        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法的文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件写入失败: {}", target, e);
            throw new BusinessException("图片保存失败，请稍后重试");
        }

        return properties.upload().urlPrefix() + "/" + relative;
    }
}
