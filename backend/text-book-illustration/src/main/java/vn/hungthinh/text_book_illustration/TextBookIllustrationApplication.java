package vn.hungthinh.text_book_illustration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import vn.hungthinh.text_book_illustration.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class TextBookIllustrationApplication {
	public static void main(String[] args) {
        System.out.println("SERVER_PORT = " + System.getenv("SERVER_PORT"));

		SpringApplication.run(TextBookIllustrationApplication.class, args);
	}

}

