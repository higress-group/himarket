/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.core.utils;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Centralized file upload validation utility.
 *
 * <p>Enforces extension whitelist, MIME type cross-validation, file size limits, and filename
 * sanitization to prevent malicious file uploads.
 */
public class FileUploadValidator {

    private FileUploadValidator() {}

    /** Maximum allowed file size: 20MB */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** Allowed file extensions (lowercase, without leading dot). */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    // Images
                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "webp",
                    "svg",
                    // Documents
                    "pdf",
                    "doc",
                    "docx",
                    "xls",
                    "xlsx",
                    "ppt",
                    "pptx",
                    "txt",
                    "csv",
                    "rtf",
                    "md",
                    "json",
                    "xml",
                    "html",
                    // Audio
                    "mp3",
                    "wav",
                    "ogg",
                    "flac",
                    "aac",
                    // Video
                    "mp4",
                    "webm",
                    "avi",
                    "mov",
                    "mkv");

    /** Mapping of each extension to its valid MIME types for cross-validation. */
    private static final Map<String, Set<String>> EXTENSION_MIME_MAP =
            Map.ofEntries(
                    // Images
                    Map.entry("png", Set.of("image/png")),
                    Map.entry("jpg", Set.of("image/jpeg")),
                    Map.entry("jpeg", Set.of("image/jpeg")),
                    Map.entry("gif", Set.of("image/gif")),
                    Map.entry("webp", Set.of("image/webp")),
                    Map.entry("svg", Set.of("image/svg+xml")),
                    // Documents
                    Map.entry("pdf", Set.of("application/pdf")),
                    Map.entry("doc", Set.of("application/msword")),
                    Map.entry(
                            "docx",
                            Set.of(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
                    Map.entry("xls", Set.of("application/vnd.ms-excel")),
                    Map.entry(
                            "xlsx",
                            Set.of(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                    Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
                    Map.entry(
                            "pptx",
                            Set.of(
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
                    Map.entry("txt", Set.of("text/plain")),
                    Map.entry("csv", Set.of("text/csv")),
                    Map.entry("rtf", Set.of("application/rtf")),
                    Map.entry("md", Set.of("text/markdown")),
                    Map.entry("json", Set.of("application/json")),
                    Map.entry("xml", Set.of("application/xml", "text/xml")),
                    Map.entry("html", Set.of("text/html")),
                    // Audio
                    Map.entry("mp3", Set.of("audio/mpeg")),
                    Map.entry("wav", Set.of("audio/wav")),
                    Map.entry("ogg", Set.of("audio/ogg")),
                    Map.entry("flac", Set.of("audio/flac")),
                    Map.entry("aac", Set.of("audio/aac")),
                    // Video
                    Map.entry("mp4", Set.of("video/mp4")),
                    Map.entry("webm", Set.of("video/webm")),
                    Map.entry("avi", Set.of("video/x-msvideo")),
                    Map.entry("mov", Set.of("video/quicktime")),
                    Map.entry("mkv", Set.of("video/x-matroska")));

    /**
     * Magic bytes (file signatures) for binary file types. Text-based formats (txt, csv, json, xml,
     * html, md, rtf, svg) are excluded since they have no fixed binary header.
     */
    private static final Map<String, byte[][]> MAGIC_BYTES =
            Map.ofEntries(
                    Map.entry("png", new byte[][] {{(byte) 0x89, 0x50, 0x4E, 0x47}}),
                    Map.entry("jpg", new byte[][] {{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}}),
                    Map.entry("jpeg", new byte[][] {{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}}),
                    Map.entry("gif", new byte[][] {{0x47, 0x49, 0x46, 0x38}}),
                    Map.entry("webp", new byte[][] {{0x52, 0x49, 0x46, 0x46}}),
                    Map.entry("pdf", new byte[][] {{0x25, 0x50, 0x44, 0x46}}),
                    // docx, xlsx, pptx are ZIP-based
                    Map.entry("docx", new byte[][] {{0x50, 0x4B, 0x03, 0x04}}),
                    Map.entry("xlsx", new byte[][] {{0x50, 0x4B, 0x03, 0x04}}),
                    Map.entry("pptx", new byte[][] {{0x50, 0x4B, 0x03, 0x04}}),
                    // Legacy Office formats (doc, xls, ppt) use OLE2 compound document
                    Map.entry("doc", new byte[][] {{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}}),
                    Map.entry("xls", new byte[][] {{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}}),
                    Map.entry("ppt", new byte[][] {{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}}),
                    // Audio
                    Map.entry(
                            "mp3",
                            new byte[][] {
                                {(byte) 0xFF, (byte) 0xFB},
                                {(byte) 0xFF, (byte) 0xF3},
                                {(byte) 0xFF, (byte) 0xF2},
                                {0x49, 0x44, 0x33}
                            }),
                    Map.entry("wav", new byte[][] {{0x52, 0x49, 0x46, 0x46}}),
                    Map.entry("ogg", new byte[][] {{0x4F, 0x67, 0x67, 0x53}}),
                    Map.entry("flac", new byte[][] {{0x66, 0x4C, 0x61, 0x43}}),
                    // Video
                    Map.entry("avi", new byte[][] {{0x52, 0x49, 0x46, 0x46}}),
                    Map.entry("mkv", new byte[][] {{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}}));

    /**
     * Validates a file upload for extension, MIME type consistency, size, and magic bytes.
     *
     * @param file the uploaded file
     * @throws BusinessException if validation fails
     */
    public static void validate(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null) {
            filename = filename.replace("\u0000", "");
        }

        String extension = extractExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "File type not allowed: ." + extension);
        }

        String contentType = file.getContentType();
        if (contentType != null) {
            Set<String> validMimes = EXTENSION_MIME_MAP.get(extension);
            if (validMimes != null && !validMimes.contains(contentType)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "MIME type mismatch");
            }
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "File size exceeds limit");
        }

        // Magic bytes validation for binary file types
        validateMagicBytes(file, extension);
    }

    /**
     * Validates that the file's magic bytes match the expected signature for its extension. Skips
     * validation for text-based formats that have no fixed binary header.
     */
    private static void validateMagicBytes(MultipartFile file, String extension) {
        byte[][] expectedSignatures = MAGIC_BYTES.get(extension);
        if (expectedSignatures == null) {
            // No magic bytes defined for this extension (text-based format), skip
            return;
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = is.read(header);
            if (bytesRead < 2) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "File content does not match its extension");
            }

            for (byte[] signature : expectedSignatures) {
                if (bytesRead >= signature.length && startsWith(header, signature)) {
                    return; // Match found
                }
            }

            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "File content does not match its extension");
        } catch (IOException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Unable to read file for validation");
        }
    }

    /** Checks if the header bytes start with the given signature. */
    private static boolean startsWith(byte[] header, byte[] signature) {
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sanitizes a filename by stripping null bytes, removing path components, filtering unsafe
     * characters, and truncating to 255 characters.
     *
     * @param filename the original filename
     * @return a safe filename
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return UUID.randomUUID().toString();
        }

        // Strip null bytes
        String sanitized = filename.replace("\u0000", "");

        // Extract filename only — remove path components
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        int lastBackslash = sanitized.lastIndexOf('\\');
        if (lastBackslash >= 0) {
            sanitized = sanitized.substring(lastBackslash + 1);
        }

        // Remove characters outside [a-zA-Z0-9._\-\s]
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._\\-\\s]", "");

        // Truncate to 255 characters
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }

        // Fallback to UUID-based name if empty
        if (sanitized.isEmpty()) {
            sanitized = UUID.randomUUID().toString();
        }

        return sanitized;
    }

    /**
     * Extracts the lowercase extension from a filename (last segment after the final dot).
     *
     * @param filename the filename
     * @return the lowercase extension, or empty string if none
     */
    private static String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
