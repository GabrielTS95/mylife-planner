document.addEventListener('DOMContentLoaded', () => {
  // =========================================================================
  // 1. Password Visibility Toggle
  // =========================================================================
  const passwordInput = document.getElementById('password');
  const toggleBtn = document.getElementById('togglePassword');
  const toggleIcon = document.getElementById('toggleIcon');
  const toggleText = document.getElementById('toggleText');

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

  // =========================================================================
  // 2. Modals and Tabs Controls
  // =========================================================================
  const infoModalEl = document.getElementById('infoModal');
  const infoModal = infoModalEl && window.bootstrap ? new window.bootstrap.Modal(infoModalEl) : null;
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

  const btnGoogle = document.getElementById('btnGoogle');
  const btnApple = document.getElementById('btnApple');
  const handleSocialClick = (provider) => {
    showInfo(
      `Acceso con ${provider}`,
      'Integración en desarrollo',
      `El inicio de sesión directo con ${provider} estará disponible próximamente. Por favor ingresa con tu usuario o correo y tu contraseña asignada.`
    );
  };
  if (btnGoogle) btnGoogle.addEventListener('click', () => handleSocialClick('Google'));
  if (btnApple) btnApple.addEventListener('click', () => handleSocialClick('Apple ID'));

  // =========================================================================
  // 3. Three.js: Figuras en 3D en Tiempo Real con Movimiento Continuo
  // =========================================================================
  const canvas = document.getElementById('canvas3d');
  const container = document.getElementById('canvas3dContainer');

  if (window.THREE && canvas && container) {
    try {
      const scene = new THREE.Scene();
      const camera = new THREE.PerspectiveCamera(45, container.clientWidth / container.clientHeight, 0.1, 100);
      camera.position.z = 18;

      const renderer = new THREE.WebGLRenderer({
        canvas: canvas,
        alpha: true,
        antialias: true
      });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.setSize(container.clientWidth, container.clientHeight);

      // Iluminación 3D
      const ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
      scene.add(ambientLight);

      const directionalLight = new THREE.DirectionalLight(0x5eead4, 1.2);
      directionalLight.position.set(10, 15, 10);
      scene.add(directionalLight);

      const pointLight = new THREE.PointLight(0x2dd4bf, 1.8, 50);
      pointLight.position.set(-8, -5, 8);
      scene.add(pointLight);

      // Grupo contenedor de figuras 3D
      const shapesGroup = new THREE.Group();
      scene.add(shapesGroup);

      // Figura 1: Torus Principal con reflejo esmeralda/teal
      const torusGeometry = new THREE.TorusGeometry(3.6, 0.95, 32, 80);
      const torusMaterial = new THREE.MeshStandardMaterial({
        color: 0x14b8a6,
        metalness: 0.75,
        roughness: 0.25,
        wireframe: false
      });
      const torus = new THREE.Mesh(torusGeometry, torusMaterial);
      torus.position.set(4, 1.5, -2);
      torus.rotation.set(0.6, 0.4, 0);
      shapesGroup.add(torus);

      // Figura 2: Icosaedro flotante con caras facetadas
      const icoGeometry = new THREE.IcosahedronGeometry(1.6, 0);
      const icoMaterial = new THREE.MeshStandardMaterial({
        color: 0x5eead4,
        metalness: 0.6,
        roughness: 0.3,
        flatShading: true
      });
      const icosahedron = new THREE.Mesh(icoGeometry, icoMaterial);
      icosahedron.position.set(-3.5, 3, -1);
      shapesGroup.add(icosahedron);

      // Figura 3: Octaedro geométrico inferior
      const octaGeometry = new THREE.OctahedronGeometry(1.4, 0);
      const octaMaterial = new THREE.MeshStandardMaterial({
        color: 0x0f766e,
        metalness: 0.8,
        roughness: 0.2
      });
      const octahedron = new THREE.Mesh(octaGeometry, octaMaterial);
      octahedron.position.set(3.2, -4, 0);
      shapesGroup.add(octahedron);

      // Partículas y esferas 3D flotantes en órbita
      const spheres = [];
      const sphereMaterial = new THREE.MeshStandardMaterial({
        color: 0x99f6e4,
        roughness: 0.15,
        metalness: 0.9
      });

      const sphereCoords = [
        { x: -2, y: -2, z: 2, r: 0.4 },
        { x: 1, y: 4, z: 1, r: 0.35 },
        { x: 5.5, y: -1, z: 3, r: 0.5 },
        { x: -4, y: 0.5, z: 1.5, r: 0.3 }
      ];

      sphereCoords.forEach(pos => {
        const geom = new THREE.SphereGeometry(pos.r, 24, 24);
        const mesh = new THREE.Mesh(geom, sphereMaterial);
        mesh.position.set(pos.x, pos.y, pos.z);
        mesh.userData = {
          baseX: pos.x,
          baseY: pos.y,
          speed: 0.5 + Math.random() * 0.8,
          phase: Math.random() * Math.PI * 2
        };
        shapesGroup.add(mesh);
        spheres.push(mesh);
      });

      // Mouse Parallax interactivo para la escena 3D
      let targetMouseX = 0;
      let targetMouseY = 0;
      let currentMouseX = 0;
      let currentMouseY = 0;

      window.addEventListener('mousemove', (e) => {
        const windowWidth = window.innerWidth;
        const windowHeight = window.innerHeight;
        targetMouseX = (e.clientX / windowWidth) * 2 - 1;
        targetMouseY = -(e.clientY / windowHeight) * 2 + 1;
      });

      // Bucle de Renderizado y Movimiento
      let clock = new THREE.Clock();

      const animate3D = () => {
        requestAnimationFrame(animate3D);
        const elapsedTime = clock.getElapsedTime();

        // Rotaciones continuas de las figuras 3D
        torus.rotation.x = elapsedTime * 0.25;
        torus.rotation.y = elapsedTime * 0.35;

        icosahedron.rotation.x = elapsedTime * 0.4;
        icosahedron.rotation.z = elapsedTime * 0.3;
        icosahedron.position.y = 3 + Math.sin(elapsedTime * 1.2) * 0.4;

        octahedron.rotation.y = elapsedTime * 0.5;
        octahedron.rotation.x = elapsedTime * 0.3;
        octahedron.position.y = -4 + Math.cos(elapsedTime * 1.1) * 0.35;

        // Esferas flotando orgánicamente
        spheres.forEach(s => {
          s.position.y = s.userData.baseY + Math.sin(elapsedTime * s.userData.speed + s.userData.phase) * 0.5;
          s.position.x = s.userData.baseX + Math.cos(elapsedTime * (s.userData.speed * 0.8) + s.userData.phase) * 0.3;
        });

        // Suavizado del movimiento del mouse (Damping / Lerp)
        currentMouseX += (targetMouseX - currentMouseX) * 0.05;
        currentMouseY += (targetMouseY - currentMouseY) * 0.05;

        shapesGroup.rotation.y = currentMouseX * 0.4;
        shapesGroup.rotation.x = -currentMouseY * 0.3;

        renderer.render(scene, camera);
      };

      animate3D();

      // Ajuste responsivo al cambiar tamaño de pantalla
      window.addEventListener('resize', () => {
        if (!container) return;
        const w = container.clientWidth;
        const h = container.clientHeight;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
      });
    } catch (err) {
      console.warn('WebGL/Three.js 3D initialization fallback:', err);
    }
  }

  // =========================================================================
  // 4. Inclinación 3D Dinámica e Interactiva de la Tarjeta (Tilt Parallax)
  // =========================================================================
  const card = document.getElementById('loginCard');
  const panel = document.getElementById('panelSection');
  const floatingComp = document.getElementById('floating3dComp');

  if (card && panel) {
    panel.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const cardCenterX = rect.left + rect.width / 2;
      const cardCenterY = rect.top + rect.height / 2;

      const deltaX = (e.clientX - cardCenterX) / (rect.width / 2);
      const deltaY = (e.clientY - cardCenterY) / (rect.height / 2);

      // Inclinación 3D sutil y elegante (hasta 7 grados)
      const rotateX = (-deltaY * 6).toFixed(2);
      const rotateY = (deltaX * 6).toFixed(2);

      card.style.transform = `perspective(1200px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateZ(8px)`;

      // Parallax en contra-perspectiva para la figura 3D lateral
      if (floatingComp) {
        floatingComp.style.transform = `translateY(calc(-50% + ${-deltaY * 12}px)) translateX(${-deltaX * 10}px) rotateY(${deltaX * 5}deg)`;
      }
    });

    panel.addEventListener('mouseleave', () => {
      card.style.transform = 'perspective(1200px) rotateX(0deg) rotateY(0deg) translateZ(0px)';
      if (floatingComp) {
        floatingComp.style.transform = 'translateY(-50%) rotateY(0deg)';
      }
    });
  }
});
