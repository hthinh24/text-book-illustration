package vn.hungthinh.text_book_illustration.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import vn.hungthinh.text_book_illustration.dto.request.IdentityRequest;
import vn.hungthinh.text_book_illustration.dto.response.IdentityResponse;
import vn.hungthinh.text_book_illustration.service.IdentityService;

@WebMvcTest(IdentityController.class)
class IdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityService identityService;

    @Test
    void identity_happyPath_returns200WithUserIdAndName() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityService.getOrCreate(any(IdentityRequest.class)))
                .thenReturn(new IdentityResponse(userId, "Alice"));

        mockMvc.perform(post("/api/v1/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "alice@example.com", "name": "Alice" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void identity_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "", "name": "Alice" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void identity_malformedEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "not-an-email", "name": "Alice" }
                                """))
                .andExpect(status().isBadRequest());
    }
}
