#!/usr/bin/env bash
set -euo pipefail

# Requires: curl and jq
# Run after: docker compose up --build
BASE_URL="${BASE_URL:-http://localhost:8080}"
PASSWORD="Password1"

ADMIN_EMAIL="admin@example.com"
SELLER_EMAIL="seller@example.com"
CUSTOMER_EMAIL="customer@example.com"

MOBILE_PRODUCT_ID="00000000-0000-0000-0000-000000003001"
LAPTOP_PRODUCT_ID="00000000-0000-0000-0000-000000003002"
APPAREL_PRODUCT_ID="00000000-0000-0000-0000-000000003003"
WAREHOUSE_ID="00000000-0000-0000-0000-0000000000d1"
ELECTRONICS_CATEGORY_ID="00000000-0000-0000-0000-000000000100"
MOBILE_CATEGORY_ID="00000000-0000-0000-0000-000000000101"

login() {
  local email="$1"
  local role="$2"
  curl -s -X POST "$BASE_URL/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$PASSWORD\",\"role\":\"$role\"}" | jq -r '.accessToken'
}

print_step() {
  printf '\n\n### %s\n' "$1"
}

print_step "Login seeded users"
ADMIN_TOKEN="$(login "$ADMIN_EMAIL" PRODUCT_ADMIN)"
SELLER_TOKEN="$(login "$SELLER_EMAIL" SELLER)"
CUSTOMER_TOKEN="$(login "$CUSTOMER_EMAIL" CUSTOMER)"
echo "Admin token: ${ADMIN_TOKEN:0:12}..."
echo "Seller token: ${SELLER_TOKEN:0:12}..."
echo "Customer token: ${CUSTOMER_TOKEN:0:12}..."

print_step "Public category list with pagination"
curl -s "$BASE_URL/api/v1/categories?page=0&size=10" | jq

print_step "Public product search from denormalized search projection"
curl -s "$BASE_URL/api/v1/search/products?q=demo&categoryId=$MOBILE_CATEGORY_ID&page=0&size=10&attr_ram=8GB" | jq

print_step "Product Admin creates a sub-classification with predefined mandatory/non-mandatory attributes"
NEW_CATEGORY_ID=$(curl -s -X POST "$BASE_URL/api/v1/categories" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"parentId\": \"$ELECTRONICS_CATEGORY_ID\",
    \"name\": \"Smart Watches\",
    \"attributes\": [
      {\"name\": \"Display Type\", \"code\": \"display_type\", \"attributeType\": \"STRING\", \"required\": true, \"searchable\": true},
      {\"name\": \"Battery Life\", \"code\": \"battery_life\", \"attributeType\": \"STRING\", \"required\": false, \"searchable\": true}
    ]
  }" | jq -r '.id')
echo "Created category: $NEW_CATEGORY_ID"

print_step "Product Admin creates a category-level UPTO_PERCENT_OFF coupon"
DEMO_COUPON_ID=$(curl -s -X POST "$BASE_URL/api/v1/coupons" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"code\": \"WATCH10\",
    \"description\": \"10 percent off smart watches capped at 300\",
    \"discountType\": \"UPTO_PERCENT_OFF\",
    \"discountScope\": \"CATEGORY\",
    \"value\": 10,
    \"maxDiscountAmount\": 300,
    \"minCartAmount\": 1000,
    \"startsAt\": \"2025-01-01T00:00:00Z\",
    \"endsAt\": \"2099-12-31T23:59:59Z\",
    \"categoryIds\": [\"$NEW_CATEGORY_ID\"]
  }" | jq -r '.id')
echo "Created coupon: $DEMO_COUPON_ID"

print_step "Seller sees seeded coupon and enrolled products"
curl -s "$BASE_URL/api/v1/coupons/ELECTRO10" -H "Authorization: Bearer $SELLER_TOKEN" | jq

print_step "Seller enrolls mobile product into another coupon; same product can be in multiple coupons"
curl -s -X POST "$BASE_URL/api/v1/coupons/FLAT200/products/$MOBILE_PRODUCT_ID/enroll" \
  -H "Authorization: Bearer $SELLER_TOKEN" | jq

print_step "Seller updates single inventory to zero, making the item out of stock"
curl -s -X PUT "$BASE_URL/api/v1/inventory" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$APPAREL_PRODUCT_ID\",\"warehouseId\":\"$WAREHOUSE_ID\",\"availableQuantity\":0,\"reason\":\"STOCK_CORRECTION\"}" | jq

print_step "Seller restores inventory using bulk update"
curl -s -X PUT "$BASE_URL/api/v1/inventory/bulk" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[
    {\"productId\":\"$MOBILE_PRODUCT_ID\",\"warehouseId\":\"$WAREHOUSE_ID\",\"availableQuantity\":5,\"reason\":\"STOCK_CORRECTION\"},
    {\"productId\":\"$APPAREL_PRODUCT_ID\",\"warehouseId\":\"$WAREHOUSE_ID\",\"availableQuantity\":10,\"reason\":\"STOCK_CORRECTION\"}
  ]}" | jq

print_step "Customer adds an electronics product to cart; live price and inventory are validated"
curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$MOBILE_PRODUCT_ID\",\"quantity\":1}" | jq

print_step "Customer applies electronics coupon; discount is allocated to eligible enrolled lines"
curl -s -X POST "$BASE_URL/api/v1/cart/coupons" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"ELECTRO10"}' | jq

print_step "Cart view shows checkoutReady, current price, available quantity, stock status, and proportional discount"
curl -s "$BASE_URL/api/v1/cart" -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq

print_step "Place order; inventory is reserved then consumed, preventing oversell"
ORDER_ID=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"shippingAddress":"Demo Address, Bengaluru"}' | jq -r '.id')
echo "Created order: $ORDER_ID"

print_step "List orders with pagination"
curl -s "$BASE_URL/api/v1/orders?page=0&size=10" -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq

print_step "Logout customer token"
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL/api/v1/auth/logout" -H "Authorization: Bearer $CUSTOMER_TOKEN"
