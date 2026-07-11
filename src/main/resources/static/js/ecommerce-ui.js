/* ===== E-Commerce UI - Token-based Auth with Role Authorization ===== */

const tokenKey = role => `ecommerce_${role}_token`;
const tokenDataKey = role => `ecommerce_${role}_data`;

// ===== TOAST NOTIFICATIONS =====
function ensureToastContainer() {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }
  return container;
}

function toast(message, type = 'info') {
  const container = ensureToastContainer();
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = message;
  container.appendChild(el);
  setTimeout(() => { el.style.opacity = '0'; el.style.transform = 'translateX(100%)'; setTimeout(() => el.remove(), 300); }, 4000);
}

// ===== UTILITIES =====
function readValue(id) {
  const element = document.getElementById(id);
  return element ? element.value.trim() : '';
}

function parseJsonOrEmpty(id, fallback) {
  const value = readValue(id);
  if (!value) return fallback;
  try { return JSON.parse(value); } catch (e) { toast('Invalid JSON format', 'error'); return fallback; }
}

function csvToUuidSet(value) {
  return value ? value.split(',').map(v => v.trim()).filter(Boolean) : [];
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]));
}

function show(targetId, payload, ok = true) {
  const target = document.getElementById(targetId);
  if (!target) return;
  target.className = ok ? 'status ok' : 'status error';
  target.style.display = 'block';
  target.innerHTML = `<pre>${escapeHtml(typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2))}</pre>`;
}

// ===== SECTION SWITCHING =====
function showSection(sectionName) {
  const target = document.getElementById(`section-${sectionName}`) || document.getElementById(`tab-${sectionName}`);
  if (!target) {
    console.warn(`Section not found: ${sectionName}`);
    return;
  }

  document.querySelectorAll('.page-section, .tab-content').forEach(section => section.classList.remove('active'));
  target.classList.add('active');

  document.querySelectorAll('.nav-link').forEach(link => {
    const handler = link.getAttribute('onclick') || '';
    link.classList.toggle('active', handler.includes(`'${sectionName}'`) || handler.includes(`"${sectionName}"`));
  });

  if (sectionName === 'cart') {
    if (isAuthenticated('CUSTOMER')) {
      const cartActions = document.getElementById('cart-actions');
      if (cartActions) cartActions.style.display = 'block';
      customerViewCart();
    } else {
      openLoginModal('Please sign in to view and modify your cart.');
    }
  }
  if (sectionName === 'orders') customerListOrders();
  if (sectionName === 'account') renderCustomerAuthArea();
}

function switchTab(tabName) { showSection(tabName); }
function switchCustomerTab(tabName) { showSection(tabName); }
function switchSellerTab(tabName) { showSection(tabName); }
function switchAdminTab(tabName) { showSection(tabName); }

// ===== AUTH GUARD =====
function requireAuth(role, actionDescription) {
  if (!isAuthenticated(role)) {
    toast(`Please sign in first to ${actionDescription}.`, 'error');
    if (role === 'CUSTOMER') {
      openLoginModal(`Please sign in first to ${actionDescription}.`);
    } else {
      showSection('login');
    }
    return false;
  }
  return true;
}

// ===== AUTH STATE =====
function isAuthenticated(role) {
  const token = localStorage.getItem(tokenKey(role));
  if (!token) return false;
  const data = localStorage.getItem(tokenDataKey(role));
  if (data) {
    try {
      const parsed = JSON.parse(data);
      if (parsed.expiresAt && new Date(parsed.expiresAt) < new Date()) {
        localStorage.removeItem(tokenKey(role));
        localStorage.removeItem(tokenDataKey(role));
        return false;
      }
    } catch (e) {}
  }
  return true;
}

