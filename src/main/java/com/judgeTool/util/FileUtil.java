package com.judgeTool.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class FileUtil {
    private FileUtil() {
    }

    public static String readText(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static void writeText(Path path, String content) throws IOException {
        Objects.requireNonNull(content, "content");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    public static void ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }
}
