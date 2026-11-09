# Criterio de adecuación de las pruebas - Iteración 6

## Objetivo

Verificar las decisiones del core según temperatura, potencia,
horario punta y estado de ejecución y switches.

Comprobar también la coordinación del engine, la vigencia de lecturas,
los cambios de inventario y la recuperación de comunicaciones.

El core se prueba sin Docker, MongoDB, MQTT ni servicios REST.

Las pruebas del engine combinan colaboradores simulados, servicios reales
del proyecto y brokers MQTT temporales mediante Testcontainers.

## Criterio de selección

Los casos representan reglas, límites, combinaciones y transiciones:

- Temperatura inferior al objetivo y temperatura objetivo alcanzada.
- Potencia insuficiente, suficiente y exactamente igual a la requerida.
- Prioridad por mayor déficit y desempate determinista por ID.
- Inicio incluido y final excluido del horario punta.
- Días HABILES y TODOS.
- Comparación de la decisión con los switches actuales.
- Secuencia antes, durante y después de punta.
- Temperatura ausente y switch desconocido.
- Control detenido y comandos innecesarios.
- Franja en viernes por la noche y domingo por la noche.
- Lista de habitaciones vacía y habitación que sola supera el máximo.
- Próximo cambio de franja: antes, dentro, en el límite y fin de semana.
- Ciclo de control con tiempo acelerado y límites cruzados durante un ciclo.
- Fallos de consulta y apagado de switches.
- Vencimiento de temperaturas con tiempo real y acelerado.
- Recuperación de lecturas y rechazo de mensajes inválidos.
- Cambios de sitio o sesión durante una evaluación.
- Pendientes de apagado vigentes y dispositivos retirados.
- Margen preventivo antes de entrar en punta.
- Reconexión y recuperación de suscripciones con un broker real.

La cantidad de pruebas por sí sola no demuestra adecuación.
Cada caso se vincula con un comportamiento del controlador.

## Pruebas del core

| Clase | Casos |
|---|---:|
| HeatingControllerTest | 12 |
| PeakScheduleTest | 14 |
| HeatingDecisionTest | 8 |
| Total core | 34 |

HeatingDecisionTest compara listas completas de acciones en ocho escenarios:

1. Entrar en punta con dos habitaciones frías y encendidas: OFF para ambas.
2. Potencia limitada: OFF de menor prioridad antes de ON de mayor déficit.
3. Habitación en objetivo: OFF mientras otra fría recibe ON.
4. Estados ya correctos: no producir comandos.
5. Antes de punta, entrada y salida: decidir con nuevos estados y lecturas.
6. Switch desconocido seleccionado: OFF antes de ON.
7. Temperatura no disponible: apagar y no seleccionar esa habitación.
8. Control detenido: producir acciones OFF en la decisión del core.

El core recibe configuración, hora y estado y devuelve Decision.

La decisión defensiva del core para un estado detenido no implica que
el motor la ejecute. HeatingEngine no consulta ni comanda switches
cuando el control está detenido.

## Pruebas del engine

| Clase | Casos | Comportamiento |
|---|---:|---|
| ControlControllerTest | 8 | Autenticación, comandos, estado y errores |
| ControlServiceTimeTest | 2 | Interpretación temporal y zona del sitio |
| ControlServiceClockTest | 3 | Reloj inyectable y avisos de cambio del control |
| EnvironmentRegistrationTest | 1 | Registro y obtención de la configuración del broker |
| HeatingEngineTest | 19 | Pendientes, fallos, revisiones, sesiones y margen de punta |
| HeatingScenarioTest | 14 | Coordinación del control con servicios reales y comunicaciones simuladas |
| SwitchClientTest | 6 | Consultas y comandos REST |
| TemperatureExpiryTest | 5 | Vigencia de lecturas según el factor y estado del control |
| TemperatureSubscriberTest | 9 | Lecturas, persistencia y recuperación MQTT |
| TickLoopTest | 7 | Inicio, despertares y una sola cadena de ejecución |
| SiteServiceTest | 6 | Persistencia e inventario con apagados pendientes |
| SiteValidatorTest | 12 | Validación del inventario |
| TickPlannerTest | 11 | Planificación del próximo ciclo con tiempo acelerado |
| VirtualClockTest | 3 | Instante inicial y avance normal o acelerado |
| VirtualHeatingTest | 1 | Entrada y salida de punta con reloj virtual |
| Total engine | 107 | |

Las pruebas temporales utilizan fuentes controladas para avanzar
el tiempo sin esperar minutos u horas reales.

Los colaboradores externos se simulan donde corresponde.
No se necesita MongoDB ni el simulador docente para ejecutar la suite.

La suite completa requiere un motor Docker accesible para que
Testcontainers cree los brokers MQTT temporales.

### Vigencia de temperaturas

Se comprueba que:

- Una lectura permanece disponible antes de vencer.
- Con factor 1, vence al cumplirse 30 segundos reales.
- Con factor 60, vence al cumplirse 5 segundos reales.
- Una lectura válida nueva restablece la temperatura disponible.
- Los mensajes inválidos no renuevan la vigencia.
- El motor apaga ante el vencimiento cuando el control está en marcha.
- Una nueva lectura permite volver a evaluar la calefacción.

La antigüedad se calcula desde la recepción local, no desde el timestamp
enviado por el termostato.

La normalización de ts a Long en milisegundos también se comprueba
antes de enviarlo al repositorio.

### Coordinación y fallas de switches

Las pruebas verifican:

