package com.queueless.config;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Wraps an HttpServletResponse to capture the response body as a String
 * while still writing it to the original stream.
 * Used by IdempotencyFilter to cache successful API responses.
 */
public class CachingResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream captureBuffer = new ByteArrayOutputStream();
    private final PrintWriter captureWriter = new PrintWriter(
            new OutputStreamWriter(captureBuffer, StandardCharsets.UTF_8), true);
    private final HttpServletResponse original;

    public CachingResponseWrapper(HttpServletResponse response) {
        super(response);
        this.original = response;
    }

    @Override
    public PrintWriter getWriter() {
        return captureWriter;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(WriteListener writeListener) {}
            @Override
            public void write(int b) {
                captureBuffer.write(b);
            }
        };
    }

    public String getCapturedBody() {
        captureWriter.flush();
        return captureBuffer.toString(StandardCharsets.UTF_8);
    }

    public void copyBodyToResponse() throws IOException {
        captureWriter.flush();
        byte[] bytes = captureBuffer.toByteArray();
        if (bytes.length > 0) {
            original.setContentLength(bytes.length);
            original.getOutputStream().write(bytes);
            original.getOutputStream().flush();
        }
    }
}
