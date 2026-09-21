# IoTEste EcoWarm

Taller de Ingeniería de Software — Java 25

Sistema de gestión inteligente de calefacción por losa radiante, integrando dispositivos Shelly (termostatos H&T y switches Pro 1PM) mediante mensajería MQTT, con persistencia de configuración y del histórico de temperaturas.

## Iteraciones

* **Iteración 1**: prototipo base de mensajería MQTT (broker + suscriptor mínimo en Java).
* **Iteración 2**: cierre de alcance del producto (Visión v2, escenarios, historias, características), metodología ágil (Kanban), generador de eventos de termostatos, extensión del consumidor con persistencia de habitaciones e histórico de temperaturas, build reproducible vía Docker, e integración continua con GitHub Actions.
* **Iteración 3** *(actual)*: API REST con Spring Boot, persistencia en MongoDB, administración de habitaciones, controlador automático de temperatura, simulación de switches mediante un stub REST, comandos de inicio/parada del controlador, autenticación mediante API key y especificación OpenAPI 3.0.

## Contexto del proyecto

IoTEste busca posicionarse en el mercado de automatización de oficinas y hogares, integrando dispositivos IoT (Shelly Pro 1PM y Shelly H&T Gen 3) mediante mensajería MQTT.

A partir de la Iteración 2, el producto se enfoca específicamente en **EcoWarm**: gestión inteligente de calefacción por losa radiante.

En la Iteración 3 se incorpora una API REST para administrar el sistema, persistencia en MongoDB para habitaciones, lecturas y estado del controlador, y un Switch Stub que permite simular el accionamiento de los dispositivos de calefacción.

## Estructura del repositorio

* `/docs/vision.md` — Documento de Visión.
* `/docs/metodologia.md` — Metodología ágil adoptada (Kanban) y su justificación.
* `/docs/producto/` — Escenarios, historias, características y licencias del producto.
* `/docs/api/openapi.yaml` — Especificación OpenAPI 3.0 de la API REST.
* `/docker/docker-compose.yml` — Orquestación de los servicios del sistema.
* `/docker/mosquitto/config/` — Configuración del broker Mosquitto.
* `/docker/mongodb/init-mongo.js` — Inicialización de MongoDB local con las habitaciones y el estado inicial del controlador.
* `/scripts/up.sh` — Levanta el entorno.
* `/scripts/down.sh` — Baja y elimina los contenedores.
* `/scripts/stop.sh` — Detiene los contenedores.
* `/scripts/build.sh` — Compila los módulos Java mediante Docker.
* `/scripts/send-temp.sh` — Publica manualmente un evento MQTT.
* `/scripts/receive-temp.sh` — Permite verificar mensajes MQTT desde consola.
* `/scripts/api-example.sh` — Ejemplo de uso de la API REST mediante `curl`.
* `/src/subscriber/` — Consumidor MQTT, persistencia y lógica del controlador automático.
* `/src/generator/` — Generador de eventos simulados de temperatura.
* `/src/api/` — API REST desarrollada con Spring Boot.
* `/src/switch-stub/` — Stub REST que simula los switches.
* `/.github/workflows/maven.yml` — Integración continua con GitHub Actions.
* `/README.md` — Documentación principal del proyecto.

## Requisitos previos

### Linux (Fedora / Ubuntu / etc.)

* Docker y Docker Compose (plugin `docker compose`).
* `curl`, para probar la API REST.
* `mosquitto-clients` instalado localmente para los scripts de publicación/suscripción manuales.
* Opcionalmente, MQTT Explorer o MQTTX para verificación visual de los mensajes MQTT.

En Ubuntu/Debian:

```bash
sudo apt install mosquitto-clients
```

En Fedora:

```bash
sudo dnf install mosquitto
```

Si existe un Mosquitto instalado nativamente como servicio, detenerlo antes de levantar el entorno Docker para evitar conflictos con el puerto 1883:

```bash
sudo systemctl stop mosquitto
sudo systemctl disable mosquitto
```

### Windows

* Docker Desktop con backend WSL2 habilitado.
* Se recomienda trabajar dentro de WSL2 con Ubuntu para ejecutar los scripts `.sh`.
* Alternativamente puede utilizarse Git Bash.
* Cliente Mosquitto para Windows si se desea realizar pruebas MQTT manuales.
* MQTTX o MQTT Explorer para inspección visual de MQTT.

## Configuración

El sistema utiliza variables de entorno definidas en un archivo `.env` ubicado en la raíz del repositorio.

Para ejecutar el entorno completo con MongoDB local:

