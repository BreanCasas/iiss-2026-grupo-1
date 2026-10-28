# Estándares de codificación — IoTEste EcoWarm

## 1. Objetivo

Definir convenciones de codificación y criterios de calidad para los módulos
core y engine de IoTEste EcoWarm. Estos estándares buscan mejorar la
legibilidad, el mantenimiento y la confiabilidad del sistema.

## 2. Lenguaje y estructura

- El proyecto utiliza Java y Maven.
- El código de producción debe ubicarse en el módulo correspondiente:
  `modules/core` o `modules/engine`.
- Las pruebas deben ubicarse en `src/test/java`, respetando los paquetes
  de las clases que verifican.
- Las dependencias entre módulos deben declararse en los archivos `pom.xml`.
- La lógica de decisión debe permanecer en core, sin incorporar allí
  comunicaciones externas.
- Las integraciones con servicios externos y la gestión de eventos deben
  permanecer en engine.

## 3. Convenciones de nombres

- Las clases e interfaces utilizan UpperCamelCase.
  Ejemplo: `HeatingController`.
- Los métodos y atributos utilizan lowerCamelCase.
  Ejemplo: `calculateDecision`.
- Las constantes utilizan MAYÚSCULAS_CON_GUIONES_BAJOS cuando corresponda.
- Los nombres deben expresar claramente el propósito de la clase,
  método o variable.
- Los paquetes utilizan nombres en minúsculas.

## 4. Diseño y legibilidad

- Cada clase debe tener una responsabilidad clara.
- Los métodos deben ser comprensibles y evitar responsabilidades no
  relacionadas.
- Se debe evitar duplicar lógica cuando pueda reutilizarse de forma clara.
- Se deben utilizar modificadores de acceso adecuados, evitando exponer
  detalles internos innecesariamente.
- Los comentarios deben explicar decisiones o comportamientos que no
  resulten evidentes a partir del código.
- No se debe conservar código comentado, código muerto ni depuraciones
  temporales en la versión de entrega.

## 5. Tipos y validaciones

- Los tipos de datos deben elegirse según el significado y el rango de
  los valores representados.
- No se deben introducir conversiones numéricas que pierdan precisión
  sin una justificación explícita.
- Las entradas externas y la configuración deben validarse antes de
  utilizarlas.
- Los estados desconocidos o inválidos deben tratarse de forma explícita
  y segura.
- Los errores de comunicación con sistemas externos no deben provocar
  fallos silenciosos ni dejar el controlador en un estado incoherente.

## 6. Excepciones y robustez

- Las excepciones deben tratarse en el nivel que pueda tomar una acción
  apropiada.
- No se deben capturar excepciones para ignorarlas sin dejar un tratamiento
  justificado.
- Los fallos de MQTT, REST y otros servicios externos deben contemplarse
  en el módulo engine.
- La recuperación de un servicio debe verificarse mediante pruebas.
- Los errores deben registrarse con información útil, evitando exponer
  secretos o datos sensibles.

## 7. Pruebas automatizadas

- Las pruebas unitarias deben verificar el comportamiento observable de
  las unidades de código.
- Los casos de prueba deben cubrir comportamientos normales, límites y
  situaciones de error relevantes.
- Las pruebas deben ser repetibles y no depender del orden de ejecución.
- Las pruebas de integración deben comprobar la colaboración entre
  componentes y las respuestas ante fallos del entorno.
- Los nombres de las pruebas deben describir el comportamiento que verifican.
- Antes de entregar cambios, se deben ejecutar las pruebas automatizadas
  mediante Maven o el mecanismo de construcción establecido por el proyecto.

## 8. Cobertura

- La cobertura se mide mediante JaCoCo.
- El módulo core debe alcanzar al menos el 85 % de cobertura, según el
  criterio establecido para la iteración.
- La cobertura no sustituye la revisión de la calidad de las aserciones
  ni demuestra por sí sola que todos los comportamientos sean correctos.
