# Reporte ecowarm-run

- **Controlador:** grupo-1 (http://localhost:8080)
- **Fecha:** 2026-11-07T21:17:49.102230400-03:00
- **Herramienta:** ecowarm-run c95d037, interop-v2

| escenario | resultado | lo que falló |
|---|---|---|
| F03 Un termostato deja de publicar | pasa |  |

## F03 Un termostato deja de publicar

`F03 (incluido)` · sha256 `8922387da4d52292…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | enRango | mejora | 3 de 3 habitaciones a ±0,5 °C al menos el 70% del tiempo; la peor, room3, el 96,7% |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 1m59s (desde 1m) |
| ✓ | apagaSinLecturas | mejora | 1 habitación sin lecturas: apagada pasados 15m |

**Observaciones**

- *grave* — apagaDetenido: 3 OFF a 3 switches con el controlador detenido (room3, room4): §5.2 pide dejar de comandar; apagar no es peligroso, pero tampoco le toca
- *menor* — formatoInstante: instantes con más precisión que milisegundos en iniciar, control-consultado (p. ej. 2026-11-03T13:00:00.004215300Z): interop-v2 los fija en ISO-8601 con milisegundos
- *menor* — formatoNumero: números en notación exponencial en iniciar, control-consultado (p. ej. 6E+1)

Detalle: `20261107-211547-1/20261107-211547-2-F03.json` (serie e interacciones al lado)
