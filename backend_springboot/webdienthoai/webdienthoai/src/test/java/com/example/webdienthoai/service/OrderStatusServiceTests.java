package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderStatusAudit;
import com.example.webdienthoai.repository.OrderStatusAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        service.validateStatus("confirmed");
        service.validateStatus("processing");
        service.validateStatus("shipped");
        service.validateStatus("shipping");
        service.validateStatus("delivered");
        service.validateStatus("completed");
        service.validateStatus("cancelled");
        service.validateStatus("rejected");
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

    @Test
    void shouldValidateAdminTransition() {
        OrderStatusAuditRepository repo = mock(OrderStatusAuditRepository.class);
        OrderStatusService service = new OrderStatusService(repo);
        service.validateAdminTransition("pending", "confirmed");
        assertThrows(IllegalArgumentException.class, () -> service.validateAdminTransition("pending", "shipped"));
    }

    @Test
    void canCustomerCancelOnlyEarlyStages() {
        OrderStatusAuditRepository repo = mock(OrderStatusAuditRepository.class);
        OrderStatusService service = new OrderStatusService(repo);
        assertTrue(service.canCustomerCancel("pending"));
        assertTrue(service.canCustomerCancel("pending_payment"));
        assertFalse(service.canCustomerCancel("confirmed"));
        assertFalse(service.canCustomerCancel("paid"));
    }
}
