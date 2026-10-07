# IoTEste EcoWarm

Taller de Ingeniería de Software — Iteración 4 — Java 25.

Control de calefacción por habitaciones mediante temperaturas recibidas
por MQTT y switches consultados y comandados mediante REST.

## Iteraciones

- Iteración 1: prototipo de mensajería MQTT.
- Iteración 2: generador de eventos, persistencia y build reproducible.
- Iteración 3: API REST, MongoDB y simulación de switches.
- Iteración 4: separación entre core y engine, restricciones de potencia,
  tarifa punta, tiempo virtual e integración con el entorno externo.

## Estructura

| Ubicación | Contenido |
|---|---|
| `pom.xml` | Proyecto Maven padre |
| `modules/core/` | Reglas de decisión y pruebas unitarias |
| `modules/engine/` | API REST, tiempo virtual, MongoDB y comunicaciones |
| `docker/control.Dockerfile` | Compilación y ejecución del controlador |
| `docker/docker-compose.yml` | Controlador y MongoDB |
| `scripts/` | Compilación, arranque, parada y pruebas |
| `docs/api/openapi.yaml` | Contrato oficial de la API |
| `docs/tests/criterio-tests.md` | Criterio de adecuación y resultados |
| `docs/arquitectura/4mas1.md` | Vista lógica y escenarios |
| `.github/workflows/maven.yml` | Integración continua |

Engine depende de core. Core no depende de Spring, MongoDB, MQTT
ni servicios REST.

El checker del docente proporciona el broker MQTT, los termostatos,
los switches y el inventario del entorno de prueba.

## Requisitos

- Docker y Docker Compose.
- Java 25 para ejecutar el checker en el entorno probado.
- `ecowarm-check.jar`, suministrado por el docente.
- Git Bash para ejecutar los scripts en Windows.
- `curl` para el script de ejemplo de la API.

La compilación mediante los scripts utiliza Maven y Java dentro de Docker.

## Configuración

Crear `.env` en la raíz:

```env
MONGODB_DATABASE=ioteste
IOTESTE_API_KEY=reemplazar-por-tu-clave
ECOWARM_ENVIRONMENT_KEY=
```

| Variable | Uso |
|---|---|
| `MONGODB_DATABASE` | Base de datos del controlador |
| `IOTESTE_API_KEY` | Clave de acceso a la API del controlador |
| `ECOWARM_ENVIRONMENT_KEY` | Clave del entorno externo, si la requiere |

El archivo `.env` no se versiona.

Compose configura MongoDB mediante `mongodb://mongodb:27017`
y utiliza estas direcciones:

| Variable | Valor |
|---|---|
| `ECOWARM_ENVIRONMENT_URL` | `http://host.docker.internal:9090` |
| `ECOWARM_PUBLIC_URL` | `http://localhost:8080` |
| `ECOWARM_LABEL` | `grupo-1` |

Estas direcciones corresponden al escenario probado:
checker en Windows y controlador en Docker Desktop.

## Arranque

Ejecutar desde la raíz del proyecto.

Primero, iniciar el checker y dejar la terminal abierta:

```powershell
java -jar ".\ecowarm-check.jar" --anunciar host.docker.internal
```

El checker utiliza los puertos 1883 para MQTT y 9090 para REST.

En otra terminal, iniciar el controlador y MongoDB:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
```

Consultar contenedores y logs:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml ps
docker compose --env-file .env -f docker/docker-compose.yml logs -f control
```

El controlador se registra ante el entorno y obtiene la dirección
del broker MQTT.

Si se reinicia el checker después del registro, reiniciar el controlador:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml restart control
```

## API REST

Dirección: `http://localhost:8080`.

Las solicitudes requieren el encabezado `X-API-Key`.

| Método | Endpoint | Función |
|---|---|---|
| GET | `/sitio` | Consultar el inventario activo |
| PUT | `/sitio` | Validar y reemplazar el inventario completo |
| GET | `/control` | Consultar estado y tiempo virtual |
| POST | `/control/comandos` | Ejecutar INICIAR o DETENER |

- Clave ausente o incorrecta: HTTP 401.
- Inventario inválido: HTTP 400; conserva la configuración anterior.
- Consulta de sitio sin inventario cargado: HTTP 404.

Los errores manejados por la API incluyen `timestamp`, `status`
y `mensaje`. El timestamp utiliza tres decimales de segundo.

### Encabezados en PowerShell

Ejecutar en la raíz:

```powershell
$keyLine = Get-Content .env |
    Where-Object { $_ -match '^\s*IOTESTE_API_KEY\s*=' } |
    Select-Object -First 1

$apiKey = ($keyLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
$headers = @{ "X-API-Key" = $apiKey }
```

Las variables quedan disponibles en esa sesión de PowerShell.

### Consultas

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/control" -Headers $headers |
    ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/sitio" -Headers $headers |
    ConvertTo-Json -Depth 10
```

### Iniciar o resincronizar

```powershell
$inicio = @'
{
  "accion": "INICIAR",
  "fechaHora": "2026-10-05T12:00:00-03:00",
  "factorTiempo": 1
}
'@

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/control/comandos" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $inicio |
    ConvertTo-Json
```

INICIAR establece el instante virtual, el desplazamiento horario y
el factor. Si el control ya está en ejecución, resincroniza el reloj.
Si se omite el factor, se utiliza 1.

### Detener

```powershell
Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/control/comandos" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body '{"accion":"DETENER"}' |
    ConvertTo-Json
