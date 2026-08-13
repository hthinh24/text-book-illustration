package vn.hungthinh.text_book_illustration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a RestTemplate and RestClient configured for Gemini API calls.
 * Configured with explicit connect and read timeouts to prevent infinite blocking.
 * Kept separate from GeminiRestClient so tests can substitute their own
 * RestTemplate backed by MockRestServiceServer without a Spring context.
 */
@Configuration
public class GeminiClientConfig {

    /**
     * RestTemplate configured with 10s connect timeout and 60s read timeout.
     * Exposed as a bean so MockRestServiceServer can attach to it in tests.
     */
    @Bean(name = "geminiRestTemplate")
    RestTemplate geminiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000); // 10 seconds connect timeout
        factory.setReadTimeout(60_000);    // 60 seconds read timeout
        return new RestTemplate(factory);
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
