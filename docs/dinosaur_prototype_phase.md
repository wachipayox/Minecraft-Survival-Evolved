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

## Variante gigante de pruebas

`mc_evolved:giant_prototype_dinosaur` hereda íntegramente la entidad del
prototipo y reutiliza el mismo modelo, textura, animaciones y renderer:

```mcfunction
/summon mc_evolved:giant_prototype_dinosaur ~ ~ ~
```

Su atributo vanilla `minecraft:scale` vale `10.0` y el registro declara
explícitamente una hitbox física de `12 × 14` bloques para esta fixture. La
anatomía visual y seleccionable se escala por separado mediante partes
procedurales, y el asiento y la altura de ojos siguen la escala. La
configuración procedural se deriva mediante escalado geométrico: offsets,
longitudes y radios de las patas, sondas de terreno, soporte, corrección
vertical, empuje de caída, distancia de anticipación y radio de giro aumentan
de forma coherente. Los ángulos, fases de marcha y nombres de huesos no se
duplican ni cambian.

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
- El servidor aislado invocó simultáneamente el prototipo normal y el gigante;
  confirmó `Scale = 10.0` y `Step Height = 6.0` para la variante gigante.
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
