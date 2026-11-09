# Reloj virtual — VirtualClock

## Objetivo

VirtualClock representa el tiempo del simulador dentro del controlador.

Permite que el tiempo virtual avance a una velocidad distinta del tiempo
real. Por ejemplo, con un factor de aceleración 60, un segundo real
representa un minuto virtual.

Se encuentra en:

`modules/engine/src/main/java/com/ioteste/engine/time/VirtualClock.java`

## Responsabilidad

La clase calcula el instante virtual actual a partir de:

- Un instante virtual inicial.
- Un factor de aceleración.
- El tiempo real transcurrido desde su creación.

No modifica la hora del sistema operativo ni espera activamente.
El instante se calcula cada vez que se invoca now().

## Constructor

```java
public VirtualClock(
        Instant initialTime,
        long timeFactor,
        LongSupplier nanoTimeSource)
```

| Parámetro | Significado |
|---|---|
| initialTime | Instante virtual desde el cual comienza el reloj. |
| timeFactor | Factor de aceleración expresado en milésimas. |
| nanoTimeSource | Fuente que proporciona una medida de tiempo transcurrido en nanosegundos. |

Al construirse, el reloj conserva los parámetros y obtiene una primera
medición de nanoTimeSource, almacenada en initialNanos.

El factor debe ser mayor que cero. Si es cero o negativo, el constructor
lanza IllegalArgumentException.

## Representación del factor

El factor se almacena como un entero long escalado a milésimas.

| Factor recibido por la API | Valor interno | Comportamiento |
|---:|---:|---|
| 0,5 | 500 | El tiempo virtual avanza a la mitad de velocidad. |
| 1 | 1000 | El tiempo virtual avanza a velocidad real. |
| 1,5 | 1500 | El tiempo virtual avanza una vez y media más rápido. |
| 60 | 60000 | Un segundo real equivale a un minuto virtual. |

La constante SCALE vale 1000 y permite convertir el factor interno
a su proporción correspondiente.

La conversión entre el número decimal de la API y el entero escalado
se realiza en los adaptadores JSON, no dentro de VirtualClock.

## Cálculo de now()

El método calcula el tiempo virtual en tres pasos.

### 1. Medir el tiempo real transcurrido

```java
long elapsedNanos =
        nanoTimeSource.getAsLong() - initialNanos;
```

Se resta la medición inicial a la medición actual.

Estas mediciones no representan fechas. Su diferencia indica cuánto
tiempo transcurrió desde la creación del reloj.

### 2. Aplicar el factor de aceleración

```java
long virtualElapsedNanos = BigInteger.valueOf(elapsedNanos)
        .multiply(BigInteger.valueOf(timeFactor))
        .divide(SCALE)
        .longValueExact();
```

La fórmula utilizada es:

```text
tiempo virtual transcurrido =
    tiempo real transcurrido × factor interno / 1000
```

Se utiliza aritmética entera. Si la división produce una fracción de
nanosegundo, esa fracción se descarta.

### 3. Obtener el instante virtual

```java
return initialTime.plusNanos(virtualElapsedNanos);
```

El resultado es un Instant: un punto de la línea temporal que no contiene
una zona horaria local.

## Ejemplo

Supongamos:

- Instante inicial: 2026-10-05T19:59:00Z.
- Factor interno: 60000, equivalente a una aceleración de 60.
- Tiempo real transcurrido: 1 segundo.

El cálculo es:

```text
1.000.000.000 ns × 60000 / 1000
    = 60.000.000.000 ns
    = 60 segundos virtuales
```

Por lo tanto:

```text
Instante inicial: 2026-10-05T19:59:00Z
Instante actual:  2026-10-05T20:00:00Z
```

En America/Montevideo, estos instantes corresponden a las 16:59 y
las 17:00, respectivamente. El controlador puede detectar así el ingreso
a la franja de tarifa punta después de un segundo real.

## Fuente de tiempo

En producción, ControlService entrega System::nanoTime como fuente.

System.nanoTime() permite medir intervalos sin depender de ajustes
del reloj de fecha y hora del sistema, como una sincronización horaria.

Su valor aislado no es una fecha ni debe interpretarse como tiempo
desde Unix epoch. Solo se utilizan diferencias entre mediciones.

Aunque otras partes del sistema trabajan en milisegundos, VirtualClock
mantiene nanosegundos para el cálculo del tiempo transcurrido y la
construcción del Instant.

Los campos fechaHora e iniciadoEn de ControlStatus se serializan
en UTC con precisión de milisegundos. El formato de salida no modifica
la precisión interna del reloj.

## Uso de BigInteger

La multiplicación entre el tiempo transcurrido y el factor podría superar
el rango de long antes de dividir por 1000.

BigInteger evita ese desbordamiento intermedio y permite realizar
la multiplicación y la división con enteros de precisión arbitraria.

La conversión final utiliza longValueExact(). Si el resultado final
no cabe en long, se lanza ArithmeticException en lugar de obtener
un valor desbordado silenciosamente.

Esto no elimina todos los límites: el tiempo virtual transcurrido final
debe caber en long y el Instant resultante debe estar dentro del rango
admitido por java.time.

## Integración con ControlService

Cuando se recibe un comando INICIAR:

1. ControlService valida la fecha y el factor.
2. Convierte la fecha recibida a Instant.
3. Crea un nuevo VirtualClock.
4. Conserva el factor y el instante real de inicio del control.

