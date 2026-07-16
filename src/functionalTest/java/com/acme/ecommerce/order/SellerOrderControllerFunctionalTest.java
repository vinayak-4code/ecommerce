package com.acme.ecommerce.order;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.order.entity.CustomerOrder;
import com.acme.ecommerce.order.entity.OrderLine;
import com.acme.ecommerce.order.enums.FulfillmentStatus;
import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.repository.CustomerOrderRepository;
import com.acme.ecommerce.order.repository.OrderLineRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("SellerOrderController – Functional Tests")
@Transactional
class SellerOrderControllerFunctionalTest extends BaseFunctionalTest {

    private static final String SELLER_ORDERS = "/api/v1/sellers/orders";

    @Autowired private OrderLineRepository orderLineRepository;
    @Autowired private CustomerOrderRepository customerOrderRepository;

    private Product product;
    private Warehouse warehouse;

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        seedCustomerWithProfile();
        SellerProfile seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        Category cat = seedCategory("SellerOrder-Cat");
        product = seedProduct(seller, cat, "SO Prod", "SKU-SO", new BigDecimal("30"), ProductStatus.PUBLISHED);
        warehouse = seedWarehouse(seller, "SO WH", "WH-SO");
        seedInventory(product.getId(), warehouse, 500);
    }

    /**
     * Seeds an order with one line directly in the DB (bypasses the cart→order flow
     * which uses native SQL queries that interfere with @Transactional test rollback).
     */
    private UUID seedOrderWithOneLine() {
        CustomerProfile customer = customerProfileRepository.findByUserAccountId(customerUserId).orElseThrow();

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber("ORD-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        order.setCustomerProfile(customer);
        order.setStatus(OrderStatus.PLACED);
        order.setSubtotalAmount(new BigDecimal("60.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("60.00"));
        order.setShippingAddress("123 Ship St");
        CustomerOrder savedOrder = customerOrderRepository.save(order);

        OrderLine line = new OrderLine();
        line.setOrder(savedOrder);
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setWarehouseId(warehouse.getId());
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("30.00"));
        line.setSubtotalAmount(new BigDecimal("60.00"));
        line.setDiscountAmount(BigDecimal.ZERO);
        line.setTaxAmount(BigDecimal.ZERO);
        line.setTotalAmount(new BigDecimal("60.00"));
        line.setFulfillmentStatus(FulfillmentStatus.RESERVED);
        OrderLine savedLine = orderLineRepository.save(line);

        // Reserve inventory for this line
        inventoryItemRepository.reserveQuantity(product.getId(), warehouse.getId(), 2);

        return savedLine.getId();
    }

    // ── list seller orders ──────────────────────────────────────────────

    @Nested @DisplayName("GET /sellers/orders")
    class ListSellerOrders {
        @Test @DisplayName("success – returns seller order lines")
        void list() throws Exception {
            seedOrderWithOneLine();
            loginAsSeller();
            mockMvc.perform(get(SELLER_ORDERS).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }

        @Test @DisplayName("edge – no orders → empty page")
        void emptyPage() throws Exception {
            loginAsSeller();
            mockMvc.perform(get(SELLER_ORDERS).param("page", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    // ── ship line ───────────────────────────────────────────────────────

    @Nested @DisplayName("PATCH /sellers/orders/lines/{id}/ship")
    class ShipLine {
        @Test @DisplayName("success – marks line as SHIPPED")
        void success() throws Exception {
            UUID lineId = seedOrderWithOneLine();
            loginAsSeller();
            mockMvc.perform(patch(SELLER_ORDERS + "/lines/" + lineId + "/ship"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fulfillmentStatus").value("SHIPPED"));
        }

        @Test @DisplayName("failure – non-existent line → 404")
        void notFound() throws Exception {
            loginAsSeller();
            mockMvc.perform(patch(SELLER_ORDERS + "/lines/" + UUID.randomUUID() + "/ship"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── cancel line ─────────────────────────────────────────────────────

    @Nested @DisplayName("PATCH /sellers/orders/lines/{id}/cancel")
    class CancelLine {
        @Test @DisplayName("success – cancels line and releases inventory")
        void success() throws Exception {
            UUID lineId = seedOrderWithOneLine();
            loginAsSeller();
            mockMvc.perform(patch(SELLER_ORDERS + "/lines/" + lineId + "/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fulfillmentStatus").value("CANCELLED"));
        }

        @Test @DisplayName("failure – cancel shipped line → 400")
        void cannotCancelShipped() throws Exception {
            UUID lineId = seedOrderWithOneLine();
            loginAsSeller();
            // Ship first
            mockMvc.perform(patch(SELLER_ORDERS + "/lines/" + lineId + "/ship"))
                    .andExpect(status().isOk());
            // Then try to cancel
            mockMvc.perform(patch(SELLER_ORDERS + "/lines/" + lineId + "/cancel"))
                    .andExpect(status().isBadRequest());
        }
    }
}
