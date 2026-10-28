# IoTEste EcoWarm

Taller de Ingeniería de Software - Iteración 5 - Java 25.

Control de calefacción por habitaciones mediante MQTT y switches REST.

## Evolución y estructura

El controlador conserva la separación en core y engine. La iteración 5
incorpora mejoras de calidad, soporte y pruebas de tiempo acelerado,
vencimiento de lecturas y verificación de recuperación de comunicaciones.

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
| docs/arquitectura/4mas1.md | Vistas de arquitectura 4+1 y secuencias |
| .github/workflows/maven.yml | Integración continua |

Engine depende de core.

Core no utiliza Spring ni comunicaciones externas.

API y suscriptor MQTT se ejecutan juntos en el contenedor control.

El simulador docente aporta broker, termostatos, switches e inventario.

Reemplaza al generator y al switch-stub propios para la integración
de esta entrega.

## Requisitos y configuración

- Docker y Docker Compose.
- Docker Desktop activo para ejecutar la suite completa en Windows.
- Java 25 para el entorno probado.
- ecowarm-check.jar del docente.
- Git Bash para scripts en Windows.
- curl para scripts/api-example.sh.

Las pruebas de integración MQTT utilizan Testcontainers para crear
brokers Mosquitto temporales. No requieren que el checker ni MongoDB
estén ejecutándose.

La primera ejecución necesita descargar las dependencias Maven y las
imágenes de Docker utilizadas por las pruebas.

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

Si se reinicia el checker después del registro y se necesita repetir
el registro y la carga inicial del entorno:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml restart control
```

Este reinicio no es necesario para recuperar únicamente la conexión
MQTT: la reconexión y las suscripciones se restablecen automáticamente.

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

Confirma apagados antes de nuevos encendidos. Si un apagado falla,
considera el posible consumo de esa habitación antes de permitir
encendidos que puedan superar la potencia máxima.

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

Si falla el guardado, la temperatura sigue disponible en memoria
hasta que venza su vigencia.
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

INICIAR resincroniza el reloj con el instante recibido.

El sitio tiene una única zona horaria: America/Montevideo (UTC-03:00,
sin cambio de horario). Se puede cambiar con la variable ECOWARM_ZONE.

La franja de tarifa se evalúa siempre en esa zona. El offset con que llegue
fechaHora no cambia la hora local: 17:00-03:00 y 20:00Z son el mismo instante
y se interpretan como las 17:00 de Uruguay. En cambio 17:00Z son las 14:00.

GET /control muestra el mismo instante en UTC.

La franja incluye el inicio y excluye el final: [desde, hasta).

HABILES significa lunes a viernes.
TODOS incluye todos los días.

Para probar entrada en punta a las 17:00,
iniciar a las 16:58 con factor 10.

Se alcanza el inicio aproximadamente 12 segundos reales después.

El factor no acelera por sí mismo la simulación térmica del checker.

El ciclo de control se despierta justo en cada cambio de franja, aunque el
tiempo esté acelerado, y también apenas se inicia o detiene el control.
Cada habitación se consulta y se comanda por separado, de modo que un
switch sin respuesta no impide apagar los demás.

## Construcción y pruebas

Desde PowerShell con Docker Desktop activo:

```powershell
& "C:\Program Files\Git\bin\bash.exe" ./scripts/build.sh
& "C:\Program Files\Git\bin\bash.exe" ./scripts/test.sh
& "C:\Program Files\Git\bin\bash.exe" ./scripts/api-example.sh
```

Desde Git Bash con Docker Desktop activo:

```bash
bash scripts/build.sh
bash scripts/test.sh
bash scripts/api-example.sh
```

Con Java 25 y Maven locales, y un motor Docker accesible para
Testcontainers:

```bash
mvn -B clean verify
```

Para ejecutar la suite desde PowerShell mediante Docker:

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount "type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --workdir /workspace maven:3.9-eclipse-temurin-25 mvn -B clean verify
```

scripts/test.sh ejecuta clean verify y proporciona al contenedor Maven
acceso al socket de Docker. TESTCONTAINERS_HOST_OVERRIDE indica la
dirección utilizada para acceder a los puertos publicados de los brokers
temporales; el valor predeterminado del script es host.docker.internal,
correspondiente al entorno Docker Desktop probado.

