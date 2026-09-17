(() => {
  const CLIENT_ID = '382079669418-97b1gh9047010ngd5ln03pns4f1e1g9n.apps.googleusercontent.com';
  let patched = false;
  const patch = () => {
    const id = window.google?.accounts?.id;
    if (!id || typeof id.initialize !== 'function' || patched) return;
    const original = id.initialize.bind(id);
    id.initialize = (options = {}) => original({ ...options, client_id: CLIENT_ID });
    patched = true;
  };
  patch();
  const timer = setInterval(() => {
    patch();
    if (patched) clearInterval(timer);
  }, 100);
  setTimeout(() => clearInterval(timer), 30000);
})();
