# Orientación procedural de cuello y cuerpo

Fecha de implementación: 2026-07-26.

## Resultado

La mirada y el desplazamiento ya no comparten una única rotación instantánea.
El prototipo distingue:

- yaw lógico del cuerpo, que determina hacia dónde avanza;
- yaw y pitch lógico de la mirada, sincronizados por los campos normales de
  la entidad de Minecraft;
- giro cervical relativo al cuerpo, repartido visualmente entre los huesos
  configurados;
- dirección solicitada por navegación o por un objetivo de mirada.

Un objetivo dentro del alcance cervical mueve únicamente cuello y cabeza. Si
rebasa el umbral cómodo, el dinosaurio empieza a andar despacio y corrige su
yaw mientras avanza. Por tanto, el cuerpo describe una curva y no gira sobre
el centro de la hitbox.

Al desaparecer el objetivo, el cuello vuelve suavemente al centro. El cuerpo
no intenta seguirlo mientras la entidad está quieta.

## Giro ligado al desplazamiento

`DinosaurMoveControl` conserva el movimiento vanilla para rutas, saltos y
strafe, pero sustituye su límite de 90 grados por tick por dos límites de la
especie:

1. un máximo absoluto de grados por tick;
2. un máximo de grados por bloque realmente recorrido.

Se usa el menor de ambos. Si la distancia horizontal del tick anterior está
por debajo del mínimo configurado, el cambio permitido es cero. Esto evita
que una colisión, una pausa de la IA o un destino recién asignado produzcan
un pivote sin desplazamiento.

Cuando solo existe un objetivo de mirada y queda fuera del cuello,
`DinosaurLookControl` solicita una maniobra locomotora corta. La maniobra
aplica avance real y usa el mismo límite dependiente de distancia. Tiene
histéresis: comienza a 50 grados y termina al entrar en 35 grados, evitando
encendidos y apagados alrededor de un único umbral.

La navegación tiene prioridad. Mientras hay una ruta activa no se crea una
segunda maniobra de mirada: el `MoveControl` ya curva el cuerpo hacia el
siguiente nodo, y el cuello sigue al objetivo dentro de sus límites.

## Navegación compatible con el radio de giro

`DinosaurGroundPathNavigation` conserva la evaluación vanilla de colisiones,
escalones, puertas y peligros, pero usa un `PathFinder` cuyo coste añade la
longitud aproximada del arco necesario para cambiar de rumbo. También compara
el primer tramo con el yaw corporal actual. De ese modo A* prefiere accesos
largos y suaves frente a zigzags o cambios bruscos que el animal no puede
seguir físicamente.

Durante el seguimiento se usa un objetivo anticipado sobre varios nodos. La
distancia de anticipación deriva del radio de giro de la especie y de la
velocidad solicitada. El objetivo solo sustituye al nodo exacto si un barrido
de la hitbox confirma colisión libre y suelo continuo; en una esquina no
segura se conserva el comportamiento vanilla. Al acercarse a un rumbo de 90
grados no se reduce artificialmente la velocidad: el animal conserva el
avance solicitado y acepta sobrepasar el objetivo para completar una curva,
en lugar de intentar girar casi quieto.

## Montura de prueba y neutralidad

El prototipo se puede montar mediante una interacción normal, sin silla,
siempre que el jugador no mantenga la acción secundaria. Al montarlo se
cancela la ruta y el objetivo hostil que tuviera; agacharse conserva el uso
normal para desmontar.

La cámara queda libre. Su yaw representa el rumbo que solicita el jinete,
pero nunca se copia directamente al yaw corporal del dinosaurio:

- avanzar y retroceder producen únicamente movimiento longitudinal;
- las teclas laterales no crean strafe;
- el yaw usa los mismos límites de grados por tick y por bloque recorrido que
  `DinosaurMoveControl` aplica al pathfinding;
- el cuerpo sigue el yaw cervical efectivo, ya limitado y suavizado respecto
  a la cámara, en vez de perseguir directamente el yaw bruto del jugador;
- un error de rumbo grande no reduce la velocidad longitudinal solicitada;
- mover la cámara estando quieto solo orienta cuello y cabeza dentro de sus
  límites, sin permitir un pivote corporal;
- al avanzar hacia un rumbo distinto, el cuerpo converge describiendo una
  curva.

La conducción no ejecuta A*: las colisiones siguen siendo las normales de
Minecraft, mientras que la dirección, la velocidad y el radio de giro
mantienen las restricciones físicas de la especie.

El prototipo también es neutral. No elige jugadores de forma preventiva,
pero `HurtByTargetGoal` identifica a quien le haga daño y
`MeleeAttackGoal` lo persigue y ataca con su atributo de daño configurado.

