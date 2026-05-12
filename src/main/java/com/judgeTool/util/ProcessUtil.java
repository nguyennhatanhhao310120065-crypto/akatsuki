package com.judgeTool.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class ProcessUtil {
    private ProcessUtil() {
    }

    public static String drain(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        return buf.toString(StandardCharsets.UTF_8);
    }

    public static boolean waitFor(Process p, long timeoutMs) throws InterruptedException {
        return p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
