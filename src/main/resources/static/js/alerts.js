(() => {
  let pending = false;
  function show({ title, message, confirmText = 'Aceptar', kind = 'success', cancel = false }) {
    if (pending) return Promise.resolve(false);
    pending = true;
    return new Promise(resolve => {
      const previousFocus = document.activeElement;
      const dialog = document.createElement('dialog');
      dialog.className = `action-alert action-alert-${kind}`;
      dialog.setAttribute('aria-labelledby', 'actionAlertTitle');
      dialog.setAttribute('aria-describedby', 'actionAlertMessage');
      dialog.innerHTML = '<div class="action-alert-icon" aria-hidden="true"></div><h2 id="actionAlertTitle"></h2><p id="actionAlertMessage"></p><div class="action-alert-buttons"><button type="button" class="action-alert-cancel">Cancelar</button><button type="button" class="action-alert-confirm"></button></div>';
      dialog.querySelector('.action-alert-icon').textContent = kind === 'success' ? '✓' : kind === 'danger' ? '!' : '?';
      dialog.querySelector('h2').textContent = title;
      dialog.querySelector('p').textContent = message;
      const cancelButton = dialog.querySelector('.action-alert-cancel');
      const confirmButton = dialog.querySelector('.action-alert-confirm');
      cancelButton.hidden = !cancel;
      confirmButton.textContent = confirmText;
      let result = false;
      confirmButton.addEventListener('click', () => { result = true; dialog.close(); });
      cancelButton.addEventListener('click', () => dialog.close());
      dialog.addEventListener('close', () => {
        dialog.remove();
        pending = false;
        previousFocus?.focus();
        resolve(result);
      }, { once: true });
      // Keep the alert inside Bootstrap's active focus trap when editing a form.
      (document.querySelector('.modal.show') || document.body).append(dialog);
      dialog.showModal();
      (cancel ? cancelButton : confirmButton).focus();
    });
  }
  const confirm = (action = 'save') => {
    const deleting = action === 'delete';
    const editing = action === 'edit';
    const duplicating = action === 'duplicate';
    const logout = action === 'logout';
    return show({
      title: logout ? '¿Cerrar sesión?' : duplicating ? '¿Crear las copias del movimiento?' : deleting ? '¿Eliminar este registro?' : editing ? '¿Guardar los cambios?' : '¿Guardar este registro?',
      message: logout ? 'Tendrás que ingresar tus credenciales para volver.' : duplicating ? 'Se crearán movimientos independientes en el periodo seleccionado.' : deleting ? 'Esta acción no se puede deshacer.' : 'Confirma que los datos son correctos para continuar.',
      confirmText: logout ? 'Sí, cerrar sesión' : duplicating ? 'Sí, crear copias' : deleting ? 'Sí, eliminar' : editing ? 'Sí, guardar cambios' : 'Sí, guardar',
      kind: deleting ? 'danger' : 'confirm', cancel: true
    });
  };
  window.AppAlerts = {
    confirm,
    success: message => show({ title: '¡Proceso completado!', message }),
    error: message => show({ title: 'No se pudo completar', message, kind: 'danger' })
  };
  const approved = new WeakSet();
  const processing = new WeakSet();
  document.addEventListener('submit', async event => {
    const form = event.target;
    if (form.method.toLowerCase() !== 'post') return;
    if (approved.has(form)) { approved.delete(form); return; }
    event.preventDefault();
    if (processing.has(form)) return;
    const path = new URL(form.action).pathname;
    const action = form.dataset.confirmAction || (path.endsWith('/delete') ? 'delete'
      : path.endsWith('/edit') || path.includes('/status/') || path.endsWith('/complete') || form.querySelector('[name="id"]')?.value ? 'edit' : 'save');
    if (!await confirm(action)) return;
    if (!form.reportValidity()) return;
    approved.add(form);
    processing.add(form);
    form.requestSubmit(event.submitter || undefined);
  });
  document.addEventListener('click', async event => {
    const link = event.target.closest('a[href]');
    if (!link) return;
    const url = new URL(link.href);
    if (url.origin !== location.origin || !/\/(delete|complete)$|\/status\//.test(url.pathname)) return;
    event.preventDefault();
    if (await confirm(url.pathname.endsWith('/delete') ? 'delete' : 'edit')) location.assign(link.href);
  });
  const success = document.getElementById('actionFeedback')?.dataset.success;
  if (success) window.AppAlerts.success(success);
})();
