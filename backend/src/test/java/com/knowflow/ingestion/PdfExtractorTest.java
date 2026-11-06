package com.knowflow.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfExtractorTest {

    private final PdfExtractor extractor = new PdfExtractor();

    @Test
    void extractsSyntheticTwoPagePdf() throws IOException {
        ReflectionTestUtils.setField(extractor, "maxPages", 100);

        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            // Page 1
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("KnowFlow Architecture Guidelines - Page 1");
                cs.endText();
            }

            // Page 2
            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("Security Protocol Standards - Page 2");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        List<PageExtractionResult> pages = extractor.extractPages(new ByteArrayInputStream(pdfBytes));

        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).pageNumber());
        assertTrue(pages.get(0).text().contains("KnowFlow Architecture Guidelines - Page 1"));
        assertEquals(2, pages.get(1).pageNumber());
        assertTrue(pages.get(1).text().contains("Security Protocol Standards - Page 2"));
    }

    @Test
    void rejectsMalformedPdfWithClassifiedCode() {
        byte[] junk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        IngestionException ex = assertThrows(IngestionException.class, () ->
                extractor.extractPages(new ByteArrayInputStream(junk)));

        assertEquals(IngestionErrorCode.MALFORMED_PDF, ex.getErrorCode());
    }
}
