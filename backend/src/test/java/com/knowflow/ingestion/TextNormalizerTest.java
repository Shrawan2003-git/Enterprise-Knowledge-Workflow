package com.knowflow.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void normalizesWhitespaceAndHyphenatedWords() {
        String input = "This is a docu-\n ment detailing the  gover-\r\n nance principles.\n\n\n\nNext section.";
        String expected = "This is a document detailing the governance principles.\n\nNext section.";

        String result = normalizer.normalize(input);
        assertEquals(expected, result);
    }

    @Test
    void removesUnprintableControlCharacters() {
        String input = "Safe text\u0000with\u0007control\u001Fchars.";
        String result = normalizer.normalize(input);
        assertEquals("Safe textwithcontrolchars.", result);
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize("   \n\t  "));
    }
}
