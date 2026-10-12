# Criterio de adecuación de las pruebas - Iteración 4

## Objetivo

Verificar las decisiones del core según temperatura, potencia,
horario punta y estado de ejecución y switches.

El core se prueba sin Docker, MongoDB, MQTT ni servicios REST.

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

La cantidad de pruebas por sí sola no demuestra adecuación.
Cada caso se vincula con un comportamiento del controlador.

## Pruebas del core

| Clase | Casos |
|---|---:|
| HeatingControllerTest | 10 |
| PeakScheduleTest | 5 |
| HeatingDecisionTest | 8 |
| Total core | 23 |

HeatingDecisionTest compara listas completas de acciones en ocho escenarios:

1. Entrar en punta con dos habitaciones frías y encendidas: OFF para ambas.
2. Potencia limitada: OFF de menor prioridad antes de ON de mayor déficit.
3. Habitación en objetivo: OFF mientras otra fría recibe ON.
4. Estados ya correctos: no producir comandos.
5. Antes de punta, entrada y salida: decidir con nuevos estados y lecturas.
6. Switch desconocido seleccionado: OFF antes de ON.
7. Temperatura no disponible: apagar y no seleccionar esa habitación.
8. Control detenido: apagar las habitaciones encendidas.

El core recibe configuración, hora y estado y devuelve Decision.
Engine realiza las comunicaciones para ejecutar las acciones.

## Pruebas de engine

| Clase | Casos | Comportamiento |
|---|---:|---|
| ControlControllerTest | 8 | Autenticación, comandos, estado y errores |
| ControlServiceTimeTest | 2 | Desplazamiento horario y resincronización |
| SiteValidatorTest | 12 | Validación del inventario |
| SiteServiceTest | 6 | Persistencia y apagados pendientes |
| HeatingEngineTest | 2 | Fallo de apagado y pendientes con control detenido |
| HeatingScenarioTest | 5 | Ejecución de escenarios completos |
| TemperatureSubscriberTest | 4 | Lecturas válidas, rechazo y fallo de guardado |
| VirtualClockTest | 3 | Instante inicial y avance normal/acelerado |
| VirtualHeatingTest | 1 | Entrada y salida de punta con reloj virtual |
| Total engine | 43 | |

Las pruebas del reloj utilizan una fuente controlada para avanzar
el tiempo sin esperar minutos u horas reales.

Los colaboradores externos se simulan donde corresponde.
No se necesita MongoDB ni el checker para ejecutar estos tests.

En la lectura válida de TemperatureSubscriberTest se comprueba también
que ts se normaliza a Long en milisegundos antes de enviarlo al repositorio.

## Ejecución

Desde PowerShell, en la raíz:

```powershell
& "C:\Program Files\Git\bin\bash.exe" ./scripts/test.sh
```

Con Java 25 y Maven locales:

```bash
mvn -B clean verify
```

## Resultados de pruebas

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| core | 23 | 0 | 0 | 0 |
| engine | 43 | 0 | 0 | 0 |
| Total | 66 | 0 | 0 | 0 |

Resultado comprobado con Java 25: BUILD SUCCESS.

## Validación con el checker

El 11 de octubre de 2026, hora de Uruguay, se obtuvo 8/8:

- Registro ante el entorno.
- PUT y GET del sitio.
- Consulta de control.
- INICIAR.
- Suscripción MQTT.
- Comandos a switches.
- Consulta de switches.

Registró 18 comandos y 204 consultas.

El checker comprueba interoperabilidad, no todas las reglas de negocio.

## Persistencia comprobada

En MongoDB se inspeccionaron las dos lecturas nuevas más recientes:

- sourceTs tenía tipo long.
- payload.ts tenía tipo long.
- sourceTsUnit era epoch_milliseconds.

Esto confirma la persistencia y el tipo de esas lecturas.

La conversión del histórico anterior no está comprobada.
El código nuevo no migra ese histórico automáticamente.

## Límites y continuación

Las pruebas no equivalen a una caída y recuperación de servicios reales.

Queda ampliar la validación de desconexiones, lecturas desactualizadas
y recuperación del entorno en la iteración 5.