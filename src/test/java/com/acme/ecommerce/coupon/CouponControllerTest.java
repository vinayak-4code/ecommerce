package com.acme.ecommerce.coupon;

import com.acme.ecommerce.coupon.controller.CouponController;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponController.class)
@AutoConfigureMockMvc(addFilters = false)
class CouponControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    @Test
    void productAdminCanCreateUpToPercentCoupon() throws Exception {
        UUID couponId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant endsAt = Instant.parse("2099-12-31T23:59:59Z");
        when(couponService.create(any(CreateCouponRequest.class))).thenReturn(new CouponResponse(
                couponId,
                "ELECTRO10",
                "10 percent off electronics",
                DiscountType.UPTO_PERCENT_OFF,
                DiscountScope.CATEGORY,
                new BigDecimal("10.00"),
                new BigDecimal("500.00"),
                new BigDecimal("1000.00"),
                CouponStatus.ACTIVE,
                startsAt,
                endsAt,
                Set.of(UUID.randomUUID()),
                Set.of()
        ));

        String payload = """
                {
                  "code": "ELECTRO10",
                  "description": "10 percent off electronics",
                  "discountType": "UPTO_PERCENT_OFF",
                  "discountScope": "CATEGORY",
                  "value": 10,
                  "maxDiscountAmount": 500,
                  "minCartAmount": 1000,
                  "startsAt": "2025-01-01T00:00:00Z",
                  "endsAt": "2099-12-31T23:59:59Z",
                  "categoryIds": []
                }
                """;

        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(couponId.toString()))
                .andExpect(jsonPath("$.discountType").value("UPTO_PERCENT_OFF"));
    }
}