```

DETENER cambia inmediatamente el estado a DETENIDO.
El siguiente ciclo intenta apagar los switches activos.
Si un apagado falla, se reintenta en ciclos posteriores.

## Actualización del inventario

Cuando cambia la configuración, PUT `/sitio`:

1. Valida el inventario recibido.
2. Agrega los switches anteriores a los apagados pendientes.
3. Guarda el inventario nuevo y los pendientes en un mismo documento.
4. Actualiza la configuración en memoria y responde.

La petición no realiza llamadas REST a los switches.

HeatingEngine procesa los apagados pendientes en ciclos posteriores,
incluso si el control está detenido. No aplica nuevos encendidos
hasta confirmar todos los apagados de esa transición.

Si un switch no responde, conserva los pendientes y reintenta.
Los pendientes se recuperan después de reiniciar el controlador.

Un inventario idéntico al activo no se vuelve a guardar ni agrega
apagados pendientes.

Si falla la persistencia, no se modifica la configuración en memoria.

## Reglas de calefacción

- Seleccionar habitaciones con temperatura inferior al objetivo.
- No seleccionar habitaciones durante tarifa punta.
- Seleccionar dentro de la potencia contratada.
- Priorizar el mayor déficit de temperatura.
- Resolver empates por identificador de habitación.
- Confirmar los apagados necesarios antes de enviar nuevos encendidos.

Las habitaciones sin temperatura disponible no se seleccionan.

## Tiempo virtual y tarifa punta

El reloj calcula:

`instante inicial + tiempo real transcurrido × factor`

Con factor 60, un segundo real representa un minuto virtual.

La franja punta incluye el inicio y excluye el final: `[desde, hasta)`.

- HABILES: lunes a viernes.
- TODOS: todos los días.

La tarifa se evalúa con la hora local y el desplazamiento recibido
en INICIAR. Por ejemplo, 17:00-03:00 se evalúa como las 17:00 locales.

GET `/control` representa el instante virtual en UTC, con Z.
El desplazamiento para evaluar la tarifa permanece fijo hasta
el siguiente INICIAR. No se aplican cambios de horario estacional.

Para probar la entrada en punta a las 17:00, iniciar a las 16:58
con factor 10: el cambio ocurre aproximadamente 12 segundos reales después.

Acelerar este reloj no acelera por sí mismo la simulación térmica
del checker.

## Persistencia

MongoDB guarda el inventario y los apagados pendientes en
la colección `site_config`, documento con `_id: active`.

Ambos se recuperan al arrancar.

Las temperaturas, el estado de ejecución y el reloj virtual se
mantienen en memoria. El controlador arranca detenido hasta recibir INICIAR.

MongoDB conserva los datos en el volumen `mongodb-data`.

## Compilación y pruebas

Desde PowerShell:

```powershell
& "C:\Program Files\Git\bin\bash.exe" ./scripts/build.sh
& "C:\Program Files\Git\bin\bash.exe" ./scripts/test.sh
& "C:\Program Files\Git\bin\bash.exe" ./scripts/api-example.sh
```

Desde Linux o Git Bash:

```bash
bash scripts/build.sh
bash scripts/test.sh
bash scripts/api-example.sh
```

Con Java 25 y Maven locales:

```bash
mvn -B clean verify
```

Maven compila primero core y después engine.

El Dockerfile construye la imagen con `-DskipTests`.
Los tests se ejecutan por separado mediante `test.sh` o Maven.

## Resultados comprobados

Validación del 6 de octubre de 2026, hora de Uruguay:

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| core | 15 | 0 | 0 | 0 |
| engine | 34 | 0 | 0 | 0 |
| Total | 49 | 0 | 0 | 0 |

Resultado: `BUILD SUCCESS`.

Checker sobre el controlador reconstruido:

- Veredicto: 8/8.
- PUT `/sitio`: HTTP 200.
- GET `/sitio`, GET `/control` e INICIAR: HTTP 200.
- Registro y suscripciones MQTT confirmados.
- 11 comandos y 88 consultas a switches.

En ejecuciones anteriores se comprobaron manualmente el corte a
las 17:00 y la restitución a las 23:00 con desplazamiento -03:00,
la temperatura objetivo y la selección con potencia limitada.

El checker verifica interoperabilidad en los escenarios ejecutados.
El criterio de adecuación y los límites de la validación se encuentran
en `docs/tests/criterio-tests.md`.

Para obtener el veredicto del checker, pulsar Ctrl+C en su terminal.

## Integración continua

El workflow `.github/workflows/maven.yml` configura Java 25 y ejecuta:

```bash
mvn -B --no-transfer-progress clean verify
```

Se activa con push a main o iteracion-4, pull request hacia main,
o ejecución manual. Guarda los reportes de Surefire.

Los tests actuales no requieren un MongoDB ni un checker activos.
La configuración del workflow no implica que ya haya sido ejecutado
en GitHub para los cambios locales.

## Detener el entorno

Detener contenedores:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml stop
```

Eliminar contenedores y red conservando el volumen:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml down
```

Los scripts equivalentes son `scripts/stop.sh` y `scripts/down.sh`.
El checker se detiene por separado con Ctrl+C.

## Documentación y gestión

- Visión: `docs/vision.md`.
- Metodología: `docs/metodologia.md`.
- Producto: `docs/producto/`.
- API: `docs/api/openapi.yaml`.
- Pruebas: `docs/tests/criterio-tests.md`.
- Arquitectura: `docs/arquitectura/4mas1.md`.

La metodología definida es Scrumban, con planificación al inicio
de la iteración, seguimiento en el Board de Jira y revisiones semanales.