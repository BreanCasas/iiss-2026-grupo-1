# IoTEste EcoWarm

Taller de Ingeniería de Software - Iteración 6 - Java 25.

Control de calefacción por habitaciones mediante MQTT y switches REST.

## Evolución y estructura

El controlador conserva la separación en core y engine. La iteración 6
incorpora mejoras de integración, tratamiento de fallas, vencimiento de
lecturas con tiempo acelerado y prevención del consumo en horario punta.

La validación incluye 141 pruebas automatizadas y los 9 escenarios
incluidos en ecowarm-run, aprobados sin observaciones.

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
| docs/arquitectura/virtualclock.md | Funcionamiento del reloj virtual |
| docs/arquitectura/roadmap.md | Limitaciones conocidas y mejoras futuras |
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
- ecowarm-run.jar del docente.
- Git Bash para scripts en Windows.
- curl para scripts/api-example.sh.

Las pruebas de integración MQTT utilizan Testcontainers para crear
brokers Mosquitto temporales. No requieren que el runner ni MongoDB
estén ejecutándose.

La primera ejecución necesita descargar las dependencias Maven y las
imágenes de Docker utilizadas por las pruebas.

Crear o conservar el archivo .env en la raíz del proyecto:

```env
MONGODB_DATABASE=ioteste_runner
IOTESTE_API_KEY=reemplazar-por-tu-clave
```

Conservar la clave propia si ya está configurada. No publicar el archivo
.env con las credenciales locales.

Se utiliza una base fija. No es necesario cambiarla para repetir los
escenarios ni para reemplazar secuencialmente el sitio activo.

Compose conecta a mongodb://mongodb:27017 y utiliza:

- ECOWARM_ENVIRONMENT_URL: http://host.docker.internal:9090
- ECOWARM_PUBLIC_URL: http://localhost:8080
- ECOWARM_LABEL: grupo-1
- ECOWARM_ENVIRONMENT_KEY: iiss-2026

Estas direcciones corresponden al runner en Windows y al controlador
en Docker Desktop.

En el Compose actual, las direcciones del entorno, la dirección pública
y la clave del simulador están definidas directamente en
docker/docker-compose.yml. Para utilizar otro entorno deben ajustarse
allí.

La dirección pública debe ser accesible desde el simulador. Si este
corre en otra computadora, debe utilizarse la IP de red de la máquina
del controlador en lugar de localhost.

## Arranque y ejecución de escenarios

Desde la raíz, levantar el controlador y MongoDB:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --build

docker compose --env-file .env -f docker/docker-compose.yml ps
```

Ejecutar la batería completa:

```powershell
java -jar ".\ecowarm-run.jar" todos --host host.docker.internal --salida ".\ecowarm-reporte-validacion"
```

Mientras el runner espera el registro, ejecutar en otra terminal:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml restart control
```

Este orden permite repetir la corrida aunque el controlador ya estuviera
registrado ante un proceso anterior del runner.

El controlador se registra y obtiene la dirección del broker.
El runner carga el sitio, inicia el control y ejecuta los escenarios.

La batería completa dura aproximadamente 18 minutos y 15 segundos.
Dejarla finalizar para obtener el resultado completo.

Para observar los logs del controlador:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml logs -f control
```

Puertos utilizados:

- MQTT del runner: 1883.
- REST del simulador: 9090.
- API del controlador: 8080.
- MongoDB: 27017.

No ejecutar simultáneamente otro checker o broker que ocupe los mismos
puertos del runner.

### Registro y nuevas corridas

Después de un registro exitoso, el controlador no vuelve a registrarse
automáticamente ante un nuevo proceso del runner.

Para otra corrida, iniciar el runner y reiniciar control mientras espera
el registro, como se muestra arriba.

No es necesario cambiar la base de datos.

Este reinicio no es necesario para recuperar únicamente la conexión
MQTT dentro de una misma ejecución: la reconexión y las suscripciones
se restablecen automáticamente.

### Escenarios y reportes

Para consultar los escenarios disponibles:

```powershell
java -jar ".\ecowarm-run.jar" listar
java -jar ".\ecowarm-run.jar" ver E11
java -jar ".\ecowarm-run.jar" verificar
```

Para ejecutar un escenario o un grupo:

```powershell
java -jar ".\ecowarm-run.jar" E02 --host host.docker.internal --salida ".\ecowarm-reporte-E02"

