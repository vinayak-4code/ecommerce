package com.acme.ecommerce.seller.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.seller.dto.CreateWarehouseRequest;
import com.acme.ecommerce.seller.dto.UpdateWarehouseRequest;
import com.acme.ecommerce.seller.dto.WarehouseResponse;
import com.acme.ecommerce.seller.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sellers/warehouses")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService warehouseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.create(CurrentUser.require().userId(), request);
    }

    @GetMapping
    public List<WarehouseResponse> list() {
        return warehouseService.list(CurrentUser.require().userId());
    }

    @PutMapping("/{warehouseId}")
    public WarehouseResponse update(@PathVariable UUID warehouseId, @Valid @RequestBody UpdateWarehouseRequest request) {
        return warehouseService.update(CurrentUser.require().userId(), warehouseId, request);
    }
}
