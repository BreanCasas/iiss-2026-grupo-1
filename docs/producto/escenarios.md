# Escenarios de uso - EcoWarm

## E1. Dos habitaciones frías antes de entrar en punta

**Historia relacionada:** H1.

**Estado:** representado en el prototipo actual.

María utiliza calefacción en el Living y el Dormitorio.
El sitio dispone de 1,2 kW para calefacción y cada habitación requiere 1,2 kW.

Es un lunes a las 16:59.
La tarifa punta comienza a las 17:00 y termina a las 23:00.
Ambos switches están apagados.

| Habitación | Temperatura actual | Objetivo | Déficit |
|---|---:|---:|---:|
| Living | 19 °C | 21,5 °C | 2,5 °C |
| Dormitorio | 17 °C | 20 °C | 3 °C |

**Disparador:** llegan las lecturas y el controlador ejecuta un ciclo.

**Desarrollo:**

1. El controlador identifica que ambas habitaciones necesitan calefacción.
2. Como no alcanza la potencia para las dos, selecciona el Dormitorio,
   que tiene mayor déficit respecto a su objetivo.
3. Enciende el Dormitorio y mantiene el Living apagado.
4. Al alcanzar las 17:00, ordena apagar la calefacción por entrada en punta.
5. A las 23:00 vuelve a evaluar las lecturas disponibles y la potencia.

**Resultado esperado:** respetar el límite de potencia y el corte en punta.

La selección posterior depende de las temperaturas de ese momento.
No se garantiza que ambas habitaciones alcancen el objetivo simultáneamente.

## E2. Regreso a casa durante una ola de frío

**Historia relacionada:** H2.

**Estado:** escenario futuro, no implementado en la iteración 4.

Diego sale de casa por la mañana.
La temperatura interior todavía es confortable,
pero el pronóstico de las próximas 48 horas anuncia un descenso importante.

**Disparador:** se obtiene un pronóstico actualizado que anticipa la ola de frío.

**Desarrollo previsto:**

1. El sistema considera el pronóstico junto con la situación de la vivienda.
2. Evalúa si conviene adelantar la calefacción bajo las restricciones eléctricas.
3. Ajusta el funcionamiento antes de que el descenso aparezca en los sensores interiores.

**Resultado esperado:** reducir la pérdida de confort durante la ausencia del usuario.

El prototipo actual decide con las temperaturas recibidas y el horario.
No consulta pronósticos ni calcula pérdidas térmicas futuras.

## E3. Comparación antes de reducir la potencia contratada

**Historia relacionada:** H3.

**Estado:** escenario futuro, no implementado en la iteración 4.

Laura dispone de cuatro habitaciones con calefacción
y considera reducir su potencia contratada.

Desea comparar el funcionamiento actual con una alternativa que permita
menos habitaciones encendidas simultáneamente.

**Disparador:** solicita evaluar una potencia hipotética menor.

**Desarrollo previsto:**

1. Conserva la configuración real de la vivienda como referencia.
2. Introduce la potencia alternativa en una simulación independiente.
3. Compara las proyecciones de confort de ambas configuraciones.
4. Utiliza la comparación para decidir sobre el contrato eléctrico.

**Resultado esperado:** obtener información para decidir
sin modificar los switches ni el inventario activo.

El reloj virtual y el checker actuales sirven para probar el controlador.
No constituyen una herramienta de proyección térmica para el cliente.