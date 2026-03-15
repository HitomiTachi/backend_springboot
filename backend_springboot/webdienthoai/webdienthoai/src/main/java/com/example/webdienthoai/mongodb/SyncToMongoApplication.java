package com.example.webdienthoai.mongodb;

import com.mongodb.client.MongoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Entry point chỉ để đồng bộ dữ liệu (categories, products + specs) lên MongoDB local (xem bằng Compass).
 * Không dùng Atlas. Không chạy web server, không dùng MySQL/JPA.
 *
 * JPA/DataSource exclusions nằm trong application-mongodb.properties (profile-specific),
 * không đặt ở annotation để tránh ảnh hưởng main app khi component-scan.
 */
@SpringBootApplication(scanBasePackages = "com.example.webdienthoai.mongodb")
@EnableMongoRepositories(basePackages = "com.example.webdienthoai.mongodb")
public class SyncToMongoApplication {

    @Value("${spring.data.mongodb.database:techhome}")
    private String databaseName;

    @Bean
    @Profile("mongodb")
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, databaseName);
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SyncToMongoApplication.class);
        app.setAdditionalProfiles("mongodb");
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
}
