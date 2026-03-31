package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderStatusAudit;
import com.example.webdienthoai.repository.OrderStatusAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderStatusServiceTests {

    @Test
    void shouldValidateAllowedStatuses() {
        OrderStatusAuditRepository repo = mock(OrderStatusAuditRepository.class);
        OrderStatusService service = new OrderStatusService(repo);
        service.validateStatus("pending");
        service.validateStatus("paid");
        service.validateStatus("shipping");
        service.validateStatus("completed");
        service.validateStatus("cancelled");
    }

    @Test
    void shouldRejectInvalidStatus() {
        OrderStatusAuditRepository repo = mock(OrderStatusAuditRepository.class);
        OrderStatusService service = new OrderStatusService(repo);
        assertThrows(IllegalArgumentException.class, () -> service.validateStatus("unknown"));
    }

    @Test
    void shouldPersistAuditWhenStatusChanges() {
        OrderStatusAuditRepository repo = mock(OrderStatusAuditRepository.class);
        when(repo.save(ArgumentMatchers.any(OrderStatusAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderStatusService service = new OrderStatusService(repo);

        Order order = new Order();
        order.setId(10L);
        order.setStatus("pending");

        service.changeStatus(order, "paid", "admin", "update");

        assertEquals("paid", order.getStatus());
        verify(repo, times(1)).save(ArgumentMatchers.any(OrderStatusAudit.class));
    }
}