El archivo modules/engine/src/test/resources/docker-java.properties
contiene:

```properties
api.version=1.44
```

Esta configuración permite utilizar el cliente Docker de las pruebas
con el Docker Engine 29 del entorno probado.

El Dockerfile construye con -DskipTests.
Los tests se ejecutan por separado.

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| core | 34 | 0 | 0 | 0 |
| engine | 86 | 0 | 0 | 0 |
| Total | 120 | 0 | 0 | 0 |

Resultado de las pruebas con Java 25: BUILD SUCCESS.

La ejecución completa también se verificó mediante scripts/test.sh.

La cobertura del core medida con JaCoCo es de 98 % de instrucciones
y 93 % de ramas. El reporte se encuentra en:

- docs/calidad/cobertura/core/index.html

## Checker y validación

Validación realizada el 28 de octubre de 2026, hora de Uruguay:

- Checker: 8/8 comprobaciones aprobadas.
- Comandos a switches: 18.
- Consultas de estado: 156.
- Suite Maven: 120 tests, sin fallos, errores ni pruebas omitidas.

La cantidad de comandos y consultas depende de la duración de la ejecución.
El checker verifica interoperabilidad; no sustituye las pruebas específicas
de fallas de comunicación.

En una inspección anterior de MongoDB, las dos lecturas más recientes
verificadas tenían sourceTs y payload.ts de tipo long, con unidad
epoch_milliseconds.

La comprobación de tipos corresponde a las lecturas nuevas inspeccionadas.
No implica que se haya migrado el histórico anterior.

Para ver el veredicto, presionar Ctrl+C en la terminal del checker.

## Integración continua

.github/workflows/maven.yml utiliza Java 25 y ejecuta:

```bash
mvn -B --no-transfer-progress clean verify
```

Se activa por push a main, iteracion-4 o iteracion5,
pull request hacia main o ejecución manual.

Guarda reportes Surefire.

Los tests no requieren MongoDB ni checker activos. La suite completa
requiere un motor Docker accesible para crear los brokers temporales.

El resultado del workflow debe comprobarse en GitHub Actions después
del push de los últimos cambios.

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
- docs/calidad/estandares-codificacion.md
- docs/calidad/cobertura/core/index.html
- docs/arquitectura/virtualclock.md

La metodología es Scrumban, con planificación,
seguimiento en Jira y revisiones semanales.

## Representación numérica interna

Las temperaturas, potencias y factores de aceleración se representan en Java
con `long`, usando milésimas como unidad interna: por ejemplo, 21,5 °C se
almacena como 21500, 2,5 kW como 2500 y un factor 1,5 como 1500. En los límites
JSON/MQTT se convierte desde/hacia números decimales sin usar coma flotante
binaria; los valores internos se mantienen como enteros escalados. El histórico
MongoDB guarda las lecturas en `tempMilliCelsius` y `tempMilliFahrenheit`.

## Vencimiento de lecturas y recuperación de comunicaciones

Cada lectura válida de temperatura se conserva junto con su instante de
recepción en milisegundos. Su vigencia es de 30 segundos reales,
independientemente del factor de aceleración del controlador.

El plazo se define en la constante TEMPERATURE_TIMEOUT_MS.
No se configura mediante .env.

Cuando transcurren 30 segundos sin una nueva lectura válida, la temperatura
se considera desconocida. En el próximo ciclo de control se ordena apagar
la calefacción de esa habitación si su estado lo requiere. Si el switch
no responde, los ciclos posteriores vuelven a intentar las acciones necesarias.

Una nueva lectura válida permite reanudar el control según la temperatura,
la configuración, la franja horaria y la potencia disponible.
Los mensajes inválidos no renuevan la vigencia de la lectura anterior.

### Pruebas automatizadas

Se verificaron los siguientes comportamientos:

