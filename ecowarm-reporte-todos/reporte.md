# Reporte ecowarm-run

- **Controlador:** grupo-1 (http://localhost:8080)
- **Fecha:** 2026-11-07T23:53:26.005203-03:00
- **Herramienta:** ecowarm-run c95d037, interop-v2

| escenario | resultado | lo que falló |
|---|---|---|
| E02 1 habitación, entra todo | pasa |  |
| E04 2 habitaciones, no entran juntas | pasa |  |
| E11 Entrar en punta | no-pasa | consumoEnPunta (riesgo) |
| E14 Subir la temperatura de 2 habitaciones | pasa |  |
| E18 Bajar la contratada | pasa |  |
| E19 Un switch ya encendido | pasa |  |
| F01 Se cae el broker y vuelve | no-pasa | soloSwitchesDelSitio (contrato) |
| F03 Un termostato deja de publicar | pasa | apagaSinLecturas (mejora) |
| F05 Se pierden los OFF | pasa |  |

## E02 1 habitación, entra todo

`E02 (incluido)` · sha256 `9b0d459cd684d9da…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 1 switches (consulta o comando); el último en 0s |
| ✓ | enRango | mejora | 1 de 1 habitaciones a ±0,5 °C al menos el 80% del tiempo; la peor, room1, el 97,5% |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 2m (desde 1m16s) |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.004408080Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-2-E02.json` (serie e interacciones al lado)

## E04 2 habitaciones, no entran juntas

`E04 (incluido)` · sha256 `73b851b2b13c3667…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 2 switches (consulta o comando); el último en 0s |
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 86,3% de lo que podía encender (mínimo 80%) |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 2,5 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003405540Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-3-E04.json` (serie e interacciones al lado)

## E11 Entrar en punta

`E11 (incluido)` · sha256 `5794aaf06a2a34d7…` · firmado

**no-pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 5 switches (consulta o comando); el último en 0s |
| ✓ | enRango | mejora | 5 de 5 habitaciones a ±0,5 °C al menos el 80% del tiempo; la peor, room3, el 98,3% |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✗ | consumoEnPunta | riesgo | en punta hubo calefactores encendidos 1s en total, 0,00 kWh (desde 1h); en punta no se enciende nada |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 1m (desde 1m59s) |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T19:00:00.002775480Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-4-E11.json` (serie e interacciones al lado)

## E14 Subir la temperatura de 2 habitaciones

`E14 (incluido)` · sha256 `d2004265623adeb9…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 5 switches (consulta o comando); el último en 0s |
| ✓ | enRango | mejora | 5 de 5 habitaciones a ±0,5 °C al menos el 80% del tiempo; la peor, room1, el 100,0% |
| ✓ | conmutaciones | mejora | la que más conmutó, room3, 1,3 veces por hora (máximo 6,0) |
| ✓ | reacciona | riesgo | 2 reacciones dentro de 5m desde el ack (1h3s) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

**Observaciones**

- *menor* — encendioSinConocer: encendió room2 en 0s sin haber consultado 1 switch (room1): si alguno estaba encendido, se podía pasar de la contratada
- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003173280Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-5-E14.json` (serie e interacciones al lado)

## E18 Bajar la contratada

`E18 (incluido)` · sha256 `58bb278b887820b8…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 5 switches (consulta o comando); el último en 0s |
| ✓ | reacciona | riesgo | 1 reacción dentro de 2m desde el ack (1h1s) |
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 88,3% de lo que podía encender (mínimo 80%) |
| ✓ | conmutaciones | mejora | la que más conmutó, room3, 4,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003665040Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-6-E18.json` (serie e interacciones al lado)

## E19 Un switch ya encendido

`E19 (incluido)` · sha256 `a0b251bea03055af…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 99,6% de lo que podía encender (mínimo 80%) |
| ✓ | conmutaciones | mejora | la que más conmutó, room5, 2,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 59s (desde 37s) |

**Observaciones**

- *grave* — apagaDetenido: 1 OFF a 1 switch con el controlador detenido (room5): §5.2 pide dejar de comandar; apagar no es peligroso, pero tampoco le toca
- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003312540Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-7-E19.json` (serie e interacciones al lado)

## F01 Se cae el broker y vuelve

`F01 (incluido)` · sha256 `ec37491242314969…` · firmado

**no-pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | enRango | mejora | 3 de 3 habitaciones a ±0,5 °C al menos el 70% del tiempo; la peor, room1, el 100,0% |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✗ | soloSwitchesDelSitio | contrato | 2 comandos a 2 switches que no están en el sitio (sw-sim-room4, sw-sim-room5), el primero en 0s |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |
| ✓ | reconecta | riesgo | se volvió a suscribir una vez tras la vuelta del broker; lo más lento, 1,5 s reales |
| — | apagaSinLecturas | mejora | no aplica: ninguna habitación quedó sin lecturas más de 15m |

**Observaciones**

- *grave* — apagaDetenido: 3 OFF a 3 switches con el controlador detenido (room2, room3): §5.2 pide dejar de comandar; apagar no es peligroso, pero tampoco le toca
- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.002662260Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-8-F01.json` (serie e interacciones al lado)

## F03 Un termostato deja de publicar

`F03 (incluido)` · sha256 `8922387da4d52292…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | enRango | mejora | 3 de 3 habitaciones a ±0,5 °C al menos el 70% del tiempo; la peor, room4, el 97,5% |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 2m (desde 1m26s) |
| ✗ | apagaSinLecturas | mejora | 1 de 1 siguieron calentando sin lecturas: room2 14m59s desde 45m |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.003803220Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-9-F03.json` (serie e interacciones al lado)

## F05 Se pierden los OFF

`F05 (incluido)` · sha256 `e0190c3302f95ff6…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 87,3% de lo que podía encender (mínimo 80%) |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 1m (desde 20s) |
| ✓ | reintenta | riesgo | retomó 1 switch después de la falla, antes de 10m |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.002898120Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-233553-1/20261107-233553-10-F05.json` (serie e interacciones al lado)
