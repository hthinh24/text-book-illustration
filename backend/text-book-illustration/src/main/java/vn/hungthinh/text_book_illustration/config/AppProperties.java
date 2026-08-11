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
}
