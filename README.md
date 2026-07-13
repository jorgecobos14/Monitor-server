# Poco Monitor

APK en Kotlin que reporta métricas del Poco X3 GT al backend en Fly.io cada N segundos.

## Compilar (GitHub Actions)

1. Sube esta carpeta como repo a GitHub (`git init`, `git add .`, `git commit`, `git push`).
2. En cuanto hagas push a `main`, la Action **Build APK** corre sola.
3. Ve a la pestaña **Actions** del repo → el run más reciente → sección **Artifacts** → descarga `poco-monitor-debug`, que trae el `app-debug.apk`.
4. Pásalo al Poco (por Termux, cable, lo que uses) e instálalo. Vas a necesitar permitir "instalar apps de orígenes desconocidos" si no lo tienes activado.

## Configurar la app

Al abrirla te va a pedir permisos de:
- Ubicación (solo se usa para poder leer el nombre de la red WiFi conectada, requisito de Android — no guarda ni manda coordenadas GPS)
- Bluetooth (para contar dispositivos pareados)
- Notificaciones (para el aviso permanente del servicio corriendo)

Luego llena:
- **URL del servidor**: `https://poco-status.fly.dev`
- **Token**: el mismo valor que pusiste en `fly secrets set REPORT_TOKEN="..."` del backend
- **Intervalo**: en segundos (recomendado 3-5, ya que le dijiste 2-5)

Dale **Iniciar monitoreo**. Va a quedar corriendo como servicio en primer plano (notificación fija) y se reactiva solo si el teléfono se reinicia.

## Nota sobre batería

Android puede matar el servicio si detecta uso agresivo de batería. Si notas que se detiene solo después de un rato, ve a Ajustes → Batería → Poco Monitor → Sin restricciones (o el equivalente en tu versión de MIUI/HyperOS, que suele ser más agresivo matando procesos en segundo plano que Android puro).
