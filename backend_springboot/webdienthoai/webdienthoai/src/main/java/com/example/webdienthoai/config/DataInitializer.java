package com.example.webdienthoai.config;

import com.example.webdienthoai.entity.Category;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.entity.User;
import com.example.webdienthoai.repository.CategoryRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Tạo dữ liệu mẫu (categories, products) nếu DB trống — dùng cho dev.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
                // Danh sách email luôn được cấp role admin trong môi trường dev.
                List<String> adminEmails = List.of("admin@techhome.com", "admin@gmail.com");
                for (String adminEmail : adminEmails) {
                    userRepository.findByEmail(adminEmail).ifPresent(u -> {
                        u.setRole("admin");
                        userRepository.save(u);
                    });
                }
                // Tạo tài khoản admin mặc định nếu chưa có.
                if (!userRepository.existsByEmail("admin@techhome.com")) {
                    userRepository.save(User.builder()
                            .name("Quản trị viên")
                            .email("admin@techhome.com")
                            .password(passwordEncoder.encode("admin123456"))
                            .role("admin")
                            .emailVerifiedAt(Instant.now())
                            .build());
                }

                // Tài khoản customer mẫu để test luồng người dùng.
                if (!userRepository.existsByEmail("user@example.com")) {
            userRepository.save(User.builder()
                    .name("Người dùng mẫu")
                    .email("user@example.com")
                    .password(passwordEncoder.encode("123456"))
                    .role("customer")
                    .emailVerifiedAt(Instant.now())
                    .build());
        }

        if (categoryRepository.count() > 0) return;

        Category phone = categoryRepository.save(Category.builder().name("Điện thoại").build());
        Category laptop = categoryRepository.save(Category.builder().name("Laptop").build());
        Category tablet = categoryRepository.save(Category.builder().name("Tablet").build());

        List<Product> products = List.of(
                Product.builder().name("iPhone 15").description("Apple iPhone 15").image("/images/iphone15.jpg")
                        .price(new BigDecimal("24990000")).category(phone).stock(50).featured(true).build(),
                Product.builder().name("Samsung Galaxy S24").description("Samsung Galaxy S24")
                        .image("/images/s24.jpg").price(new BigDecimal("21990000")).category(phone).stock(30).featured(true).build(),
                Product.builder().name("iPhone 17 e").description("Apple iPhone 17 e - Chip A19, màn hình 6.1 inch, camera 48MP")
                        .image("/images/iphone17e.jpg").price(new BigDecimal("25990000")).category(phone).stock(35).featured(true).build(),
                Product.builder().name("iPhone 17").description("Apple iPhone 17 - Chip A19, màn hình 6.3 inch, camera 48MP")
                        .image("/images/iphone17.jpg").price(new BigDecimal("29990000")).category(phone).stock(30).featured(true).build(),
                Product.builder().name("iPhone 17 Pro").description("Apple iPhone 17 Pro - Chip A19 Pro, màn hình 6.3 inch, camera 48MP")
                        .image("/images/iphone17pro.jpg").price(new BigDecimal("36990000")).category(phone).stock(25).featured(true).build(),
                Product.builder().name("iPhone 17 Pro Max").description("Apple iPhone 17 Pro Max - Chip A19 Pro, màn hình 6.9 inch, camera 48MP")
                        .image("/images/iphone17promax.jpg").price(new BigDecimal("42990000")).category(phone).stock(20).featured(true).build(),
                Product.builder().name("iPhone Air").description("Apple iPhone Air - Chip A19 Pro, màn hình 6.5 inch, khung Titanium")
                        .image("/images/iphoneair.jpg").price(new BigDecimal("32990000")).category(phone).stock(22).featured(true).build(),
                Product.builder().name("MacBook Pro M3").description("Apple MacBook Pro M3")
                        .image("/images/macbook.jpg").price(new BigDecimal("45990000")).category(laptop).stock(20).featured(true).build(),
                Product.builder().name("iPad Pro").description("Apple iPad Pro")
                        .image("/images/ipad.jpg").price(new BigDecimal("27990000")).category(tablet).stock(25).featured(false).build()
        );
        productRepository.saveAll(products);
    }
}
