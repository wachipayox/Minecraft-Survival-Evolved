# Fase 2: muestreo de terreno y pose corporal

Fecha de implementación: 2026-07-26.

## Resultado

El prototipo dispone de la primera capa procedural exclusivamente visual. El
renderer toma cuatro muestras de suelo alineadas con los pivotes reales de los
pies, calcula inclinación longitudinal y lateral, suaviza ambas magnitudes y
las suma a la animación JSON del hueso `body`.

No se modifica la hitbox, la posición autoritativa ni la navegación. El
servidor continúa usando una entidad vanilla convencional; las consultas de
terreno, el suavizado, los datos de render y los gizmos sólo existen en
cliente.

## Configuración del prototipo

Los apoyos se expresan en bloques a partir de las coordenadas del modelo:

| Apoyo | X del modelo | Z del modelo | Hueso previsto |
| --- | ---: | ---: | --- |
| frontal izquierdo | +0.25 | -0.5625 | `foot_front_left` |
| frontal derecho | -0.25 | -0.5625 | `foot_front_right` |
| trasero izquierdo | +0.25 | +0.5625 | `foot_back_left` |
| trasero derecho | -0.25 | +0.5625 | `foot_back_right` |

`DinosaurProceduralConfig` también fija:

- búsqueda vertical: 1.25 bloques por encima y 1.5 por debajo;
- pitch máximo: 18 grados;
- roll máximo: 15 grados;
- zona muerta: 0.5 grados;
- respuesta exponencial de suavizado: 9 por segundo.

Los valores son inmutables y sustituibles por especie.

## Muestreo

`DinosaurTerrainSampler` transforma los offsets del modelo con la misma
rotación usada por el renderer (`180° - bodyYaw`). Para cada apoyo recorre una
columna vertical acotada y consulta la `VoxelShape` de colisión en el punto
local exacto.

La consulta Y de `VoxelShape.max` recibe los otros ejes en orden Z/X. Esto
permite distinguir correctamente la altura local de slabs, escaleras y otras
formas parciales sin reducirlas a un bloque completo.

Si falta un chunk, un apoyo no encuentra superficie o la entidad no está en
suelo, el objetivo procedural vuelve a cero. No se fuerza la carga de chunks.

## Integración con GeckoLib

`PrototypeDinosaurRenderer.addRenderData` captura una
`DinosaurProceduralPose` en un `DataTicket`. Después de que GeckoLib aplique
los controladores JSON, `adjustModelBonesForRender` suma pitch y roll al
`BoneSnapshot` de `body`.

El suavizado se conserva por entidad en un `WeakHashMap`; usa tiempo de render
fraccionario y evita avanzar dos veces si una entidad genera más de un pase en
el mismo frame.

## Depuración

Con la pantalla F3 visible, `DinosaurDebugRenderer` emite gizmos para:

- los cuatro puntos de apoyo, con colores distintos;
- una línea vertical entre el origen de la entidad y cada altura;
- el offset vertical de cada muestra;
- pitch y roll suavizados sobre la entidad.

El registro utiliza `RegisterDebugRenderersEvent` y la API de gizmos de
Minecraft 26.1.2. Fuera de F3 el renderer sale inmediatamente.

## Verificaciones

- `gradlew clean compileJava`: correcto y sin avisos Java de API obsoleta.
- `gradlew clean build`: correcto.
- arranque de cliente NeoForge: correcto, con GeckoLib, Mixins y
  `mc_evolved` cargados.
- carga de recursos GeckoLib: un modelo y un archivo de animaciones, sin
  errores.
- el exportador fue corregido para serializar timestamps como `0.0`, `1.0`,
  etc. y mantener orden temporal; desapareció el aviso de keyframes fuera de
  orden que reveló el primer smoke test.

La puerta visual de jitter todavía exige observar una entidad sobre plano,
slab, escalera y desnivel dentro de un mundo. Hasta cerrarla no se implementa
la corrección vertical individual de patas.
