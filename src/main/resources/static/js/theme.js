(function () {
  let theme = 'light';
  try {
    if (localStorage.getItem('mylife-planner-theme-v2') === 'dark') theme = 'dark';
  } catch (ignored) {}
  document.documentElement.setAttribute('data-theme', theme);
})();