## Configuración por especie

`DinosaurOrientationConfig` pertenece a `DinosaurProceduralConfig`; no
contiene nombres exclusivos del prototipo. Cada especie declara:

- una lista arbitraria de huesos de cuello/cabeza;
- pesos de yaw y pitch para cada hueso;
- yaw máximo del cuello y umbrales de inicio/fin del giro corporal;
- pitch máximo hacia arriba y hacia abajo;
- velocidades de seguimiento y recentrado;
- velocidad angular máxima del cuerpo;
- curvatura máxima en grados por bloque;
- distancia mínima necesaria para girar;
- velocidad de la maniobra iniciada por la mirada;
- multiplicador del radio y distancia máxima usados para anticipar la ruta;
- respuesta del suavizado visual.

Los pesos de cada eje deben sumar uno. Una especie puede tener solo cabeza,
dos vértebras, un cuello largo de muchos segmentos o huesos con reparto
distinto de yaw y pitch. El tamaño y la agilidad se representan cambiando la
curvatura y el límite angular, sin ramificaciones por clase concreta.

El prototipo reparte el yaw `25 % / 35 % / 40 %` y el pitch
`20 % / 35 % / 45 %` entre `neck_1`, `neck_2` y `head`. Su cuello alcanza
60 grados por lado. El cuerpo gira como máximo 3 grados por tick y 30 grados
por bloque recorrido.

## Cliente y servidor

Sin jinete, el servidor es autoritativo sobre `getYRot()`, `yHeadRot`,
`xRot`, avance y navegación. Durante la montura se conserva el modelo vanilla
del pasajero controlador: cliente y servidor ejecutan las mismas fórmulas de
dirección y velocidad y la entidad usa la sincronización normal de vehículos.
En ninguno de los casos se envían paquetes de huesos.

El cliente interpola cuerpo y cabeza, calcula la diferencia relativa y la
reparte en los snapshots de GeckoLib después de la animación base. El pitch
cervical compensa el pequeño pitch residual del torso para que mirar al
horizonte no duplique la inclinación del terreno.

El yaw lógico mantiene el signo de Minecraft para que servidor, navegación y
flecha de depuración coincidan. Al aplicarlo a los huesos se invierte una sola
vez, porque el horneado Bedrock/GeckoLib usa el eje visual contrario. Esto
evita que cuello y cabeza apunten al lado opuesto del giro corporal.

`DinosaurBodyRotationControl` solo presenta el cuerpo en la dirección lógica
cuando ha existido desplazamiento. Se ha eliminado deliberadamente el
comportamiento vanilla que hace rotar el cuerpo hacia la cabeza tras unos
ticks quieto.

## Depuración

Con F3:

- la flecha azul muestra el yaw corporal;
- la flecha magenta muestra yaw y pitch de la mirada;
- el texto indica los grados cervicales actuales;
- se conservan los datos de terreno, IK, marcha y estabilidad.

## Rig

La revisión del proyecto de Blockbench confirmó la jerarquía:

`body -> chest -> neck_1 -> neck_2 -> head`

Los pivotes y parentescos son válidos. Esta fase no modificó ni reexportó el
`.bbmodel`; toda la orientación añadida es procedural y aditiva.

## Verificación

- `gradlew compileJava`: correcto;
- servidor dedicado aislado: cargó NeoForge, GeckoLib y `mc_evolved` y alcanzó
  el estado `Done`;
- prueba dedicada por RCON: construyó un
  `mc_evolved:prototype_dinosaur`, recibió daño de un zombi, confirmó que ese
  zombi pasó a ser su objetivo de represalia y cerró limpiamente;
- la configuración valida huesos únicos y pesos normalizados;
- A* incorpora el coste de curvatura y el seguidor comprueba cada objetivo
  anticipado antes de omitir un nodo;
- el código común no depende de clases de cliente;
- no se añadió ningún Mixin porque las APIs públicas actuales son suficientes.

Pruebas visuales recomendadas para el prototipo:

- objetivos a 30, 60, 90 y 180 grados;
- pérdida del objetivo durante una maniobra;
- ruta recta, giro de ruta y destino situado detrás;
- obstáculo frontal que impida avanzar, comprobando que tampoco gire;
- terreno inclinado mientras mira arriba y abajo;
- montado y quieto, girar la cámara 180 grados y confirmar que solo responde
  el cuello;
- montado y avanzando, solicitar giros de 30, 90 y 180 grados y confirmar que
  el cuerpo describe curvas;
- golpear al prototipo sin montarlo y comprobar que persigue al atacante.
