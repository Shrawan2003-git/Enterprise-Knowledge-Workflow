package com.knowflow.common;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback MessageConverter that masks sensitive secrets, JWT tokens, passwords,
 * and API keys before outputting log records.
 */
public class LogMaskingConverter extends MessageConverter {

    private static final Pattern SENSITIVE_PATTERNS = Pattern.compile(
            "(?i)(password|token|secret|apiKey|authorization|bearer)[\\s:=]+([\"']?[a-zA-Z0-9._\\-~!@#$%^&*()+=]+[\"']?)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null || message.isBlank()) {
            return message;
        }

        Matcher matcher = SENSITIVE_PATTERNS.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            matcher.appendReplacement(sb, key + "=\"[REDACTED]\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
