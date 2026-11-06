package com.knowflow.tools;

import com.knowflow.security.CurrentUser;
import java.util.Map;

public interface ToolDefinition {

    String getName();

    String getDescription();

    Map<String, Object> getParameterSchema();

    ToolExecutionResult execute(Map<String, Object> rawArgs, CurrentUser user);
}
