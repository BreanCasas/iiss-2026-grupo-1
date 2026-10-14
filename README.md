# IoTEste EcoWarm

Taller de Ingeniería de Software - Iteración 4 - Java 25.

Control de calefacción por habitaciones mediante MQTT y switches REST.

## Evolución y estructura

La iteración 4 conserva la recepción MQTT, el control REST y la persistencia
de las entregas anteriores y organiza el controlador en core y engine.

| Ubicación | Contenido |
|---|---|
| modules/core/ | Modelo y reglas de decisión, con tests |
| modules/engine/ | API REST, suscriptor MQTT, reloj, MongoDB y switches |
| pom.xml | Proyecto Maven padre |
| docker/docker-compose.yml | Controlador y MongoDB |
| docker/control.Dockerfile | Construcción y ejecución del controlador |
| scripts/ | Construcción, arranque, parada y tests |
| docs/api/openapi.yaml | Contrato estandarizado de la API |
| docs/tests/criterio-tests.md | Criterio de adecuación y resultados |
| docs/arquitectura/4mas1.md | Vista lógica y secuencias |
| .github/workflows/maven.yml | Integración continua |

Engine depende de core.

Core no utiliza Spring ni comunicaciones externas.

API y suscriptor MQTT se ejecutan juntos en el contenedor control.

El simulador docente aporta broker, termostatos, switches e inventario.

Reemplaza al generator y al switch-stub propios para la integración
de esta entrega.

## Requisitos y configuración

- Docker y Docker Compose.
- Java 25 para el entorno probado.
- ecowarm-check.jar del docente.
- Git Bash para scripts en Windows.
- curl para scripts/api-example.sh.

Conservar los valores existentes de .env.

Variables utilizadas:

```env
MONGODB_DATABASE=ioteste
IOTESTE_API_KEY=reemplazar-por-tu-clave
ECOWARM_ENVIRONMENT_KEY=
```

Compose conecta a mongodb://mongodb:27017 y utiliza:

- ECOWARM_ENVIRONMENT_URL: http://host.docker.internal:9090
- ECOWARM_PUBLIC_URL: http://localhost:8080
- ECOWARM_LABEL: grupo-1

Estas direcciones corresponden a checker en Windows
y control en Docker Desktop.

## Arranque

Desde la raíz, iniciar el checker y dejarlo abierto:

```powershell
java -jar ".\ecowarm-check.jar" --anunciar host.docker.internal
```

En otra terminal:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
docker compose --env-file .env -f docker/docker-compose.yml ps
docker compose --env-file .env -f docker/docker-compose.yml logs -f control
```

Puertos:

- MQTT: 1883.
- REST del checker: 9090.
- API del controlador: 8080.

El controlador se registra y obtiene la dirección del broker.

Si se reinicia el checker después del registro:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml restart control
```

## API REST

Dirección: http://localhost:8080.

Encabezado requerido: X-API-Key.

| Método | Endpoint | Función |
|---|---|---|
| GET | /sitio | Consultar inventario |
| PUT | /sitio | Validar y reemplazar configuración |
| GET | /control | Consultar estado y tiempo virtual |
| POST | /control/comandos | INICIAR o DETENER |

- Clave ausente o incorrecta: 401.
- Inventario inválido: 400.
- Sitio todavía no cargado: 404.

Los errores gestionados incluyen timestamp, status y mensaje.

### Encabezados en PowerShell

```powershell
$keyLine = Get-Content .env |
    Where-Object { $_ -match '^\s*IOTESTE_API_KEY\s*=' } |
    Select-Object -First 1

$apiKey = ($keyLine -split '=', 2)[1].Trim().Trim('"').Trim("'")
$headers = @{ "X-API-Key" = $apiKey }
```

### Consultar

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/control" -Headers $headers | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/sitio" -Headers $headers | ConvertTo-Json -Depth 10
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

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/control/comandos" -Headers $headers -ContentType "application/json" -Body $inicio | ConvertTo-Json
```

### Detener

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/control/comandos" -Headers $headers -ContentType "application/json" -Body '{"accion":"DETENER"}' | ConvertTo-Json
```

DETENER cambia el estado.
Los siguientes ciclos intentan apagar los switches.

## Decisión y reglas

HeatingController.decide recibe configuración, hora y estado.

Devuelve una Decision con acciones OFF antes de ON.

- Selecciona habitaciones por debajo del objetivo, fuera de punta.
- Respeta la potencia máxima del sitio.
- Prioriza mayor déficit y desempata por ID.
- No selecciona habitaciones sin temperatura disponible.
- Evita comandos si el switch ya tiene el estado correcto.
- Para estado desconocido ordena OFF antes de un eventual ON.

