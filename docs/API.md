# API Reference with Sample Responses

Base URL:

```text
http://localhost:8080
```

All protected APIs use:

```http
Authorization: Bearer <accessToken>
```
## Reviewer UI

The same APIs can be exercised through small Thymeleaf pages:

```text
/admin     Product Admin category/coupon journey
/seller    Seller warehouse/product/inventory/coupon enrollment journey
/customer  Customer search/cart/coupon/order journey
```

The pages log in through `POST /api/v1/auth/login`, store the Bearer token in browser local storage, and call the REST APIs below.


## Auth

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Password1","role":"PRODUCT_ADMIN"}'
```

Sample response:

```json
{
  "tokenType": "Bearer",
  "accessToken": "token",
  "expiresAt": "2099-01-01T00:00:00Z",
  "userId": "00000000-0000-0000-0000-0000000000a1",
  "role": "PRODUCT_ADMIN"
}
```

### Logout

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

Response: `204 No Content`

## Categories

### List categories, paginated

```bash
curl 'http://localhost:8080/api/v1/categories?page=0&size=10'
```

Sample response:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000100",
      "parentId": null,
      "name": "Electronics",
      "slug": "electronics",
      "active": true,
      "attributes": []
    }
  ],
  "pageable": {"pageNumber": 0, "pageSize": 10},
  "totalElements": 8,
  "totalPages": 1
}
```

### Product Admin creates category/sub-classification

```bash
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "parentId": "00000000-0000-0000-0000-000000000100",
    "name": "Smart Watches",
    "attributes": [
      {"name":"Display Type","code":"display_type","attributeType":"STRING","required":true,"searchable":true},
      {"name":"Battery Life","code":"battery_life","attributeType":"STRING","required":false,"searchable":true}
    ]
  }'
```

Sample response:

```json
{
  "id": "category-id",
  "parentId": "00000000-0000-0000-0000-000000000100",
  "name": "Smart Watches",
  "slug": "smart-watches",
  "active": true,
  "attributes": [
    {"id":"attr-id","name":"Display Type","code":"display_type","attributeType":"STRING","required":true,"searchable":true}
  ]
}
```

## Product search

### Search products from denormalized search projection

```bash
curl 'http://localhost:8080/api/v1/search/products?q=demo&page=0&size=10&attr_ram=8GB'
```

Sample response:

```json
{
  "content": [
    {
      "productId": "00000000-0000-0000-0000-000000003001",
      "categoryName": "Mobile Phones",
      "name": "Pixel Demo Phone",
      "price": 49999.00,
      "currency": "INR",
      "status": "PUBLISHED",
      "attributes": {"ram":"8GB","storage":"128GB"},
      "totalAvailableQuantity": 5
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

## Coupons

### Product Admin creates coupon

```bash
curl -X POST http://localhost:8080/api/v1/coupons \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "code": "ELECTRO10",
    "description": "10 percent off electronics capped at 500",
    "discountType": "UPTO_PERCENT_OFF",
    "discountScope": "CATEGORY",
    "value": 10,
    "maxDiscountAmount": 500,
    "minCartAmount": 1000,
    "startsAt": "2025-01-01T00:00:00Z",
    "endsAt": "2099-12-31T23:59:59Z",
    "categoryIds": ["00000000-0000-0000-0000-000000000100"]
  }'
```

Sample response:

```json
{
  "id": "coupon-id",
  "code": "ELECTRO10",
  "discountType": "UPTO_PERCENT_OFF",
  "discountScope": "CATEGORY",
  "value": 10.00,
  "maxDiscountAmount": 500.00,
  "minCartAmount": 1000.00,
  "status": "ACTIVE",
  "eligibleCategoryIds": ["00000000-0000-0000-0000-000000000100"],
  "enrolledProductIds": []
}
```

### List coupons, paginated

```bash
curl 'http://localhost:8080/api/v1/coupons?page=0&size=10' \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Seller enrolls product into coupon

```bash
curl -X POST http://localhost:8080/api/v1/coupons/ELECTRO10/products/00000000-0000-0000-0000-000000003001/enroll \
  -H "Authorization: Bearer $SELLER_TOKEN"
```

Sample response:

```json
{
  "enrollmentId": "enrollment-id",
  "couponId": "00000000-0000-0000-0000-000000006001",
  "couponCode": "ELECTRO10",
  "productId": "00000000-0000-0000-0000-000000003001",
  "sellerId": "00000000-0000-0000-0000-0000000000b2"
}
```

## Inventory

### Single inventory update

```bash
curl -X PUT http://localhost:8080/api/v1/inventory \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "productId":"00000000-0000-0000-0000-000000003001",
    "warehouseId":"00000000-0000-0000-0000-0000000000d1",
    "availableQuantity":5,
    "reason":"STOCK_CORRECTION"
  }'
```

Sample response:

```json
{
  "productId": "00000000-0000-0000-0000-000000003001",
  "warehouseId": "00000000-0000-0000-0000-0000000000d1",
  "availableQuantity": 5,
  "reservedQuantity": 0,
  "consolidatedAvailableQuantity": 5
}
```

### Bulk inventory update

```bash
curl -X PUT http://localhost:8080/api/v1/inventory/bulk \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"items":[
    {"productId":"00000000-0000-0000-0000-000000003001","warehouseId":"00000000-0000-0000-0000-0000000000d1","availableQuantity":5,"reason":"STOCK_CORRECTION"},
    {"productId":"00000000-0000-0000-0000-000000003002","warehouseId":"00000000-0000-0000-0000-0000000000d1","availableQuantity":2,"reason":"STOCK_CORRECTION"}
  ]}'
```

## Cart

### Add item

```bash
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productId":"00000000-0000-0000-0000-000000003001","quantity":1}'
```

### Apply coupon

```bash
curl -X POST http://localhost:8080/api/v1/cart/coupons \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"ELECTRO10"}'
```

### View cart

```bash
curl http://localhost:8080/api/v1/cart \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

Sample response:

```json
{
  "cartId": "cart-id",
  "items": [
    {
      "productId": "00000000-0000-0000-0000-000000003001",
      "productName": "Pixel Demo Phone",
      "quantity": 1,
      "availableQuantity": 5,
      "stockStatus": "IN_STOCK",
      "unitPrice": 49999.00,
      "subtotal": 49999.00,
      "discountAmount": 500.00,
      "totalAmount": 49499.00
    }
  ],
  "couponCode": "ELECTRO10",
  "checkoutReady": true,
  "subtotal": 49999.00,
  "discountAmount": 500.00,
  "totalAmount": 49499.00
}
```

## Orders

### Place order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"shippingAddress":"Demo Address, Bengaluru"}'
```

Sample response:

```json
{
  "id": "order-id",
  "orderNumber": "ORD-...",
  "status": "PLACED",
  "paymentStatus": "PENDING",
  "subtotalAmount": 49999.00,
  "discountAmount": 500.00,
  "taxAmount": 0.00,
  "totalAmount": 49499.00,
  "lines": [
    {
      "productId": "00000000-0000-0000-0000-000000003001",
      "quantity": 1,
      "unitPrice": 49999.00,
      "discountAmount": 500.00,
      "fulfillmentStatus": "PENDING"
    }
  ]
}
```

### List orders, paginated

```bash
curl 'http://localhost:8080/api/v1/orders?page=0&size=10' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```
