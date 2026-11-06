package com.knowflow.copilot;

import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/copilot")
@Tag(name = "Copilot", description = "Grounded enterprise knowledge copilot endpoints")
public class CopilotController {

    private final CopilotService copilotService;

    public CopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question to the copilot grounded in authorized enterprise documents")
    public ResponseEntity<CopilotAnswer> ask(@Valid @RequestBody CopilotAskRequest request) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();

        int topK = request.topK() != null && request.topK() > 0 ? request.topK() : 5;
        double minScore = request.minScore() != null ? request.minScore() : 0.01;
        RetrievalOptions options = new RetrievalOptions(topK, 3000, minScore);

        CopilotAnswer answer = copilotService.ask(request.question(), user, options);
        return ResponseEntity.ok(answer);
    }
}
