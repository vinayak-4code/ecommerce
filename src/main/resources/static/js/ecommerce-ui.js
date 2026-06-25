const tokenKey = role => `ecommerce_${role}_token`;

function readValue(id) {
  const element = document.getElementById(id);
  return element ? element.value.trim() : '';
}

function parseJsonOrEmpty(id, fallback) {
  const value = readValue(id);
  if (!value) return fallback;
  return JSON.parse(value);
}

function csvToUuidSet(value) {
  return value ? value.split(',').map(v => v.trim()).filter(Boolean) : [];
}

function money(value) {
  return Number(value || 0).toFixed(2);
}

function show(targetId, payload, ok = true) {
  const target = document.getElementById(targetId);
  if (!target) return;
  target.className = ok ? 'status ok' : 'status error';
  target.innerHTML = `<pre>${escapeHtml(typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2))}</pre>`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]));
}

async function api(role, path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const token = localStorage.getItem(tokenKey(role));
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(path, { ...options, headers });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = body || { status: response.status, error: response.statusText };
    throw message;
  }
  return body;
}

async function login(role, outputId) {
  try {
    const email = readValue(`${role}-email`);
    const password = readValue(`${role}-password`);
    const result = await api(role, '/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password, role })
    });
    localStorage.setItem(tokenKey(role), result.accessToken);
    show(outputId, { message: 'Login successful. Bearer token saved in browser storage.', role: result.role, expiresAt: result.expiresAt });
  } catch (error) {
    show(outputId, error, false);
  }
}

function logout(role, outputId) {
  localStorage.removeItem(tokenKey(role));
  show(outputId, 'Logged out locally. Token removed from browser storage.');
}

async function loadCategories(outputId = 'admin-output') {
  try {
    show(outputId, await api('PRODUCT_ADMIN', '/api/v1/categories?page=0&size=50'));
  } catch (error) { show(outputId, error, false); }
}

async function adminCreateCategory() {
  try {
    const payload = {
      parentId: readValue('admin-category-parent-id') || null,
      name: readValue('admin-category-name'),
      attributes: parseJsonOrEmpty('admin-category-attributes', [])
    };
    show('admin-output', await api('PRODUCT_ADMIN', '/api/v1/categories', { method: 'POST', body: JSON.stringify(payload) }));
  } catch (error) { show('admin-output', error, false); }
}

async function adminCreateCoupon() {
  try {
    const payload = {
      code: readValue('admin-coupon-code'),
      description: readValue('admin-coupon-description'),
      discountType: readValue('admin-coupon-discount-type'),
      discountScope: readValue('admin-coupon-scope'),
      value: Number(readValue('admin-coupon-value')),
      maxDiscountAmount: readValue('admin-coupon-max') ? Number(readValue('admin-coupon-max')) : null,
      minCartAmount: readValue('admin-coupon-min') ? Number(readValue('admin-coupon-min')) : null,
      startsAt: readValue('admin-coupon-starts'),
      endsAt: readValue('admin-coupon-ends'),
      categoryIds: csvToUuidSet(readValue('admin-coupon-category-ids'))
    };
    show('admin-output', await api('PRODUCT_ADMIN', '/api/v1/coupons', { method: 'POST', body: JSON.stringify(payload) }));
  } catch (error) { show('admin-output', error, false); }
}

async function adminListCoupons() {
  try { show('admin-output', await api('PRODUCT_ADMIN', '/api/v1/coupons?page=0&size=50')); }
  catch (error) { show('admin-output', error, false); }
}

async function sellerProfile() {
  try { show('seller-output', await api('SELLER', '/api/v1/sellers/me')); }
  catch (error) { show('seller-output', error, false); }
}

async function sellerWarehouses() {
  try { show('seller-output', await api('SELLER', '/api/v1/sellers/warehouses?page=0&size=20')); }
  catch (error) { show('seller-output', error, false); }
}

