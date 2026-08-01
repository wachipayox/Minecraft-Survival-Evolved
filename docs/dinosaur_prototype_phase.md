# Fase 1: entidad GeckoLib mínima

Fecha de cierre: 2026-07-26.

## Resultado

La primera puerta del plan queda implementada. `prototype_dinosaur` es una
entidad convencional de NeoForge que se registra como
`mc_evolved:prototype_dinosaur`, dispone de atributos propios, pasea mediante
la navegación vanilla y usa un renderer GeckoLib exclusivo del cliente.

El controlador `movement` selecciona:

- `animation.prototype_dinosaur.idle` cuando la entidad está quieta;
- `animation.prototype_dinosaur.walk` cuando GeckoLib detecta movimiento.

Puede invocarse en un mundo con:

```mcfunction
/summon mc_evolved:prototype_dinosaur ~ ~ ~
```

## Pruebas de escala

La fixture `mc_evolved:giant_prototype_dinosaur` ya no existe. El mismo tipo de
entidad se prueba a cualquier tamaño mediante el atributo vanilla:

```mcfunction
/attribute @e[type=mc_evolved:prototype_dinosaur,limit=1] minecraft:scale base set 10
```

El modelo, las partes anatómicas, hitboxes precisas, asiento, altura de ojos,
sondas de terreno y distancias procedurales se derivan de esa escala en tiempo
de ejecución. Esto también permite probar escalas menores que `1` sin registrar
una entidad distinta.

## Activo temporal

Se creó un modelo temporal y sustituible en Blockbench para poder validar la
integración antes de recibir el modelo definitivo:

- 23 huesos y 23 cubos;
- raíz explícita `root`;
- jerarquías separadas para torso, cuello, cabeza, mandíbula, cuatro patas y
  tres segmentos de cola;
- textura pixel art de 128 × 128;
- cuatro pies apoyados en Y=0;
- clips `idle` de 2 segundos y `walk` de 1 segundo, ambos en bucle;
- clip `bite` de 0,8 segundos, reproducido una vez por ataque.

El `.bbmodel` conserva 3 claves nativas para `idle`, 6 para `walk` y 24 para
`bite`. Los
exports de GeckoLib sólo animan `body.position` en esta fase para mantener una
base deliberadamente segura; la locomoción articular y las correcciones
procedurales pertenecen a fases posteriores.

![Vista del dinosaurio prototipo](images/prototype_dinosaur.png)

## Verificaciones realizadas

- El exportador GeckoLib confirmó 23 huesos, 23 cubos, tres animaciones,
  referencias de huesos válidas y UV de seis caras por cubo.
- La auditoría local confirmó textura 128 × 128, extremos de bucle coincidentes
  y claves nativas persistentes tras cerrar y reabrir el `.bbmodel`.
- `gradlew clean build` terminó correctamente.
- El servidor aislado cargó correctamente el registro de especies y el perfil
  `mc_evolved:prototype_dinosaur`.
- El JAR contiene clases, descriptor, configuración de Mixins y los tres
  recursos runtime de GeckoLib.
- El JAR no contiene el proyecto `.bbmodel`.

No se inició un servidor dedicado porque el workspace aún no tiene un
`eula.txt`; aceptar la EULA no se automatiza. La separación cliente/servidor se
mantiene: el registro de la entidad y sus atributos es común, mientras que el
renderer sólo se carga en `Dist.CLIENT`.

## Alcance pendiente

Esta fase no incluye muestreo de terreno, adaptación individual de patas,
orientación procedural de cuello, giro gradual, multipartes ni depuración con
gizmos. El siguiente incremento es la fase 2 del plan: configuración por
especie, cuatro muestras visuales de terreno y depuración de sus puntos antes
de modificar la pose.
