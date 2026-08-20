# Estado de RootEngine

## Revisión de alcance (2026-08-20)

`RootEngine` permanece en el código fuente, pero está fuera del camino de producción:

- No hay referencias de producción a `RootEngine`.
- `HardwareProfiler` sí tiene referencias activas desde `ThermalViewModel` para leer zonas térmicas; esto no activa `RootEngine` ni sus controles root/sysfs.
- No hay componentes Android, tareas de WorkManager ni acciones de UI que instancien o invoquen `RootEngine`.

No se elimina el archivo en este ciclo para mantener el cambio reversible. El único fallback que invoca `ActivityManager.killBackgroundProcesses` comprueba explícitamente `KILL_BACKGROUND_PROCESSES` y sale sin acción si no está concedido; la aplicación no declara ese permiso. Antes de conectar cualquier capacidad root/sysfs se debe hacer una revisión independiente de seguridad, permisos, validación de rutas y pruebas en dispositivos compatibles.

## Guardia de mantenimiento

Las búsquedas de referencias de producción (`RootEngine`, sus métodos de mutación y sus acciones `su`) deben mantenerse en cero hasta que exista una decisión explícita de producto. Esta nota documenta la cuarentena actual y evita interpretar la presencia del archivo como una capacidad disponible de la aplicación.
