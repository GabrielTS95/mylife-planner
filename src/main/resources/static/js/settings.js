document.getElementById('copyTemporaryPassword')?.addEventListener('click', async () => {
  const input = document.getElementById('temporaryPassword');
  const status = document.getElementById('copyStatus');
  try {
    await navigator.clipboard.writeText(input.value);
    status.textContent = 'Contraseña copiada.';
  } catch (error) {
    input.select();
    status.textContent = 'Seleccionada. Copia la contraseña con Ctrl+C o el menú de tu dispositivo.';
  }
});
document.querySelectorAll('.reset-password-form').forEach(form => {
  form.addEventListener('submit', event => {
    if (!confirm('Se reemplazará la contraseña actual y se cerrarán las sesiones de este usuario. ¿Continuar?')) event.preventDefault();
  });
});
