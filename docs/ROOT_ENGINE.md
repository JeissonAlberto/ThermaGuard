# Estado de RootEngine

## Revisión de alcance (2026-08-20)

`RootEngine` es una fachada de compatibilidad completamente inerte. ThermaGuard
no solicita root ni ejecuta comandos de shell, no escribe nodos `sysfs`, no
modifica CPU/GPU, brillo o radios y no termina procesos.

Las funciones históricas permanecen únicamente para evitar referencias de
código accidentales durante la transición. Todas las operaciones mutantes
retornan explícitamente `false`; `activateSuperCool` devuelve un resultado con
todas sus capacidades en `false` y `appsKilled = 0`; la lectura de frecuencias
retorna un mapa vacío. No existe una ruta de producción que pueda convertir
esta fachada en control del sistema.

`HardwareProfiler` es independiente y solo se utiliza para leer zonas térmicas
expuestas por el kernel durante el diagnóstico. Esa lectura no habilita
RootEngine ni implica control de hardware.

## Guardia de mantenimiento

`RootEngineTest` verifica que root, CPU/GPU, radios, brillo, terminación de
procesos y el resultado de super-enfriamiento permanezcan explícitamente no
disponibles. No se deben añadir permisos invasivos, llamadas a `su`, escrituras
`sysfs` ni acciones de control del sistema a esta aplicación.
