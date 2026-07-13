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

function clearMessage(id){
  const el = document.getElementById(id);
  if(el) el.classList.add('hidden');
}

function setLoading(btnId, loading){
  const btn = document.getElementById(btnId);
  if(!btn) return;
  if(loading){
    btn.classList.add('loading');
    btn.disabled = true;
    btn.dataset.originalText = btn.textContent;
    btn.textContent = 'Please wait…';
  } else {
    btn.classList.remove('loading');
    btn.disabled = false;
    if(btn.dataset.originalText) btn.textContent = btn.dataset.originalText;
  }
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

function validateEmail(email){
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function validatePassword(password){
  return password && password.length >= 8;
}

function shakeField(inputEl){
  if(!inputEl) return;
  inputEl.style.borderColor = '#dc2626';
  inputEl.style.animation = 'none';
  inputEl.offsetHeight;
  inputEl.style.animation = 'authShake .4s ease';
  setTimeout(() => { inputEl.style.borderColor = ''; inputEl.style.animation = ''; }, 600);
}

async function loginRole(role){
  const emailEl = document.getElementById('email');
  const passwordEl = document.getElementById('password');
  const email = emailEl?.value.trim();
  const password = passwordEl?.value;
  const messageId = 'auth-message';

  clearMessage(messageId);

  if(!email || !validateEmail(email)){
    shakeField(emailEl);
    setMessage(messageId, 'Please enter a valid email address.', 'error');
    emailEl?.focus();
    return;
  }
  if(!password){
    shakeField(passwordEl);
    setMessage(messageId, 'Please enter your password.', 'error');
    passwordEl?.focus();
    return;
  }

  setLoading('btn-login', true);
  try{
    const data = await authFetch('/api/v1/auth/login', {email, password, role});
    persistAuth(role, data);
    setMessage(messageId, '✓ Signed in successfully! Redirecting…', 'success');
    window.setTimeout(() => window.location.assign(ROLE_PATHS[role]), 600);
  }catch(error){
    setMessage(messageId, error.message, 'error');
    setLoading('btn-login', false);
  }
}

async function signupSeller(){
  const businessNameEl = document.getElementById('businessName');
  const emailEl = document.getElementById('email');
  const passwordEl = document.getElementById('password');
  const messageId = 'auth-message';

  clearMessage(messageId);

  const businessName = businessNameEl?.value.trim();
  const email = emailEl?.value.trim();
  const password = passwordEl?.value;
  const contactNumber = document.getElementById('contactNumber')?.value.trim();

  if(!businessName){
    shakeField(businessNameEl);
    setMessage(messageId, 'Business name is required.', 'error');
    businessNameEl?.focus();
    return;
  }
  if(!email || !validateEmail(email)){
    shakeField(emailEl);
    setMessage(messageId, 'Please enter a valid email address.', 'error');
    emailEl?.focus();
    return;
  }
  if(!validatePassword(password)){
    shakeField(passwordEl);
    setMessage(messageId, 'Password must be at least 8 characters.', 'error');
    passwordEl?.focus();
    return;
  }

  setLoading('btn-signup', true);
  try{
    const data = await authFetch('/api/v1/auth/sellers/signup', {
      businessName, email, password,
      contactNumber: contactNumber || null
    });
    persistAuth('SELLER', data);
    setMessage(messageId, '✓ Seller account created! Opening workspace…', 'success');
    window.setTimeout(() => window.location.assign('/seller/dashboard'), 600);
  }catch(error){
    setMessage(messageId, error.message, 'error');
    setLoading('btn-signup', false);
  }
}

async function signupCustomer(){
  const fullNameEl = document.getElementById('fullName');
  const emailEl = document.getElementById('email');
  const passwordEl = document.getElementById('password');
  const messageId = 'auth-message';

  clearMessage(messageId);

  const fullName = fullNameEl?.value.trim();
  const email = emailEl?.value.trim();
  const password = passwordEl?.value;
  const phoneNumber = document.getElementById('phoneNumber')?.value.trim();

  if(!fullName){
    shakeField(fullNameEl);
    setMessage(messageId, 'Full name is required.', 'error');
    fullNameEl?.focus();
    return;
  }
  if(!email || !validateEmail(email)){
    shakeField(emailEl);
    setMessage(messageId, 'Please enter a valid email address.', 'error');
    emailEl?.focus();
    return;
  }
  if(!validatePassword(password)){
    shakeField(passwordEl);
    setMessage(messageId, 'Password must be at least 8 characters.', 'error');
    passwordEl?.focus();
    return;
  }

  setLoading('btn-signup', true);
  try{
    const data = await authFetch('/api/v1/auth/customers/signup', {
      fullName, email, password,
      phoneNumber: phoneNumber || null
    });
    persistAuth('CUSTOMER', data);
    setMessage(messageId, '✓ Account created! Opening storefront…', 'success');
    window.setTimeout(() => window.location.assign('/customer/dashboard'), 600);
  }catch(error){
    setMessage(messageId, error.message, 'error');
    setLoading('btn-signup', false);
  }
}

function fillDemo(role){
  const map = {PRODUCT_ADMIN:'admin@example.com', SELLER:'seller@example.com', CUSTOMER:'customer@example.com'};
  const emailEl = document.getElementById('email');
  const passwordEl = document.getElementById('password');
  if(emailEl) emailEl.value = map[role];
  if(passwordEl) passwordEl.value = 'Password1';
  [emailEl, passwordEl].forEach(el => {
    if(!el) return;
    el.style.background = '#eff6ff';
    setTimeout(() => el.style.background = '', 600);
  });
}

function togglePassword(){
  const input = document.getElementById('password');
  const btn = document.getElementById('toggle-password');
  if(!input || !btn) return;
  if(input.type === 'password'){
    input.type = 'text';
    btn.textContent = '🙈';
    btn.title = 'Hide password';
  } else {
    input.type = 'password';
    btn.textContent = '👁';
    btn.title = 'Show password';
  }
}

document.addEventListener('keydown', function(e){
  if(e.key !== 'Enter') return;
  const btn = document.getElementById('btn-login') || document.getElementById('btn-signup');
  if(btn && !btn.disabled) btn.click();
});

(function(){
  const style = document.createElement('style');
  style.textContent = '@keyframes authShake{0%,100%{transform:translateX(0)}20%,60%{transform:translateX(-6px)}40%,80%{transform:translateX(6px)}}';
  document.head.appendChild(style);
})();
