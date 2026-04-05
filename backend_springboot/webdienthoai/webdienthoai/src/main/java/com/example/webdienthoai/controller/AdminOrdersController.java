package com.example.webdienthoai.controller;

import com.example.webdienthoai.dto.AdminOrderDto;
import com.example.webdienthoai.dto.AdminOrderItemDto;
import com.example.webdienthoai.dto.OrderStatusHistoryDto;
import com.example.webdienthoai.dto.UpdateAdminOrderStatusRequest;
import com.example.webdienthoai.entity.Address;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderItem;
import com.example.webdienthoai.entity.Product;
import com.example.webdienthoai.repository.AddressRepository;
import com.example.webdienthoai.repository.OrderRepository;
import com.example.webdienthoai.repository.OrderStatusAuditRepository;
import com.example.webdienthoai.repository.ProductRepository;
import com.example.webdienthoai.security.UserPrincipal;
import com.example.webdienthoai.service.OrderStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOrdersController {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final OrderStatusAuditRepository orderStatusAuditRepository;
    private final OrderStatusService orderStatusService;

    private String toShippingAddressSummary(Address addr) {
        if (addr == null) return "—";
        String line2 = addr.getLine2() != null && !addr.getLine2().isBlank() ? ", " + addr.getLine2().trim() : "";
        String statePart = addr.getState() != null && !addr.getState().isBlank() ? ", " + addr.getState().trim() : "";
        String zipPart = addr.getZipCode() != null && !addr.getZipCode().isBlank() ? " " + addr.getZipCode().trim() : "";

        // Keep it short for admin table.
        return (addr.getName() != null ? addr.getName().trim() : "—")
                + " — "
                + (addr.getStreet() != null ? addr.getStreet().trim() : "—")
                + line2
                + ", "
                + (addr.getCity() != null ? addr.getCity().trim() : "—")
                + statePart
                + zipPart
                + (addr.getCountry() != null && !addr.getCountry().isBlank() ? ", " + addr.getCountry().trim() : "");
    }

    private AdminOrderDto mapOrder(Order o) {
        Address shipping = null;
        if (o.getShippingAddressId() != null) {
            shipping = addressRepository.findById(o.getShippingAddressId()).orElse(null);
        }

        List<AdminOrderItemDto> itemDtos = o.getItems().stream()
                .map(item -> new AdminOrderItemDto(
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getProductName(),
                        item.getProductImage(),
                        item.getQuantity(),
                        item.getPriceAtOrder(),
                        item.getLineTotal(),
                        item.getSelectedColor(),
                        item.getSelectedStorage()
                ))
                .toList();

        return AdminOrderDto.builder()
                .id(o.getId())
                .customerName(o.getUser() != null ? o.getUser().getName() : "—")
                .shippingAddressSummary(toShippingAddressSummary(shipping))
                .items(itemDtos)
                .subtotal(o.getSubtotal())
                .discountAmount(o.getDiscountAmount())
                .shippingCost(o.getShippingCost())
                .totalPrice(o.getTotalPrice())
                .paymentMethod(o.getPaymentMethod())
                .notes(o.getNotes())
                .status(o.getStatus())
                .createdAt(o.getCreatedAt())
                .build();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var orderPage = orderRepository.searchForAdmin(
                status != null && !status.isBlank() ? status.trim() : null,
                PageRequest.of(page, size, Sort.by(direction, "createdAt")));
        List<AdminOrderDto> items = orderPage.getContent().stream().map(this::mapOrder).toList();
        return ResponseEntity.ok(Map.of(
                "items", items,
                "page", orderPage.getNumber(),
                "size", orderPage.getSize(),
                "total", orderPage.getTotalElements(),
                "totalElements", orderPage.getTotalElements(),
                "totalPages", orderPage.getTotalPages()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<AdminOrderDto> getOrderById(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(this::mapOrder)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/{id}/status-history")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getOrderStatusHistory(@PathVariable Long id) {
        if (!orderRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        List<OrderStatusHistoryDto> items = orderStatusAuditRepository.findByOrderIdOrderByChangedAtDesc(id)
                .stream()
                .map(OrderStatusHistoryDto::fromEntity)
                .toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateOrderStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody UpdateAdminOrderStatusRequest req) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return orderRepository.findById(id).map(o -> {
            try {
                String oldStatus = orderStatusService.normalize(o.getStatus());
                String newStatus = orderStatusService.normalize(req.getStatus());
                orderStatusService.validateStatus(newStatus);

                if ("vnpay".equalsIgnoreCase(String.valueOf(o.getPaymentMethod()))
                        && "pending_payment".equals(oldStatus)
                        && ("cancelled".equals(newStatus) || "rejected".equals(newStatus))) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message",
                            "Không hủy/từ chối đơn VNPay đang chờ thanh toán từ admin — chờ kết quả cổng thanh toán."));
                }

                orderStatusService.validateAdminTransition(oldStatus, newStatus);

                boolean restock = !"cancelled".equals(oldStatus)
                        && !"rejected".equals(oldStatus)
                        && ("cancelled".equals(newStatus) || "rejected".equals(newStatus));
                if (restock) {
                    for (OrderItem item : o.getItems()) {
                        Product p = item.getProduct();
                        if (p != null) {
                            int stock = p.getStock() != null ? p.getStock() : 0;
                            p.setStock(stock + (item.getQuantity() != null ? item.getQuantity() : 0));
                            productRepository.save(p);
                        }
                    }
                }

                String actor = "admin:" + principal.getUserId() + "(" + principal.getEmail() + ")";
                String note = "Admin cập nhật trạng thái đơn";
                if (req.getReason() != null && !req.getReason().isBlank()) {
                    note = note + " — " + req.getReason().trim();
                }
                orderStatusService.changeStatus(o, newStatus, actor, note);
                Order saved = orderRepository.save(o);
                return ResponseEntity.ok(mapOrder(saved));
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
            }
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

