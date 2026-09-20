# IoTEste EcoWarm

Taller de Ingeniería de Software — Java 25

Sistema de gestión inteligente de calefacción por losa radiante, integrando dispositivos
Shelly (termostatos H&T y switches Pro 1PM) mediante mensajería MQTT, con persistencia de
configuración y del histórico de temperaturas.

## Iteraciones

- **Iteración 1**: prototipo base de mensajería MQTT (broker + suscriptor mínimo en Java).
- **Iteración 2** *(actual)*: cierre de alcance del producto (Visión v2, escenarios, historias,
  características), metodología ágil (Kanban), generador de eventos de termostatos,
  extensión del consumidor con persistencia de habitaciones e histórico de temperaturas,
  build reproducible vía Docker, e integración continua con GitHub Actions.

## Contexto del proyecto

IoTEste busca posicionarse en el mercado de automatización de oficinas y hogares, integrando
dispositivos IoT (Shelly Pro 1PM y Shelly H&T Gen 3) mediante mensajería MQTT. A partir de la
Iteración 2, el producto se enfoca específicamente en **EcoWarm**: gestión inteligente de
calefacción por losa radiante, optimizando el consumo según las tarifas eléctricas disponibles.

## Estructura del repositorio

/docs/vision.md Documento de Visión (v2)
/docs/metodologia.md Metodología ágil adoptada (Kanban) y su justificación
/docs/producto/escenarios.md Escenarios de uso de EcoWarm
/docs/producto/historias.md Historias de usuario
/docs/producto/caracteristicas.md Características del producto
/docker/docker-compose.yml Orquestación: Mosquitto + MongoDB + subscriber + generator
/docker/mosquitto/config/ Configuración del broker Mosquitto
/docker/data/rooms.json Configuración predefinida de habitaciones
/scripts/up.sh Levanta el entorno completo (build + up -d)
/scripts/down.sh Baja y elimina los contenedores
/scripts/stop.sh Detiene los contenedores sin eliminarlos
/scripts/build.sh Compila todos los módulos Java vía Docker
/scripts/send-temp.sh Publica un mensaje simulado de temperatura/humedad (manual)
/scripts/receive-temp.sh Se suscribe por consola (verificación manual)
/src/subscriber/ Consumidor de eventos (Java): recibe, muestra y persiste
/src/generator/ Generador de eventos (Java): simula termostatos
/.github/workflows/maven.yml Integración continua (GitHub Actions + Maven)
/README.md


## Requisitos previos

### Linux (Fedora / Ubuntu / etc.)

- Docker y Docker Compose (plugin `docker compose`)
- `mosquitto-clients` instalado localmente, para los scripts de publish/subscribe manuales
  (en Fedora: `sudo dnf install mosquitto` · en Ubuntu/Debian: `sudo apt install mosquitto-clients`)
