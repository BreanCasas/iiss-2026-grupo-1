# Metodología de trabajo

## Evolución

En las iteraciones anteriores se adoptó Kanban para visualizar
y organizar el trabajo del equipo.

A partir de la Iteración 4, la consigna establece Scrumban:
se mantiene el tablero y se incorporan la planificación al inicio
de cada iteración y las revisiones semanales.

## Organización del trabajo

El equipo utiliza el Board de Jira para registrar y seguir
las actividades.

Las columnas representan el avance del trabajo:

- Por hacer: actividades pendientes.
- En curso: actividades que se están realizando.
- Listo: actividades terminadas y verificadas.

Se procura limitar el trabajo simultáneo y terminar las actividades
iniciadas antes de comenzar otras.

## Planificación de la iteración

Al inicio de cada iteración se deben:

- Revisar los requisitos y entregables de la consigna.
- Registrar las actividades necesarias en el Board.
- Ordenarlas según sus dependencias y prioridad.
- Distribuir el trabajo entre los integrantes.

## Revisiones semanales

Cada semana se debe revisar:

- El avance respecto a lo planificado.
- Los resultados de las pruebas.
- Los problemas y bloqueos encontrados.
- Los ajustes de alcance acordados con el Product Owner.

Los cambios de planificación deben reflejarse en el Board.

## Verificación del trabajo

Una actividad se considera terminada cuando su resultado cumple
el alcance previsto y se ha realizado la verificación correspondiente.

Para los cambios de código, se ejecutan las pruebas pertinentes.
Para la integración, se comprueba la comunicación con el checker.
Para la documentación, se revisa que describa la implementación actual.

## TDD en el core

El desarrollo de las reglas del core sigue el ciclo:

1. Escribir una prueba que describa el comportamiento requerido.
2. Ejecutarla y comprobar que detecta el comportamiento faltante.
3. Implementar la regla necesaria para que pase.
4. Mejorar el código conservando las pruebas aprobadas.

El criterio de adecuación de las pruebas se documenta en
`docs/tests/criterio-tests.md`.

## Evidencias

La planificación y las revisiones realizadas se registran
en Jira y en las notas del equipo.

Este documento describe la metodología adoptada; no sustituye
el registro de las reuniones ni demuestra por sí mismo
que se hayan realizado.