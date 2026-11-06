package com.knowflow.copilot;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fake chat model for offline tests and CI.
 * Allows scriptable responses, simulated provider errors, and validation of prompt boundaries.
 */
public class FakeChatModel implements ChatModelGateway {

    private final Queue<String> scriptedResponses = new ConcurrentLinkedQueue<>();
    private RuntimeException nextException = null;

    public void enqueueScriptedResponse(String jsonResponse) {
        scriptedResponses.offer(jsonResponse);
    }

    public void setNextException(RuntimeException ex) {
        this.nextException = ex;
    }

    public void clear() {
        scriptedResponses.clear();
        nextException = null;
    }

    @Override
    public ChatResponse generate(String systemPrompt, String userMessage) {
        if (nextException != null) {
            RuntimeException ex = nextException;
            nextException = null;
            throw ex;
        }

        if (!scriptedResponses.isEmpty()) {
            return new ChatResponse(scriptedResponses.poll(), 250, 80, 330);
        }

        // Default smart heuristic response based on available [S#] tags in userMessage
        Pattern pattern = Pattern.compile("\\[S(\\d+)\\]");
        Matcher matcher = pattern.matcher(userMessage != null ? userMessage : "");

        boolean hasPassages = matcher.find();
        if (!hasPassages) {
            return new ChatResponse(
                    """
                    {
                      "answer": "No relevant evidence was found in the authorized documents to answer this question.",
                      "citations": [],
                      "confidence": "INSUFFICIENT"
                    }
                    """,
                    100, 30, 130
            );
        }

        // Return answered response with [S1]
        return new ChatResponse(
                """
                {
                  "answer": "Based on the verified enterprise documentation, the policy applies as documented [S1].",
                  "citations": [
                    {"sourceId": "[S1]"}
                  ],
                  "confidence": "ANSWERED"
                }
                """,
                200, 50, 250
        );
    }

    @Override
    public String getModelName() {
        return "fake-gpt-4o-mini";
    }
}
