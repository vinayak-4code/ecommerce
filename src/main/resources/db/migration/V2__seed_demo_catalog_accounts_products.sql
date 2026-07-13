-- Demo password for all seeded users: Password1
insert into user_accounts(id, email, password_hash, role, status, created_at, updated_at)
values
('00000000-0000-0000-0000-0000000000a1', 'admin@example.com', '$2y$10$NP9N0KKr8824Y1ruHSwNleaOlKnmw3xPuL2QVduWHhqZ8qA5De9Ku', 'PRODUCT_ADMIN', 'ACTIVE', now(), now()),
('00000000-0000-0000-0000-0000000000b1', 'seller@example.com', '$2y$10$NP9N0KKr8824Y1ruHSwNleaOlKnmw3xPuL2QVduWHhqZ8qA5De9Ku', 'SELLER', 'ACTIVE', now(), now()),
('00000000-0000-0000-0000-0000000000c1', 'customer@example.com', '$2y$10$NP9N0KKr8824Y1ruHSwNleaOlKnmw3xPuL2QVduWHhqZ8qA5De9Ku', 'CUSTOMER', 'ACTIVE', now(), now());

insert into seller_profiles(id, user_id, business_name, contact_number, created_at)
values ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000b1', 'Acme Demo Seller', '9999999999', now());

insert into customer_profiles(id, user_id, full_name, phone_number, created_at)
values ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000c1', 'Demo Customer', '8888888888', now());

insert into warehouses(id, seller_id, name, code, address_line1, city, state, country, postal_code, status, created_at, updated_at)
values ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000b2', 'Bengaluru Fulfillment Hub', 'BLR-01', 'Demo Warehouse Road', 'Bengaluru', 'Karnataka', 'India', '560001', 'ACTIVE', now(), now());

insert into categories(id, parent_id, name, slug, active, created_at)
values
('00000000-0000-0000-0000-000000000100', null, 'Electronics', 'electronics', true, now()),
('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000100', 'Mobile Phones', 'mobile-phones', true, now()),
('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000100', 'Laptops', 'laptops', true, now()),
('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000100', 'Cameras', 'cameras', true, now()),
('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000100', 'Accessories', 'accessories', true, now()),
('00000000-0000-0000-0000-000000000200', null, 'Apparel', 'apparel', true, now()),
('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000200', 'Men', 'men', true, now()),
('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000200', 'Women', 'women', true, now());

insert into category_attribute_definitions(id, category_id, name, code, attribute_type, required, searchable)
values
('00000000-0000-0000-0000-000000001001', '00000000-0000-0000-0000-000000000101', 'RAM', 'ram', 'STRING', true, true),
('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000000101', 'Storage', 'storage', 'STRING', true, true),
('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000101', 'Screen Size', 'screen_size', 'STRING', false, true),
('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000101', 'Battery Capacity', 'battery_capacity', 'STRING', false, true),
('00000000-0000-0000-0000-000000001005', '00000000-0000-0000-0000-000000000102', 'Processor', 'processor', 'STRING', true, true),
('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000000102', 'RAM', 'ram', 'STRING', true, true),
('00000000-0000-0000-0000-000000001007', '00000000-0000-0000-0000-000000000102', 'Storage', 'storage', 'STRING', true, true),
('00000000-0000-0000-0000-000000001008', '00000000-0000-0000-0000-000000000102', 'Display Size', 'display_size', 'STRING', false, true),
('00000000-0000-0000-0000-000000001009', '00000000-0000-0000-0000-000000000201', 'Size', 'size', 'STRING', true, true),
('00000000-0000-0000-0000-000000001010', '00000000-0000-0000-0000-000000000201', 'Color', 'color', 'STRING', true, true),
('00000000-0000-0000-0000-000000001011', '00000000-0000-0000-0000-000000000201', 'Fabric', 'fabric', 'STRING', false, true);

insert into products(id, seller_id, category_id, name, description, sku, price, currency, status, version_number, created_at, updated_at)
values
('00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000101', 'Pixel Demo Phone', 'Demo Android phone with clean catalog attributes.', 'MOB-PIXEL-DEMO', 49999.00, 'INR', 'PUBLISHED', 1, now(), now()),
('00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000102', 'ThinkBook Demo Laptop', 'Demo business laptop for search and coupon testing.', 'LAP-THINK-DEMO', 84999.00, 'INR', 'PUBLISHED', 1, now(), now()),
('00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000201', 'Cotton Demo Shirt', 'Demo apparel product that should not receive electronics coupons.', 'APP-SHIRT-DEMO', 1499.00, 'INR', 'PUBLISHED', 1, now(), now());

