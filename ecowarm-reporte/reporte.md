# Reporte ecowarm-run

- **Controlador:** grupo-1 (http://localhost:8080)
- **Fecha:** 2026-11-07T23:26:29.262216900-03:00
- **Herramienta:** ecowarm-run c95d037, interop-v2

| escenario | resultado | lo que falló |
|---|---|---|
| E02 1 habitación, entra todo | pasa |  |

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
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 2m (desde 1m22s) |

**Observaciones**

- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.004002180Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-232428-3/20261107-232428-4-E02.json` (serie e interacciones al lado)