- Los informes de cobertura deben conservarse en
  `docs/calidad/cobertura/`.

## 9. Criterios de revisión antes de la entrega

- El proyecto compila sin errores.
- Las pruebas automatizadas terminan correctamente.
- Se revisan los cambios para detectar código duplicado, errores y
  convenciones inconsistentes.
- La documentación se actualiza cuando cambia el comportamiento o la
  arquitectura.
- Los archivos entregados no contienen credenciales, secretos ni archivos
  temporales innecesarios.

## 10. Verificación del cumplimiento

La verificación combina revisión del código, ejecución de pruebas y
medición de cobertura. Cada evidencia respalda los aspectos indicados;
no implica una comprobación automática de todas las reglas del documento.

### Revisión del código

| Criterio | Evidencia revisada |
|---|---|
| Separación entre lógica e integraciones | Las clases de producción de core importan únicamente tipos de Java. Las comunicaciones MQTT, REST y MongoDB se implementan en engine. |
| Validación del inventario | SiteValidator comprueba campos obligatorios, potencias positivas, identificadores únicos, horarios y URLs de switches. |
| Nombres descriptivos | Las clases revisadas incluyen HeatingController, SiteValidator y TemperatureSubscriber. La constante TEMPERATURE_TIMEOUT_MS expresa el plazo y su unidad. |
| Precisión numérica | Los adaptadores convierten valores decimales a long escalado mediante operaciones exactas. |
| Tratamiento de fallos | HeatingEngine registra fallos de switches y continúa intentando los demás apagados. TemperatureSubscriber registra problemas de recepción y persistencia. |
| Vigencia de observaciones | TemperatureSubscriber devuelve null cuando una lectura cumple 30 segundos sin renovación válida. |
| Pruebas temporales repetibles | Las pruebas de vencimiento utilizan un Clock simulado, sin esperas reales. |
| Dependencias de pruebas | Testcontainers se declara con alcance test en modules/engine/pom.xml y no modifica las dependencias de producción. |

Esta revisión aporta evidencia sobre los componentes indicados. No
constituye una auditoría exhaustiva de todas las convenciones de nombres,
duplicaciones, comentarios, accesos o posibles secretos del repositorio.

### Compilación y pruebas

Se ejecutó desde la raíz del proyecto, en PowerShell, con Docker Desktop
activo:

    docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount "type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --workdir /workspace maven:3.9-eclipse-temurin-25 mvn -B clean verify

También se verificó la ejecución mediante:

    & "C:\Program Files\Git\bin\bash.exe" ./scripts/test.sh

Resultado comprobado:

- Core: 34 pruebas.
- Engine: 86 pruebas.
- Total: 120 pruebas.
- Fallos: 0.
- Errores: 0.
- Pruebas omitidas: 0.
- Resultado final: BUILD SUCCESS.

Las pruebas incluyen vencimiento y recuperación de lecturas, rechazo de
mensajes inválidos sin renovar la vigencia, inicio y parada del control,
y modificación de la temperatura esperada durante la ejecución.

También se verifica que una ausencia de mensajes de 20 segundos conserva
válida la lectura y que una lectura nueva renueva su plazo de vigencia.
Este caso simula ausencia de recepción, no pérdida de paquetes en la red.

Las pruebas de integración MQTT incorporan un broker real mediante
Testcontainers y eclipse-mosquitto:2.0.22. La suite completa requiere
un motor Docker accesible, pero no requiere que MongoDB ni el checker
estén ejecutándose.

El archivo modules/engine/src/test/resources/docker-java.properties
contiene api.version=1.44 para la compatibilidad del cliente Docker
de las pruebas con el Docker Engine 29 del entorno probado.

### Cobertura del core

Se ejecutó:

    docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --workdir /workspace maven:3.9-eclipse-temurin-25 mvn -B -pl modules/core -am clean verify

El reporte JaCoCo generado y copiado a docs/calidad/cobertura/core
mostró:

