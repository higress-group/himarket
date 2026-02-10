package com.alibaba.himarket.service.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Converts office documents (PPTX, PPT, etc.) to PDF using LibreOffice headless.
 *
 * <p>Converted PDFs are cached next to the source file. A cached PDF is reused if it is newer than
 * the source file.
 */
@Service
@Slf4j
public class DocumentConversionService {

    private static final long CONVERSION_TIMEOUT_SECONDS = 60;
    private static final String LIBREOFFICE_COMMAND = "libreoffice";

    /**
     * Convert an office document to PDF.
     *
     * @param sourceFile absolute path to the source document (.pptx, .ppt, etc.)
     * @return path to the converted PDF, or {@code null} if conversion failed
     */
    public Path convertToPdf(Path sourceFile) {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path cachedPdf = resolveCachedPdfPath(normalizedSource);

        Path cached = getCachedPdfIfUpToDate(normalizedSource);
        if (cached != null) {
            log.debug("Using cached PDF: {}", cached);
            return cached;
        }

        // Run LibreOffice headless conversion
        try {
            Path outDir = normalizedSource.getParent();
            ProcessBuilder pb =
                    new ProcessBuilder(
                            LIBREOFFICE_COMMAND,
                            "--headless",
                            "--convert-to",
                            "pdf",
                            normalizedSource.getFileName().toString(),
                            "--outdir",
                            outDir.toString());
            pb.directory(outDir.toFile());
            pb.redirectErrorStream(true);

            log.info("Converting to PDF: {}", normalizedSource);
            Process process = pb.start();

            boolean finished = process.waitFor(CONVERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn(
                        "LibreOffice conversion timed out after {}s: {}",
                        CONVERSION_TIMEOUT_SECONDS,
                        normalizedSource);
                return null;
            }

            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                log.warn(
                        "LibreOffice conversion failed (exit={}): {}\n{}",
                        process.exitValue(),
                        normalizedSource,
                        output);
                return null;
            }

            // LibreOffice outputs <stem>.pdf in the outdir (e.g. demo.pptx → demo.pdf)
            String stem = stripExtension(normalizedSource.getFileName().toString());
            Path generatedPdf = outDir.resolve(stem + ".pdf");

            if (!Files.exists(generatedPdf)) {
                log.warn("Expected PDF not found after conversion: {}", generatedPdf);
                return null;
            }

            // Rename to our cache path to avoid collisions
            if (!generatedPdf.equals(cachedPdf)) {
                Files.move(
                        generatedPdf, cachedPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            log.info(
                    "Conversion successful: {} → {}",
                    normalizedSource.getFileName(),
                    cachedPdf.getFileName());
            return cachedPdf;

        } catch (IOException e) {
            log.error("LibreOffice conversion I/O error: {}", normalizedSource, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LibreOffice conversion interrupted: {}", normalizedSource);
            return null;
        }
    }

    /**
     * Get the cache path for converted PDF.
     *
     * @param sourceFile source office file
     * @return cache path, pattern: {@code <original>.pdf}
     */
    public Path resolveCachedPdfPath(Path sourceFile) {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        return normalizedSource.resolveSibling(normalizedSource.getFileName() + ".pdf");
    }

    /**
     * Return the cached PDF path if it exists and is newer than source, otherwise {@code null}.
     */
    public Path getCachedPdfIfUpToDate(Path sourceFile) {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path cachedPdf = resolveCachedPdfPath(normalizedSource);

        try {
            if (Files.exists(cachedPdf)
                    && Files.getLastModifiedTime(cachedPdf)
                                    .compareTo(Files.getLastModifiedTime(normalizedSource))
                            >= 0) {
                return cachedPdf;
            }
        } catch (IOException e) {
            log.warn("Failed to check cached PDF timestamp for {}", normalizedSource, e);
        }
        return null;
    }

    private static String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }
}
