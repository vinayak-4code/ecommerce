package com.acme.ecommerce;

import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.enums.AccountStatus;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.repository.UserAccountRepository;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.CategoryRepository;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.config.TestSecurityConfig;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.repository.CustomerProfileRepository;
import com.acme.ecommerce.inventory.entity.InventoryItem;
import com.acme.ecommerce.inventory.repository.InventoryItemRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import com.acme.ecommerce.seller.enums.WarehouseStatus;
import com.acme.ecommerce.seller.repository.SellerProfileRepository;
import com.acme.ecommerce.seller.repository.WarehouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for functional tests. Boots the full Spring context with H2, Flyway disabled,
 * and security disabled. Provides reusable data-seeding helpers that insert directly
 * into the database — no mocking.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public abstract class BaseFunctionalTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserAccountRepository userAccountRepository;
    @Autowired protected SellerProfileRepository sellerProfileRepository;
    @Autowired protected CustomerProfileRepository customerProfileRepository;
    @Autowired protected CategoryRepository categoryRepository;
    @Autowired protected ProductRepository productRepository;
    @Autowired protected WarehouseRepository warehouseRepository;
    @Autowired protected InventoryItemRepository inventoryItemRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    // Fixed test user IDs — each test seeds its own rows using these IDs
    protected static final String SELLER_EMAIL = "seller@acme-test.com";
    protected static final String CUSTOMER_EMAIL = "customer@acme-test.com";
    protected static final String ADMIN_EMAIL = "admin@acme-test.com";
    protected static final String PASSWORD = "Password1";

    // Populated by seedSellerWithProfile / seedCustomerWithProfile
    protected UUID sellerUserId;
    protected UUID sellerProfileId;
    protected UUID customerUserId;
    protected UUID customerProfileId;
    protected UUID adminUserId;

    @AfterEach
    void clearSecurityContext() {
        TestSecurityConfig.clearSecurityContext();
    }

    // ── identity helpers ────────────────────────────────────────────────

    protected void loginAsSeller()   { TestSecurityConfig.setSecurityContext(sellerUserId,   SELLER_EMAIL,   UserRole.SELLER); }
    protected void loginAsCustomer() { TestSecurityConfig.setSecurityContext(customerUserId, CUSTOMER_EMAIL, UserRole.CUSTOMER); }
    protected void loginAsAdmin()    { TestSecurityConfig.setSecurityContext(adminUserId,    ADMIN_EMAIL,    UserRole.PRODUCT_ADMIN); }

    // ── data-seeding helpers ────────────────────────────────────────────

    protected UserAccount seedUserAccount(String email, UserRole role) {
        UserAccount ua = new UserAccount();
        ua.setEmail(email);
        ua.setPasswordHash(passwordEncoder.encode(PASSWORD));
        ua.setRole(role);
        ua.setStatus(AccountStatus.ACTIVE);
        return userAccountRepository.save(ua);
    }

    protected void seedSellerWithProfile() {
        UserAccount ua = seedUserAccount(SELLER_EMAIL, UserRole.SELLER);
        sellerUserId = ua.getId();
        SellerProfile sp = new SellerProfile();
        sp.setUserAccount(ua);
        sp.setBusinessName("Acme Test Store");
        sp.setContactNumber("9876543210");
        sp = sellerProfileRepository.save(sp);
        sellerProfileId = sp.getId();
    }

    protected void seedCustomerWithProfile() {
        UserAccount ua = seedUserAccount(CUSTOMER_EMAIL, UserRole.CUSTOMER);
        customerUserId = ua.getId();
        CustomerProfile cp = new CustomerProfile();
        cp.setUserAccount(ua);
        cp.setFullName("Test Customer");
        cp.setPhoneNumber("1234567890");
        cp = customerProfileRepository.save(cp);
        customerProfileId = cp.getId();
    }

    protected void seedAdmin() {
        UserAccount ua = seedUserAccount(ADMIN_EMAIL, UserRole.PRODUCT_ADMIN);
        adminUserId = ua.getId();
    }

    protected Category seedCategory(String name) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        c.setActive(true);
        return categoryRepository.save(c);
    }

    protected Category seedChildCategory(String name, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        c.setActive(true);
        c.setParent(parent);
        return categoryRepository.save(c);
    }

    protected Product seedProduct(SellerProfile seller, Category category, String name, String sku,
                                  BigDecimal price, ProductStatus status) {
        Product p = new Product();
        p.setSellerProfile(seller);
        p.setCategory(category);
        p.setName(name);
        p.setDescription("Test product description");
        p.setSku(sku);
        p.setPrice(price);
        p.setCurrency(CurrencyCode.USD);
        p.setStatus(status);
        return productRepository.save(p);
    }

    protected Warehouse seedWarehouse(SellerProfile seller, String name, String code) {
        Warehouse w = new Warehouse();
        w.setSellerProfile(seller);
        w.setName(name);
        w.setCode(code);
        w.setAddressLine1("123 Test St");
        w.setCity("TestCity");
        w.setState("TS");
        w.setCountry("US");
        w.setPostalCode("12345");
        w.setStatus(WarehouseStatus.ACTIVE);
        return warehouseRepository.save(w);
    }

    protected InventoryItem seedInventory(UUID productId, Warehouse warehouse, int available) {
        InventoryItem ii = new InventoryItem();
        ii.setProductId(productId);
        ii.setWarehouse(warehouse);
        ii.setAvailableQuantity(available);
        ii.setReservedQuantity(0);
        return inventoryItemRepository.save(ii);
    }
}
