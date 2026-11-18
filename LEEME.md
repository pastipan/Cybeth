# Cybeth SG para Android: cómo instalarla

Cybeth SG te avisa cuando te llega plata o cuando envías plata por Nequi, Bancolombia, Daviplata y otros bancos. La puede decir en voz alta: *"Te llegaron 50.000 pesos de Laura por Nequi"*.

**Lo que registra:** solo transferencias, las que recibes y las que envías (Nequi a Nequi, Nequi a otro banco, Bre-B, entre bancos).
**Lo que ignora sin avisar:** compras con tarjeta, pagos PSE, pagos en línea, facturas, recargas y retiros.

Hay dos formas de detectar las transferencias:
1. **Notificaciones del celular:** la notificación de Nequi o de tu banco cuando entra o sale plata. Es instantáneo y sirve aunque el banco no mande correo.
2. **Correo:** revisa cada 15 minutos los correos que mandan los bancos.

Todo se guarda solo en tu celular.

---

## Paso 1. Instalar Android Studio en el computador (una sola vez)

1. Descárgalo gratis en **developer.android.com/studio** e instálalo con las opciones que trae por defecto.
2. Ábrelo y deja que termine de descargar lo que pide la primera vez. Puede tardar un rato.

## Paso 2. Abrir el proyecto

1. Descomprime el archivo **CuadraAndroid.zip**.
2. En Android Studio, entra a **File → Open** (o "Open" en la pantalla de inicio) y elige la carpeta **CuadraAndroid**.
3. Espera a que abajo diga que terminó de sincronizar ("Gradle sync"). La primera vez descarga varias cosas y tarda unos minutos.

## Paso 3. Pasarla al celular

**Opción A: con cable (recomendada)**
1. En el celular, entra a **Ajustes → Acerca del teléfono** y toca **7 veces** "Número de compilación". Así se activan las "Opciones de desarrollador".
2. Entra a **Ajustes → Sistema → Opciones de desarrollador** y activa **Depuración USB**.
3. Conecta el celular al computador con el cable. En el celular, acepta "¿Permitir depuración USB?".
4. En Android Studio, arriba aparece el nombre de tu celular. Toca el botón verde **▶ (Run)**. Cybeth SG se instala y se abre sola.

**Opción B: sin cable (con el archivo APK)**
1. En Android Studio, entra a **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
2. Cuando termine, toca **"locate"**. Ese es el archivo `app-debug.apk`.
3. Pásalo al celular (por Drive, correo o WhatsApp a ti mismo) y ábrelo. Android te pedirá permitir "instalar apps desconocidas".
4. **Importante en Android 13 o más nuevo:** si al dar el permiso del paso 4 te sale "Configuración restringida", ve a **Ajustes → Aplicaciones → Cybeth SG → ⋮ (arriba a la derecha) → Permitir configuración restringida** y vuelve a intentar.

## Paso 3b. Crear tu clave

La primera vez que abras Cybeth SG te pide **crear una clave de 4 dígitos**, como en Nequi. Si tu celular tiene huella, te pregunta si quieres entrar con ella.
- La clave se pide cada vez que abres la app y cuando vuelves después de 30 segundos.
- Después de 5 intentos fallidos, la app se bloquea un minuto.
- Por privacidad, la app no deja tomar pantallazos y no se ve en "apps recientes".
- Si olvidas la clave: "¿Olvidaste tu clave?" borra los datos de Cybeth para empezar de nuevo. Tus bancos y tu Nequi no se tocan.

## Paso 4. Dar los permisos (en el celular)

Al abrir Cybeth SG:
1. Toca **"Activar ahora"**. Se abre una lista: busca **Cybeth SG**, actívalo y acepta. Esto deja que Cybeth SG lea las notificaciones de los bancos.
2. Entra a **Ajustes** (dentro de Cybeth SG) y toca **"Permitir notificaciones de Cybeth SG"**.
3. En **"Que Android no la cierre"**, toca **Abrir**, busca Cybeth SG y elige **"No optimizar"** o **"Sin restricciones"**. Así sigue funcionando aunque no la abras.
4. Revisa que en Nequi y en tus bancos estén **activadas las notificaciones** del celular.

## Paso 5. Enlazar el correo (opcional)

Gmail no deja usar tu clave normal. Se usa una **contraseña de aplicación**:
1. Activa la **verificación en 2 pasos** en tu cuenta de Google, si no la tienes.
2. Entra a **myaccount.google.com/apppasswords**.
3. En nombre escribe **Cybeth SG** y toca **Crear**.
4. Google te muestra **16 letras**. Cópialas.
5. En Cybeth SG, entra a **Ajustes → Correo electrónico**, escribe tu Gmail, pega las 16 letras y toca **Enlazar correo**.

Cybeth SG revisa los últimos 3 días y desde ahí sigue revisando cada 15 minutos. Solo lee, nunca envía ni borra nada. Si algún día quieres cortar el acceso, borra esa contraseña en la misma página de Google.

## Paso 5b. Avisar por correo (por ejemplo a la jefe con iPhone)

1. Primero enlaza tu Gmail (paso 5). Los avisos salen desde ese correo.
2. En **Ajustes → Avisar por correo**, escribe el correo de la persona (puedes poner varios, separados por coma) y toca **Guardar**.
3. Toca **Enviar correo de prueba** y pídele que revise su bandeja de entrada. Si no lo ve, que busque en *Spam* y lo marque como "No es spam".

Desde ahí, cada transferencia que llegue le manda un correo con el monto, quién la envió y a qué banco llegó.
**Importante:** el celular Android con Cybeth debe tener instalado el Nequi (o el banco) **donde llega la plata**, y estar prendido y con internet.

## Paso 6. Probar

- Pídele a alguien que te mande **$1.000 por Nequi**. Debe sonar "Te llegaron mil pesos…" y aparecer en Inicio.
- O, en **Ajustes → Probar un mensaje**, pega el texto de una notificación o correo de tu banco y mira cómo lo entiende.
- Si una transferencia **no aparece**, mira en Ajustes la línea "Última notificación de banco vista". Si ahí no sale tu banco, falta el permiso del paso 4 o el banco tiene las notificaciones apagadas.

---

### Si Android Studio muestra un error al compilar
Toma un pantallazo del mensaje rojo y mándamelo para corregirlo.

### Si lo subes a GitHub
El proyecto trae `.github/workflows/build-apk.yml`. Si subes la carpeta a un repositorio de GitHub, el APK se compila solo y lo descargas en la pestaña **Actions → Artifacts**.
