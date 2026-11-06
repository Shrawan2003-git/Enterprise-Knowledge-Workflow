package com.knowflow.documents;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TaxonomyController {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public TaxonomyController(CategoryRepository categoryRepository, TagRepository tagRepository) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> listCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('KNOWLEDGE_MANAGER', 'ADMIN')")
    public ResponseEntity<Category> createCategory(@RequestBody Category category) {
        if (categoryRepository.existsByNameIgnoreCase(category.getName())) {
            throw new IllegalStateException("Category already exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryRepository.save(category));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<Tag>> listTags() {
        return ResponseEntity.ok(tagRepository.findAll());
    }

    @PostMapping("/tags")
    @PreAuthorize("hasAnyRole('KNOWLEDGE_MANAGER', 'ADMIN')")
    public ResponseEntity<Tag> createTag(@RequestBody Tag tag) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                tagRepository.findByNameIgnoreCase(tag.getName())
                        .orElseGet(() -> tagRepository.save(new Tag(tag.getName())))
        );
    }
}
