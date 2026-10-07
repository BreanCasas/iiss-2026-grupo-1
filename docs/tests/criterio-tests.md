# Criterio de adecuación de las pruebas — Iteración 4

## Objetivo

Verificar las decisiones de calefacción según temperatura,
potencia disponible y tarifa punta.

El core se prueba sin Docker, MongoDB, MQTT ni servicios REST.

## Criterio de selección

El conjunto incluye:

- Casos donde corresponde calefaccionar y donde corresponde apagar.
- Límites de temperatura, potencia y horario.
- Competencia entre habitaciones por la potencia disponible.
- Desempates deterministas.
- Entrada y salida de tarifa punta.

Cada prueba comprueba un comportamiento requerido.
La cantidad de pruebas por sí sola no demuestra adecuación.

## Cobertura funcional del core

| Regla | Situación | Resultado esperado |
|---|---|---|
| Habitación fría | Debajo del objetivo, fuera de punta y con potencia suficiente | Seleccionar |
| Temperatura objetivo | Temperatura igual o superior al objetivo | No seleccionar |
| Potencia insuficiente | Potencia requerida mayor que la disponible | No seleccionar |
| Límite de potencia | Potencia requerida igual a la disponible | Seleccionar |
| Tarifa punta | Habitación fría dentro de la franja | No seleccionar |
| Prioridad | No alcanza para todas | Priorizar mayor déficit |
| Potencia suficiente | Todas las habitaciones frías caben | Seleccionarlas |
| Empate | Igual déficit | Priorizar por identificador |
| Entrada en punta | Paso de 16:59 a 17:00 | Dejar de seleccionar |
| Salida de punta | Hora igual a 23:00 | Permitir calefacción |
| HABILES | Lunes dentro de la franja | Reconocer punta |
| Fin de semana | Sábado con HABILES | No reconocer punta |
| Inicio de franja | Hora igual al inicio | Reconocer punta |
| Final de franja | Hora igual al final | No reconocer punta |
| TODOS | Domingo dentro de la franja | Reconocer punta |

Clases:

- `HeatingControllerTest`: 10 pruebas.
- `PeakScheduleTest`: 5 pruebas.

## Tiempo virtual

Las pruebas del reloj utilizan una fuente de tiempo controlada.
Permiten avanzar el tiempo sin esperar minutos u horas reales.

Se comprueban el instante inicial, el avance normal y acelerado,
y la entrada en punta al avanzar el reloj.

También se comprueba que INICIAR conserva el desplazamiento
horario recibido y que una resincronización lo reemplaza.

La tarifa utiliza la hora local del comando.
17:00-03:00 se evalúa como las 17:00 locales.

## Pruebas complementarias de engine

| Clase | Casos | Comportamiento |
|---|---:|---|
| `ControlControllerTest` | 8 | Autenticación, estado, comandos y errores |
| `ControlServiceTimeTest` | 2 | Desplazamiento horario y resincronización |
| `SiteValidatorTest` | 12 | Campos, identificadores, versión, potencias y horarios |
| `SiteServiceTest` | 6 | Persistencia, repetición y apagados pendientes |
| `HeatingEngineTest` | 2 | Fallo de apagado y pendientes con control detenido |
| `VirtualClockTest` | 3 | Instante inicial y avance del reloj |
| `VirtualHeatingTest` | 1 | Cambio de decisión al entrar en punta |
| Total engine | 34 | |

### Actualización del inventario

SiteServiceTest comprueba:

1. Rechazo de un inventario inválido sin persistirlo.
2. Persistencia del inventario nuevo junto a los switches anteriores pendientes.
3. Conservación del inventario activo si falla la persistencia.
4. Aceptación de un inventario idéntico sin guardar nuevamente.
5. Recuperación de pendientes al reconstruir el servicio.
6. Imposibilidad de que una revisión anterior borre pendientes de otra actualización.

HeatingEngineTest comprueba:

1. Un fallo de apagado impide confirmar los pendientes y evaluar encendidos.
2. Los apagados pendientes se procesan aunque el control esté detenido.

Estas pruebas utilizan colaboradores simulados.
No equivalen a probar una caída y recuperación de MongoDB real.

## Ejecución

Desde PowerShell, en la raíz del proyecto:

```powershell
& "C:\Program Files\Git\bin\bash.exe" ./scripts/test.sh
```

Con Maven y Java 25 locales:

```bash
mvn -B clean verify
```

## Resultados

El 6 de octubre de 2026, hora de Uruguay:

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| core | 15 | 0 | 0 | 0 |
| engine | 34 | 0 | 0 | 0 |
| Total | 49 | 0 | 0 | 0 |

Resultado de Maven: `BUILD SUCCESS`.

El registro de Maven finalizó a las
`2026-10-07T00:17:32Z`, equivalente al 6 de octubre en Uruguay.

## Validación con el checker

Después de reconstruir el controlador se obtuvo 8/8:

| Comprobación | Resultado |
|---|---|
| Registro ante el entorno | Aprobado |
| PUT `/sitio` | Aprobado, HTTP 200 |
| GET `/sitio` | Aprobado |
| GET `/control` | Aprobado |
| INICIAR | Aprobado |
| Suscripción MQTT | Aprobado |
| Comandos a switches | Aprobado |
| Consulta de switches | Aprobado |

La ejecución registró 11 comandos y 88 consultas a switches.

Esta ejecución confirma interoperabilidad con el checker.
No demuestra por sí sola todas las reglas del core ni todos
los escenarios de actualización o recuperación.

## Comprobaciones manuales anteriores

- Entrada en punta a las 17:00 con desplazamiento -03:00:
  switches apagados.
- Salida de punta a las 23:00 con desplazamiento -03:00:
  calefacción permitida nuevamente.
- Apagado al alcanzar la temperatura objetivo.
- Selección de una habitación a la vez al limitar la potencia a 1,2 kW.

Estas observaciones pertenecen a ejecuciones anteriores;
no se presentan como una repetición completa sobre la última versión.

## Alcance y pendientes

El conjunto aporta evidencia sobre las reglas y límites descritos.

Quedan para ampliar las pruebas de fallas del entorno, incluyendo
desconexiones, lecturas desactualizadas y recuperación de servicios,
según el alcance de la Iteración 5.

Las pruebas se amplían cuando aparece un comportamiento nuevo
o un defecto que todavía no está representado.