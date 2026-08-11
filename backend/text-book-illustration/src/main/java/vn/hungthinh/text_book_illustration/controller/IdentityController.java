package vn.hungthinh.text_book_illustration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.hungthinh.text_book_illustration.dto.request.IdentityRequest;
import vn.hungthinh.text_book_illustration.dto.response.IdentityResponse;
import vn.hungthinh.text_book_illustration.service.IdentityService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class IdentityController {

    private final IdentityService identityService;

    /**
     * POST /api/v1/identity
     * <p>
     * Idempotent: if a user with the given email already exists the existing record is
     * returned unchanged (name from request body is ignored).
     */
    @PostMapping("/identity")
    public ResponseEntity<IdentityResponse> identity(@Valid @RequestBody IdentityRequest request) {
        return ResponseEntity.ok(identityService.getOrCreate(request));
    }
}
