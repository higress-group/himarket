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

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileUploadValidator {

    private FileUploadValidator() {}

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    // Images
                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "bmp",
                    "webp",
                    "svg",
                    // Documents
                    "txt",
                    "md",
                    "pdf",
                    "doc",
                    "docx",
                    "xls",
                    "xlsx",
                    "ppt",
                    "pptx",
                    "csv",
                    // Audio
                    "mp3",
                    "wav",
                    "ogg",
                    "aac",
                    "flac",
                    // Video
                    "mp4",
                    "avi",
                    "mov",
                    "wmv",
                    "webm",
                    // Archives
                    "zip");

    private static final Map<String, Set<String>> MIME_TO_EXTENSIONS =
            Map.ofEntries(
                    // Images
                    Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
                    Map.entry("image/png", Set.of("png")),
                    Map.entry("image/gif", Set.of("gif")),
                    Map.entry("image/bmp", Set.of("bmp")),
                    Map.entry("image/webp", Set.of("webp")),
                    Map.entry("image/svg+xml", Set.of("svg")),
                    // Documents
                    Map.entry("text/plain", Set.of("txt", "md", "csv")),
                    Map.entry("text/markdown", Set.of("md")),
                    Map.entry("text/csv", Set.of("csv")),
                    Map.entry("application/pdf", Set.of("pdf")),
                    Map.entry("application/msword", Set.of("doc")),
                    Map.entry(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            Set.of("docx")),
                    Map.entry("application/vnd.ms-excel", Set.of("xls")),
                    Map.entry(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            Set.of("xlsx")),
                    Map.entry("application/vnd.ms-powerpoint", Set.of("ppt")),
                    Map.entry(
                            "application/vnd.openxmlformats-officedocument.presentationml"
                                    + ".presentation",
                            Set.of("pptx")),
                    // Audio
                    Map.entry("audio/mpeg", Set.of("mp3")),
                    Map.entry("audio/wav", Set.of("wav")),
                    Map.entry("audio/ogg", Set.of("ogg")),
                    Map.entry("audio/aac", Set.of("aac")),
                    Map.entry("audio/flac", Set.of("flac")),
                    // Video
                    Map.entry("video/mp4", Set.of("mp4")),
                    Map.entry("video/x-msvideo", Set.of("avi")),
                    Map.entry("video/quicktime", Set.of("mov")),
                    Map.entry("video/x-ms-wmv", Set.of("wmv")),
                    Map.entry("video/webm", Set.of("webm")),
                    // Archives
                    Map.entry("application/zip", Set.of("zip")),
                    // Common fallback MIME types
                    Map.entry("application/octet-stream", ALLOWED_EXTENSIONS));

    /**
     * Extract and validate the file extension against the whitelist.
     *
     * @return the lowercase extension, or {@code null} if the filename has no extension
     * @throws IllegalArgumentException if the extension is not in the whitelist
     */
    public static String validateExtension(String filename) {
        String ext = extractExtension(filename);
        if (ext == null) {
            throw new IllegalArgumentException(
                    "File has no extension. Allowed extensions: " + ALLOWED_EXTENSIONS);
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                    "File extension '" + ext + "' is not allowed. Allowed: " + ALLOWED_EXTENSIONS);
        }
        return ext;
    }

    /**
     * Validate that the MIME type is consistent with the file extension. When the MIME type is
     * {@code null} or {@code application/octet-stream}, the check is skipped (extension whitelist
     * alone is sufficient).
     *
     * @throws IllegalArgumentException if the MIME type does not match the extension
     */
    public static void validateMimeType(String mimeType, String extension) {
        if (mimeType == null || "application/octet-stream".equals(mimeType)) {
            return;
        }
        Set<String> expected = MIME_TO_EXTENSIONS.get(mimeType);
        if (expected != null && !expected.contains(extension)) {
            throw new IllegalArgumentException(
                    "MIME type '"
                            + mimeType
                            + "' does not match file extension '."
                            + extension
                            + "'");
        }
    }

    /**
     * Sanitize a filename for safe storage. Removes path traversal sequences, null bytes, and
     * characters that are problematic in file systems or URLs.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }

        // Strip path components (both Unix and Windows separators)
        int lastSep = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (lastSep >= 0) {
            filename = filename.substring(lastSep + 1);
        }

        // Remove null bytes and control characters
        filename = filename.replaceAll("[\\x00-\\x1f]", "");

        // Remove path traversal sequences
        filename = filename.replace("..", "");

        // Keep only safe characters: letters, digits, dots, hyphens, underscores, spaces, CJK
        filename = filename.replaceAll("[^\\w.\\-\\s\\u4e00-\\u9fff\\u3400-\\u4dbf]", "_");

        // Collapse multiple dots or underscores
        filename = filename.replaceAll("[_.]{2,}", "_");

        // Trim leading/trailing dots and spaces
        filename = filename.replaceAll("^[.\\s]+|[.\\s]+$", "").trim();

        if (filename.isEmpty()) {
            return "unnamed";
        }

        return filename;
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
