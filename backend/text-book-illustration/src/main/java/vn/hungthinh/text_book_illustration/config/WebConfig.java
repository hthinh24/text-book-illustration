package vn.hungthinh.text_book_illustration.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(appProperties.getFileStorageRoot()).toAbsolutePath().normalize();
        String uploadUri = uploadPath.toUri().toString();

        System.out.println("==================================================");
        System.out.println("[DEBUG] Absolute Storage Path: " + uploadPath.toString());
        System.out.println("[DEBUG] Resource Location URI: " + uploadPath.toUri().toString());
        System.out.println("==================================================");

        registry.addResourceHandler("/data/**")
                .addResourceLocations(uploadUri);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/data/portraits/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET");
    }
}