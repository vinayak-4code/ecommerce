package com.acme.ecommerce.order.service;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.inventory.service.InventoryService;
import com.acme.ecommerce.order.dto.SellerOrderLineResponse;
import com.acme.ecommerce.order.entity.CustomerOrder;
import com.acme.ecommerce.order.entity.OrderLine;
import com.acme.ecommerce.order.enums.FulfillmentStatus;
import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.repository.CustomerOrderRepository;
import com.acme.ecommerce.order.repository.OrderLineRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seller fulfillment service exposing only order lines that belong to the authenticated seller.
 *
 * <p>This keeps the customer order model intact while giving sellers an isolated operational view.
 * Sellers can mark their own lines as shipped or cancel them; they cannot see or change another
 * seller's order lines.</p>
 */
@Service
@RequiredArgsConstructor
public class SellerOrderService {
    private final SellerService sellerService;
    private final ProductRepository productRepository;
    private final OrderLineRepository orderLineRepository;
    private final CustomerOrderRepository orderRepository;
    private final InventoryService inventoryService;

    /** Lists customer order lines containing the current seller's products. */
    @Transactional(readOnly = true)
    public Page<SellerOrderLineResponse> list(UUID sellerUserId, int page, int size) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Set<UUID> productIds = productRepository.findAllBySellerProfileId(seller.getId()).stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (productIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return orderLineRepository.findSellerLines(productIds, pageable).map(this::toResponse);
    }

    /** Marks one seller-owned order line as shipped and consumes the reservation. */
    @Transactional
    public SellerOrderLineResponse ship(UUID sellerUserId, UUID lineId) {
        OrderLine line = requireSellerLine(sellerUserId, lineId);
        if (line.getFulfillmentStatus() == FulfillmentStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cancelled order line cannot be shipped");
        }
        if (line.getFulfillmentStatus() == FulfillmentStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Delivered order line cannot be changed");
        }
        if (line.getFulfillmentStatus() == FulfillmentStatus.SHIPPED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Order line is already shipped");
        }
        // Consume reservation for this specific line: decrement reservedQuantity (stock permanently leaves warehouse)
        inventoryService.consumeForLine(line.getProductId(), line.getWarehouseId(), line.getQuantity(), line.getOrder().getId());
        line.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
        OrderLine saved = orderLineRepository.save(line);
        refreshOrderStatus(saved.getOrder());
        return toResponse(saved);
    }

    /** Cancels one seller-owned order line and releases the reserved quantity back to available. */
    @Transactional
    public SellerOrderLineResponse cancel(UUID sellerUserId, UUID lineId) {
        OrderLine line = requireSellerLine(sellerUserId, lineId);
        if (line.getFulfillmentStatus() == FulfillmentStatus.SHIPPED || line.getFulfillmentStatus() == FulfillmentStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Shipped or delivered order line cannot be cancelled by seller");
        }
        if (line.getFulfillmentStatus() != FulfillmentStatus.CANCELLED) {
            // Release reservation for this specific line: move reserved back to available
            inventoryService.releaseForLine(line.getProductId(), line.getWarehouseId(), line.getQuantity(), line.getOrder().getId());
            line.setFulfillmentStatus(FulfillmentStatus.CANCELLED);
        }
        OrderLine saved = orderLineRepository.save(line);
        refreshOrderStatus(saved.getOrder());
        return toResponse(saved);
    }

    private OrderLine requireSellerLine(UUID sellerUserId, UUID lineId) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Set<UUID> productIds = productRepository.findAllBySellerProfileId(seller.getId()).stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            throw new ResourceNotFoundException("Order line not found for seller");
        }
        return orderLineRepository.findByIdAndProductIdIn(lineId, productIds)
                .orElseThrow(() -> new ResourceNotFoundException("Order line not found for seller"));
    }

    private void refreshOrderStatus(CustomerOrder order) {
        List<OrderLine> lines = orderLineRepository.findByOrderId(order.getId());
        boolean allCancelled = lines.stream().allMatch(line -> line.getFulfillmentStatus() == FulfillmentStatus.CANCELLED);
        boolean allTerminal = lines.stream().allMatch(line -> line.getFulfillmentStatus() == FulfillmentStatus.CANCELLED || line.getFulfillmentStatus() == FulfillmentStatus.SHIPPED || line.getFulfillmentStatus() == FulfillmentStatus.DELIVERED);
        if (allCancelled) {
            order.setStatus(OrderStatus.CANCELLED);
        } else if (allTerminal) {
            order.setStatus(OrderStatus.COMPLETED);
        } else {
            order.setStatus(OrderStatus.PLACED);
        }
        orderRepository.save(order);
    }

    private SellerOrderLineResponse toResponse(OrderLine line) {
        CustomerOrder order = line.getOrder();
        return new SellerOrderLineResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getCustomerProfile().getFullName(),
                order.getShippingAddress(),
                line.getId(),
                line.getProductId(),
                line.getProductName(),
                line.getWarehouseId(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getDiscountAmount(),
                line.getTotalAmount(),
                line.getFulfillmentStatus()
        );
    }
}
