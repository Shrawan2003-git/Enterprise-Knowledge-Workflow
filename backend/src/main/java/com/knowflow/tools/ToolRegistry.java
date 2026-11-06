package com.knowflow.tools;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions != null) {
            for (ToolDefinition tool : toolDefinitions) {
                tools.put(tool.getName(), tool);
            }
        }
    }

    public Optional<ToolDefinition> getTool(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name.trim()));
    }

    public boolean isAllowed(String name) {
        return name != null && tools.containsKey(name.trim());
    }

    public List<ToolDefinition> listAllTools() {
        return new ArrayList<>(tools.values());
    }

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}