```env
MONGODB_URI=mongodb://mongodb:27017
MONGODB_DATABASE=ioteste
IOTESTE_API_KEY=ioteste-secret
```

`MONGODB_URI` contiene la URI utilizada por los servicios para conectarse al contenedor MongoDB dentro de la red Docker.

`MONGODB_DATABASE` indica la base de datos utilizada por EcoWarm.

`IOTESTE_API_KEY` define la clave necesaria para acceder a la API REST.

El archivo `.env` puede contener credenciales y **no debe versionarse en Git**.

## Cómo levantar el sistema completo

Desde la raíz del repositorio:

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
```

El entorno está compuesto por **6 servicios**:

* **ioteste-mosquitto**: broker MQTT, expuesto en `localhost:1883`.
* **ioteste-generator**: genera periódicamente eventos simulados de temperatura mediante MQTT.
* **ioteste-subscriber**: consume los eventos MQTT, persiste las lecturas en MongoDB y ejecuta la lógica del controlador automático.
* **ioteste-switch-stub**: servicio REST que simula los switches y recibe las órdenes ON/OFF, expuesto en `localhost:8081`.
* **ioteste-api**: API REST Spring Boot para administrar habitaciones, consultar lecturas, controlar switches y arrancar/detener el controlador automático, expuesta en `localhost:8080`.
* **ioteste-mongodb**: base de datos MongoDB local utilizada para persistir habitaciones, histórico de temperaturas y estado del controlador, expuesta en `localhost:27017`.

MongoDB utiliza el volumen nombrado `mongodb-data` para conservar la información almacenada.

Al crear la base de datos por primera vez, el archivo:

```text
docker/mongodb/init-mongo.js
```

inicializa la base `ioteste` con las habitaciones de prueba y el estado inicial del controlador.

Para comprobar los servicios:

```bash
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Para ver todos los logs:

```bash
docker compose --env-file .env -f docker/docker-compose.yml logs -f
```

Para ver únicamente los eventos procesados por el subscriber:

```bash
docker compose --env-file .env -f docker/docker-compose.yml logs -f subscriber
```

Para ver las órdenes recibidas por el Switch Stub:

```bash
docker compose --env-file .env -f docker/docker-compose.yml logs -f switch-stub
```

## Cómo compilar el sistema

El proyecto proporciona un build reproducible mediante Docker:

```bash
cd scripts
./build.sh
```

De esta forma no es necesario disponer de una instalación local de Maven para realizar el build del proyecto.

## Cómo compilar un módulo manualmente

Para desarrollo o depuración también es posible utilizar Maven directamente.

Por ejemplo, para el subscriber:

```bash
cd src/subscriber
mvn clean package
```

Para la API:

```bash
cd src/api
mvn clean package
```

Para el Switch Stub:

```bash
cd src/switch-stub
mvn clean package
```

Para el generator:

```bash
cd src/generator
mvn clean package
```

## Variables de entorno del subscriber

| Variable           | Default                 | Descripción                              |
| ------------------ | ----------------------- | ---------------------------------------- |
| `MQTT_BROKER_HOST` | `localhost`             | Host del broker MQTT                     |
| `MQTT_BROKER_PORT` | `1883`                  | Puerto del broker MQTT                   |
| `MQTT_TOPIC`       | `+/status/#`            | Topic al que se suscribe                 |
| `MQTT_CLIENT_ID`   | `ioteste-subscriber`    | Client ID utilizado en MQTT              |
| `MONGODB_URI`      | *(obligatoria)*         | URI de conexión a MongoDB                |
| `MONGODB_DATABASE` | `ioteste`               | Base de datos utilizada por EcoWarm      |
| `SWITCH_STUB_URL`  | `http://localhost:8081` | URL del servicio que simula los switches |

El subscriber recibe las lecturas de temperatura mediante MQTT y busca en MongoDB la habitación correspondiente al termostato.

Las lecturas válidas se persisten en MongoDB.

Cuando el controlador está habilitado, el subscriber compara la temperatura recibida con `targetTempC`:

* Si la temperatura es menor que `targetTempC`, envía una orden **ON** al Switch Stub.
* Si la temperatura es mayor o igual que `targetTempC`, envía una orden **OFF** al Switch Stub.
* Si el controlador está detenido, la lectura se persiste pero no se acciona el switch.

## Variables de entorno del generator

| Variable                | Default             | Descripción                                   |
| ----------------------- | ------------------- | --------------------------------------------- |
| `MQTT_BROKER_HOST`      | `localhost`         | Host del broker MQTT                          |
| `MQTT_BROKER_PORT`      | `1883`              | Puerto del broker MQTT                        |
| `MQTT_CLIENT_ID`        | `ioteste-generator` | Client ID utilizado en MQTT                   |
| `GENERATOR_INTERVAL_MS` | `10000`             | Intervalo entre publicaciones en milisegundos |

