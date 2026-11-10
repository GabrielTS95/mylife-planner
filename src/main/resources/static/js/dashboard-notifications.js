(() => {
  const center = document.getElementById('paymentReminderCenter');
  if (!center) return;

  const reminders = [...center.querySelectorAll('.payment-reminder')];
  if (!reminders.length) return;

  const interval = Number(center.dataset.interval) || 10000;
  let currentIndex = -1;

  const hide = reminder => {
    reminder.classList.remove('is-visible');
    reminder.hidden = true;
  };

  const showNext = () => {
    if (currentIndex >= 0) hide(reminders[currentIndex]);
    currentIndex = (currentIndex + 1) % reminders.length;
    const reminder = reminders[currentIndex];
    reminder.hidden = false;
    requestAnimationFrame(() => reminder.classList.add('is-visible'));
  };

  reminders.forEach(reminder => {
    reminder.querySelector('.payment-reminder-close')?.addEventListener('click', () => hide(reminder));
  });

  window.setTimeout(showNext, 700);
  window.setInterval(showNext, interval);
})();
