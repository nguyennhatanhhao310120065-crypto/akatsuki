package com.judgeTool.service;

import java.nio.file.Path;

/**
 * OCR sử dụng Gemini Vision (Gemini 2.5 Flash hỗ trợ ảnh native).
 * Không cần cài Tesseract / tess4j.
 */
public class OcrService {

    private final GeminiService gemini;

    public OcrService(GeminiService gemini) {
        this.gemini = gemini;
    }

    public String extractTextFromImage(Path imagePath) throws Exception {
        if (gemini == null) {
            throw new IllegalStateException("GeminiService chưa được khởi tạo.");
        }
        return gemini.extractTextFromImage(imagePath);
    }
}