El generator publica eventos simulados de temperatura periódicamente para permitir probar el comportamiento completo del sistema sin disponer de termostatos físicos.

## Persistencia en MongoDB

En la Iteración 3, MongoDB se utiliza como base de datos no relacional para almacenar información persistente del sistema.

MongoDB se ejecuta localmente como un servicio adicional dentro del entorno Docker Compose utilizando la imagen `mongo:8`.

Entre los datos almacenados se encuentran:

* habitaciones;
* configuración de cada habitación;
* histórico de lecturas de temperatura;
* estado del controlador automático.

El subscriber y la API utilizan la misma base configurada mediante:

```text
MONGODB_URI
MONGODB_DATABASE
```

Dentro de Docker, los servicios se conectan mediante:

```text
mongodb://mongodb:27017
```

La información de MongoDB se conserva utilizando el volumen:

```text
mongodb-data
```

De esta forma, los cambios realizados mediante la API pueden ser utilizados por el controlador sin depender de un archivo local de habitaciones.

### Inicialización de MongoDB

El archivo:

```text
docker/mongodb/init-mongo.js
```

se ejecuta automáticamente cuando MongoDB inicializa por primera vez su volumen de datos.

El script crea la base:

```text
ioteste
```

e inserta inicialmente las habitaciones:

* `room1` — Living — temperatura objetivo `21.5 °C`.
* `room2` — Bedroom — temperatura objetivo `20.0 °C`.

También crea el estado inicial del controlador:

```text
id: controller
enabled: false
```

Por lo tanto, el controlador automático comienza detenido hasta que se inicia mediante la API REST.

## Configuración de habitaciones

Las habitaciones se administran mediante la API REST y se persisten en MongoDB.

Una habitación contiene información como:

```json
{
  "id": "room1",
  "name": "Living",
  "targetTempC": 21.5,
  "thermostatId": "ht-sim-room1",
  "switchId": "pro1pm-room1"
}
```

El `thermostatId` permite asociar los mensajes MQTT recibidos con una habitación.

El `switchId` identifica el switch que debe ser accionado.

`targetTempC` representa la temperatura objetivo configurada para la habitación.

## API REST

La API se encuentra disponible localmente en:

```text
http://localhost:8080
```

Los endpoints están protegidos mediante una API key.

La clave debe enviarse utilizando el header:

```text
X-API-Key
```

Por ejemplo:

```bash
curl http://localhost:8080/rooms \
  -H "X-API-Key: ${IOTESTE_API_KEY}"
```

Sin la API key, o utilizando una clave incorrecta, la API responde con HTTP `401 Unauthorized`.

### Endpoints principales

#### Habitaciones

```text
GET    /rooms
GET    /rooms/{id}
POST   /rooms
PUT    /rooms/{id}
PATCH  /rooms/{id}
DELETE /rooms/{id}
```

#### Histórico de temperatura

```text
GET /rooms/{id}/readings
```

#### Control manual del switch

```text
POST /rooms/{id}/switch/on
POST /rooms/{id}/switch/off
```

#### Validación de habitaciones

```text
POST /rooms/validate
```

#### Controlador automático

```text
POST /controller/start
POST /controller/stop
GET  /controller/status
```

## Ejemplo de uso de la API

Se proporciona el script:

```text
scripts/api-example.sh
```

Para ejecutarlo:

```bash
set -a
source .env
set +a

./scripts/api-example.sh
```

El script realiza ejemplos de consulta de habitaciones, consulta del estado del controlador y validación de la configuración utilizando la API key definida en `.env`.

## Controlador automático

El controlador puede iniciarse mediante:

```bash
curl -X POST http://localhost:8080/controller/start \
  -H "X-API-Key: ${IOTESTE_API_KEY}"
```

Consultar su estado:

```bash
curl http://localhost:8080/controller/status \
  -H "X-API-Key: ${IOTESTE_API_KEY}"
```

Y detenerlo mediante:

```bash
curl -X POST http://localhost:8080/controller/stop \
  -H "X-API-Key: ${IOTESTE_API_KEY}"
```

Cuando está detenido, el subscriber continúa recibiendo y persistiendo temperaturas, pero no envía órdenes de control al Switch Stub.

Cuando está habilitado, para cada lectura válida el subscriber compara la temperatura recibida con la temperatura objetivo de la habitación.

Por ejemplo:

