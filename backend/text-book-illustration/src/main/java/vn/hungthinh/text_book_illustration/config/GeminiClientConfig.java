package vn.hungthinh.text_book_illustration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a RestTemplate and RestClient configured for Gemini API calls.
 * Kept separate from GeminiRestClient so tests can substitute their own
 * RestTemplate backed by MockRestServiceServer without a Spring context.
 */
@Configuration
public class GeminiClientConfig {

    /**
     * Plain RestTemplate — no custom timeouts; Spring's defaults apply.
     * Exposed as a bean so MockRestServiceServer can attach to it in tests.
     */
    @Bean(name = "geminiRestTemplate")
    RestTemplate geminiRestTemplate() {
        return new RestTemplate();
    }

    /**
     * RestClient backed by geminiRestTemplate.
     * Headers (api-key, Api-Revision) and base URL are applied per-request
     * inside GeminiRestClient to keep bean initialisation free of external calls.
     */
    @Bean(name = "geminiHttpClient")
    RestClient geminiHttpClient(RestTemplate geminiRestTemplate) {
        return RestClient.create(geminiRestTemplate);
    }
}
