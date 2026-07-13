package com.acme.ecommerce.common.config;

public final class ApiPaths {
    public static final String API_V1 = "/api/v1";
    public static final String AUTH = API_V1 + "/auth/**";
    public static final String ACTUATOR = "/actuator/**";
    public static final String CATEGORIES = API_V1 + "/categories/**";
    public static final String SEARCH = API_V1 + "/search/**";
    public static final String PRODUCTS = API_V1 + "/products/**";
    public static final String SELLERS = API_V1 + "/sellers/**";
    public static final String INVENTORY = API_V1 + "/inventory/**";
    public static final String COUPONS = API_V1 + "/coupons/**";
    public static final String CART = API_V1 + "/cart/**";
    public static final String ORDERS = API_V1 + "/orders/**";

    private ApiPaths() {
    }
}
