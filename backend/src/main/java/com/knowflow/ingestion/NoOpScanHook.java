package com.knowflow.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@ConditionalOnMissingBean(type = "com.knowflow.ingestion.ClamAvScanHook")
public class NoOpScanHook implements ScanHook {

    @Override
    public ScanResult scan(InputStream fileStream, String filename) {
        // Safe default: passes through without error
        return new ScanResult(true, "Scan bypassed (NoOpScanHook)");
    }
}
