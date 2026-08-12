package vn.hungthinh.text_book_illustration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Root directory for all project files (book text, generated images). */
    private String fileStorageRoot;

    /** Seconds before a RUNNING step is considered stuck and eligible for stuck-recovery retry. */
    private int stepTimeoutSeconds = 180;

    /** Maximum number of retries allowed per step (inclusive — retry_count must be <= this). */
    private int maxRetryCount = 3;
}
