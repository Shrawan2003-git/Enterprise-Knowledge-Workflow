package com.knowflow.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractor.class);

    @Value("${knowflow.ingestion.max-pages:1000}")
    private int maxPages;

    public List<PageExtractionResult> extractPages(InputStream inputStream) {
        if (inputStream == null) {
            throw new IngestionException(IngestionErrorCode.FILE_NOT_FOUND, "Input stream is null");
        }

        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new IngestionException(IngestionErrorCode.STORAGE_READ_ERROR, "Failed reading document data from stream", e);
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IngestionException(IngestionErrorCode.PDF_ENCRYPTED, "PDF document is encrypted or password-protected");
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount > maxPages) {
                throw new IngestionException(IngestionErrorCode.PAGE_LIMIT_EXCEEDED, 
                        "Document exceeds maximum allowed page count (" + pageCount + " > " + maxPages + ")");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<PageExtractionResult> pages = new ArrayList<>(pageCount);
            for (int p = 1; p <= pageCount; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(document);
                pages.add(new PageExtractionResult(p, pageText != null ? pageText : ""));
            }

            log.info("Extracted {} pages successfully", pageCount);
            return pages;
        } catch (IngestionException ie) {
            throw ie;
        } catch (Throwable t) {
            log.error("PDFBox extraction error: {}", t.getMessage());
            String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
            if (msg.contains("encrypted") || msg.contains("password")) {
                throw new IngestionException(IngestionErrorCode.PDF_ENCRYPTED, "PDF document is password protected", t);
            }
            throw new IngestionException(IngestionErrorCode.MALFORMED_PDF, "Document could not be parsed as a valid PDF: " + t.getMessage(), t);
        }
    }
}
