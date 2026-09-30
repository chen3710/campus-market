package com.campus.market.controller;

import com.campus.market.auth.LoginRequired;
import com.campus.market.common.Result;
import com.campus.market.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @LoginRequired
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileStorageService.store(file), "上传成功");
    }
}
