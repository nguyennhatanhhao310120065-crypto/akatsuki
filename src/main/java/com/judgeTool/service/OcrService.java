package com.judgeTool.service;

import java.nio.file.Path;

/**
 * OCR qua tess4j là tuỳ chọn theo spec (dependency thường bị comment).
 * Triển khai: hướng dẫn người dùng bật tess4j hoặc dán text thủ công.
 */
public class OcrService {

    public String extractTextFromImage(Path imagePath) throws Exception {
        throw new UnsupportedOperationException(
                "OCR chưa bật: thêm dependency tess4j vào pom.xml, cài Tesseract OCR và cấu hình TESSDATA_PREFIX, "
                        + "hoặc dán nội dung đề vào ô text. File: " + imagePath);
    }
}