Una nueva orden INICIAR reemplaza el reloj anterior y resincroniza
el tiempo virtual con los valores recibidos.

Mientras el control está en ejecución, status() consulta clock.now()
para informar el instante virtual actual.

Al recibir DETENER, ControlService elimina la referencia al reloj
y cambia el estado del controlador a detenido.

Con el control detenido, HeatingEngine no envía comandos a switches.
DETENER no representa una orden de apagado general.

## Zona horaria y tarifa punta

VirtualClock no decide la zona horaria ni evalúa tarifas.

HeatingEngine obtiene el Instant virtual y lo convierte a la zona
del sitio, proporcionada por ControlService.

La zona predeterminada es America/Montevideo y puede configurarse
mediante ECOWARM_ZONE.

PeakSchedule evalúa si esa fecha y hora local pertenece a la franja
punta. De esta forma se separan:

- El cálculo del instante virtual.
- La conversión a hora local.
- La regla de tarifa punta.

## Relación con la planificación de ciclos

VirtualClock calcula qué hora virtual es, pero no programa tareas.

TickPlanner calcula cuánto tiempo real conviene esperar hasta el próximo
ciclo, considerando el factor y el siguiente cambio de franja.

TickLoop ejecuta el ciclo y programa la siguiente ejecución con la espera
calculada.

Esta coordinación evita depender únicamente de una espera fija cuando
el tiempo está acelerado. Los tiempos efectivos también dependen de
la ejecución de las tareas y de las comunicaciones externas.

### Margen preventivo de entrada en punta

HeatingEngine utiliza un margen de 2000 milisegundos reales antes
del inicio de punta para iniciar los apagados y evitar nuevos encendidos.

El tiempo virtual restante se compara con el margen real convertido
mediante el factor vigente.

Por ejemplo, con factor 60, un margen de 2000 milisegundos reales
equivale a 120 segundos virtuales.

Dentro del margen, el motor puede evaluar la decisión del core usando
el instante de inicio de punta. Esto no adelanta VirtualClock ni cambia
la hora informada por GET /control.

El margen reduce el riesgo de llegar a punta con calefactores encendidos,
pero no constituye una garantía de tiempo real ante latencias superiores
al margen disponible.

## Diferencia con el vencimiento de temperaturas

El reloj virtual determina el tiempo utilizado para evaluar las reglas
horarias del controlador.

La vigencia de las temperaturas se mide por separado en
TemperatureSubscriber, mediante Clock y milisegundos desde la recepción.

Mientras el control está en ejecución, el plazo considera 5 minutos
virtuales y un máximo de 30 segundos reales.

Para un factor f expresado como número del protocolo:

```text
vigencia en milisegundos reales =
    mínimo(30000, máximo(1, parte entera de 300000 / f))
```

| Factor | Vigencia real |
|---:|---|
| 1 | 30 segundos |
| 10 | 30 segundos |
| 60 | 5 segundos |

Con el control detenido se utiliza el límite de 30 segundos reales.

El plazo se calcula con el factor vigente. Si cambia, la antigüedad
acumulada se evalúa con ese factor; no se integra el historial de factores.

El instante ts enviado por el termostato se conserva en el histórico,
pero no se utiliza para renovar ni calcular la vigencia local.

Una lectura inválida no renueva el instante de recepción de la última
lectura válida.

## Pruebas

La fuente LongSupplier se puede reemplazar en las pruebas por una
fuente controlada, por ejemplo un AtomicLong.

Esto permite avanzar la medición de tiempo sin usar Thread.sleep()
ni esperar realmente.

Las pruebas relacionadas se encuentran en:

- `modules/engine/src/test/java/com/ioteste/engine/time/VirtualClockTest.java`
- `modules/engine/src/test/java/com/ioteste/engine/control/ControlServiceClockTest.java`
- `modules/engine/src/test/java/com/ioteste/engine/time/VirtualHeatingTest.java`
- `modules/engine/src/test/java/com/ioteste/engine/time/TickPlannerTest.java`

Estas pruebas verifican el reloj y su colaboración con el control
y la planificación temporal.

TemperatureExpiryTest verifica el vencimiento de temperaturas,
incluyendo el factor de aceleración. HeatingEngineTest comprueba
el comportamiento del motor ante la entrada en punta y su margen
preventivo.

La validación con ecowarm-run incluye E11, entrada en punta, y F03,
ausencia prolongada de publicaciones de un termostato. Ambos escenarios
pasaron en la batería completa de la iteración 6, sin observaciones.

## Límites de la implementación

- El factor debe ser positivo.
- El constructor no valida explícitamente que initialTime y
  nanoTimeSource sean distintos de null.
- La fuente de medición debe ser apropiada para medir tiempo transcurrido;
  en producción se utiliza System.nanoTime().
- El factor queda fijo durante la vida de cada instancia. Para cambiarlo,
  ControlService crea un reloj nuevo mediante INICIAR.
- El cálculo final puede lanzar una excepción si excede los rangos
  numéricos o temporales admitidos.
- El reloj no se persiste. Después de reiniciar la aplicación,
  el controlador requiere una nueva orden INICIAR para establecerlo.
- El cálculo del instante no garantiza que los comandos externos se
  ejecuten exactamente en ese momento.

Las limitaciones generales y las mejoras futuras se describen
en [roadmap](roadmap.md).