java -jar ".\ecowarm-run.jar" E19 F01 F03 F05 --host host.docker.internal --salida ".\ecowarm-reporte-pendientes"
```

Cada nuevo proceso del runner requiere repetir el registro indicado
en la sección anterior.

El runner acepta escenarios firmados del curso. Las variantes propias
se prueban mediante el laboratorio y los tests del proyecto.

El directorio de salida contiene:

- reporte.md: resultados y observaciones.
- reporte.json: resultados en formato procesable.
- Directorios de corrida con detalles, series e interacciones.

Para leer el reporte completo:

```powershell
Get-Content -Encoding UTF8 ".\ecowarm-reporte-validacion\reporte.md"
```

Para abrirlo en el Bloc de notas:

```powershell
notepad ".\ecowarm-reporte-validacion\reporte.md"
```

El runner termina con código 0 si todo pasa, 1 si algún escenario falla
o no termina y 2 si no se pudo ejecutar.

Los avisos de sun.misc.Unsafe mostrados por la JVM no impidieron
la ejecución de las pruebas.

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

DETENER cambia el estado y detiene el envío de comandos a switches.
No implica ordenar su apagado.

Durante una corrida del runner, este administra los comandos de control.
No enviar comandos manuales si se quiere conservar la validez de la prueba.

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

Antes de cada comando comprueba que la revisión del sitio y la sesión
de ejecución sigan vigentes.

Con el control detenido, el motor no ejecuta las acciones del core.

## Inventario y persistencia

PUT /sitio valida y guarda inventario nuevo y apagados pendientes
en un mismo documento antes de actualizar la memoria.

La petición no llama a switches.

HeatingEngine procesa pendientes cuando el control está en ejecución.

Los pendientes que ya no corresponden a un switch del sitio vigente
se descartan sin enviar comandos al dispositivo.

Ese descarte no confirma que el dispositivo haya quedado físicamente
apagado.

Si falla un apagado de un dispositivo que sigue vigente, conserva
el trabajo y vuelve a intentar.

Antes de cada comando comprueba revisión y ejecución vigentes.

MongoDB conserva:

- site_config, documento _id active: inventory y pendingShutdowns.
- temperature_readings: histórico de lecturas válidas.

Inventario y pendientes se recuperan al arrancar.

Existe un único sitio activo por base. Cada PUT /sitio reemplaza
su configuración. No se conservan sitios activos independientes
para varios entornos simultáneos.

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

GET /control muestra el mismo instante en UTC. Los campos fechaHora
e iniciadoEn se serializan con precisión de milisegundos.

La franja incluye el inicio y excluye el final: [desde, hasta).

HABILES significa lunes a viernes.
TODOS incluye todos los días.

Para probar entrada en punta a las 17:00,
iniciar a las 16:58 con factor 10.

Se alcanza el inicio aproximadamente 12 segundos reales después.

El factor del controlador no acelera por sí mismo la simulación térmica
del entorno.

El motor utiliza un margen preventivo de 2000 milisegundos reales antes
de entrar en punta para iniciar el apagado. Durante ese margen también
evita nuevos encendidos.

La anticipación no modifica el reloj virtual ni la hora informada
por la API. No garantiza el cumplimiento del límite ante latencias
superiores al margen disponible.

El ciclo periódico se complementa con despertares en los cambios
de franja y al cambiar el estado del control.

Cada habitación se consulta y se comanda por separado. Un fallo se trata
sin abandonar los demás dispositivos, aunque las llamadas HTTP
bloqueantes pueden retrasar el ciclo.

La descripción detallada está en
[VirtualClock](docs/arquitectura/virtualclock.md).

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
| engine | 107 | 0 | 0 | 0 |
| Total | 141 | 0 | 0 | 0 |

Resultado de las pruebas con Java 25: BUILD SUCCESS.

La ejecución completa también se verificó mediante scripts/test.sh.

La cobertura del core medida con JaCoCo es de 98 % de instrucciones
y 93 % de ramas. El reporte se encuentra en:

- docs/calidad/cobertura/core/index.html

## Validación con ecowarm-run

Corrida completa finalizada el 8 de noviembre de 2026, hora de Uruguay,
con ecowarm-run c95d037 e interop-v2:

| Escenario | Comportamiento | Resultado |
|---|---|---|
| E02 | Calentar una habitación | Pasa |
| E04 | Alternar habitaciones sin superar la potencia contratada | Pasa |
| E11 | Entrar en horario punta | Pasa |
| E14 | Subir la temperatura esperada durante la ejecución | Pasa |
| E18 | Reducir la potencia contratada durante la ejecución | Pasa |
| E19 | Reconocer un switch inicialmente encendido | Pasa |
| F01 | Recuperarse de la caída del broker | Pasa |
| F03 | Apagar ante la ausencia prolongada de lecturas | Pasa |
| F05 | Reintentar apagados fallidos | Pasa |

Resultado: 9 de 9 escenarios aprobados, sin observaciones.

En F01 se recuperó la suscripción después de la vuelta del broker
en 0,7 segundos reales.

En F03 se verificó el apagado de la habitación que dejó de publicar
temperaturas.

En E11 no hubo calefactores encendidos durante el horario punta.

La evidencia de esta corrida se conserva en
ecowarm-reporte-validacion/, incluyendo reporte, detalles, series
e interacciones.

SHA-256 del JAR utilizado:

```text
3e38a7e09c274445a307d7e9b2b9eda4c7a58f857d2d7793cbffe72cee2a3cd8
```

Los resultados corresponden a los escenarios incluidos y ejecutados.
Las limitaciones y las variantes pendientes se describen en
[roadmap](docs/arquitectura/roadmap.md).

### Validación histórica con ecowarm-check

Validación realizada el 28 de octubre de 2026, hora de Uruguay,
durante la iteración 5:

- Checker: 8/8 comprobaciones aprobadas.
- Comandos a switches: 18.
- Consultas de estado: 156.

La cantidad de comandos y consultas depende de la duración de la ejecución.
El checker verifica interoperabilidad; no sustituye las pruebas específicas
de fallas de comunicación.

En una inspección anterior de MongoDB, las dos lecturas más recientes
verificadas tenían sourceTs y payload.ts de tipo long, con unidad
epoch_milliseconds.

La comprobación de tipos corresponde a las lecturas nuevas inspeccionadas.
No implica que se haya migrado el histórico anterior.

El checker anterior puede ejecutarse con:

```powershell
java -jar ".\ecowarm-check.jar" --anunciar host.docker.internal
```

Para ver su veredicto, presionar Ctrl+C en su terminal.

No ejecutarlo simultáneamente con ecowarm-run en los mismos puertos.

## Integración continua

.github/workflows/maven.yml utiliza Java 25 y ejecuta:

```bash
mvn -B --no-transfer-progress clean verify
```

Se activa por push a main, iteracion-4 o iteracion5,
pull request hacia main o ejecución manual.

Guarda reportes Surefire.

Los tests no requieren MongoDB ni simulador activos. La suite completa
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

El runner termina al completar los escenarios.
Si se utiliza el checker anterior, se detiene aparte con Ctrl+C.

## Documentación y metodología

- docs/vision.md
- docs/metodologia.md
- docs/producto/
- docs/api/openapi.yaml
- docs/tests/criterio-tests.md
- docs/arquitectura/4mas1.md
- docs/arquitectura/roadmap.md
- docs/calidad/estandares-codificacion.md
- docs/calidad/cobertura/core/index.html
- docs/arquitectura/virtualclock.md

La metodología es Scrumban, con planificación,
seguimiento en Jira y revisiones semanales.

## Representación numérica interna

Las temperaturas, potencias y factores de aceleración se representan en Java
con long, usando milésimas como unidad interna: por ejemplo, 21,5 °C se
almacena como 21500, 2,5 kW como 2500 y un factor 1,5 como 1500. En los límites
JSON/MQTT se convierte desde/hacia números decimales sin usar coma flotante
binaria; los valores internos se mantienen como enteros escalados. El histórico
MongoDB guarda las lecturas en tempMilliCelsius y tempMilliFahrenheit.

Los números serializados mediante MilliLongSerializer se escriben como
números JSON decimales sin notación exponencial.

## Vencimiento de lecturas y recuperación de comunicaciones

Cada lectura válida de temperatura se conserva junto con su instante de
recepción en milisegundos.

Mientras el control está en ejecución, su vigencia considera un límite
de 5 minutos virtuales y un máximo de 30 segundos reales.

Para un factor de aceleración f, expresado como número del protocolo:

```text
vigencia en milisegundos reales =
    mínimo(30000, máximo(1, parte entera de 300000 / f))
