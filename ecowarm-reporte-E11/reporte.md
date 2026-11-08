# Reporte ecowarm-run

- **Controlador:** grupo-1 (http://localhost:8080)
- **Fecha:** 2026-11-07T22:07:47.151414-03:00
- **Herramienta:** ecowarm-run c95d037, interop-v2

| escenario | resultado | lo que falló |
|---|---|---|
| E11 Entrar en punta | pasa |  |

## E11 Entrar en punta

`E11 (incluido)` · sha256 `5794aaf06a2a34d7…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 5 switches (consulta o comando); el último en 4s |
| ✓ | enRango | mejora | 5 de 5 habitaciones a ±0,5 °C al menos el 80% del tiempo; la peor, room3, el 98,3% |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | consumoEnPunta | riesgo | en punta no hubo nada encendido |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 59s (desde 1m45s) |

Detalle: `20261107-220545-1/20261107-220545-2-E11.json` (serie e interacciones al lado)
