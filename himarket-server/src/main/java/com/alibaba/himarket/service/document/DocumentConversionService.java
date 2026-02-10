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
        // The cached PDF uses the pattern: <original>.pdf  (e.g. demo.pptx.pdf)
        // This avoids name collision when demo.pptx and demo.pdf coexist.
        Path cachedPdf = sourceFile.resolveSibling(sourceFile.getFileName() + ".pdf");

        // Return cached version if it exists and is newer than the source
        try {
            if (Files.exists(cachedPdf)
                    && Files.getLastModifiedTime(cachedPdf)
                                    .compareTo(Files.getLastModifiedTime(sourceFile))
                            >= 0) {
                log.debug("Using cached PDF: {}", cachedPdf);
                return cachedPdf;
            }
        } catch (IOException e) {
            log.warn("Failed to check cached PDF timestamp, will re-convert", e);
        }

        // Run LibreOffice headless conversion
        try {
            Path outDir = sourceFile.getParent();
            ProcessBuilder pb =
                    new ProcessBuilder(
                            LIBREOFFICE_COMMAND,
                            "--headless",
                            "--convert-to",
                            "pdf",
                            sourceFile.getFileName().toString(),
                            "--outdir",
                            outDir.toString());
            pb.directory(outDir.toFile());
            pb.redirectErrorStream(true);

            log.info("Converting to PDF: {}", sourceFile);
            Process process = pb.start();

            boolean finished = process.waitFor(CONVERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("LibreOffice conversion timed out after {}s: {}", CONVERSION_TIMEOUT_SECONDS, sourceFile);
                return null;
            }

            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                log.warn("LibreOffice conversion failed (exit={}): {}\n{}", process.exitValue(), sourceFile, output);
                return null;
            }

            // LibreOffice outputs <stem>.pdf in the outdir (e.g. demo.pptx → demo.pdf)
            String stem = stripExtension(sourceFile.getFileName().toString());
            Path generatedPdf = outDir.resolve(stem + ".pdf");

            if (!Files.exists(generatedPdf)) {
                log.warn("Expected PDF not found after conversion: {}", generatedPdf);
                return null;
            }

            // Rename to our cache path to avoid collisions
            if (!generatedPdf.equals(cachedPdf)) {
                Files.move(generatedPdf, cachedPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Conversion successful: {} → {}", sourceFile.getFileName(), cachedPdf.getFileName());
            return cachedPdf;

        } catch (IOException e) {
            log.error("LibreOffice conversion I/O error: {}", sourceFile, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LibreOffice conversion interrupted: {}", sourceFile);
            return null;
        }
    }

    private static String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }
}
