package vn.hungthinh.text_book_illustration.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record IdentityRequest(
        @Email(message = "email must be a valid email address")
        @NotBlank(message = "email must not be blank")
        String email,

        @NotBlank(message = "name must not be blank")
        String name
) {}
