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

El servidor es autoritativo sobre `getYRot()`, `yHeadRot`, `xRot`, avance y
navegación. Se reutiliza la sincronización vanilla de esas rotaciones; no se
envían paquetes de huesos.

El cliente interpola cuerpo y cabeza, calcula la diferencia relativa y la
reparte en los snapshots de GeckoLib después de la animación base. El pitch
cervical compensa el pequeño pitch residual del torso para que mirar al
horizonte no duplique la inclinación del terreno.

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
- la configuración valida huesos únicos y pesos normalizados;
- el código común no depende de clases de cliente;
- no se añadió ningún Mixin porque las APIs públicas actuales son suficientes.

Pruebas visuales recomendadas para el prototipo:

- objetivos a 30, 60, 90 y 180 grados;
- pérdida del objetivo durante una maniobra;
- ruta recta, giro de ruta y destino situado detrás;
- obstáculo frontal que impida avanzar, comprobando que tampoco gire;
- terreno inclinado mientras mira arriba y abajo.
