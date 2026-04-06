package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.ProductRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRatingRepository extends JpaRepository<ProductRating, Long> {

    Optional<ProductRating> findByOrderItem_Id(Long orderItemId);

    @Query("SELECT r.product.id, AVG(r.rating), COUNT(r) FROM ProductRating r WHERE r.product.id IN :ids GROUP BY r.product.id")
    List<Object[]> aggregateByProductIds(@Param("ids") Collection<Long> ids);
}
