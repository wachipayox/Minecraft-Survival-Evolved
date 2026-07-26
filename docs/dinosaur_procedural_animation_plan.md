# Plan de animación procedural para dinosaurios

Fecha de inspección: 2026-07-26.

Estado: fase 2 implementada. La entidad mínima se documenta en
`dinosaur_prototype_phase.md` y el muestreo de terreno, pitch/roll y gizmos en
`dinosaur_procedural_terrain_phase.md`. La puerta visual de jitter permanece
abierta antes de añadir corrección independiente de patas.

## Estado técnico verificado

| Componente | Versión o estado |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.84 |
| Java | 25 |
| Gradle | 9.2.1 |
| ModDevGradle | 2.0.142 |
| Mappings | Nombres oficiales de Mojang incluidos por el MDK; no hay Parchment ni otra capa |
| GeckoLib | 5.5.2 para NeoForge 26.1.2 |
| Sponge Mixin | 0.8.7 mediante `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7` |
| ID del mod | `mc_evolved` |
| Nombre | Minecraft Survival Evolved |
| Paquete base | `com.wachi.mse` |
| Clase principal | `com.wachi.mse.MseMod` |

`gradlew build` terminó correctamente tanto antes de modificar la plantilla como después del cambio de identidad, la activación de Mixins y la instalación de GeckoLib.

El repositorio inicial era el MDK de NeoForge sin contenido propio. No había registro de entidades, entidades personalizadas, renderizadores, modelos, texturas, animaciones ni archivos de GeckoLib. El contenido de ejemplo de la plantilla se eliminó para dejar una base limpia.

Mixins está preparado mediante `mc_evolved.mixins.json`, el bloque `[[mixins]]` del descriptor del mod y el paquete `com.wachi.mse.mixin`. Las listas están vacías deliberadamente: se añadirá un Mixin solo si una fase demuestra que no existe una API pública apropiada.

## Arquitectura propuesta

La entidad conservará una única hitbox principal de Minecraft para movimiento, colisión con bloques, navegación y posición autoritativa. El sistema se separará así:

1. `PrototypeDinosaurEntity`: estado, IA, animaciones lógicas y partes autoritativas.
2. `DinosaurProceduralConfig`: valores inmutables por especie, incluidos huesos, apoyos y partes.
3. `DinosaurBodyRotationControl` y, solo si hace falta, `DinosaurMoveControl`: giro lógico limitado en servidor.
4. `DinosaurProceduralState`: estado lógico pequeño e interpolable.
5. `DinosaurTerrainSampler`: solver geométrico común y determinista para cliente y servidor.
6. `DinosaurProceduralPose`: resultado común de pitch, roll, validez por eje y contactos.
7. `DinosaurProceduralAnimator`: aplica la pose a snapshots de huesos después de las animaciones JSON.
8. `DinosaurPartEntity`: selección y daño por zona sin IA, guardado ni paquete de aparición independiente.
9. `DinosaurDebugRenderer`: puntos, direcciones, valores y hitboxes mediante el sistema de gizmos actual.

El modelo y las animaciones no se generarán ni se renombrarán a ciegas. Primero se incorporará el modelo del concurso y se leerá su jerarquía. Si aún no existe, se hará una geometría temporal mínima y claramente sustituible con los huesos exigidos.

## APIs concretas verificadas

### Registro y entidad

- `DeferredRegister.createEntities(String)` y `DeferredRegister.Entities.registerEntityType(...)`.
- `EntityAttributeCreationEvent.put(...)` para atributos.
- `EntityRenderersEvent.RegisterRenderers.registerEntityRenderer(...)` en cliente.
- `GeoEntity`, `GeckoLibUtil.createInstanceCache(...)`, `AnimatableManager.ControllerRegistrar` y `AnimationController` de GeckoLib 5.
- `DefaultedEntityGeoModel`, con recursos bajo `assets/mc_evolved/geckolib/models/entity/`, `assets/mc_evolved/geckolib/animations/entity/` y `assets/mc_evolved/textures/entity/`.

### Integración procedural GeckoLib 5

