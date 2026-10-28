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

Este documento define los criterios de referencia. El cumplimiento debe
comprobarse revisando el código y ejecutando las pruebas del proyecto.
La existencia de este documento no implica que todos los criterios hayan
sido verificados automáticamente.
