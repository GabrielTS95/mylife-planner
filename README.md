# MyLife Planner

Sistema web personal en Java 21 + Spring Boot + MVC + Thymeleaf + JPA + H2.

## Requisitos
- IntelliJ IDEA
- JDK 21
- Maven (IntelliJ puede usar el Maven Wrapper/embebido del IDE)

## Ejecutar en IntelliJ IDEA
1. Descomprime el proyecto.
2. En IntelliJ: **File > Open** y selecciona la carpeta `mylife-planner` o su `pom.xml`.
3. Espera a que Maven descargue las dependencias.
4. Configura **Project SDK = Java 21**.
5. Ejecuta `MyLifePlannerApplication.java` con el botón Run.
6. Abre: http://localhost:8080

## Base de datos
Se usa H2 local y persistente. El archivo se crea dentro de `./data/`.

Al iniciar, la aplicación actualiza los tipos de movimiento de las bases existentes para admitir ingresos, gastos y ahorros, conservando los registros. Después de actualizar el código, reinicia la aplicación para aplicar esta migración; no es necesario borrar `data/`.

La consola H2 está desactivada. El acceso a las páginas requiere iniciar sesión en `/login`.

## Cuenta personal y relaciones
- Usuario inicial: `erwintorr`. La contraseña acordada se almacena como hash BCrypt; no se guarda en texto plano.
- `app_users` contiene el identificador, el usuario único y el hash de la contraseña.
- `tasks`, `activities`, `goals`, `finance_transactions` y `finance_categories` tienen una clave foránea `user_id` obligatoria hacia `app_users`.
- Al iniciar, los registros anteriores sin propietario se vinculan a la cuenta personal. La inicialización no sobrescribe una cuenta existente ni su contraseña.
- La cuenta inicial administra **Ajustes**. Desde allí crea usuarios, asigna acceso a Home, Movimientos, Metas, Tareas y Mi semana, y activa o desactiva cuentas. No hay registro público.
- Cada usuario tiene sus propios datos. Los permisos se verifican tanto en el menú como en las rutas del servidor, y se aplican a las sesiones existentes.
- Al crear un usuario o restablecer su acceso se genera una contraseña temporal aleatoria, visible una sola vez, válida durante 24 horas. Se exige cambiarla al ingresar; solo se guarda su hash BCrypt.
- Desactivar un usuario o cambiar/restablecer su contraseña invalida sus sesiones anteriores. La cuenta administradora no puede desactivarse desde Ajustes.
- Los formularios y el cierre de sesión usan POST y protección CSRF. La sesión caduca tras 30 minutos de inactividad.
- El tema inicial es claro, independientemente del tema del dispositivo. El botón de tema permite elegir explícitamente modo oscuro para ese navegador.

## Módulos incluidos
- Dashboard
- Mi semana / actividades
- Tareas y prioridades
- Finanzas: ingresos, gastos y balance
- Mi ciclo: día de cobro por usuario, saldo inicial y arrastre, pagos pendientes, vencidos y cuotas mensuales
- Metas financieras
- Ajustes y administración de usuarios

## Control entre cobros

En **Finanzas → Mi ciclo**, configura el día de cobro (28 por defecto). Cada ciclo termina el día anterior al siguiente cobro; los días 29–31 se ajustan al último día de los meses cortos. Puedes consultar ciclos anteriores.

El saldo se calcula con los ingresos, gastos y ahorros que tienen fecha de pago hasta el corte. Los movimientos futuros o sin fecha no se consideran dinero disponible. El sueldo se registra como ingreso cuando se recibe: configurar el día no genera ingresos. Los movimientos ya existentes con fecha se consideran realizados; revisa las copias mensuales que hayas creado como previsión.

Si necesitas empezar desde un saldo conocido, introduce el saldo disponible al inicio de una fecha. Los movimientos anteriores a ella no se vuelven a sumar. Sin fecha inicial se utiliza todo el historial fechado. El saldo que sobra se arrastra automáticamente y no debe volver a registrarse cada mes.

Crea un pago único o entre 2 y 120 cuotas mensuales de igual importe, indicando acreedor, concepto, importe por cuota (incluyendo los cargos acordados) y primer vencimiento. Cada cuota se puede editar o eliminar mientras esté pendiente. El disponible tras compromisos resta los pagos pendientes hasta el final del ciclo, incluidos los vencidos anteriores. Las cuotas posteriores se muestran por separado.

**Registrar pago** crea un único gasto vinculado, con fecha real no futura. Los envíos repetidos del mismo pago no duplican el gasto. **Anular pago** devuelve la cuota a pendiente y elimina ese gasto, permitiendo corregirla. Los gastos vinculados se gestionan desde Mi ciclo; en Movimientos no pueden editarse, duplicarse ni eliminarse por separado. Los estados de ciclos anteriores se calculan al corte de ese ciclo.

Las nuevas tablas y columnas se crean al reiniciar mediante la actualización del esquema; los movimientos y usuarios existentes se conservan. Cada usuario solo accede a su configuración y pagos, con el permiso de Finanzas.

## Arquitectura de código
Controller -> Service -> Repository -> Database
View: Thymeleaf + Bootstrap