- El renderer capturará datos con `GeoRenderer.addRenderData(...)` en un `DataTicket<DinosaurProceduralPose>`.
- El punto correcto de modificación es `GeoRenderer.adjustModelBonesForRender(RenderPassInfo<R>, BoneSnapshots)`.
- `RenderPassInfo.create(...)` registra primero `applyAnimationControllers` y después `adjustModelBonesForRender`; por tanto, la corrección será realmente posterior a la animación base.
- `BoneSnapshots.get(...)` devuelve el `BoneSnapshot` de cada hueso para ese frame.
- `BoneSnapshot.setRotX/Y/Z(...)` trabaja en radianes; `setTranslateY(...)` usa unidades del modelo y GeckoLib aplica la conversión a bloques.
- Los datos de la entidad se copiarán durante la extracción del render state. La fase de render no conservará ni consultará una referencia autoritativa de servidor.

### Terreno

- `BlockState.getCollisionShape(BlockGetter, BlockPos, CollisionContext)`.
- `VoxelShape.max(Direction.Axis.Y, ..., ...)` o `VoxelShape.clip(...)` para respetar slabs, escaleras y formas parciales.
- Un `MutableBlockPos` reutilizable y cuatro offsets locales rotados por el yaw corporal.
- Búsqueda vertical acotada; no se escanearán volúmenes ni se hará un raycast por hueso.

### Movimiento y orientación

- `Mob.createBodyControl()` permite instalar un `BodyRotationControl` propio.
- `BodyRotationControl.clientTick()`, pese a su nombre, es el punto usado por `Mob.tickHeadTurn(...)`.
- `LookControl.setLookAt(...)`, `LookControl.tick()` y `LookControl.clampHeadRotationToBody()`.
- `MoveControl.setWantedPosition(...)`, `MoveControl.tick()` y su `rotlerp(...)` protegido.
- `yBodyRot`, `yHeadRot`, sus valores anteriores y `Mth.rotLerp`/`Mth.wrapDegrees` para interpolación angular.

La primera versión limitará el giro con `BodyRotationControl` y los límites de cabeza del mob. Solo se sustituirá `MoveControl` si las pruebas muestran que su máximo vanilla de 90 grados por tick produce giros visibles demasiado bruscos.

### Multipartes

- `net.neoforged.neoforge.entity.PartEntity<T>`.
- El padre sobrescribirá `isMultipartEntity()`, `getParts()` y `setId(int)`; los IDs de las partes serán sucesivos al del padre.
- Cada parte sobrescribirá `hurtServer(ServerLevel, DamageSource, float)`, `getDimensions(Pose)`, `isPickable()` y `shouldBeSaved()`.
- `PartEntity.getAddEntityPacket(...)` rechaza paquetes independientes.
- `ServerLevel` y `ClientLevel` registran y retiran automáticamente las partes devueltas por `getParts()` al comenzar o terminar el seguimiento.
- La implementación del Ender Dragon de 26.1.2 es la referencia local para ciclo de vida, IDs y redirección de daño.

Las posiciones autoritativas de cabeza, torso y cola se calcularán con offsets lógicos, yaw y postura. Nunca se leerán matrices de huesos del renderer para posicionarlas.

### Depuración

- `RegisterDebugRenderersEvent.register(...)`.
- `DebugRenderer.SimpleDebugRenderer.emitGizmos(...)`.
- `Gizmos.cuboid(...)`, `Gizmos.point(...)`, `Gizmos.arrow(...)` y texto de depuración.

El renderer se registrará solo en código cliente y saldrá inmediatamente si el flag de depuración está desactivado.

## Archivos previstos

Se crearán de forma incremental, no todos en la siguiente fase:

- `com/wachi/mse/registry/MseEntities.java`
- `com/wachi/mse/entity/dinosaur/PrototypeDinosaurEntity.java`
- `com/wachi/mse/entity/dinosaur/config/DinosaurProceduralConfig.java`
- `com/wachi/mse/entity/dinosaur/config/DinosaurBoneNames.java`
- `com/wachi/mse/entity/dinosaur/control/DinosaurBodyRotationControl.java`
- `com/wachi/mse/entity/dinosaur/control/DinosaurMoveControl.java`, solo si la puerta de giro lo exige
- `com/wachi/mse/entity/dinosaur/part/DinosaurPartEntity.java`
- `com/wachi/mse/entity/dinosaur/part/DinosaurHitZone.java`
- `com/wachi/mse/entity/dinosaur/procedural/DinosaurProceduralState.java`
- `com/wachi/mse/entity/dinosaur/procedural/DinosaurTerrainSampler.java`
- `com/wachi/mse/entity/dinosaur/procedural/DinosaurProceduralPose.java`
- `com/wachi/mse/entity/dinosaur/procedural/DinosaurTerrainSample.java`
- `com/wachi/mse/client/animation/DinosaurProceduralAnimator.java`
- `com/wachi/mse/client/model/PrototypeDinosaurModel.java`
- `com/wachi/mse/client/renderer/PrototypeDinosaurRenderer.java`
- `com/wachi/mse/client/debug/DinosaurDebugRenderer.java`
- `com/wachi/mse/client/MseClientEvents.java`
- recursos del prototipo bajo `assets/mc_evolved/`
- `docs/dinosaur_procedural_animation_results.md` al cerrar el prototipo