- Cobertura de instrucciones: 98 %.
- Cobertura de ramas: 93 %.

Ambos porcentajes superan el mínimo requerido del 85 %.

Reporte HTML: [Cobertura del core](cobertura/core/index.html).

### Recuperación de comunicaciones

Se verificó manualmente la recuperación del entorno deteniendo y
reiniciando el checker sin reiniciar el controlador.

Los logs mostraron reconexión MQTT, renovación de suscripciones,
recepción de nuevas temperaturas y reanudación del control.

El checker reúne MQTT, termostatos y switches REST. Esta primera
prueba corresponde a la recuperación del entorno completo.

Además, se verificó una interrupción aislada de la comunicación MQTT,
bloqueando el tráfico TCP saliente hacia el puerto 1883 desde la red
del contenedor ioteste-control. REST permaneció disponible en 9090.

Durante el corte se observó:

- Pérdida de conexión MQTT y reintentos.
- Ausencia de nuevas temperaturas.
- Confirmación del apagado de room1 aproximadamente 30,6 segundos
  después de su última lectura. Room2 ya estaba apagada.

Al retirar el bloqueo, sin reiniciar el controlador ni el checker,
se observaron:

- Reconexión MQTT.
- Renovación de ambas suscripciones.
- Nuevas temperaturas de ambas habitaciones.
- Confirmación de encendido de ambos switches.

El procedimiento de bloqueo y restauración figura en README.md.
Los horarios de la evidencia se registran en docs/arquitectura/4mas1.md.

Esta prueba manual simula una falla de red hacia MQTT.

### Detención y recuperación del servidor MQTT

La prueba realBrokerRestartAllowsSubscriptionAndReadingRecovery
detiene y arranca un proceso Mosquitto real dentro de un contenedor
temporal.

Se verifican la desconexión, el vencimiento de la lectura mediante
un reloj simulado, la recuperación de la suscripción y la recepción
de una nueva temperatura.

El contenedor y su dirección se conservan durante la prueba.
El cliente MQTT del suscriptor es el mismo antes y después de
la recuperación.

La prueba pasó tanto en la ejecución de TemperatureSubscriberTest
como en la suite completa.

### Publicaciones no entregadas

La prueba publicationsMissedWithoutSubscriptionDoNotRenewTheReading
retira temporalmente la suscripción de un cliente conectado a un
broker real y publica dos mensajes no retenidos.

Se comprueba que esas publicaciones no modifican la temperatura
disponible ni renuevan su plazo original.

Después del vencimiento se restablece la suscripción y una publicación
nueva recupera la lectura. El repositorio recibe únicamente la lectura
inicial y la nueva.

Este caso verifica pérdida de entrega de publicaciones por ausencia
de suscripción; no inyecta pérdidas individuales de paquetes TCP.

### Interoperabilidad

El checker obtuvo 8/8 comprobaciones aprobadas el 28 de octubre de 2026,
hora de Uruguay, con 18 comandos y 156 consultas de estado.

Este resultado verifica la interoperabilidad evaluada por el checker;
no sustituye las pruebas unitarias ni las pruebas específicas de fallas.

### Integración continua

El archivo .github/workflows/maven.yml configura Java 25 y ejecuta
mvn clean verify para los eventos definidos, incluidos los pushes
a iteracion5. También configura la conservación de reportes de pruebas.

La suite completa requiere Docker accesible en el entorno de ejecución
para crear los brokers temporales de Testcontainers.

El resultado de esa ejecución deberá comprobarse en GitHub Actions
después del push de los últimos cambios. Una ejecución anterior en verde
no demuestra el resultado de las pruebas incorporadas posteriormente.

### Alcance de la evidencia

Las pruebas automatizadas verifican comportamiento y la cobertura mide
ejecución del código. Ninguna de las dos comprueba por sí sola todas las
convenciones de estilo.

No se ha incorporado un verificador automático de estilo. Queda pendiente
la revisión final de los cambios antes de la entrega.