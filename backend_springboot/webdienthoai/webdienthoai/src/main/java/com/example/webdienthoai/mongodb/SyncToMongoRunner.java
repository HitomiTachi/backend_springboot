package com.example.webdienthoai.mongodb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Đồng bộ dữ liệu mẫu (categories, products + specs) lên MongoDB local (Compass).
 * Chạy khi khởi động {@link SyncToMongoApplication}. Không dùng Atlas.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SyncToMongoRunner implements CommandLineRunner {

    private final CategoryDocRepository categoryDocRepository;
    private final ProductDocRepository productDocRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        log.info("Bắt đầu đồng bộ dữ liệu lên MongoDB (local / Compass)...");

        categoryDocRepository.deleteAll();
        productDocRepository.deleteAll();

        CategoryDoc phone = categoryDocRepository.save(CategoryDoc.builder().name("Điện thoại").slug("dien-thoai").build());
        CategoryDoc laptop = categoryDocRepository.save(CategoryDoc.builder().name("Laptop").slug("laptop").build());
        CategoryDoc tablet = categoryDocRepository.save(CategoryDoc.builder().name("Tablet").slug("tablet").build());

        String iphone17ProMaxSpecs = loadSpecs("data/iphone-17-pro-max-specs.json");
        String iphone17ProSpecs = loadSpecs("data/iphone-17-pro-specs.json");
        String iphone17Specs = loadSpecs("data/iphone-17-specs.json");
        String iphone17ESpecs = loadSpecs("data/iphone-17-e-specs.json");
        String iphoneAirSpecs = loadSpecs("data/iphone-air-specs.json");

        List<ProductDoc> products = List.of(
                product("iPhone 15", "iphone-15", "Apple iPhone 15", "/images/iphone15.jpg", "24990000", phone, 50, true, null),
                product("Samsung Galaxy S24", "samsung-galaxy-s24", "Samsung Galaxy S24", "/images/s24.jpg", "21990000", phone, 30, true, null),
                product("iPhone 17 e", "iphone-17-e", "Apple iPhone 17 e - Chip A19, màn hình 6.1\" Super Retina XDR, camera 48MP, 256GB", "/images/iphone17e.jpg", "25990000", phone, 35, true, iphone17ESpecs),
                product("iPhone 17", "iphone-17", "Apple iPhone 17 - Chip A19, màn hình 6.3\" Super Retina XDR OLED, camera 48MP Fusion Main, 256GB", "/images/iphone17.jpg", "29990000", phone, 30, true, iphone17Specs),
                product("iPhone 17 Pro", "iphone-17-pro", "Apple iPhone 17 Pro - Chip A19 Pro, màn hình 6.3\" Super Retina XDR, camera 48MP, 256GB", "/images/iphone17pro.jpg", "36990000", phone, 25, true, iphone17ProSpecs),
                product("iPhone 17 Pro Max", "iphone-17-pro-max", "Apple iPhone 17 Pro Max - Chip A19 Pro, màn hình 6.9\" Super Retina XDR, camera 48MP", "/images/iphone17promax.jpg", "42990000", phone, 20, true, iphone17ProMaxSpecs),
                product("iPhone Air", "iphone-air", "Apple iPhone Air - Chip A19 Pro, màn hình 6.5\" Super Retina XDR, khung Titanium, 5.6mm, 256GB", "/images/iphoneair.jpg", "32990000", phone, 22, true, iphoneAirSpecs),
                product("MacBook Pro M3", "macbook-pro-m3", "Apple MacBook Pro M3", "/images/macbook.jpg", "45990000", laptop, 20, true, null),
                product("iPad Pro", "ipad-pro", "Apple iPad Pro", "/images/ipad.jpg", "27990000", tablet, 25, false, null)
        );

        productDocRepository.saveAll(products);
        log.info("Đồng bộ xong vào database '{}': {} categories, {} products.",
                mongoTemplate.getDb().getName(), categoryDocRepository.count(), productDocRepository.count());
    }

    private static ProductDoc product(String name, String slug, String desc, String image, String price,
                                     CategoryDoc category, int stock, boolean featured, String specs) {
        return ProductDoc.builder()
                .name(name)
                .slug(slug)
                .description(desc)
                .image(image)
                .price(new BigDecimal(price))
                .categoryId(category.getId())
                .categoryName(category.getName())
                .stock(stock)
                .featured(featured)
                .specifications(specs)
                .build();
    }

    private static String loadSpecs(String path) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