Engine reúne lecturas y estados, consulta al core y ejecuta los comandos.

Confirma apagados antes de nuevos encendidos.

## Inventario y persistencia

PUT /sitio valida y guarda inventario nuevo y apagados pendientes
en un mismo documento antes de actualizar la memoria.

La petición no llama a switches.

HeatingEngine procesa pendientes aunque el control esté detenido.

Si falla un apagado, conserva el trabajo y vuelve a intentar.

Antes de ON comprueba revisión y ejecución vigentes.

MongoDB conserva:

- site_config, documento _id active: inventory y pendingShutdowns.
- temperature_readings: histórico de lecturas válidas.

Inventario y pendientes se recuperan al arrancar.

La última temperatura se conserva en memoria.
Se espera una nueva lectura MQTT tras reiniciar.

Si falla el guardado, la temperatura sigue disponible en memoria.
Esa escritura no tiene reintentos automáticos.

El histórico no se expone mediante los antiguos endpoints /rooms.

Estado de ejecución y reloj están en memoria.
El controlador arranca detenido.

MongoDB utiliza el volumen mongodb-data.

## Timestamp de las lecturas

sourceTs y payload.ts de las lecturas nuevas utilizan BSON Int64
en milisegundos desde Unix epoch.

sourceTsUnit indica epoch_milliseconds.

Los segundos decimales del protocolo se leen exactamente como BigDecimal
y se normalizan a long sin pasar por double.

Los timestamps nuevos no se redondean.
Se rechazan valores fuera de rango o con fracción inferior al milisegundo.

receivedAt sigue siendo la fecha real de recepción, no la hora virtual.

Actualizar el código no convierte automáticamente los documentos antiguos.

## Tiempo virtual

El reloj y factorTiempo conservan su implementación y contrato numérico:

instante inicial + tiempo real transcurrido × factor

Con factor 60, un segundo real representa un minuto virtual.

El factor es positivo y vale 1 si se omite.

INICIAR resincroniza y conserva el desplazamiento recibido.

La tarifa se evalúa con esa hora local.
GET /control muestra el instante en UTC.

El desplazamiento permanece fijo hasta otro INICIAR.

La franja incluye el inicio y excluye el final: [desde, hasta).

HABILES significa lunes a viernes.
TODOS incluye todos los días.

Para probar entrada en punta a las 17:00,
iniciar a las 16:58 con factor 10.

Se alcanza el inicio aproximadamente 12 segundos reales después.

El factor no acelera por sí mismo la simulación térmica del checker.

## Construcción y pruebas

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

El Dockerfile construye con -DskipTests.
Los tests se ejecutan por separado.

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| core | 23 | 0 | 0 | 0 |
| engine | 43 | 0 | 0 | 0 |
| Total | 66 | 0 | 0 | 0 |

Resultado de las pruebas con Java 25: BUILD SUCCESS.

## Checker y validación

Validación realizada el 11 de octubre de 2026, hora de Uruguay:

- Checker: 8/8 comprobaciones aprobadas.
- Comandos a switches: 18.
- Consultas de estado: 204.
- Suite Maven: 66 tests, sin fallos, errores ni pruebas omitidas.
- MongoDB: las dos lecturas más recientes verificadas tenían sourceTs
  y payload.ts de tipo long, con unidad epoch_milliseconds.

La cantidad de comandos y consultas depende de la duración de la ejecución.

La comprobación de tipos corresponde a las lecturas nuevas inspeccionadas.
No implica que se haya migrado el histórico anterior.

Para ver el veredicto, presionar Ctrl+C en la terminal del checker.

## Integración continua

.github/workflows/maven.yml utiliza Java 25 y ejecuta:

```bash
mvn -B --no-transfer-progress clean verify
```

Se activa por push a main o iteracion-4,
pull request hacia main o ejecución manual.

Guarda reportes Surefire.

Los tests no requieren MongoDB ni checker activos.

## Detener el entorno

```powershell
docker compose --env-file .env -f docker/docker-compose.yml stop

docker compose --env-file .env -f docker/docker-compose.yml down
```

stop detiene.

down elimina contenedores y red conservando el volumen.

No utilizar down -v si se quieren conservar los datos.

El checker se detiene aparte con Ctrl+C.

## Documentación y metodología

- docs/vision.md
- docs/metodologia.md
- docs/producto/
- docs/api/openapi.yaml
- docs/tests/criterio-tests.md
- docs/arquitectura/4mas1.md

La metodología es Scrumban, con planificación,
seguimiento en Jira y revisiones semanales.