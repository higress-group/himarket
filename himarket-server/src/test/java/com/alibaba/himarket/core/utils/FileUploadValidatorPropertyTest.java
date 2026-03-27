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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.core.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import net.jqwik.api.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Property-based tests for FileUploadValidator.
 *
 * <p>Property 1: Bug Condition — Dangerous File Uploads Accepted
 *
 * <p>These tests MUST FAIL on unfixed code (FileUploadValidator does not exist yet), confirming
 * that no file upload validation exists. Once the fix is implemented, these tests encode the
 * expected behavior and should pass.
 *
 * <p>Property 2: Preservation — Valid File Uploads Continue to Succeed
 *
 * <p>These tests verify that valid file uploads (allowed extension + matching MIME type + within
 * size limit + clean filename) are accepted without exception. Once the validator is implemented,
 * these tests should PASS, confirming that the fix does not break valid uploads.
 */
class FileUploadValidatorPropertyTest {

    // ==================== Magic bytes for test mocks ====================

    private static final Map<String, byte[]> TEST_MAGIC_BYTES =
            Map.ofEntries(
                    Map.entry(
                            ".png",
                            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
                    Map.entry(
                            ".jpg",
                            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}),
                    Map.entry(
                            ".jpeg",
                            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}),
                    Map.entry(".gif", new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}),
                    Map.entry(".webp", new byte[] {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00}),
                    Map.entry(".pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D}),
                    Map.entry(".doc", new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}),
                    Map.entry(".docx", new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00}),
                    Map.entry(".xls", new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}),
                    Map.entry(".xlsx", new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00}),
                    Map.entry(".ppt", new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}),
                    Map.entry(".pptx", new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00}),
                    Map.entry(".mp3", new byte[] {0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00}),
                    Map.entry(".wav", new byte[] {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00}),
                    Map.entry(".ogg", new byte[] {0x4F, 0x67, 0x67, 0x53, 0x00, 0x02}),
                    Map.entry(".flac", new byte[] {0x66, 0x4C, 0x61, 0x43, 0x00, 0x00}),
                    Map.entry(".mp4", new byte[] {0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}),
                    Map.entry(
                            ".webm", new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x00}),
                    Map.entry(".avi", new byte[] {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00}),
                    Map.entry(".mov", new byte[] {0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}),
                    Map.entry(
                            ".mkv", new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x00}));

    /** Returns a mock InputStream with correct magic bytes for the given extension. */
    private static void mockMagicBytes(MultipartFile mockFile, String extension)
            throws IOException {
        byte[] magicBytes = TEST_MAGIC_BYTES.get(extension);
        if (magicBytes != null) {
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(magicBytes));
        } else {
            // Text-based formats: provide some generic text content
            when(mockFile.getInputStream())
                    .thenReturn(new ByteArrayInputStream("test content".getBytes()));
        }
    }

    // ==================== Arbitraries ====================

    private static final String[] DANGEROUS_EXTENSIONS = {
        ".jsp", ".asp", ".php", ".aspx", ".exe", ".sh", ".bat", ".cmd", ".ps1", ".war", ".jar",
        ".cgi", ".pl", ".py", ".rb", ".dll", ".com", ".msi", ".scr", ".vbs", ".wsf"
    };

    private static final String[] ALLOWED_EXTENSIONS = {
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".pdf", ".doc", ".docx", ".xls", ".xlsx",
        ".ppt", ".pptx", ".txt", ".csv", ".rtf", ".md", ".json", ".xml", ".html", ".mp3", ".wav",
        ".ogg", ".flac", ".aac", ".mp4", ".webm", ".avi", ".mov", ".mkv"
    };

    private static final String[] MISMATCHED_MIME_TYPES = {
        "application/javascript",
        "application/x-httpd-php",
        "application/x-sh",
        "application/x-msdownload",
        "text/x-python",
        "application/java-archive"
    };

    @Provide
    Arbitrary<String> dangerousExtensions() {
        return Arbitraries.of(DANGEROUS_EXTENSIONS);
    }

    @Provide
    Arbitrary<String> allowedExtensions() {
        return Arbitraries.of(ALLOWED_EXTENSIONS);
    }

    @Provide
    Arbitrary<String> mismatchedMimeTypes() {
        return Arbitraries.of(MISMATCHED_MIME_TYPES);
    }

    @Provide
    Arbitrary<String> pathTraversalFilenames() {
        return Arbitraries.of(
                "../../../etc/passwd.png",
                "..\\..\\windows\\system32\\config.png",
                "file\u0000.php.png",
                "../../shell.jsp",
                "..\\payload.exe",
                "normal\u0000.jsp",
                "%2e%2e%2fshell.jsp",
                "....//....//etc/passwd.png");
    }

    @Provide
    Arbitrary<String> cleanBaseNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> s.toLowerCase());
    }

    // ==================== Property 1: Bug Condition Tests ====================

    /**
     * <b>Validates: Requirements 1.1, 2.1</b>
     *
     * <p>Bug Condition: For any file with a dangerous extension (not in the allowed whitelist),
     * FileUploadValidator.validate() SHALL throw BusinessException. On unfixed code, this test
     * fails because FileUploadValidator does not exist — confirming no validation is in place.
     */
    @Property(tries = 50)
    void dangerousExtensionsShouldBeRejected(
            @ForAll("cleanBaseNames") String baseName,
            @ForAll("dangerousExtensions") String extension) {
        String filename = baseName + extension;
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn("application/octet-stream");
        when(mockFile.getSize()).thenReturn(1024L);

        assertThrows(
                BusinessException.class,
                () -> FileUploadValidator.validate(mockFile),
                "Dangerous extension should be rejected: " + filename);
    }

    /**
     * <b>Validates: Requirements 1.2, 2.2</b>
     *
     * <p>Bug Condition: For any file with an allowed extension but a mismatched MIME type,
     * FileUploadValidator.validate() SHALL throw BusinessException. On unfixed code, this test
     * fails because no MIME type cross-validation exists.
     */
    @Property(tries = 50)
    void mismatchedMimeTypeShouldBeRejected(
            @ForAll("cleanBaseNames") String baseName,
            @ForAll("allowedExtensions") String extension,
            @ForAll("mismatchedMimeTypes") String mimeType) {
        String filename = baseName + extension;
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn(mimeType);
        when(mockFile.getSize()).thenReturn(1024L);

        assertThrows(
                BusinessException.class,
                () -> FileUploadValidator.validate(mockFile),
                "Mismatched MIME type should be rejected: " + filename + " with " + mimeType);
    }

    /**
     * <b>Validates: Requirements 1.4, 2.4</b>
     *
     * <p>Bug Condition: For any file with a path-traversal or null-byte filename,
     * FileUploadValidator.validate() SHALL either reject the file or sanitize the filename. On
     * unfixed code, this test fails because no filename sanitization exists.
     */
    @Property(tries = 20)
    void pathTraversalFilenamesShouldBeSanitized(
            @ForAll("pathTraversalFilenames") String maliciousFilename) {
        String sanitized = FileUploadValidator.sanitizeFilename(maliciousFilename);

        assertNotNull(sanitized, "Sanitized filename should not be null");
        assertFalse(sanitized.contains("../"), "Sanitized filename should not contain ../");
        assertFalse(sanitized.contains("..\\"), "Sanitized filename should not contain ..\\");
        assertFalse(
                sanitized.contains("\u0000"), "Sanitized filename should not contain null bytes");
        assertTrue(
                sanitized.length() <= 255, "Sanitized filename should not exceed 255 characters");
        assertFalse(sanitized.isEmpty(), "Sanitized filename should not be empty");
    }

    /**
     * <b>Validates: Requirements 1.5, 2.5</b>
     *
     * <p>Bug Condition: For any file with a null MIME type and a dangerous extension,
     * FileUploadValidator.validate() SHALL throw BusinessException. On unfixed code, this test
     * fails because null MIME types are silently accepted.
     */
    @Property(tries = 30)
    void nullMimeTypeWithDangerousExtensionShouldBeRejected(
            @ForAll("cleanBaseNames") String baseName,
            @ForAll("dangerousExtensions") String extension) {
        String filename = baseName + extension;
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn(null);
        when(mockFile.getSize()).thenReturn(1024L);

        assertThrows(
                BusinessException.class,
                () -> FileUploadValidator.validate(mockFile),
                "Null MIME type with dangerous extension should be rejected: " + filename);
    }

    // ==================== Property 2: Preservation Arbitraries ====================

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB

    private static final String[][] IMAGE_EXTENSIONS_AND_MIMES = {
        {".png", "image/png"},
        {".jpg", "image/jpeg"},
        {".jpeg", "image/jpeg"},
        {".gif", "image/gif"},
        {".webp", "image/webp"},
        {".svg", "image/svg+xml"}
    };

    private static final String[][] DOCUMENT_EXTENSIONS_AND_MIMES = {
        {".pdf", "application/pdf"},
        {".doc", "application/msword"},
        {".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
        {".xls", "application/vnd.ms-excel"},
        {".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
        {".ppt", "application/vnd.ms-powerpoint"},
        {".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
        {".txt", "text/plain"},
        {".csv", "text/csv"},
        {".rtf", "application/rtf"},
        {".md", "text/markdown"},
        {".json", "application/json"},
        {".xml", "application/xml"},
        {".html", "text/html"}
    };

    private static final String[][] AUDIO_EXTENSIONS_AND_MIMES = {
        {".mp3", "audio/mpeg"},
        {".wav", "audio/wav"},
        {".ogg", "audio/ogg"},
        {".flac", "audio/flac"},
        {".aac", "audio/aac"}
    };

    private static final String[][] VIDEO_EXTENSIONS_AND_MIMES = {
        {".mp4", "video/mp4"},
        {".webm", "video/webm"},
        {".avi", "video/x-msvideo"},
        {".mov", "video/quicktime"},
        {".mkv", "video/x-matroska"}
    };

    @Provide
    Arbitrary<String[]> validImageExtensionAndMime() {
        return Arbitraries.of(IMAGE_EXTENSIONS_AND_MIMES);
    }

    @Provide
    Arbitrary<String[]> validDocumentExtensionAndMime() {
        return Arbitraries.of(DOCUMENT_EXTENSIONS_AND_MIMES);
    }

    @Provide
    Arbitrary<String[]> validAudioExtensionAndMime() {
        return Arbitraries.of(AUDIO_EXTENSIONS_AND_MIMES);
    }

    @Provide
    Arbitrary<String[]> validVideoExtensionAndMime() {
        return Arbitraries.of(VIDEO_EXTENSIONS_AND_MIMES);
    }

    @Provide
    Arbitrary<String[]> validExtensionAndMime() {
        return Arbitraries.oneOf(
                validImageExtensionAndMime(),
                validDocumentExtensionAndMime(),
                validAudioExtensionAndMime(),
                validVideoExtensionAndMime());
    }

    @Provide
    Arbitrary<Long> validFileSizes() {
        return Arbitraries.longs().between(1L, MAX_FILE_SIZE);
    }

    @Provide
    Arbitrary<String> cleanFilenames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(30);
    }

    // ==================== Property 2: Preservation Tests ====================

    /**
     * <b>Validates: Requirements 3.1</b>
     *
     * <p>Preservation: For any valid image file (allowed image extension + matching MIME type +
     * within size limit + clean filename), FileUploadValidator.validate() SHALL NOT throw any
     * exception.
     */
    @Property(tries = 50)
    void validImageUploadsShouldBeAccepted(
            @ForAll("cleanFilenames") String baseName,
            @ForAll("validImageExtensionAndMime") String[] extAndMime,
            @ForAll("validFileSizes") long fileSize)
            throws IOException {
        String filename = baseName + extAndMime[0];
        String mimeType = extAndMime[1];
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn(mimeType);
        when(mockFile.getSize()).thenReturn(fileSize);
        mockMagicBytes(mockFile, extAndMime[0]);

        assertDoesNotThrow(
                () -> FileUploadValidator.validate(mockFile),
                "Valid image upload should be accepted: " + filename + " (" + mimeType + ")");
    }

    /**
     * <b>Validates: Requirements 3.2</b>
     *
     * <p>Preservation: For any valid document file (allowed document extension + matching MIME type
     * + within size limit + clean filename), FileUploadValidator.validate() SHALL NOT throw any
     * exception.
     */
    @Property(tries = 50)
    void validDocumentUploadsShouldBeAccepted(
            @ForAll("cleanFilenames") String baseName,
            @ForAll("validDocumentExtensionAndMime") String[] extAndMime,
            @ForAll("validFileSizes") long fileSize)
            throws IOException {
        String filename = baseName + extAndMime[0];
        String mimeType = extAndMime[1];
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn(mimeType);
        when(mockFile.getSize()).thenReturn(fileSize);
        mockMagicBytes(mockFile, extAndMime[0]);

        assertDoesNotThrow(
                () -> FileUploadValidator.validate(mockFile),
                "Valid document upload should be accepted: " + filename + " (" + mimeType + ")");
    }

    /**
     * <b>Validates: Requirements 3.3</b>
     *
     * <p>Preservation: For any valid audio file (allowed audio extension + matching MIME type +
     * within size limit + clean filename), FileUploadValidator.validate() SHALL NOT throw any
     * exception.
     */
    @Property(tries = 50)
    void validAudioUploadsShouldBeAccepted(
            @ForAll("cleanFilenames") String baseName,
            @ForAll("validAudioExtensionAndMime") String[] extAndMime,
            @ForAll("validFileSizes") long fileSize)
            throws IOException {
        String filename = baseName + extAndMime[0];
        String mimeType = extAndMime[1];
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn(mimeType);
        when(mockFile.getSize()).thenReturn(fileSize);
        mockMagicBytes(mockFile, extAndMime[0]);

        assertDoesNotThrow(
                () -> FileUploadValidator.validate(mockFile),
                "Valid audio upload should be accepted: " + filename + " (" + mimeType + ")");
    }

    /**
     * <b>Validates: Requirements 3.4</b>
     *
     * <p>Preservation: For any valid video file (allowed video extension + matching MIME type +
     * within size limit + clean filename), FileUploadValidator.validate() SHALL NOT throw any
     * exception.
     */
    @Property(tries = 50)
    void validVideoUploadsShouldBeAccepted(
            @ForAll("cleanFilenames") String baseName,
            @ForAll("validVideoExtensionAndMime") String[] extAndMime,
            @ForAll("validFileSizes") long fileSize)
            throws IOException {
        String filename = baseName + extAndMime[0];
        String mimeType = extAndMime[1];
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getContentType()).thenReturn(mimeType);
        when(mockFile.getSize()).thenReturn(fileSize);
        mockMagicBytes(mockFile, extAndMime[0]);

        assertDoesNotThrow(
                () -> FileUploadValidator.validate(mockFile),
                "Valid video upload should be accepted: " + filename + " (" + mimeType + ")");
    }

    /**
     * <b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b>
     *
     * <p>Preservation: For any valid file with a clean filename,
     * FileUploadValidator.sanitizeFilename() SHALL preserve the original filename unchanged (since
     * it contains only safe characters).
     */
    @Property(tries = 50)
    void sanitizeFilenamePreservesCleanFilenames(
            @ForAll("cleanFilenames") String baseName,
            @ForAll("validExtensionAndMime") String[] extAndMime) {
        String filename = baseName + extAndMime[0];
        String sanitized = FileUploadValidator.sanitizeFilename(filename);

        assertEquals(
                filename,
                sanitized,
                "Clean filename should be preserved by sanitizeFilename: " + filename);
    }
}