- La lectura permanece disponible antes de los 30 segundos.
- La lectura vence al cumplirse exactamente 30 segundos.
- Una nueva lectura válida restablece la temperatura disponible.
- Un mensaje inválido no extiende la vigencia de la lectura anterior.
- Una interrupción de recepción de 20 segundos mantiene válida la lectura;
  una nueva lectura renueva el plazo de vigencia.
- El engine ordena encender, apagar por vencimiento y volver a encender
  al recibir una nueva lectura válida.
- INICIAR permite encender y DETENER provoca el apagado.
- Una reducción de la temperatura esperada durante la ejecución provoca
  el apagado y conserva el control en marcha.
- La detención y el arranque de un proceso Mosquitto real permiten
  recuperar la conexión, la suscripción y la recepción con el mismo
  cliente MQTT.
- Las publicaciones realizadas durante la ausencia de suscripción
  no llegan al suscriptor ni renuevan la lectura; una publicación nueva,
  después de restablecer la suscripción, permite recuperar la temperatura.

Las pruebas de vigencia utilizan un reloj simulado, sin esperar
30 segundos reales.

Las dos pruebas de integración MQTT utilizan Testcontainers y la imagen
eclipse-mosquitto:2.0.22. Crean brokers temporales independientes del
checker y del controlador desplegado.

En la prueba de recuperación se detiene y arranca únicamente el proceso
Mosquitto, conservando el contenedor y su dirección. Se comprueba también
que el cliente MQTT del suscriptor no haya sido reemplazado.

La pérdida de entrega se provoca retirando temporalmente la suscripción
y publicando mensajes no retenidos. Después se restablece la suscripción
y se verifica que solo se almacenaron la lectura inicial y la nueva.
No se inyectan pérdidas individuales de paquetes TCP.

Resultado de la ejecución: 120 pruebas, con 0 fallos y 0 errores
(34 del core y 86 del engine).

### Prueba manual de recuperación del entorno

Con el controlador funcionando, se detuvo el checker y se volvió a iniciar.
El checker reúne el broker MQTT, los termostatos y los switches REST, por
lo que esta prueba interrumpe las comunicaciones MQTT y REST.

Los logs del controlador mostraron la reconexión a MQTT, la renovación
de ambas suscripciones, la recepción de nuevas temperaturas y la
reanudación de los comandos de calefacción, sin reiniciar el controlador.

Esta prueba verifica la recuperación del entorno completo.

### Prueba manual de interrupción aislada de la comunicación MQTT

Con el checker y el controlador funcionando, se bloqueó únicamente
el tráfico saliente TCP hacia el puerto 1883 desde la red del contenedor
ioteste-control. REST permaneció disponible en el puerto 9090.

Bloqueo desde PowerShell:

```powershell
docker run --rm --network container:ioteste-control --cap-add NET_ADMIN nicolaka/netshoot iptables -I OUTPUT -p tcp --dport 1883 -j REJECT --reject-with tcp-reset
```

Se mantuvo la interrupción durante más de 30 segundos.

Restauración:

```powershell
docker run --rm --network container:ioteste-control --cap-add NET_ADMIN nicolaka/netshoot iptables -D OUTPUT -p tcp --dport 1883 -j REJECT --reject-with tcp-reset
```

Cada ejecución del bloqueo agrega una regla. El comando de restauración
debe ejecutarse una vez por cada regla agregada.

Evidencia observada el 28 de octubre de 2026; los horarios siguientes
corresponden a UTC, como aparecen en los logs:

- 03:12:57.895: última temperatura de room1 y pérdida de conexión MQTT.
- 03:13:28.540: confirmación OFF de sw-room1, aproximadamente 30,6 segundos
  después de su última lectura. Room2 ya estaba apagada.
- Durante el corte se registraron reintentos de conexión MQTT.
- 03:15:49.353: reconexión MQTT y renovación de ambas suscripciones.
- 03:15:52: recepción de temperaturas nuevas para ambas habitaciones.
- 03:15:53: confirmación ON de ambos switches.

El apagado y los encendidos confirmados demuestran que REST siguió
disponible. No se reiniciaron el controlador ni el checker.

Esta prueba manual simula una interrupción de red hacia MQTT.
La detención real del proceso del broker se verifica por separado
en la prueba automatizada con Mosquitto.