async function sellerCreateWarehouse() {
  try {
    const payload = {
      name: readValue('seller-warehouse-name'),
      code: readValue('seller-warehouse-code'),
      addressLine1: readValue('seller-warehouse-address'),
      city: readValue('seller-warehouse-city'),
      state: readValue('seller-warehouse-state'),
      country: readValue('seller-warehouse-country'),
      postalCode: readValue('seller-warehouse-postal')
    };
    show('seller-output', await api('SELLER', '/api/v1/sellers/warehouses', { method: 'POST', body: JSON.stringify(payload) }));
  } catch (error) { show('seller-output', error, false); }
}

async function sellerCreateProduct() {
  try {
    const payload = {
      categoryId: readValue('seller-product-category-id'),
      name: readValue('seller-product-name'),
      description: readValue('seller-product-description'),
      sku: readValue('seller-product-sku'),
      price: Number(readValue('seller-product-price')),
      currency: 'INR',
      attributes: parseJsonOrEmpty('seller-product-attributes', [])
    };
    show('seller-output', await api('SELLER', '/api/v1/products', { method: 'POST', body: JSON.stringify(payload) }));
  } catch (error) { show('seller-output', error, false); }
}

async function sellerPublishProduct() {
  try {
    const productId = readValue('seller-product-id-action');
    show('seller-output', await api('SELLER', `/api/v1/products/${productId}/publish`, { method: 'PATCH' }));
  } catch (error) { show('seller-output', error, false); }
}

async function sellerInventoryUpdate() {
  try {
    const payload = {
      productId: readValue('seller-inventory-product-id'),
      warehouseId: readValue('seller-inventory-warehouse-id'),
      availableQuantity: Number(readValue('seller-inventory-quantity')),
      reason: readValue('seller-inventory-reason')
    };
    show('seller-output', await api('SELLER', '/api/v1/inventory', { method: 'PUT', body: JSON.stringify(payload) }));
  } catch (error) { show('seller-output', error, false); }
}

async function sellerEnrollCoupon() {
  try {
    const code = readValue('seller-coupon-code');
    const productId = readValue('seller-coupon-product-id');
    show('seller-output', await api('SELLER', `/api/v1/coupons/${code}/products/${productId}/enroll`, { method: 'POST' }));
  } catch (error) { show('seller-output', error, false); }
}

async function customerSearch() {
  try {
    const params = new URLSearchParams({ page: '0', size: '20', sortBy: 'name', direction: 'ASC' });
    const q = readValue('customer-search-q');
    const categoryId = readValue('customer-search-category-id');
    if (q) params.set('q', q);
    if (categoryId) params.set('categoryId', categoryId);
    show('customer-output', await api('CUSTOMER', `/api/v1/search/products?${params}`));
  } catch (error) { show('customer-output', error, false); }
}

async function customerAddToCart() {
  try {
    const payload = { productId: readValue('customer-cart-product-id'), quantity: Number(readValue('customer-cart-quantity')) };
    show('customer-output', await api('CUSTOMER', '/api/v1/cart/items', { method: 'POST', body: JSON.stringify(payload) }));
  } catch (error) { show('customer-output', error, false); }
}

async function customerViewCart() {
  try { show('customer-output', await api('CUSTOMER', '/api/v1/cart')); }
  catch (error) { show('customer-output', error, false); }
}

async function customerApplyCoupon() {
  try { show('customer-output', await api('CUSTOMER', '/api/v1/cart/coupon', { method: 'POST', body: JSON.stringify({ code: readValue('customer-coupon-code') }) })); }
  catch (error) { show('customer-output', error, false); }
}

async function customerRemoveCoupon() {
  try { show('customer-output', await api('CUSTOMER', '/api/v1/cart/coupon', { method: 'DELETE' })); }
  catch (error) { show('customer-output', error, false); }
}

async function customerPlaceOrder() {
  try { show('customer-output', await api('CUSTOMER', '/api/v1/orders', { method: 'POST', body: JSON.stringify({ shippingAddress: readValue('customer-shipping-address') }) })); }
  catch (error) { show('customer-output', error, false); }
}

async function customerListOrders() {
  try { show('customer-output', await api('CUSTOMER', '/api/v1/orders?page=0&size=20')); }
  catch (error) { show('customer-output', error, false); }
}
