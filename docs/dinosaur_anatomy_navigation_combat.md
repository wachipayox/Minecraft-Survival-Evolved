# Anatomía lógica, navegación y combate

Fecha de implementación: 2026-07-27.

## Una geometría común

`DinosaurSkeletonConfig` describe por especie la jerarquía de huesos, sus
pivotes y las cajas pequeñas que forman cada región corporal. Las coordenadas
se guardan en bloques de Blockbench sin escalar. `DinosaurPoseTransforms`
aplica después, en este orden:

1. escala de la especie;
2. inclinación y traslación del cuerpo por terreno;
3. IK e inclinación local de cada pie;
4. giro procedural de cuello y cabeza;
5. pose del ataque activo;
6. orientación y posición de la entidad en el mundo.

Esta transformación es compartida por hitboxes, culling y ataques. No existe
una aproximación separada que pueda quedarse atrás respecto al modelo.

## Colisión física, selección y culling

La caja registrada en `EntityType` sigue siendo la única colisión física
contra bloques. Puede excluir cabeza y cola para que un dinosaurio largo no
quede bloqueado por anatomía que Minecraft solo sabe representar mediante un
único AABB rígido.

La selección usa diez `PartEntity` de NeoForge en el prototipo:

- torso;
- dos regiones de cuello;
- cabeza, hocico y mandíbula;
- dos regiones de cola;
- una región por pata.

Cada parte es una fase amplia barata. Un golpe de jugador vuelve a proyectar
su rayo contra las cajas pequeñas transformadas de los huesos que contiene;
un proyectil comprueba su caja y su barrido. Dar al aire que queda dentro del
rectángulo amplio no produce daño ni permite interactuar.

El renderer sobrescribe su caja de culling con la unión de la caja física y
las diez partes. Reducir la colisión central ya no hace desaparecer cabeza o
cola al salir del frustum. Las partes se actualizan cada dos ticks, repartidas
por ID de entidad, y cada tick durante un ataque. El muestreo procedural
autoritativo se cachea una vez por tick y lo reutilizan equilibrio, hitboxes y
combate.

## Combate anatómico

`DinosaurCombatConfig` contiene una lista de ataques por especie. Cada ataque
declara:

- clip de GeckoLib y duración;
- fotogramas activos;
- cooldown, daño y empuje;
- uno o más volúmenes ligados a un hueso;
- modo de objetivo único o área;
- las mismas claves de rotación necesarias para reproducir su volumen en
  servidor.

El prototipo usa `bite`. La animación de 0,8 segundos adelanta cuello y
cabeza, abre la mandíbula y solo hace daño en el impacto. El objetivo debe
intersectar el volumen transformado de la cabeza; estar junto a la cola ya no
cuenta como alcance cuerpo a cuerpo. La configuración escala con el gigante y
está preparada para mordiscos, coletazos, pisotones o ataques de área de
especies futuras sin condicionales específicos en la entidad.

## Navegación por tamaño y giro bloqueado

Los ejemplares normales conservan el A* de Minecraft con un coste adicional
por cambios bruscos de dirección y seguimiento de curva mediante
pure-pursuit. El presupuesto de nodos es configurable por especie.

Un ejemplar cuyo `modelScale` supera el umbral configurado usa un planificador
local reactivo. En vez de pedir a la cuadrícula vanilla que resuelva una caja
gigante, prueba un abanico de direcciones con el AABB físico real, su
`step_height` y soporte al final de la maniobra. Se recalcula cada tres ticks
y mantiene velocidad locomotora completa mientras el cuerpo converge hacia
la dirección elegida.

`DinosaurMoveControl` sigue ligando el giro normal al desplazamiento. Si hay
una orden de movimiento pero la posición no progresa durante ocho ticks,
habilita un giro lento de desbloqueo de 3 grados por tick. El control continúa
intentando avanzar: en espacio libre el desplazamiento vuelve a producirse y
la trayectoria sigue siendo la de un animal que gira en arco; contra una
esquina el cuerpo puede reorientarse sin quedar bloqueado para siempre. La
misma excepción se aplica al control montado.

Los árboles todavía son colisión real. El planificador grande los evita o
busca una dirección alternativa; derribarlos será una capacidad explícita de
especie en una fase posterior, no una omisión silenciosa de colisiones.

## Pies sobre superficies inclinadas

Cada planta añade cuatro rayos verticales cortos: dos laterales y dos
longitudinales. Las diferencias de altura producen pitch y roll locales,
limitados actualmente a 25 grados en el prototipo. La inclinación se atenúa
con el peso de apoyo, por lo que un pie en vuelo no copia una pendiente.

Estos rayos usan directamente las `VoxelShape` de colisión y no repiten la
búsqueda volumétrica profunda de la pata. Por tanto reconocen slabs,
escaleras y bloques parciales con un coste constante pequeño.

## Límites deliberados

- La caja física de bloques sigue siendo un AABB vertical de Minecraft.
- Las partes anatómicas precisan golpes, proyectiles y culling, pero no
  colisionan individualmente con bloques.
- El servidor reproduce las claves que afectan al volumen de ataque; todo
  ataque nuevo debe declarar ese perfil junto a su clip GeckoLib.
- La destrucción de vegetación y obstáculos por masa no forma parte de esta
  entrega.
