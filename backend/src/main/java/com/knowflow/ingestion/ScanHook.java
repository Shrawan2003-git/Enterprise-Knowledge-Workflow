package com.knowflow.ingestion;

import java.io.InputStream;

public interface ScanHook {

    record ScanResult(boolean passed, String detail) {}

    ScanResult scan(InputStream fileStream, String filename);
}
