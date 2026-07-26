# Fase 2: muestreo de terreno y pose corporal

Fecha de implementación: 2026-07-26.

## Resultado

El prototipo dispone de la primera capa procedural de pose corporal. Toma
cuatro apoyos alineados con los pivotes de los pies, calcula inclinación
longitudinal y lateral y suma el resultado a la animación JSON del hueso
`body`.

El cálculo geométrico está en el paquete común
`com.wachi.mse.entity.dinosaur.procedural`: no consulta objetivos,
navegación, `NoAI` ni el valor cacheado de `onGround`. El cliente lo ejecuta
con una transformación interpolada para render; el servidor puede ejecutar
exactamente el mismo solver mediante
`DinosaurTerrainSampler.sampleAuthoritative(...)` cuando una parte lógica
necesite conocer la postura.

El suavizado, el `DataTicket`, los snapshots de GeckoLib y los gizmos siguen
siendo exclusivamente de cliente. No se modifica todavía la hitbox, la
posición autoritativa, la navegación ni las patas.

## Configuración del prototipo

Los apoyos se expresan en bloques a partir de las coordenadas del modelo:

| Apoyo | X del modelo | Z del modelo | Hueso previsto |
| --- | ---: | ---: | --- |
| frontal izquierdo | +0.25 | -0.5625 | `foot_front_left` |
| frontal derecho | -0.25 | -0.5625 | `foot_front_right` |
| trasero izquierdo | +0.25 | +0.5625 | `foot_back_left` |
| trasero derecho | -0.25 | +0.5625 | `foot_back_right` |

`DinosaurProceduralConfig` también fija:

- radio de la huella de contacto: 0.125 bloques;
- pivote del cuerpo: 0.875 bloques sobre la base del modelo;
- búsqueda vertical: 1.25 bloques por encima y 1.5 por debajo;
- caída inferida cuando no aparece terreno: 1.5 bloques;
- corrección vertical corporal máxima: 0.75 bloques;
- pitch máximo: 35 grados;
- roll máximo: 15 grados;
- zona muerta: 0.5 grados;
- respuesta exponencial de suavizado: 9 por segundo.

Los valores son inmutables y sustituibles por especie.

## Muestreo robusto

`DinosaurTerrainSampler` transforma los offsets con la misma rotación usada
por el renderer (`180° - bodyYaw`). Cada apoyo representa una pequeña huella
circular, no un rayo infinitamente fino. El solver recorre solamente las
columnas que intersectan esa huella y consulta las cajas de la `VoxelShape`
de colisión. Así conserva las alturas reales de slabs, escaleras y otras
formas parciales, pero un pivote situado unos píxeles fuera del borde de un
bloque todavía puede reconocer que el pie lo toca.

La altura elegida es el contacto más alto dentro del margen vertical y, en
caso de empate, el más cercano al centro nominal del pie. Si no aparece
ninguna superficie, la muestra se conserva en rojo como una caída inferida
hasta el límite inferior. De esta forma un apoyo real junto a un vacío sigue
produciendo pendiente en lugar de cancelar el eje. Si los cuatro apoyos están
en el aire, la pose permanece neutra. No se fuerzan cargas de chunks.

Para calcular pitch y roll:

- las superficies encontradas aportan su altura real;
- las muestras sin superficie aportan la caída inferior inferida;
- al menos un contacto real activa la pose;
- el límite de pitch de 35 grados evita rotaciones verticales extremas.

La reducción de tres a 1.5 bloques impide que un suelo muy lejano convierta
el vacío en una falsa superficie baja.

## Compensación del pivote corporal

El hueso `body` rota alrededor de Y=14 píxeles, mientras que el contacto de
los pies está en Y=0. Una rotación con el pivote fijo levanta las patas del
lado alto aunque su sonda siga apoyada. El solver reproduce el orden de
rotación X/Z de GeckoLib, calcula cuánto se desplazaría cada pie con contacto
real y obtiene una traslación Y común del cuerpo.

Esta traslación mantiene el apoyo principal mientras el cuerpo se inclina y
se suaviza junto con pitch y roll. No sustituye la futura corrección vertical
de cada pata: esa fase resolverá el residuo individual cuando varios pies
deban alcanzar alturas diferentes, sin obligar a estirar una sola pata para
sostener todo el modelo.

## Integración con GeckoLib

`PrototypeDinosaurRenderer.addRenderData` solicita la pose interpolada y la
guarda en un `DataTicket`. Después de que GeckoLib aplique los controladores
JSON, `adjustModelBonesForRender` suma pitch, roll y la compensación vertical
al `BoneSnapshot` de `body`.

El suavizado se conserva por entidad en un `WeakHashMap`; usa tiempo de render
fraccionario y evita avanzar dos veces si una entidad genera más de un pase
en el mismo frame. Esta parte visual no altera el resultado determinista que
puede consultar el servidor.

## Depuración

Con F3 visible, `DinosaurDebugRenderer` emite:

- los cuatro contactos, con colores distintos;
- rojo para una muestra sin superficie;
- una línea entre la altura de origen y cada contacto;
- el offset vertical de cada muestra;
- pitch, roll y `bodyY` suavizados, estado `ok` o `--` de cada eje y total de
  muestras reales.

El texto es verde cuando las cuatro superficies son reales, amarillo cuando
la pose combina contactos con caídas inferidas y naranja cuando no hay ningún
apoyo real.

## Incidencia corregida

La primera implementación hacía:

`onGround && las_cuatro_muestras_son_válidas`

Esa condición explicaba los dos síntomas observados: `NoAI` podía dejar el
flag de suelo sin actualizar de la forma esperada por el renderer, y cualquier
punto fuera de un borde anulaba pitch y roll simultáneamente. La condición se
ha eliminado; ahora la pose depende sólo de geometría reproducible.

## Verificaciones

- `gradlew clean compileJava`: correcto.
- `gradlew clean build`: correcto.
- el paquete común no importa ninguna clase `net.minecraft.client`.
- no quedan usos de `onGround()` en el cálculo procedural.

La corrección vertical individual de patas y la distribución de giro entre
cuello y cabeza permanecen fuera de esta fase. El movimiento común de
`body` sólo corrige el flotado introducido por su pivote al inclinarse.