insert into product_attribute_values(id, product_id, attribute_definition_id, attribute_value)
values
('00000000-0000-0000-0000-000000004001', '00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-000000001001', '8GB'),
('00000000-0000-0000-0000-000000004002', '00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-000000001002', '128GB'),
('00000000-0000-0000-0000-000000004003', '00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-000000001003', '6.2 inch'),
('00000000-0000-0000-0000-000000004004', '00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-000000001004', '4500 mAh'),
('00000000-0000-0000-0000-000000004005', '00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-000000001005', 'Intel i7'),
('00000000-0000-0000-0000-000000004006', '00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-000000001006', '16GB'),
('00000000-0000-0000-0000-000000004007', '00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-000000001007', '512GB SSD'),
('00000000-0000-0000-0000-000000004008', '00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-000000001008', '14 inch'),
('00000000-0000-0000-0000-000000004009', '00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-000000001009', 'M'),
('00000000-0000-0000-0000-000000004010', '00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-000000001010', 'Blue'),
('00000000-0000-0000-0000-000000004011', '00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-000000001011', 'Cotton');

insert into inventory_items(id, product_id, warehouse_id, available_quantity, reserved_quantity, version, created_at, updated_at)
values
('00000000-0000-0000-0000-000000005001', '00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-0000000000d1', 5, 0, 0, now(), now()),
('00000000-0000-0000-0000-000000005002', '00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-0000000000d1', 2, 0, 0, now(), now()),
('00000000-0000-0000-0000-000000005003', '00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-0000000000d1', 10, 0, 0, now(), now());

insert into coupons(id, code, description, discount_type, discount_scope, value, max_discount_amount, min_cart_amount, status, starts_at, ends_at, created_at, updated_at)
values
('00000000-0000-0000-0000-000000006001', 'ELECTRO10', '10 percent off electronics, capped at 500.', 'UPTO_PERCENT_OFF', 'CATEGORY', 10.00, 500.00, 1000.00, 'ACTIVE', '2025-01-01T00:00:00Z', '2099-12-31T23:59:59Z', now(), now()),
('00000000-0000-0000-0000-000000006002', 'FLAT200', 'Flat 200 off enrolled products above 1000.', 'FLAT', 'CART', 200.00, null, 1000.00, 'ACTIVE', '2025-01-01T00:00:00Z', '2099-12-31T23:59:59Z', now(), now()),
('00000000-0000-0000-0000-000000006003', 'APPAREL50', 'Flat 50 off apparel products.', 'FLAT', 'CATEGORY', 50.00, null, 500.00, 'ACTIVE', '2025-01-01T00:00:00Z', '2099-12-31T23:59:59Z', now(), now());

insert into coupon_category_eligibilities(id, coupon_id, category_id)
values
('00000000-0000-0000-0000-000000006101', '00000000-0000-0000-0000-000000006001', '00000000-0000-0000-0000-000000000100'),
('00000000-0000-0000-0000-000000006102', '00000000-0000-0000-0000-000000006003', '00000000-0000-0000-0000-000000000200');

insert into coupon_product_enrollments(id, coupon_id, product_id, seller_id, created_at)
values
('00000000-0000-0000-0000-000000006201', '00000000-0000-0000-0000-000000006001', '00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-0000000000b2', now()),
('00000000-0000-0000-0000-000000006202', '00000000-0000-0000-0000-000000006001', '00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-0000000000b2', now()),
('00000000-0000-0000-0000-000000006203', '00000000-0000-0000-0000-000000006002', '00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-0000000000b2', now()),
('00000000-0000-0000-0000-000000006204', '00000000-0000-0000-0000-000000006002', '00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-0000000000b2', now()),
('00000000-0000-0000-0000-000000006205', '00000000-0000-0000-0000-000000006003', '00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-0000000000b2', now());

insert into product_search_documents(product_id, seller_id, category_id, category_name, name, description, sku, price, currency, status, attributes_json, total_available_quantity, updated_at)
values
('00000000-0000-0000-0000-000000003001', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000101', 'Mobile Phones', 'Pixel Demo Phone', 'Demo Android phone with clean catalog attributes.', 'MOB-PIXEL-DEMO', 49999.00, 'INR', 'PUBLISHED', '{"ram":"8GB","storage":"128GB","screen_size":"6.2 inch","battery_capacity":"4500 mAh"}', 5, now()),
('00000000-0000-0000-0000-000000003002', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000102', 'Laptops', 'ThinkBook Demo Laptop', 'Demo business laptop for search and coupon testing.', 'LAP-THINK-DEMO', 84999.00, 'INR', 'PUBLISHED', '{"processor":"Intel i7","ram":"16GB","storage":"512GB SSD","display_size":"14 inch"}', 2, now()),
('00000000-0000-0000-0000-000000003003', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000201', 'Men', 'Cotton Demo Shirt', 'Demo apparel product that should not receive electronics coupons.', 'APP-SHIRT-DEMO', 1499.00, 'INR', 'PUBLISHED', '{"size":"M","color":"Blue","fabric":"Cotton"}', 10, now());