function updateAuthBar(role) {
  const bar = document.getElementById(`${role}-auth-bar`);
  const data = JSON.parse(localStorage.getItem(tokenDataKey(role)) || '{}');
  const signedIn = isAuthenticated(role);

  if (bar) {
    if (signedIn) {
      bar.className = 'auth-bar';
      bar.innerHTML = `<span class="auth-indicator"></span> Signed in as <strong>${escapeHtml(data.email || role)}</strong>`;
    } else {
      bar.className = 'auth-bar disconnected';
      bar.innerHTML = `<span class="auth-indicator"></span> Not signed in — please sign in to use protected features.`;
    }
  }

  const genericNavStatus = document.getElementById('nav-auth-status');
  if (genericNavStatus) genericNavStatus.innerHTML = signedIn ? `<span class="user-dot"></span> ${escapeHtml(data.email || role)}` : `<span class="user-dot off"></span> Sign In`;

  const roleNavMap = {
    PRODUCT_ADMIN: { user: 'admin-nav-user', links: 'admin-nav-links', loginSection: 'gate', defaultSection: 'categories' },
    SELLER: { user: 'seller-nav-user', links: 'seller-nav-links', loginSection: 'gate', defaultSection: 'products' },
    CUSTOMER: { user: 'customer-nav-user', links: null, loginSection: 'account', defaultSection: 'shop' }
  };
  const cfg = roleNavMap[role];
  if (!cfg) return;

  const userEl = document.getElementById(cfg.user);
  if (userEl) {
    if (signedIn) {
      userEl.innerHTML = `${escapeHtml(data.email || role)} <button class="ghost" style="padding:4px 8px;margin-left:8px;font-size:.75rem;" onclick="logout('${role}','${role.toLowerCase()}-logout-output');afterLogout('${role}')">Logout</button>`;
    } else if (role === 'CUSTOMER') {
      userEl.innerHTML = `<a href="#" onclick="showSection('account');return false;" style="color:#fff;text-decoration:none;">Sign In</a>`;
    } else {
      userEl.textContent = '';
    }
  }

  const linksEl = cfg.links ? document.getElementById(cfg.links) : null;
  if (linksEl) linksEl.style.display = signedIn ? 'flex' : 'none';

  renderCustomerAuthArea();
}

function afterLogout(role) {
  updateAuthBar(role);
  if (role === 'PRODUCT_ADMIN' || role === 'SELLER') showSection('gate');
  if (role === 'CUSTOMER') {
    const cartActions = document.getElementById('cart-actions');
    if (cartActions) cartActions.style.display = 'none';
    showSection('shop');
  }
}

// ===== API FUNCTION =====
async function api(role, path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const token = localStorage.getItem(tokenKey(role));
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch(path, { ...options, headers });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      toast(`Access denied (${response.status}). Please sign in again.`, 'error');
      if (response.status === 401) {
        localStorage.removeItem(tokenKey(role));
        localStorage.removeItem(tokenDataKey(role));
        updateAuthBar(role);
      }
    }
    throw body || { status: response.status, error: response.statusText };
  }
  return body;
}

