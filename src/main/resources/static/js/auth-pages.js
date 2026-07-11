const ROLE_PATHS = {
  PRODUCT_ADMIN: '/admin/dashboard',
  SELLER: '/seller/dashboard',
  CUSTOMER: '/customer/dashboard'
};

function tokenKey(role){ return `vincommerce.${role}.jwt`; }
function userKey(role){ return `vincommerce.${role}.user`; }
function setMessage(id, text, type='info'){
  const el = document.getElementById(id);
  if(!el) return;
  el.className = `alert ${type}`;
  el.textContent = text;
  el.classList.remove('hidden');
}
async function authFetch(path, body){
  const response = await fetch(path, {
    method: 'POST',
    headers: {'Content-Type':'application/json'},
    body: JSON.stringify(body)
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if(!response.ok){
    const message = data.message || data.error || `Request failed with ${response.status}`;
    throw new Error(message);
  }
  return data;
}
function persistAuth(role, data){
  localStorage.setItem(tokenKey(role), data.accessToken);
  localStorage.setItem(userKey(role), JSON.stringify({role, userId:data.userId, expiresAt:data.expiresAt}));
}
async function loginRole(role){
  const email = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value;
  const messageId = 'auth-message';
  try{
    const data = await authFetch('/api/v1/auth/login', {email, password, role});
    persistAuth(role, data);
    setMessage(messageId, 'Signed in successfully. Opening your workspace...', 'success');
    window.setTimeout(() => window.location.assign(ROLE_PATHS[role]), 450);
  }catch(error){
    setMessage(messageId, error.message, 'error');
  }
}
async function signupSeller(){
  try{
    const data = await authFetch('/api/v1/auth/sellers/signup', {
      businessName: document.getElementById('businessName').value.trim(),
      email: document.getElementById('email').value.trim(),
      password: document.getElementById('password').value,
      contactNumber: document.getElementById('contactNumber').value.trim()
    });
    persistAuth('SELLER', data);
    setMessage('auth-message', 'Seller account created. Opening seller workspace...', 'success');
    window.setTimeout(() => window.location.assign('/seller/dashboard'), 450);
  }catch(error){ setMessage('auth-message', error.message, 'error'); }
}
async function signupCustomer(){
  try{
    const data = await authFetch('/api/v1/auth/customers/signup', {
      fullName: document.getElementById('fullName').value.trim(),
      email: document.getElementById('email').value.trim(),
      password: document.getElementById('password').value,
      phoneNumber: document.getElementById('phoneNumber').value.trim()
    });
    persistAuth('CUSTOMER', data);
    setMessage('auth-message', 'Customer account created. Opening storefront...', 'success');
    window.setTimeout(() => window.location.assign('/customer/dashboard'), 450);
  }catch(error){ setMessage('auth-message', error.message, 'error'); }
}
function fillDemo(role){
  const map = {PRODUCT_ADMIN:'admin@example.com', SELLER:'seller@example.com', CUSTOMER:'customer@example.com'};
  document.getElementById('email').value = map[role];
  document.getElementById('password').value = 'Password1';
}
