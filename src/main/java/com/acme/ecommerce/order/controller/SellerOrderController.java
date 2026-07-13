package com.acme.ecommerce.order.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.order.dto.SellerOrderLineResponse;
import com.acme.ecommerce.order.service.SellerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Seller order-management API.
 *
 * <p>Each response is filtered to the authenticated seller's product lines. A seller can ship or
 * cancel only those lines; cross-seller order lines are hidden by service-level ownership checks.</p>
 */
@RestController
@RequestMapping("/api/v1/sellers/orders")
@RequiredArgsConstructor
public class SellerOrderController {
    private final SellerOrderService sellerOrderService;

    /** Lists customer order lines that contain products owned by the current seller. */
    @GetMapping
    public Page<SellerOrderLineResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return sellerOrderService.list(CurrentUser.require().userId(), page, size);
    }

    /** Marks a seller-owned order line as shipped. */
    @PatchMapping("/lines/{lineId}/ship")
    public SellerOrderLineResponse ship(@PathVariable UUID lineId) {
        return sellerOrderService.ship(CurrentUser.require().userId(), lineId);
    }

    /** Cancels a seller-owned order line and returns the quantity to available inventory. */
    @PatchMapping("/lines/{lineId}/cancel")
    public SellerOrderLineResponse cancel(@PathVariable UUID lineId) {
        return sellerOrderService.cancel(CurrentUser.require().userId(), lineId);
    }
}
