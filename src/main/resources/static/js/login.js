document.addEventListener('DOMContentLoaded', () => {
  const passwordInput = document.getElementById('password');
  const toggleBtn = document.getElementById('togglePassword');
  const toggleIcon = document.getElementById('toggleIcon');
  const toggleText = document.getElementById('toggleText');

  // Password visibility toggle
  if (toggleBtn && passwordInput) {
    toggleBtn.addEventListener('click', () => {
      const isPassword = passwordInput.type === 'password';
      passwordInput.type = isPassword ? 'text' : 'password';
      toggleText.textContent = isPassword ? 'Ocultar' : 'Mostrar';
      toggleBtn.setAttribute('aria-label', isPassword ? 'Ocultar contraseña' : 'Mostrar contraseña');
      toggleBtn.setAttribute('aria-pressed', String(isPassword));
      if (toggleIcon) {
        toggleIcon.className = isPassword ? 'bi bi-eye-slash me-1' : 'bi bi-eye me-1';
      }
    });
  }

  // Modal helpers
  const infoModalEl = document.getElementById('infoModal');
  const infoModal = infoModalEl ? new bootstrap.Modal(infoModalEl) : null;
  const modalTitle = document.getElementById('infoModalTitle');
  const modalSubtitle = document.getElementById('infoModalSubtitle');
  const modalText = document.getElementById('infoModalText');

  const showInfo = (title, subtitle, text) => {
    if (!infoModal) return;
    if (modalTitle) modalTitle.textContent = title;
    if (modalSubtitle) modalSubtitle.textContent = subtitle;
    if (modalText) modalText.textContent = text;
    infoModal.show();
  };

  // Register Tab
  const tabRegister = document.getElementById('tabRegister');
  const tabLogin = document.getElementById('tabLogin');
  if (tabRegister) {
    tabRegister.addEventListener('click', () => {
      showInfo(
        'Creación de Cuenta',
        'Acceso Privado Administrado',
        'El registro en MyLife Planner es gestionado directamente por el Administrador. Si necesitas un usuario, solicítale al administrador que te registre y configure tus módulos de acceso.'
      );
    });
  }
  if (tabLogin) {
    tabLogin.addEventListener('click', () => {
      tabLogin.classList.add('active');
      if (tabRegister) tabRegister.classList.remove('active');
    });
  }

  // Forgot Password
  const forgotBtn = document.getElementById('forgotBtn');
  if (forgotBtn) {
    forgotBtn.addEventListener('click', (e) => {
      e.preventDefault();
      showInfo(
        'Recuperación de Contraseña',
        'Contraseñas Temporales Seguras',
        'Por seguridad de los datos personales, las contraseñas temporales son emitidas por el Administrador del sistema y tienen una vigencia de 24 horas. Comunícate con el administrador para restablecer tu acceso.'
      );
    });
  }

  // Social Login Buttons
  const btnGoogle = document.getElementById('btnGoogle');
  const btnApple = document.getElementById('btnApple');
  const handleSocialClick = (provider) => {
    showInfo(
      `Acceso con ${provider}`,
      'Integración en desarrollo',
      `El inicio de sesión directo con ${provider} estará disponible próximamente. Por favor ingresa con tu usuario o correo y tu contraseña asignada.`
    );
  };

  if (btnGoogle) {
    btnGoogle.addEventListener('click', () => handleSocialClick('Google'));
  }
  if (btnApple) {
    btnApple.addEventListener('click', () => handleSocialClick('Apple ID'));
  }
});
