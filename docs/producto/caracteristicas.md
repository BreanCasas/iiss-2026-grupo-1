# Características del producto - EcoWarm

## C1. Configuración del sitio por habitaciones

Asocia cada habitación con un termostato, un switch,
una temperatura objetivo y una potencia requerida.

La configuración también define el máximo eléctrico del sitio
y la franja de tarifa punta.

**Prototipo actual:** consulta y reemplazo del inventario mediante REST,
validación de datos y persistencia de la configuración en MongoDB.

## C2. Control de calefacción con restricciones eléctricas

Combina necesidades de temperatura, límite de potencia y horario tarifario
para decidir qué habitaciones reciben energía.

**Prototipo actual:** control reactivo por temperatura objetivo,
corte durante punta, selección por mayor déficit
y desempate determinista por ID.

El precalentamiento planificado y la optimización entre alternativas tarifarias
forman parte de la evolución del producto; no están implementados.

## C3. Anticipación mediante información meteorológica

Incorpora un pronóstico externo de hasta 48 horas como información adicional
para anticipar necesidades de calefacción.

**Estado:** capacidad prevista.
El prototipo no integra un servicio meteorológico.

## C4. Histórico de temperaturas y evaluación del desempeño

Conserva las mediciones por habitación y sus fechas
para permitir el análisis del comportamiento térmico de la vivienda.

**Prototipo actual:** almacenamiento de lecturas en MongoDB.

El timestamp de origen y el del payload normalizado
se guardan como Long en milisegundos.

La consulta del histórico mediante API y los cálculos de tiempos de
calentamiento, enfriamiento y otras métricas están pendientes.

## C5. Simulación de alternativas de consumo

Permite comparar configuraciones hipotéticas de potencia contratada
mediante proyecciones de temperatura, sin afectar el sistema real.

**Estado:** capacidad prevista.

El prototipo no incluye un modelo predictivo
ni una simulación independiente orientada al cliente.

## Correspondencia con las historias

| Historia | Capacidades relacionadas |
|---|---|
| H1. Confort con menor costo eléctrico | C1 y C2 |
| H2. Preparación ante una ola de frío | C3 |
| H3. Evaluación de la potencia contratada | C5 |

C4 aporta datos de medición para evaluar el funcionamiento pasado.