```

Ejemplos:

- Factor 1: 30 segundos reales.
- Factor 10: 30 segundos reales.
- Factor 60: 5 segundos reales.

Con el control detenido se utiliza el límite de 30 segundos reales.

Los plazos se definen en las constantes TEMPERATURE_TIMEOUT_MS
y VIRTUAL_TEMPERATURE_TIMEOUT_MS.
No se configuran mediante .env.

Cuando vence la lectura, la temperatura se considera desconocida.
En el próximo ciclo con el control en ejecución se ordena apagar la
calefacción de esa habitación si su estado lo requiere. Si el switch
no responde, los ciclos posteriores vuelven a intentar las acciones
necesarias.

Una nueva lectura válida permite reanudar el control según la temperatura,
la configuración, la franja horaria y la potencia disponible.
Los mensajes inválidos no renuevan la vigencia de la lectura anterior.

Si cambia el factor de tiempo, la antigüedad acumulada se evalúa con
el factor actual. No se integra el historial de factores anteriores.

### Pruebas automatizadas

Se verificaron los siguientes comportamientos:

- La lectura permanece disponible antes de vencer.
- Con factor 1, vence al cumplirse 30 segundos reales.
- Con factor 60, vence al cumplirse 5 segundos reales.
- Una nueva lectura válida restablece la temperatura disponible.
- Un mensaje inválido no extiende la vigencia de la lectura anterior.
- Una interrupción de recepción de 20 segundos mantiene válida la lectura
  con factor 1; una nueva lectura renueva el plazo.
- El engine ordena encender, apagar por vencimiento y volver a encender
  al recibir una nueva lectura válida.
- INICIAR permite controlar y DETENER interrumpe los comandos a switches.
- Una reducción de la temperatura esperada durante la ejecución provoca
  el apagado y conserva el control en marcha.
- Los pendientes ajenos al sitio vigente se descartan sin comandar
  dispositivos retirados.
- Un cambio de sitio o sesión invalida los comandos de una evaluación
  anterior.
- El margen de entrada en punta evita nuevos encendidos y anticipa
  el apagado.
- La detención y el arranque de un proceso Mosquitto real permiten
  recuperar la conexión, la suscripción y la recepción con el mismo
  cliente MQTT.
- Las publicaciones realizadas durante la ausencia de suscripción
  no llegan al suscriptor ni renuevan la lectura; una publicación nueva,
  después de restablecer la suscripción, permite recuperar la temperatura.

Las pruebas de vigencia utilizan un reloj simulado, sin esperar
los plazos reales.

Las dos pruebas de integración MQTT utilizan Testcontainers y la imagen
eclipse-mosquitto:2.0.22. Crean brokers temporales independientes del
simulador y del controlador desplegado.

En la prueba de recuperación se detiene y arranca únicamente el proceso
Mosquitto, conservando el contenedor y su dirección. Se comprueba también
que el cliente MQTT del suscriptor no haya sido reemplazado.

La pérdida de entrega se provoca retirando temporalmente la suscripción
y publicando mensajes no retenidos. Después se restablece la suscripción
y se verifica que solo se almacenaron la lectura inicial y la nueva.
No se inyectan pérdidas individuales de paquetes TCP.

Resultado de la ejecución: 141 pruebas, con 0 fallos y 0 errores
(34 del core y 107 del engine).

### Pruebas manuales históricas de la iteración 5

Las pruebas siguientes corresponden al comportamiento y a los plazos
de vigencia verificados durante la iteración 5. La validación actual
con tiempo acelerado se documenta en la sección de ecowarm-run.

#### Recuperación del entorno

Con el controlador funcionando, se detuvo el checker y se volvió a iniciar.
El checker reúne el broker MQTT, los termostatos y los switches REST, por
lo que esta prueba interrumpe las comunicaciones MQTT y REST.

Los logs del controlador mostraron la reconexión a MQTT, la renovación
de ambas suscripciones, la recepción de nuevas temperaturas y la
reanudación de los comandos de calefacción, sin reiniciar el controlador.

Esta prueba verifica la recuperación de las comunicaciones del entorno.
No implica que se haya repetido el registro ante el nuevo proceso
del checker.

#### Interrupción aislada de la comunicación MQTT

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