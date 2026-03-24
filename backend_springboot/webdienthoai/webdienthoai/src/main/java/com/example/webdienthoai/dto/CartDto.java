package com.example.webdienthoai.dto;

import com.example.webdienthoai.entity.Cart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartDto {
    private Long id;
    private Long userId;
    private List<CartItemDto> items;
    private Integer itemCount;
    private BigDecimal totalPrice;

    public static CartDto fromEntity(Cart cart) {
        if (cart == null) return null;
        
        List<CartItemDto> itemDtos = cart.getItems() == null ? List.of() :
                cart.getItems().stream()
                        .map(CartItemDto::fromEntity)
                        .collect(Collectors.toList());
        
        int itemCount = itemDtos.size();
        BigDecimal totalPrice = itemDtos.stream()
                .map(CartItemDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .items(itemDtos)
                .itemCount(itemCount)
                .totalPrice(totalPrice)
                .build();
    }
}
