create table user_accounts (
    id uuid primary key,
    email varchar(320) not null,
    password_hash varchar(120) not null,
    role varchar(40) not null,
    status varchar(40) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_user_email unique (email)
);

create table auth_tokens (
    token varchar(80) primary key,
    user_id uuid not null references user_accounts(id),
    expires_at timestamp with time zone not null,
    revoked boolean not null,
    created_at timestamp with time zone not null
);
create index idx_auth_token_user on auth_tokens(user_id);
create index idx_auth_token_expires on auth_tokens(expires_at);

create table seller_profiles (
    id uuid primary key,
    user_id uuid not null unique references user_accounts(id),
    business_name varchar(180) not null,
    contact_number varchar(40),
    created_at timestamp with time zone not null
);

create table customer_profiles (
    id uuid primary key,
    user_id uuid not null unique references user_accounts(id),
    full_name varchar(160) not null,
    phone_number varchar(40),
    created_at timestamp with time zone not null
);

create table warehouses (
    id uuid primary key,
    seller_id uuid not null references seller_profiles(id),
    name varchar(160) not null,
    code varchar(80) not null,
    address_line1 varchar(240) not null,
    city varchar(120) not null,
    state varchar(120) not null,
    country varchar(120) not null,
    postal_code varchar(20) not null,
    status varchar(40) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
create index idx_warehouse_seller on warehouses(seller_id);

create table categories (
    id uuid primary key,
    parent_id uuid references categories(id),
    name varchar(140) not null,
    slug varchar(160) not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    constraint uk_category_slug unique (slug)
);

create table category_attribute_definitions (
    id uuid primary key,
    category_id uuid not null references categories(id),
    name varchar(120) not null,
    code varchar(80) not null,
    attribute_type varchar(40) not null,
    required boolean not null,
    searchable boolean not null,
    constraint uk_category_attr_code unique (category_id, code)
);

create table products (
    id uuid primary key,
    seller_id uuid not null references seller_profiles(id),
    category_id uuid not null references categories(id),
    name varchar(220) not null,
    description text,
    sku varchar(80) not null,
    price numeric(19,2) not null,
    currency varchar(3) not null,
    status varchar(40) not null,
    version_number integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_seller_sku unique (seller_id, sku)
);
create index idx_product_seller on products(seller_id);
create index idx_product_category on products(category_id);
create index idx_product_status on products(status);

create table product_attribute_values (
    id uuid primary key,
    product_id uuid not null references products(id),
    attribute_definition_id uuid not null references category_attribute_definitions(id),
    attribute_value varchar(400) not null,
    constraint uk_product_attribute unique (product_id, attribute_definition_id)
);

create table product_versions (
    id uuid primary key,
    product_id uuid not null,
    version_number integer not null,
    snapshot_json text not null,
    created_by uuid not null,
    created_at timestamp with time zone not null
);
create index idx_product_version_product on product_versions(product_id, version_number);

create table inventory_items (
    id uuid primary key,
    product_id uuid not null,
    warehouse_id uuid not null references warehouses(id),
    available_quantity integer not null check (available_quantity >= 0),
    reserved_quantity integer not null check (reserved_quantity >= 0),
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_inventory_product_warehouse unique (product_id, warehouse_id)
);
create index idx_inventory_product on inventory_items(product_id);
create index idx_inventory_warehouse on inventory_items(warehouse_id);

create table inventory_reservations (
    id uuid primary key,
    order_id uuid,
    product_id uuid not null,
    warehouse_id uuid not null,
    quantity integer not null check (quantity > 0),
    status varchar(40) not null,
    expires_at timestamp with time zone,
    created_at timestamp with time zone not null
);
create index idx_reservation_order on inventory_reservations(order_id);
create index idx_reservation_product on inventory_reservations(product_id);

create table coupons (
    id uuid primary key,
    code varchar(80) not null,
    description varchar(500),
    discount_type varchar(40) not null,
    discount_scope varchar(40) not null,
    value numeric(19,2) not null,
    max_discount_amount numeric(19,2),
    min_cart_amount numeric(19,2),
    status varchar(40) not null,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_coupon_code unique (code)
);

create table coupon_category_eligibilities (
    id uuid primary key,
    coupon_id uuid not null references coupons(id),
    category_id uuid not null references categories(id),
    constraint uk_coupon_category unique (coupon_id, category_id)
);
create index idx_coupon_category_coupon on coupon_category_eligibilities(coupon_id);
create index idx_coupon_category_category on coupon_category_eligibilities(category_id);

create table coupon_product_enrollments (
    id uuid primary key,
    coupon_id uuid not null references coupons(id),
    product_id uuid not null references products(id),
    seller_id uuid not null references seller_profiles(id),
    created_at timestamp with time zone not null,
    constraint uk_coupon_product_enrollment unique (coupon_id, product_id)
);
create index idx_coupon_product_coupon on coupon_product_enrollments(coupon_id);
create index idx_coupon_product_product on coupon_product_enrollments(product_id);
create index idx_coupon_product_seller on coupon_product_enrollments(seller_id);

create table carts (
    id uuid primary key,
    customer_id uuid not null references customer_profiles(id),
    status varchar(40) not null,
    coupon_code varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
create index idx_cart_customer_status on carts(customer_id, status);

create table cart_items (
    id uuid primary key,
    cart_id uuid not null references carts(id),
    product_id uuid not null references products(id),
    quantity integer not null check (quantity >= 0),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_cart_product unique (cart_id, product_id)
);

create table customer_orders (
    id uuid primary key,
    order_number varchar(40) not null unique,
    customer_id uuid not null references customer_profiles(id),
    status varchar(40) not null,
    payment_status varchar(40) not null,
    subtotal_amount numeric(19,2) not null,
    discount_amount numeric(19,2) not null,
    tax_amount numeric(19,2) not null,
    total_amount numeric(19,2) not null,
    shipping_address varchar(1000),
    created_at timestamp with time zone not null
);
create index idx_order_customer on customer_orders(customer_id);
create index idx_order_status on customer_orders(status);

create table order_lines (
    id uuid primary key,
    order_id uuid not null references customer_orders(id),
    product_id uuid not null references products(id),
    product_name varchar(220) not null,
    warehouse_id uuid not null,
    quantity integer not null,
    unit_price numeric(19,2) not null,
    subtotal_amount numeric(19,2) not null,
    discount_amount numeric(19,2) not null,
    tax_amount numeric(19,2) not null,
    total_amount numeric(19,2) not null,
    fulfillment_status varchar(40) not null
);
create index idx_order_line_order on order_lines(order_id);
create index idx_order_line_product on order_lines(product_id);

create table product_search_documents (
    product_id uuid primary key,
    seller_id uuid not null,
    category_id uuid not null,
    category_name varchar(140) not null,
    name varchar(220) not null,
    description text,
    sku varchar(80) not null,
    price numeric(19,2) not null,
    currency varchar(3) not null,
    status varchar(40) not null,
    attributes_json text not null,
    total_available_quantity bigint not null,
    updated_at timestamp with time zone not null
);
create index idx_search_status on product_search_documents(status);
create index idx_search_category on product_search_documents(category_id);
create index idx_search_name on product_search_documents(name);

create table outbox_events (
    id uuid primary key,
    aggregate_id uuid not null,
    aggregate_type varchar(80) not null,
    event_type varchar(80) not null,
    payload_json text not null,
    status varchar(40) not null,
    created_at timestamp with time zone not null,
    published_at timestamp with time zone
);
create index idx_outbox_status_created on outbox_events(status, created_at);
create index idx_outbox_aggregate on outbox_events(aggregate_type, aggregate_id);