- (Opcional) [MQTT Explorer](http://mqtt-explorer.com/) o [MQTTX](https://mqttx.app/) para verificación visual cruzada
- **Importante:** si hay un Mosquitto instalado nativamente en el sistema (como servicio),
  detenerlo antes de levantar el entorno Docker, porque ambos compiten por el puerto 1883:
```bash
  sudo systemctl stop mosquitto
  sudo systemctl disable mosquitto
```

### Windows

- **Docker Desktop** (con backend WSL2 habilitado): https://www.docker.com/products/docker-desktop
- Se recomienda trabajar dentro de **WSL2 con Ubuntu** (`wsl --install` desde PowerShell como
  administrador) para poder correr los scripts `.sh` sin modificaciones. Alternativamente,
  **Git Bash** (incluido con Git para Windows) también permite ejecutarlos.
- Cliente Mosquitto para Windows (si no se usa WSL2): https://mosquitto.org/download/
- MQTTX o MQTT Explorer tienen instaladores `.exe` nativos para Windows.

## Cómo levantar el sistema completo

```bash
cd scripts
./up.sh
```

Esto construye y levanta 4 servicios:

- **ioteste-mosquitto**: broker MQTT, expuesto en `localhost:1883`
- **ioteste-mongodb**: base de datos MongoDB local, expuesta en `localhost:27017`, con sus
  datos en un volumen Docker (`mongodb-data`)
- **ioteste-generator**: genera eventos de temperatura simulados cada 10 segundos (por
  defecto), para las habitaciones configuradas en `docker/data/rooms.json`
- **ioteste-subscriber**: recibe esos eventos, los despliega en consola (vía logger, sin
  `System.out`) y los persiste en MongoDB (base `ioteste`, colección `temperature_readings`)

El sistema se pone a correr automáticamente al levantar el compose, generando y consumiendo
eventos sin necesidad de intervención manual.

Para confirmar que los 4 servicios están activos:

```bash
docker compose -f ../docker/docker-compose.yml ps
```

Para ver los eventos en tiempo real (generación + recepción + persistencia):

```bash
docker compose -f ../docker/docker-compose.yml logs -f
```

## Cómo compilar el sistema (build reproducible, sin Java/Maven local)

```bash
cd scripts
./build.sh
```

Este script compila ambos módulos Java (`subscriber` y `generator`) usando exclusivamente
Docker — no depende de tener Java ni Maven instalados en el sistema anfitrión.

## Cómo compilar un módulo manualmente (sin Docker, para debug en IntelliJ)

```bash
cd src/subscriber   # o src/generator
mvn clean package
java -jar target/subscriber-jar-with-dependencies.jar   # ajustar nombre según el módulo
```

Variables de entorno del **subscriber**:

| Variable | Default | Descripción |
|---|---|---|
| `MQTT_BROKER_HOST` | `localhost` | Host del broker MQTT |
| `MQTT_BROKER_PORT` | `1883` | Puerto del broker MQTT |
| `MQTT_TOPIC` | `+/status/#` | Topic al que se suscribe |
| `MQTT_CLIENT_ID` | `ioteste-subscriber` | Client ID usado en la conexión MQTT |
| `ROOMS_FILE` | `/data/rooms.json` | Ruta al archivo de configuración de habitaciones |
| `MONGODB_URI` | *(obligatoria)* | URI de conexión a MongoDB (en Docker: `mongodb://mongodb:27017`) |
| `MONGODB_DATABASE` | `ioteste` | Base de datos donde se persiste el histórico de temperaturas |

Para correr el subscriber fuera de Docker (por ejemplo, desde IntelliJ), con el entorno Docker
levantado, definir `MONGODB_URI=mongodb://localhost:27017` (el compose expone ese puerto) y
ajustar `ROOMS_FILE` a la ruta local de `rooms.json`.

Variables de entorno del **generator**:

| Variable | Default | Descripción |
|---|---|---|
| `MQTT_BROKER_HOST` | `localhost` | Host del broker MQTT |
| `MQTT_BROKER_PORT` | `1883` | Puerto del broker MQTT |
| `MQTT_CLIENT_ID` | `ioteste-generator` | Client ID usado en la conexión MQTT |
| `GENERATOR_INTERVAL_MS` | `10000` | Intervalo entre publicaciones, en milisegundos |

## Verificar la persistencia

```bash
docker exec -it ioteste-mongodb mongosh
```

Dentro de `mongosh`:

```
use ioteste
show collections
db.temperature_readings.countDocuments()
db.temperature_readings.find().sort({receivedAt: -1}).limit(3)
exit
```

Debería verse la colección `temperature_readings`, con un documento por cada lectura recibida
(campos `roomId`, `tempCelsius`, `tempFahrenheit`, `sourceTs` y `receivedAt`, este último con
el momento en que el sistema la recibió y persistió). El contador de documentos crece a medida
que llegan lecturas.

## Configuración de habitaciones

La configuración predefinida del sitio vive en `docker/data/rooms.json`, montada como volumen
de solo lectura dentro del consumidor. Cada habitación define su termostato, su switch y su
temperatura objetivo:

```json
[
  {
    "id": "room1",
    "name": "Living",
    "thermostatId": "ht-sim-room1",
    "switchId": "pro1pm-room1",
    "targetTempC": 21.5
  }
]
```

## Verificación cruzada con un cliente MQTT externo (opcional)

Se puede usar [MQTT Explorer](http://mqtt-explorer.com/) o [MQTTX](https://mqttx.app/):

1. Conectar la aplicación contra `localhost:1883` (sin autenticación, puerto 1883, sin TLS).
2. Suscribirse al topic `ht-sim-+/status/temperature:+`.
3. Publicar un mensaje manual con `./send-temp.sh`, o simplemente observar los eventos que ya
   genera automáticamente el servicio `generator`.
4. Confirmar que los mismos mensajes aparecen tanto en la interfaz del cliente MQTT como en
   los logs del contenedor `ioteste-subscriber`.

## Metodología ágil

El equipo trabaja bajo **Kanban**. La justificación completa y la configuración del tablero
están documentadas en [`docs/metodologia.md`](docs/metodologia.md).

## Integración continua

Cada `push` o `pull request` sobre `main` dispara el workflow definido en
[`.github/workflows/maven.yml`](.github/workflows/maven.yml), que compila ambos módulos Java
(`subscriber` y `generator`) con Maven sobre JDK 25.

## Cómo bajar el entorno

```bash
./down.sh    # elimina los contenedores (conserva volúmenes de datos)
./stop.sh    # solo detiene los contenedores, sin eliminarlos
```

## Proyecto Jira

Tablero del equipo: https://estudiantes-team-luofpxgu.atlassian.net/?continue=https%3A%2F%2Festudiantes-team-luofpxgu.atlassian.net%2Fwelcome%2Fsoftware%3FprojectId%3D10000&atlOrigin=eyJpIjoiOThhMjllOWZlMDUxNGE2Zjk1NGRlYTNkMWEzOTM3N2UiLCJwIjoiamlyYS1zb2Z0d2FyZSJ9

## Próximos pasos

Iteraciones futuras incorporarán: optimización según tarifa eléctrica multihorario, ajuste
según pronóstico climático, capacidad de simulación de consumo, y posible integración con
GenAI/LLM.