- Apagados antes de nuevos encendidos.
- Consideración del posible consumo de un switch cuyo apagado falló.
- Reintentos de acciones que siguen siendo necesarias.
- Conservación de pendientes ante fallos.
- Descarte de pendientes ajenos al sitio sin enviarles comandos.
- Confirmación de pendientes completados sin perder los fallidos.
- Invalidación de evaluaciones por cambios de sitio o sesión.
- Ausencia de comandos con el control detenido.
- Prevención de encendidos y anticipación del apagado antes de punta.

### Recuperación con un broker real

TemperatureSubscriberTest utiliza Testcontainers y
eclipse-mosquitto:2.0.22 para dos pruebas de integración MQTT.

realBrokerRestartAllowsSubscriptionAndReadingRecovery comprueba:

1. Conexión y suscripción.
2. Recepción de una temperatura.
3. Detención del proceso Mosquitto.
4. Detección de desconexión y vencimiento de la lectura.
5. Arranque de Mosquitto dentro del mismo contenedor.
6. Recuperación de conexión y suscripción con el mismo cliente.
7. Recepción de una lectura nueva.

El contenedor y su puerto publicado se conservan durante la prueba.

publicationsMissedWithoutSubscriptionDoNotRenewTheReading retira
temporalmente la suscripción y publica mensajes no retenidos.

Esas publicaciones no modifican ni renuevan la última lectura.
Después de restablecer la suscripción, una publicación nueva recupera
la temperatura.

Esta prueba reproduce publicaciones no entregadas por ausencia
de suscripción. No inyecta pérdidas individuales de paquetes TCP.

## Ejecución

Desde PowerShell, en la raíz, con Docker Desktop activo:

```powershell
& "C:\Program Files\Git\bin\bash.exe" ./scripts/test.sh
```

Con Java 25 y Maven locales, y un motor Docker accesible:

```bash
mvn -B clean verify
```

El archivo modules/engine/src/test/resources/docker-java.properties
configura api.version=1.44 para el cliente Docker de las pruebas.

El Dockerfile de producción utiliza -DskipTests.
Construir la imagen no sustituye la ejecución de la suite.

## Resultados de pruebas

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| core | 34 | 0 | 0 | 0 |
| engine | 107 | 0 | 0 | 0 |
| Total | 141 | 0 | 0 | 0 |

Resultado comprobado con Java 25: BUILD SUCCESS.

La ejecución completa también se verificó mediante scripts/test.sh.

La cobertura del core medida con JaCoCo es:

- Instrucciones: 98 %.
- Ramas: 93 %.

El reporte está en docs/calidad/cobertura/core/index.html.

## Validación con ecowarm-run

La corrida completa finalizó el 8 de noviembre de 2026, hora de Uruguay,
con ecowarm-run c95d037 e interop-v2.

| Escenario | Comportamiento | Resultado |
|---|---|---|
| E02 | Calefacción de una habitación | Pasa |
| E04 | Alternancia con potencia limitada | Pasa |
| E11 | Entrada en horario punta | Pasa |
| E14 | Aumento de temperatura esperada durante la ejecución | Pasa |
| E18 | Reducción de potencia contratada durante la ejecución | Pasa |
| E19 | Switch inicialmente encendido | Pasa |
| F01 | Caída y recuperación del broker | Pasa |
| F03 | Termostato sin publicaciones | Pasa |
| F05 | Apagados fallidos | Pasa |

Resultado: 9 de 9 escenarios aprobados, sin observaciones.

En F01 se recuperaron las suscripciones en 0,7 segundos reales.
La expectativa de apagado por ausencia prolongada de lecturas no aplicó
en ese escenario porque el corte no alcanzó el plazo evaluado.

F03 verificó específicamente el apagado ante la ausencia prolongada
de lecturas.

En E11 no hubo calefactores encendidos durante punta.
F05 verificó la recuperación después de una falla de apagado.

### Reproducción

Levantar el controlador y MongoDB:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
```

Iniciar el runner:

```powershell
java -jar ".\ecowarm-run.jar" todos --host host.docker.internal --salida ".\ecowarm-reporte-validacion"
```

Mientras espera el registro, ejecutar en otra terminal:

```powershell
docker compose --env-file .env -f docker/docker-compose.yml restart control
```

Mantener la misma base configurada en MONGODB_DATABASE.
No se requiere cambiarla entre escenarios ni al repetir la batería.

No enviar comandos manuales ni interrumpir el runner durante
la validación.

### Evidencia

Conservar ecowarm-reporte-validacion/, que contiene:

- reporte.md.
- reporte.json.
- Detalles de cada escenario.
- Series de potencia y temperatura.
- Interacciones con el controlador.

SHA-256 del JAR utilizado:

```text
3e38a7e09c274445a307d7e9b2b9eda4c7a58f857d2d7793cbffe72cee2a3cd8
```

## Persistencia comprobada

En una inspección anterior de MongoDB se verificaron dos lecturas nuevas:

- sourceTs tenía tipo long.
- payload.ts tenía tipo long.
- sourceTsUnit era epoch_milliseconds.

Esto confirma la persistencia y el tipo de esas lecturas.

La conversión del histórico anterior no está comprobada.
El código nuevo no migra ese histórico automáticamente.

## Límites y continuación

Los escenarios incluidos en el runner son una muestra y no constituyen
todas las variantes posibles del sistema.

La suite utiliza un broker MQTT real, pero la persistencia de inventario
y pendientes se prueba con colaboradores simulados.

No se inyectan pérdidas individuales de paquetes TCP.
Tampoco se comprueba la migración del histórico antiguo.

Los escenarios propios se deben ejecutar en el laboratorio, ya que
ecowarm-run acepta escenarios firmados del curso.

Las limitaciones y las prioridades de evolución se describen en
[roadmap](../arquitectura/roadmap.md).