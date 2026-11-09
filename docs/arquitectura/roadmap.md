# Limitaciones conocidas y roadmap

## Estado de la iteración 6

La validación realizada incluye:

- 141 pruebas automatizadas: 34 del core y 107 del engine, sin fallos.
- Los 9 escenarios incluidos en ecowarm-run c95d037, interop-v2.
- Una corrida completa del 8 de noviembre de 2026 con los 9 escenarios
  aprobados, sin fallos ni observaciones.

Los escenarios ejecutados fueron E02, E04, E11, E14, E18, E19, F01, F03
y F05. El reporte y sus archivos de detalle se encuentran en
`ecowarm-reporte-validacion/`.

Estos resultados corresponden a los escenarios ejecutados y no sustituyen
la validación de nuevas variantes o condiciones de despliegue.

## Limitaciones conocidas

### Registro ante el entorno

El controlador reintenta el registro inicial mientras no obtiene la
configuración del broker. Después de registrarse correctamente, no vuelve
a registrarse automáticamente ante una nueva ejecución del simulador.

Para iniciar otro proceso de ecowarm-run, se debe levantar el runner y,
mientras espera el registro, reiniciar el servicio `control`.

La reconexión MQTT dentro de una misma ejecución funciona automáticamente.
El escenario F01 verificó la reconexión y la recuperación de las
suscripciones tras la caída del broker.

### Persistencia y cambio de sitio

Se utiliza una base fija, configurada mediante `MONGODB_DATABASE`.
No es necesario cambiarla para repetir la batería de escenarios.

El controlador mantiene un único sitio activo. Cada `PUT /sitio` reemplaza
el inventario vigente. Los pendientes correspondientes a switches que ya
no pertenecen al sitio se descartan sin enviar comandos a esos dispositivos.

Descartar un pendiente no confirma que el dispositivo haya quedado
físicamente apagado.

La aplicación no mantiene configuraciones activas independientes para
varios entornos simultáneos. El histórico tampoco está separado por
ejecución del simulador.

### Control detenido y dispositivos retirados

Con el control detenido, el motor no envía comandos a los switches.

La retirada de un dispositivo del inventario no garantiza su apagado
físico. Si se necesita apagarlo antes de retirarlo, esa operación debe
coordinarse mientras todavía pertenece al sitio y el control está activo.

### Latencia y entrada en horario punta

El motor utiliza un margen preventivo de 2000 milisegundos reales antes
del comienzo del horario punta para iniciar el apagado.

Este margen no modifica el reloj virtual ni la hora publicada por la API.
Reduce el riesgo de llegar al horario punta con calefactores encendidos,
pero no constituye una garantía de tiempo real ante demoras superiores
al margen disponible.

Las llamadas HTTP a los switches son bloqueantes. Un dispositivo lento
puede retrasar el procesamiento de otros dispositivos.

### Vigencia de las temperaturas

Las lecturas tienen un vencimiento que considera el factor de tiempo
vigente y un límite máximo de 30 segundos reales.

El cálculo utiliza la hora de recepción local. Una lectura inválida no
renueva la vigencia de la última temperatura válida.

Si cambia el factor de tiempo, la antigüedad acumulada se evalúa con el
factor actual. No se integra el historial de cambios del factor.

Los mensajes MQTT retenidos o la reutilización de un topic pueden requerir
controles adicionales para distinguir lecturas de distintas ejecuciones.

### Horarios y política de calefacción

La configuración contempla un intervalo de horario punta y la selección
de días hábiles o todos los días.

No contempla un calendario de feriados, varios intervalos diarios ni un
intervalo que atraviese la medianoche.

La política de calefacción no incorpora una banda de histéresis
configurable.

### Histórico y recuperación

El sitio se persiste en MongoDB. El estado de ejecución del control y la
caché de temperaturas no se recuperan como una sesión activa después de
reiniciar el proceso.

El histórico de temperaturas no cuenta con una política de retención ni
una cola persistente para reintentar escrituras fallidas.

### Despliegue y alcance de las pruebas

El despliegue actual está orientado al laboratorio. Utiliza HTTP y requiere
revisar la exposición de puertos, la gestión de claves y la configuración
de MongoDB antes de utilizarlo fuera de ese entorno.

Las pruebas de integración incluyen un broker MQTT real mediante
Testcontainers. La integración con MongoDB no tiene la misma cobertura
con una instancia real en la batería actual.

## Roadmap

### Prioridad alta

1. Permitir renovar el registro ante una nueva ejecución del entorno,
   sin reiniciar el controlador.
2. Definir un procedimiento explícito para retirar dispositivos y
   distinguir la retirada del inventario de la confirmación de apagado.
3. Ampliar las pruebas con variantes de los escenarios: salida de punta,
   fines de semana, cambios de inventario y fallas combinadas.
4. Incorporar pruebas de persistencia y recuperación con MongoDB real
   mediante Testcontainers.

### Prioridad media

1. Reducir el impacto de las llamadas HTTP bloqueantes manteniendo el
   orden de apagado antes de encendido y el límite de potencia.
2. Evaluar el margen de apagado ante distintas latencias y cantidades
   de dispositivos.
3. Utilizar una referencia monotónica para medir la antigüedad de las
   lecturas frente a cambios del reloj del sistema.
4. Incorporar una banda de histéresis configurable y medir su efecto
   sobre las conmutaciones.
5. Definir retención del histórico y tratamiento de escrituras fallidas.
6. Revisar seguridad y configuración para despliegues fuera del laboratorio.

### Evoluciones opcionales

1. Separar configuraciones e históricos por entorno o ejecución si se
   requiere conservarlos de forma independiente. Esta mejora no es
   necesaria para repetir los escenarios con una base fija.
2. Admitir varios intervalos de punta, feriados e intervalos nocturnos.
3. Incorporar métricas de reconexiones, lecturas vencidas, latencias
   y comandos fallidos.
4. Asociar los reportes de validación con el commit y la imagen utilizados.

## Criterio para futuras modificaciones

Cada cambio debe acompañarse de pruebas que verifiquen el comportamiento
afectado.

Los cambios en la coordinación del motor, el reloj, la potencia o las
comunicaciones deben validarse también con los escenarios correspondientes
del simulador. Antes de una nueva entrega se debe ejecutar la batería
completa y conservar el reporte.

## Documentación relacionada

- [README](../../README.md)
- [Arquitectura 4+1](4mas1.md)
- [VirtualClock](virtualclock.md)
- [Criterios de pruebas](../tests/criterio-tests.md)