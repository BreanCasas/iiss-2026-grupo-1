# Reporte ecowarm-run

- **Controlador:** grupo-1 (http://localhost:8080)
- **Fecha:** 2026-11-07T21:45:06.425971-03:00
- **Herramienta:** ecowarm-run c95d037, interop-v2

| escenario | resultado | lo que falló |
|---|---|---|
| E19 Un switch ya encendido | pasa |  |
| F01 Se cae el broker y vuelve | pasa |  |
| F03 Un termostato deja de publicar | pasa |  |
| F05 Se pierden los OFF | pasa |  |

## E19 Un switch ya encendido

`E19 (incluido)` · sha256 `a0b251bea03055af…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 98,3% de lo que podía encender (mínimo 80%) |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 59s (desde 1m) |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.004759200Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-213834-1/20261107-213834-2-E19.json` (serie e interacciones al lado)

## F01 Se cae el broker y vuelve

`F01 (incluido)` · sha256 `ec37491242314969…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | enRango | mejora | 3 de 3 habitaciones a ±0,5 °C al menos el 70% del tiempo; la peor, room1, el 100,0% |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |
| ✓ | reconecta | riesgo | se volvió a suscribir una vez tras la vuelta del broker; lo más lento, 0,9 s reales |
| — | apagaSinLecturas | mejora | no aplica: ninguna habitación quedó sin lecturas más de 15m |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003956520Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-213834-1/20261107-213834-3-F01.json` (serie e interacciones al lado)

## F03 Un termostato deja de publicar

`F03 (incluido)` · sha256 `8922387da4d52292…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | enRango | mejora | 3 de 3 habitaciones a ±0,5 °C al menos el 70% del tiempo; la peor, room4, el 99,2% |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 1m (desde 1m37s) |
| ✓ | apagaSinLecturas | mejora | 1 habitación sin lecturas: apagada pasados 15m |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003875940Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-213834-1/20261107-213834-4-F03.json` (serie e interacciones al lado)

## F05 Se pierden los OFF

`F05 (incluido)` · sha256 `e0190c3302f95ff6…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 87,3% de lo que podía encender (mínimo 80%) |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 1m (desde 30s) |
| ✓ | reintenta | riesgo | retomó 1 switch después de la falla, antes de 10m |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003043440Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-213834-1/20261107-213834-5-F05.json` (serie e interacciones al lado)
