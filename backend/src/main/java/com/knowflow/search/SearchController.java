package com.knowflow.search;

import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<Page<SearchService.DocumentSearchResult>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) UUID department,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID owner,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(searchService.search(
                q, category, tag, department, status, owner, classification, from, to, user, pageable
        ));
    }

    @GetMapping("/chunks")
    public ResponseEntity<java.util.List<SearchService.ChunkSearchResult>> searchChunks(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(searchService.searchChunks(q, user, limit));
    }
}