// ===== LOGIN / LOGOUT =====
async function login(role, outputId) {
  try {
    const email = readValue(`${role}-email`);
    const password = readValue(`${role}-password`);
    if (!email || !password) { toast('Enter email and password', 'error'); return; }
    const result = await api(role, '/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password, role }) });
    localStorage.setItem(tokenKey(role), result.accessToken);
    localStorage.setItem(tokenDataKey(role), JSON.stringify({ email, role: result.role, expiresAt: result.expiresAt, userId: result.userId }));
    updateAuthBar(role);
    toast('✅ Signed in successfully!', 'success');
    show(outputId, { message: 'Signed in!', email, role: result.role, userId: result.userId });
    return result;
  } catch (error) { show(outputId, error, false); toast('Sign in failed.', 'error'); throw error; }
}

function logout(role, outputId) {
  const token = localStorage.getItem(tokenKey(role));
  if (token) fetch('/api/v1/auth/logout', { method: 'POST', headers: { 'Authorization': `Bearer ${token}` } }).catch(() => {});
  localStorage.removeItem(tokenKey(role));
  localStorage.removeItem(tokenDataKey(role));
  updateAuthBar(role);
  toast('Signed out.', 'info');
  show(outputId, 'Signed out successfully.');
}

// ===== SIGNUP =====
async function signupCustomer() {
  try {
    const email = readValue('signup-customer-email'), password = readValue('signup-customer-password');
    const fullName = readValue('signup-customer-fullname'), phoneNumber = readValue('signup-customer-phone');
    if (!email || !password || !fullName) { toast('Fill all required fields', 'error'); return; }
    const result = await api('CUSTOMER', '/api/v1/auth/customers/signup', { method: 'POST', body: JSON.stringify({ email, password, fullName, phoneNumber: phoneNumber || null }) });
    localStorage.setItem(tokenKey('CUSTOMER'), result.accessToken);
    localStorage.setItem(tokenDataKey('CUSTOMER'), JSON.stringify({ email, role: result.role, expiresAt: result.expiresAt, userId: result.userId }));
    toast('Account created! Redirecting...', 'success');
    setTimeout(() => window.location.href = '/customer', 1000);
  } catch (error) { show('signup-output', error, false); toast('Signup failed', 'error'); }
}

async function signupSeller() {
  try {
    const email = readValue('signup-seller-email'), password = readValue('signup-seller-password');
    const businessName = readValue('signup-seller-business'), contactNumber = readValue('signup-seller-contact');
    if (!email || !password || !businessName) { toast('Fill all required fields', 'error'); return; }
    const result = await api('SELLER', '/api/v1/auth/sellers/signup', { method: 'POST', body: JSON.stringify({ email, password, businessName, contactNumber: contactNumber || null }) });
    localStorage.setItem(tokenKey('SELLER'), result.accessToken);
    localStorage.setItem(tokenDataKey('SELLER'), JSON.stringify({ email, role: result.role, expiresAt: result.expiresAt, userId: result.userId }));
    toast('Seller account created! Redirecting...', 'success');
    setTimeout(() => window.location.href = '/seller', 1000);
  } catch (error) { show('signup-output', error, false); toast('Signup failed', 'error'); }
}

function switchLoginTab(role) {
  document.querySelectorAll('.auth-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
  event.target.classList.add('active');
  document.getElementById('login-role').value = role;
  const emails = { CUSTOMER: 'customer@example.com', SELLER: 'seller@example.com', PRODUCT_ADMIN: 'admin@example.com' };
  document.getElementById('login-email').value = emails[role] || '';
  document.getElementById('login-password').value = 'Password1';
}

async function loginFromPage() {
  const role = readValue('login-role'), email = readValue('login-email'), password = readValue('login-password');
  if (!email || !password) { toast('Enter email and password', 'error'); return; }
  try {
    const result = await api(role, '/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password, role }) });
    localStorage.setItem(tokenKey(role), result.accessToken);
    localStorage.setItem(tokenDataKey(role), JSON.stringify({ email, role: result.role, expiresAt: result.expiresAt, userId: result.userId }));
    toast('Signed in! Redirecting...', 'success');
    const redirects = { CUSTOMER: '/customer', SELLER: '/seller', PRODUCT_ADMIN: '/admin' };
    setTimeout(() => window.location.href = redirects[role] || '/', 800);
  } catch (error) { show('login-output', error, false); toast('Login failed.', 'error'); }
}

function switchSignupTab(tab) {
  document.querySelectorAll('.auth-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
  event.target.classList.add('active');
  document.getElementById('signup-customer-form').style.display = tab === 'customer' ? 'block' : 'none';
  document.getElementById('signup-seller-form').style.display = tab === 'seller' ? 'block' : 'none';
}

// ===== RENDER HELPERS =====
function renderProductCards(products, containerId, addToCartEnabled = false) {
  const container = document.getElementById(containerId);
  if (!container) return;
  if (!products || products.length === 0) {
    container.innerHTML = `<div class="empty-state"><div class="empty-icon">📦</div><div class="empty-title">No products found</div></div>`;
    return;
  }
  container.innerHTML = products.map(p => `
    <div class="product-card">
      <div class="product-name">${escapeHtml(p.name || 'Product')}</div>
      <div class="product-sku">${escapeHtml(p.sku || '')}</div>
      <div class="product-price">₹${(p.price || 0).toLocaleString('en-IN')}</div>
      ${p.categoryName ? `<div style="font-size:.78rem;color:var(--muted);margin-top:4px;">${escapeHtml(p.categoryName)}</div>` : ''}
      ${p.status ? `<span class="product-status ${(p.status||'').toLowerCase()}">${p.status}</span>` : ''}
      <div class="product-actions">
        ${addToCartEnabled ? `<button onclick="addProductToCart('${p.id}')">🛒 Add to Cart</button>` : ''}
      </div>
    </div>
  `).join('');
}

function addProductToCart(productId) {
  document.getElementById('customer-cart-product-id').value = productId;
  document.getElementById('customer-cart-quantity').value = '1';
  customerAddToCart();
  switchCustomerTab('cart');
}

function renderCategoryList(categories, containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  if (!categories || categories.length === 0) {
    container.innerHTML = `<div class="empty-state"><div class="empty-icon">📁</div><div class="empty-title">No categories</div></div>`;
    return;
  }
  container.innerHTML = `<ul class="category-tree">${categories.map(c => `
    <li><span class="cat-name">📁 ${escapeHtml(c.name || 'Unnamed')}</span>
    <span class="cat-id" onclick="navigator.clipboard.writeText('${c.id}');toast('ID copied!','success')" style="cursor:pointer;" title="Copy ID">${c.id ? c.id.substring(0,8)+'...' : ''} 📋</span></li>
  `).join('')}</ul>`;
}

function renderCouponList(coupons, containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  if (!coupons || coupons.length === 0) {
    container.innerHTML = `<div class="empty-state"><div class="empty-icon">🏷️</div><div class="empty-title">No coupons</div></div>`;
    return;
  }
  container.innerHTML = `<table class="data-table"><thead><tr><th>Code</th><th>Type</th><th>Value</th><th>Scope</th></tr></thead><tbody>${coupons.map(c => `
    <tr><td><strong>${escapeHtml(c.code||'')}</strong></td><td>${escapeHtml(c.discountType||'')}</td><td>${c.value||''}${c.discountType==='UPTO_PERCENT_OFF'?'%':' ₹'}</td><td>${escapeHtml(c.discountScope||c.scope||'')}</td></tr>
  `).join('')}</tbody></table>`;
}

function renderCartItems(cart, containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  const items = cart.items || [];
  if (items.length === 0) {
    container.innerHTML = `<div class="empty-state" style="padding:40px 20px;"><div class="empty-icon">🛒</div><div class="empty-title">Your cart is empty</div><div class="empty-text">Browse products and add items to your cart</div></div>`;
    return;
  }
  let html = items.map(item => `
    <div class="cart-item">
      <div class="item-info">
        <div class="item-name">${escapeHtml(item.productName || 'Product')}</div>
        <div class="item-meta">Qty: ${item.quantity} × ₹${(item.unitPrice||0).toLocaleString('en-IN')} · ${item.stockStatus==='IN_STOCK'?'✅ In Stock':'⚠️ '+item.stockStatus}</div>
      </div>
      <div class="item-price">₹${(item.subtotal||0).toLocaleString('en-IN')}${item.discountAmount>0?`<br><span style="color:var(--ok);font-size:.8rem;">-₹${item.discountAmount.toLocaleString('en-IN')}</span>`:''}</div>
    </div>
  `).join('');
  html += `<div style="margin-top:16px;padding:16px;background:var(--accent-soft);border-radius:var(--radius-sm);">
    <div style="display:flex;justify-content:space-between;font-size:.9rem;margin-bottom:6px;"><span>Subtotal</span><span>₹${(cart.subtotal||0).toLocaleString('en-IN')}</span></div>
    ${cart.discountAmount>0?`<div style="display:flex;justify-content:space-between;font-size:.9rem;color:var(--ok);margin-bottom:6px;"><span>Discount${cart.couponCode?' ('+cart.couponCode+')':''}</span><span>-₹${cart.discountAmount.toLocaleString('en-IN')}</span></div>`:''}
    <div style="display:flex;justify-content:space-between;font-weight:700;font-size:1.1rem;border-top:1px solid var(--accent-light);padding-top:10px;margin-top:10px;"><span>Total</span><span>₹${(cart.totalAmount||0).toLocaleString('en-IN')}</span></div>
    ${cart.checkoutReady?'<div style="margin-top:8px;font-size:.82rem;color:var(--ok);">✅ Ready for checkout</div>':'<div style="margin-top:8px;font-size:.82rem;color:var(--warning);">⚠️ Check item availability</div>'}
  </div>`;
  container.innerHTML = html;
  // Update badge
  const badge = document.getElementById('nav-cart-badge');
  if (badge) { badge.textContent = items.length; badge.style.display = 'inline'; }
  const badge2 = document.getElementById('cart-count');
  if (badge2) badge2.textContent = items.length;
}

function renderOrders(orders, containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  if (!orders || orders.length === 0) {
    container.innerHTML = `<div class="empty-state"><div class="empty-icon">📦</div><div class="empty-title">No orders yet</div></div>`;
    return;
  }
  container.innerHTML = orders.map(o => `
    <div class="order-card">
      <div class="order-header"><span class="order-id">Order #${(o.id||'').substring(0,8)}</span><span class="order-status">${escapeHtml(o.status||'PLACED')}</span></div>
      <div class="order-details">
        ${o.totalAmount?`<div>₹${o.totalAmount.toLocaleString('en-IN')}</div>`:''}
        ${o.shippingAddress?`<div style="font-size:.82rem;color:var(--muted);">📍 ${escapeHtml(o.shippingAddress.substring(0,60))}</div>`:''}
        ${o.createdAt?`<div style="font-size:.82rem;color:var(--muted);">📅 ${new Date(o.createdAt).toLocaleDateString()}</div>`:''}
      </div>
    </div>
  `).join('');
}

// ===== ADMIN FUNCTIONS =====
async function loadCategories() {
  try {
    const result = await api('PRODUCT_ADMIN', '/api/v1/categories?page=0&size=50');
    show('admin-categories-output', result);
    renderCategoryList(result.content || result.data || result || [], 'admin-categories-display');
    toast('Categories loaded', 'success');
  } catch (error) { show('admin-categories-output', error, false); }
}

async function adminCreateCategory() {
  if (!requireAuth('PRODUCT_ADMIN', 'create categories')) return;
  try {
    const payload = { parentId: readValue('admin-category-parent-id') || null, name: readValue('admin-category-name'), attributes: parseJsonOrEmpty('admin-category-attributes', []) };
    if (!payload.name) { toast('Category name required', 'error'); return; }
    const result = await api('PRODUCT_ADMIN', '/api/v1/categories', { method: 'POST', body: JSON.stringify(payload) });
    show('admin-create-category-output', result);
    toast('Category created!', 'success');
  } catch (error) { show('admin-create-category-output', error, false); }
}

async function adminCreateCoupon() {
  if (!requireAuth('PRODUCT_ADMIN', 'create coupons')) return;
  try {
    const payload = { code: readValue('admin-coupon-code'), description: readValue('admin-coupon-description'), discountType: readValue('admin-coupon-discount-type'), discountScope: readValue('admin-coupon-scope'), value: Number(readValue('admin-coupon-value')), maxDiscountAmount: readValue('admin-coupon-max')?Number(readValue('admin-coupon-max')):null, minCartAmount: readValue('admin-coupon-min')?Number(readValue('admin-coupon-min')):null, startsAt: readValue('admin-coupon-starts'), endsAt: readValue('admin-coupon-ends'), categoryIds: csvToUuidSet(readValue('admin-coupon-category-ids')) };
    if (!payload.code) { toast('Coupon code required', 'error'); return; }
    const result = await api('PRODUCT_ADMIN', '/api/v1/coupons', { method: 'POST', body: JSON.stringify(payload) });
    show('admin-create-coupon-output', result);
    toast('Coupon created!', 'success');
  } catch (error) { show('admin-create-coupon-output', error, false); }
}

async function adminListCoupons() {
  if (!requireAuth('PRODUCT_ADMIN', 'list coupons')) return;
  try {
    const result = await api('PRODUCT_ADMIN', '/api/v1/coupons?page=0&size=50');
    show('admin-coupons-output', result);
    renderCouponList(result.content || result.data || result || [], 'admin-coupons-display');
    toast('Coupons loaded', 'success');
  } catch (error) { show('admin-coupons-output', error, false); }
}

// ===== SELLER FUNCTIONS =====
async function sellerProfile() {
  if (!requireAuth('SELLER', 'view profile')) return;
  try { show('seller-output', await api('SELLER', '/api/v1/sellers/me')); } catch (e) { show('seller-output', e, false); }
}
async function sellerWarehouses() {
  if (!requireAuth('SELLER', 'list warehouses')) return;
  try { show('seller-warehouse-output', await api('SELLER', '/api/v1/sellers/warehouses?page=0&size=20')); toast('Loaded', 'success'); } catch (e) { show('seller-warehouse-output', e, false); }
}
async function sellerCreateWarehouse() {
  if (!requireAuth('SELLER', 'create warehouses')) return;
  try {
    const payload = { name: readValue('seller-warehouse-name'), code: readValue('seller-warehouse-code'), addressLine1: readValue('seller-warehouse-address'), city: readValue('seller-warehouse-city'), state: readValue('seller-warehouse-state'), country: readValue('seller-warehouse-country'), postalCode: readValue('seller-warehouse-postal') };
    show('seller-warehouse-output', await api('SELLER', '/api/v1/sellers/warehouses', { method: 'POST', body: JSON.stringify(payload) }));
    toast('Warehouse created!', 'success');
  } catch (e) { show('seller-warehouse-output', e, false); }
}
async function sellerCreateProduct() {
  if (!requireAuth('SELLER', 'create products')) return;
  try {
    const payload = { categoryId: readValue('seller-product-category-id'), name: readValue('seller-product-name'), description: readValue('seller-product-description'), sku: readValue('seller-product-sku'), price: Number(readValue('seller-product-price')), currency: 'INR', attributes: parseJsonOrEmpty('seller-product-attributes', []) };
    if (!payload.name || !payload.categoryId) { toast('Name and category required', 'error'); return; }
    show('seller-create-output', await api('SELLER', '/api/v1/products', { method: 'POST', body: JSON.stringify(payload) }));
    toast('Product draft created!', 'success');
  } catch (e) { show('seller-create-output', e, false); }
}
async function sellerPublishProduct() {
  if (!requireAuth('SELLER', 'publish products')) return;
  try {
    const id = readValue('seller-product-id-action');
    if (!id) { toast('Product ID required', 'error'); return; }
    show('seller-products-output', await api('SELLER', `/api/v1/products/${id}/publish`, { method: 'PATCH' }));
    document.getElementById('seller-products-output').style.display = 'block';
    toast('Published!', 'success');
  } catch (e) { show('seller-products-output', e, false); document.getElementById('seller-products-output').style.display='block'; }
}
async function sellerGetProduct() {
  try {
    const id = readValue('seller-product-id-action');
    if (!id) { toast('Product ID required', 'error'); return; }
    show('seller-products-output', await api('SELLER', `/api/v1/products/${id}`));
    document.getElementById('seller-products-output').style.display = 'block';
    toast('Loaded', 'success');
  } catch (e) { show('seller-products-output', e, false); document.getElementById('seller-products-output').style.display='block'; }
}
async function sellerListProducts() {
  if (!requireAuth('SELLER', 'list products')) return;
  try {
    const result = await api('SELLER', '/api/v1/search/products?page=0&size=20&sortBy=name&direction=ASC');
    show('seller-products-output', result);
    const items = result.content || result.data || result || [];
    renderProductCards(Array.isArray(items) ? items : [items], 'seller-products-display', false);
    toast('Products loaded', 'success');
  } catch (e) { show('seller-products-output', e, false); }
}
async function sellerInventoryUpdate() {
  if (!requireAuth('SELLER', 'update inventory')) return;
  try {
    const payload = { productId: readValue('seller-inventory-product-id'), warehouseId: readValue('seller-inventory-warehouse-id'), availableQuantity: Number(readValue('seller-inventory-quantity')), reason: readValue('seller-inventory-reason') };
    show('seller-inventory-output', await api('SELLER', '/api/v1/inventory', { method: 'PUT', body: JSON.stringify(payload) }));
    toast('Inventory updated!', 'success');
  } catch (e) { show('seller-inventory-output', e, false); }
}
async function sellerEnrollCoupon() {
  if (!requireAuth('SELLER', 'enroll in coupons')) return;
  try {
    const code = readValue('seller-coupon-code'), productId = readValue('seller-coupon-product-id');
    if (!code || !productId) { toast('Code and product ID required', 'error'); return; }
    show('seller-coupon-output', await api('SELLER', `/api/v1/coupons/${code}/products/${productId}/enroll`, { method: 'POST' }));
    toast('Enrolled!', 'success');
  } catch (e) { show('seller-coupon-output', e, false); }
}

// ===== CUSTOMER FUNCTIONS =====
async function customerSearch() {
  try {
    const params = new URLSearchParams({ page: '0', size: '20', sortBy: 'name', direction: 'ASC' });
    const q = readValue('customer-search-q');
    const categoryId = readValue('customer-search-category-id');
    if (q) params.set('q', q);
    if (categoryId) params.set('categoryId', categoryId);
    const result = await api('CUSTOMER', `/api/v1/search/products?${params}`);
    show('customer-search-output', result);
    const items = result.content || result.data || result || [];
    renderProductCards(Array.isArray(items) ? items : [items], 'customer-product-results', true);
  } catch (error) { show('customer-search-output', error, false); }
}

async function customerAddToCart() {
  if (!requireAuth('CUSTOMER', 'add to cart')) return;
  try {
    const payload = { productId: readValue('customer-cart-product-id'), quantity: Number(readValue('customer-cart-quantity') || 1) };
    if (!payload.productId) { toast('Product ID required', 'error'); return; }
    await api('CUSTOMER', '/api/v1/cart/items', { method: 'POST', body: JSON.stringify(payload) });
    toast('Added to cart!', 'success');
    customerViewCart();
  } catch (e) { show('customer-cart-output', e, false); toast('Failed to add', 'error'); }
}

async function customerViewCart() {
  if (!requireAuth('CUSTOMER', 'view cart')) return;
  try {
    const result = await api('CUSTOMER', '/api/v1/cart');
    renderCartItems(result, 'customer-cart-display');
  } catch (e) { /* silent */ }
}

async function customerApplyCoupon() {
  if (!requireAuth('CUSTOMER', 'apply coupon')) return;
  try {
    const code = readValue('customer-coupon-code');
    if (!code) { toast('Enter coupon code', 'error'); return; }
    await api('CUSTOMER', '/api/v1/cart/coupon', { method: 'POST', body: JSON.stringify({ code }) });
    toast('Coupon applied!', 'success');
    customerViewCart();
  } catch (e) { toast(e.message || 'Coupon failed', 'error'); }
}

async function customerRemoveCoupon() {
  if (!requireAuth('CUSTOMER', 'remove coupon')) return;
  try {
    await api('CUSTOMER', '/api/v1/cart/coupon', { method: 'DELETE' });
    toast('Coupon removed', 'info');
    customerViewCart();
  } catch (e) { toast('Failed', 'error'); }
}

async function customerPlaceOrder() {
  if (!requireAuth('CUSTOMER', 'place order')) return;
  try {
    const address = readValue('customer-shipping-address');
    if (!address) { toast('Enter shipping address', 'error'); return; }
    await api('CUSTOMER', '/api/v1/orders', { method: 'POST', body: JSON.stringify({ shippingAddress: address }) });
    toast('🎉 Order placed!', 'success');
    setTimeout(() => { switchCustomerTab('orders'); customerListOrders(); }, 800);
  } catch (e) { toast(e.message || 'Order failed', 'error'); }
}

async function customerListOrders() {
  if (!requireAuth('CUSTOMER', 'view orders')) return;
  try {
    const result = await api('CUSTOMER', '/api/v1/orders?page=0&size=20');
    const items = result.content || result.data || result || [];
    renderOrders(Array.isArray(items) ? items : [items], 'customer-orders-display');
  } catch (e) { toast('Failed to load orders', 'error'); }
}

// ===== PAGE-SPECIFIC AUTH HELPERS =====
function fillRoleCredentials(role) {
  const emailMap = { PRODUCT_ADMIN: 'admin@example.com', SELLER: 'seller@example.com', CUSTOMER: 'customer@example.com' };
  const emailField = document.getElementById(`${role}-email`);
  const passwordField = document.getElementById(`${role}-password`);
  if (emailField) emailField.value = emailMap[role] || '';
  if (passwordField) passwordField.value = 'Password1';
}

async function doAdminLogin() {
  try {
    await login('PRODUCT_ADMIN', 'admin-login-output');
    updateAuthBar('PRODUCT_ADMIN');
    showSection('categories');
    await loadCategories();
  } catch (e) { /* login() already rendered the error */ }
}

async function doSellerLogin() {
  try {
    await login('SELLER', 'seller-login-output');
    updateAuthBar('SELLER');
    showSection('products');
    await sellerListProducts();
  } catch (e) { /* login() already rendered the error */ }
}

async function doSellerSignup() {
  try {
    const email = readValue('seller-signup-email');
    const password = readValue('seller-signup-password');
    const businessName = readValue('seller-signup-business');
    const contactNumber = readValue('seller-signup-contact');
    if (!email || !password || !businessName) { toast('Fill all required fields', 'error'); return; }
    const result = await api('SELLER', '/api/v1/auth/sellers/signup', { method: 'POST', body: JSON.stringify({ email, password, businessName, contactNumber: contactNumber || null }) });
    localStorage.setItem(tokenKey('SELLER'), result.accessToken);
    localStorage.setItem(tokenDataKey('SELLER'), JSON.stringify({ email, role: result.role, expiresAt: result.expiresAt, userId: result.userId }));
    show('seller-signup-output', { message: 'Seller account created', email, role: result.role });
    updateAuthBar('SELLER');
    toast('Seller account created!', 'success');
    showSection('products');
    await sellerListProducts();
  } catch (error) { show('seller-signup-output', error, false); toast('Signup failed', 'error'); }
}

function openLoginModal(message) {
  const modal = document.getElementById('login-modal');
  if (!modal) return;
  const email = document.getElementById('modal-login-email');
  const password = document.getElementById('modal-login-password');
  const err = document.getElementById('modal-login-error');
  const p = modal.querySelector('p');
  if (p && message) p.textContent = message;
  if (email && !email.value) email.value = 'customer@example.com';
  if (password && !password.value) password.value = 'Password1';
  if (err) err.style.display = 'none';
  modal.style.display = 'flex';
}

function closeLoginModal() {
  const modal = document.getElementById('login-modal');
  if (modal) modal.style.display = 'none';
}

async function modalLogin() {
  const email = readValue('modal-login-email');
  const password = readValue('modal-login-password');
  const err = document.getElementById('modal-login-error');
  try {
    if (!email || !password) throw new Error('Email and password are required.');
    const result = await api('CUSTOMER', '/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password, role: 'CUSTOMER' }) });
    localStorage.setItem(tokenKey('CUSTOMER'), result.accessToken);
    localStorage.setItem(tokenDataKey('CUSTOMER'), JSON.stringify({ email, role: result.role, expiresAt: result.expiresAt, userId: result.userId }));
    updateAuthBar('CUSTOMER');
    closeLoginModal();
    toast('Signed in successfully.', 'success');
    customerViewCart();
  } catch (error) {
    if (err) { err.textContent = error.message || 'Login failed.'; err.style.display = 'block'; }
    toast('Login failed.', 'error');
  }
}

function renderCustomerAuthArea() {
  const area = document.getElementById('customer-auth-area');
  if (!area) return;
  if (isAuthenticated('CUSTOMER')) {
    const data = JSON.parse(localStorage.getItem(tokenDataKey('CUSTOMER')) || '{}');
    area.innerHTML = `<div class="form-card"><h3>Signed in</h3><p class="hint">${escapeHtml(data.email || 'Customer')}</p><button class="ghost" onclick="logout('CUSTOMER','customer-logout-output');afterLogout('CUSTOMER')">Logout</button><div id="customer-logout-output" class="form-message"></div></div>`;
  } else {
    area.innerHTML = `<div class="form-card"><h3>Customer Sign In</h3><div class="form-group"><label>Email</label><input id="CUSTOMER-email" type="email" value="customer@example.com"></div><div class="form-group"><label>Password</label><input id="CUSTOMER-password" type="password" value="Password1"></div><button class="btn-full" onclick="customerPageLogin()">Sign In</button><p class="auth-switch">New customer? <a href="/signup">Create an account</a></p><div id="customer-login-output" class="form-message"></div></div>`;
  }
}

async function customerPageLogin() {
  try {
    await login('CUSTOMER', 'customer-login-output');
    updateAuthBar('CUSTOMER');
    toast('Customer signed in.', 'success');
    showSection('shop');
    customerViewCart();
  } catch (e) { /* rendered in login() */ }
}

function addCategoryAttribute() {
  const container = document.getElementById('category-attributes-list');
  if (!container) return;
  const row = document.createElement('div');
  row.className = 'kv-row';
  row.innerHTML = `
    <input class="attr-name" placeholder="Label, e.g. RAM">
    <input class="attr-code" placeholder="Code, e.g. ram">
    <select class="attr-type"><option value="STRING">TEXT</option><option value="NUMBER">NUMBER</option><option value="BOOLEAN">BOOLEAN</option></select>
    <label><input class="attr-required" type="checkbox" checked> Required</label>
    <button type="button" class="kv-remove" onclick="this.closest('.kv-row').remove()">×</button>
  `;
  container.appendChild(row);
}

function collectCategoryAttributes() {
  const rows = Array.from(document.querySelectorAll('#category-attributes-list .kv-row'));
  return rows.map(row => {
    const name = row.querySelector('.attr-name')?.value.trim();
    const code = row.querySelector('.attr-code')?.value.trim() || (name ? name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '') : '');
    const attributeType = row.querySelector('.attr-type')?.value || 'STRING';
    const required = Boolean(row.querySelector('.attr-required')?.checked);
    return name ? { name, code, attributeType, required, searchable: true } : null;
  }).filter(Boolean);
}

// Patch adminCreateCategory to support the visual key/value attribute rows.
const originalAdminCreateCategory = adminCreateCategory;
adminCreateCategory = async function() {
  if (!requireAuth('PRODUCT_ADMIN', 'create categories')) return;
  try {
    const attributes = document.getElementById('admin-category-attributes')
      ? parseJsonOrEmpty('admin-category-attributes', [])
      : collectCategoryAttributes();
    const payload = { parentId: readValue('admin-category-parent-id') || null, name: readValue('admin-category-name'), attributes };
    if (!payload.name) { toast('Category name required', 'error'); return; }
    const result = await api('PRODUCT_ADMIN', '/api/v1/categories', { method: 'POST', body: JSON.stringify(payload) });
    show('admin-create-category-output', result);
    toast('Category created!', 'success');
    showSection('categories');
    await loadCategories();
  } catch (error) { show('admin-create-category-output', error, false); }
};

// ===== INIT =====
document.addEventListener('DOMContentLoaded', () => {
  ['PRODUCT_ADMIN', 'SELLER', 'CUSTOMER'].forEach(role => updateAuthBar(role));

  const path = window.location.pathname;
  if (path === '/customer') {
    renderCustomerAuthArea();
    customerSearch();
    return;
  }

  if (path === '/seller') {
    if (isAuthenticated('SELLER')) {
      updateAuthBar('SELLER');
      showSection('products');
      sellerListProducts();
    } else {
      showSection('gate');
    }
    return;
  }

  if (path === '/admin') {
    if (isAuthenticated('PRODUCT_ADMIN')) {
      updateAuthBar('PRODUCT_ADMIN');
      showSection('categories');
      loadCategories();
    } else {
      showSection('gate');
    }
    return;
  }

  const loginEmail = document.getElementById('login-email');
  const loginPassword = document.getElementById('login-password');
  if (loginEmail && !loginEmail.value) loginEmail.value = 'customer@example.com';
  if (loginPassword && !loginPassword.value) loginPassword.value = 'Password1';
});