Se modificarán `MseMod.java`, `MseModClient.java` y los recursos de idioma para conectar registros, eventos y controles de depuración.

## División cliente-servidor

Servidor y lógica común:

- movimiento, navegación, objetivo, yaw lógico y postura;
- solver de terreno y pose corporal autoritativa bajo demanda;
- creación y actualización de multipartes;
- aplicación del daño y multiplicadores por zona;
- flags de bloqueo procedural que afecten al combate;
- `SynchedEntityData` únicamente para estados lógicos que el cliente no pueda derivar.

Solo cliente:

- suavizado visual de pitch, roll, altura y patas;
- distribución visual del cuello entre huesos;
- `DataTicket`, snapshots de GeckoLib y gizmos;
- descarte o reducción de actualización por distancia.

No se sincronizarán alturas de suelo, offsets de patas ni transformaciones de
huesos. Cliente y servidor pueden derivar la pose con el mismo solver a partir
de su copia del terreno; el servidor sólo lo ejecutará cuando una operación
lógica necesite una parte inclinada. La rotación lógica ya disponible en la
entidad se reutilizará antes de añadir nuevos datos sincronizados.

## Orden de implementación y puertas

1. Entidad mínima: registrar el prototipo, atributos, renderer y animaciones idle/walk sin corrección procedural. Verificar cliente y servidor dedicado.
2. Configuración y cuatro muestras: visualizar primero los puntos; después calcular pitch/roll sin patas. Probar plano, slab, escaleras y un bloque.
3. Puerta 1: no continuar si existe vibración severa quieto. Ajustar histéresis, frecuencia y límites.
4. Corrección vertical independiente de cuatro patas, ponderada por apoyo aproximado.
5. Puerta 2: comparar caminando y quieto con el sistema activado y desactivado. No añadir IK si la traslación vertical convence.
6. Giro gradual: yaw lógico de servidor y distribución visual `neck_1`/`neck_2`/`head`; añadir pitch después del yaw.
7. Puerta 3: probar objetivos a 30, 60, 90 y 180 grados, ataques y pérdida del objetivo.
8. Multipartes: empezar con cabeza, torso y cola; ampliar a cuello/cadera solo si son estables.
9. Puerta 4: melee, proyectiles, muerte, descarga de chunks y servidor dedicado.
10. Completar depuración, pruebas con varias entidades y documento de resultados.

## Riesgos y criterios de simplificación

- No hay modelo ni animaciones: es el bloqueo principal para una validación visual real.
- GeckoLib 5 usa render states y snapshots distintos de GeckoLib 4; no se reutilizarán ejemplos antiguos.
- Las formas de escaleras pueden dar discontinuidades. Se aplicarán umbral, histéresis y suavizado; no física adicional.
- Un cuerpo demasiado largo puede necesitar seis muestras. Se empezará con cuatro y solo se ampliará si falla visualmente.
- Si cuatro sondas acotadas por entidad resultan costosas, se bajará su frecuencia y se reutilizarán resultados según movimiento y distancia.
- Si detectar la fase de apoyo desde la animación no es estable, se usará `walkAnimationPos`/`walkAnimationSpeed`.
- Si la traslación vertical funciona, se cancelan rotación articular e IK.
- Si el cuello interfiere con ataques, el estado de ataque reducirá sus pesos a cero.
- Si los multiplicadores de daño complican el primer multipartes, todas las zonas redirigirán daño 1:1.
- Si una API pública cubre la necesidad, no se añadirá un Mixin.
- Cualquier fase que rompa servidor dedicado, cause jitter severo o deje el build inestable se revierte o simplifica antes de continuar.

## Primer cambio mínimo siguiente

Crear únicamente `MseEntities`, `PrototypeDinosaurEntity`, su registro de atributos y un renderer GeckoLib con idle/walk. El objetivo será poder invocar una entidad convencional, sin terreno, cuello procedural ni multipartes, y verificar el modelo y los nombres reales de sus huesos antes de construir el sistema reutilizable.
