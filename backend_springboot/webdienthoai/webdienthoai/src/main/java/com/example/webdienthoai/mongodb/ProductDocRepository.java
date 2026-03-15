package com.example.webdienthoai.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductDocRepository extends MongoRepository<ProductDoc, String> {
}