```text
temperatura actual < targetTempC
→ Switch ON
```

```text
temperatura actual >= targetTempC
→ Switch OFF
```

## Modificar la temperatura objetivo

Por ejemplo, para cambiar la temperatura objetivo de `room1`:

```bash
curl -X PATCH http://localhost:8080/rooms/room1 \
  -H "X-API-Key: ${IOTESTE_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "targetTempC": 20.0
  }'
```

El subscriber utiliza el nuevo valor persistido en MongoDB al procesar las siguientes lecturas.

## Histórico de temperaturas

Las lecturas recibidas por MQTT se almacenan en MongoDB y pueden consultarse mediante la API.

Por ejemplo:

```bash
curl http://localhost:8080/rooms/room1/readings \
  -H "X-API-Key: ${IOTESTE_API_KEY}"
```

Cada lectura contiene información como:

```json
{
  "id": "identificador-generado-por-mongodb",
  "roomId": "room1",
  "tempCelsius": 21.0,
  "tempFahrenheit": 69.8,
  "sourceTs": 1789961658302,
  "receivedAt": "2026-09-21T03:34:18.306Z"
}
```

## Validación de habitaciones

La configuración de las habitaciones puede validarse mediante:

```bash
curl -X POST http://localhost:8080/rooms/validate \
  -H "X-API-Key: ${IOTESTE_API_KEY}"
```

La validación comprueba la existencia de los identificadores necesarios y detecta valores duplicados de `thermostatId` y `switchId`.

Si la configuración es válida, devuelve una lista vacía.

Si existen problemas, devuelve una lista con los errores detectados.

## Switch Stub

El servicio `switch-stub` simula el comportamiento del dispositivo de calefacción.

Está disponible dentro de la red Docker mediante:

```text
http://switch-stub:8081
```

y desde el host mediante:

```text
http://localhost:8081
```

El subscriber envía órdenes REST al stub cuando el controlador automático está habilitado.

La API también permite enviar manualmente órdenes ON/OFF mediante:

```text
POST /rooms/{id}/switch/on
POST /rooms/{id}/switch/off
```

Las órdenes pueden observarse mediante:

```bash
docker compose --env-file .env -f docker/docker-compose.yml logs -f switch-stub
```

Ejemplo de log:

```text
Switch pro1pm-room1 -> ON
```

o:

```text
Switch pro1pm-room1 -> OFF
```

## OpenAPI

La especificación de la API está documentada utilizando OpenAPI 3.0 en:

```text
docs/api/openapi.yaml
```

La especificación incluye los endpoints REST y el esquema de autenticación mediante `X-API-Key`.

## Verificación cruzada con un cliente MQTT externo

Opcionalmente se puede utilizar MQTT Explorer o MQTTX.

1. Conectar el cliente contra `localhost:1883`.
2. Suscribirse al topic utilizado por los termostatos simulados.
3. Publicar un mensaje manual mediante `scripts/send-temp.sh` o utilizar el `generator`.
4. Confirmar que el subscriber recibe el mensaje.
5. Verificar que la lectura se persiste y, si el controlador está habilitado, que se genera la orden correspondiente al Switch Stub.

## Metodología ágil

El equipo trabaja bajo **Kanban**.

La justificación y configuración utilizada por el equipo se encuentra documentada en:

```text
docs/metodologia.md
```

La planificación de la Iteración 3 se mantiene en el Board del equipo.

## Integración continua

El repositorio utiliza GitHub Actions para integración continua.

El workflow se encuentra en:

```text
.github/workflows/maven.yml
```

El objetivo es verificar automáticamente el build del proyecto ante cambios enviados al repositorio.

## Cómo bajar el entorno

Desde la carpeta `scripts`:

```bash
./down.sh
```

Esto baja los contenedores del entorno.

Para detenerlos sin eliminarlos:

```bash
./stop.sh
```

También puede utilizarse Docker Compose directamente desde la raíz:

```bash
docker compose --env-file .env -f docker/docker-compose.yml down
```

Para eliminar también el volumen local de MongoDB y provocar que `init-mongo.js` vuelva a ejecutarse en el próximo arranque:

```bash
docker compose --env-file .env -f docker/docker-compose.yml down -v
```

**Advertencia:** `-v` elimina los volúmenes del entorno y, por lo tanto, los datos persistidos localmente.

## Proyecto Jira

La planificación y seguimiento del proyecto se realiza mediante el Board Kanban del equipo en Jira.

## Próximos pasos

Las siguientes iteraciones podrán extender EcoWarm con nuevas capacidades de automatización, optimización del consumo y utilización de información externa para mejorar las decisiones del sistema.