# Terreno procedural e IK de patas

Fecha de implementación: 2026-07-26.

## Resultado actual

La adaptación al terreno usa cadenas de dos segmentos:

`cadera -> rodilla -> pie`

Las rodillas absorben primero el desnivel. El cuerpo recibe una traslación Y
común y solo empieza a inclinarse cuando ocurre una de estas situaciones:

- una pata apoyada del lado alto debe recogerse por debajo del umbral
  configurado;
- una pata del lado bajo ya no puede alcanzar su objetivo, incluido el caso
  en que no hay suelo dentro de la búsqueda.

La inclinación residual copia solo una fracción de la pendiente y tiene
límites propios. Así se conserva el equilibrio sin volver al efecto rígido de
rotar el animal entero.

Cada pata se resuelve en tres dimensiones:

- la cadera orienta el primer segmento;
- la rodilla coloca el segundo segmento;
- el pie contrarrota cuerpo, cadera y rodilla para conservar la planta
  horizontal.

No se escala ningún hueso. «Estirar» una pata significa abrir la rodilla
hasta casi la suma de ambos segmentos. El alcance útil del prototipo está
entre el 35 % y el 98,5 %, evitando tanto el plegado degenerado como la
singularidad de una cadena perfectamente recta.

El cálculo vive en el paquete común
`com.wachi.mse.entity.dinosaur.procedural`. No consulta IA, navegación,
`NoAI` ni `onGround`, por lo que `sampleAuthoritative(...)` puede reproducir
en servidor la misma postura geométrica que se renderiza en cliente.

## Configuración por especie

El núcleo no contiene las constantes `front_left`, `front_right`,
`back_left` o `back_right`. Son únicamente IDs elegidos por la configuración
del prototipo.

Cada `DinosaurLegRig` declara:

- ID estable y etiqueta corta de depuración;
- huesos superior, inferior y pie;
- posición X/Z del apoyo en espacio de Blockbench;
- alturas de los tres pivotes, que determinan la longitud real;
- dirección preferida de flexión de rodilla;
- fase de vuelo dentro del ciclo de marcha.

`DinosaurProceduralConfig.legs()` admite cualquier cantidad de patas. Un
bípedo configura dos; un cuadrúpedo, cuatro; una criatura con más apoyos
añade más entradas. Las proporciones patilargas, paticortas o asimétricas
salen de las alturas de pivote de cada cadena y no de valores exclusivos del
prototipo.

La interfaz `ProceduralDinosaur` hace que cada entidad entregue su propia
configuración. El sampler, el estado de marcha, el solver, el suavizado y el
almacén de depuración operan sobre `LivingEntity` y sobre la lista
configurada, de modo que no dependen de `PrototypeDinosaurEntity`.

La configuración actual del prototipo usa:

- huella de contacto de 0,125 bloques;
- búsqueda mínima de 1,25 bloques por encima y 0,75 por debajo;
- observación inferior adaptativa de una longitud de pata más allá de su
  alcance físico;
- corrección vertical común máxima de 0,75 bloques;
- elevación de vuelo de 2,5 píxeles;
- inicio de inclinación por compresión al 60 % de extensión;
- 35 % de la pendiente como inclinación residual;
- máximos residuales de 10° de pitch y 7° de roll;
- respuesta exponencial de suavizado de 9 por segundo.

## Pendiente para cualquier distribución de patas

Los contactos con peso de apoyo ajustan por mínimos cuadrados el plano:

`y = lateral * x + longitudinal * z + altura`

La X usada es la X ya horneada por GeckoLib. GeckoLib invierte la X de los
pivotes Bedrock/Blockbench; aplicar esa misma conversión al muestreo corrige
el error anterior en el que una trampilla bajo la pata izquierda hacía
reaccionar la derecha.

Un eje solo se considera resuelto si las patas activas tienen separación en
ese eje:

- un bípedo con ambas patas en el mismo Z puede resolver roll, pero no
  inventa pitch;
- tres o más apoyos no colineales pueden resolver el plano completo;
- apoyos colineales diagonales resuelven únicamente el eje geométricamente
  dominante, porque no hay información suficiente para separar ambos.

Las patas en vuelo aportan peso cero y no alteran el plano ni la altura
corporal. El mismo reloj de `DinosaurGaitState` se utiliza para la lógica de
apoyo y para la elevación visible.

## Contactos, huecos y altura común

Cada sonda usa la `VoxelShape` de colisión real, incluyendo slabs, escaleras
y formas parciales. La profundidad inferior se calcula por pata a partir de
la suma real de sus dos segmentos, su fracción máxima de extensión y la
corrección Y disponible para el cuerpo. A ese alcance físico se añade una
distancia de observación configurable en múltiplos de la longitud de esa
pata. Por ello un dinosaurio patilargo puede leer un escalón más profundo que
uno paticorto sin usar constantes del prototipo.

El terreno encontrado en la zona adicional sirve para medir el desnivel,
pero no se convierte en apoyo si el IK no puede alcanzarlo. Tampoco participa
en la optimización de altura común: así un suelo lejano no hace que el cuerpo
baje tanto como para perder las patas que sí estaban sosteniéndolo. Si no se
encuentra terreno ni siquiera en la zona de observación, se conserva el
objetivo visual acotado a 0,75 bloques; aumentar la conciencia del entorno no
hace que una pata cuelgue indefinidamente sobre el vacío.

El solver busca numéricamente la traslación Y que minimiza las violaciones de
alcance de todas las patas realmente apoyadas. Después resuelve cada cadena
con IK de dos segmentos en 3D y marca `reachable()` como falso si tuvo que
limitar el objetivo.

## Estabilidad y caída desde bordes

La flotación del `AABB` sobre un bloque central no se decide contando patas.
El sistema proyecta el centro de masas configurable de cada especie sobre el
plano horizontal y construye la envolvente convexa de áreas de pie finitas.
Solo entran en esa envolvente las patas que:

- tienen colisión real bajo el pie;
- están en fase de apoyo;
- alcanzan el contacto sin que el IK tenga que limitarse.

Esto permite que dos apoyos diagonales de un cuadrúpedo o los dos pies de un
bípedo formen una base válida, mientras que dos patas del mismo lado dejan el
centro de masas fuera. El prototipo usa un radio de pie de 0,125 bloques, una
tolerancia exterior de 0,0625 bloques y el centro de masas X/Z en el origen
del modelo. Cada especie puede desplazar ese centro para representar colas,
torsos o cabezas con distribuciones distintas.

El resultado geométrico tiene tres etapas lógicas:

1. estable: el centro está dentro del polígono o de su pequeño margen;
2. recuperación: permanece fuera, pero todavía corre una gracia de 8 ticks;
3. caída: el servidor aplica una aceleración horizontal acotada desde el
   borde de soporte hacia el lado sin apoyo.

La aceleración no simula un ragdoll ni sustituye las colisiones. Su única
función es sacar de forma sostenida el `AABB` principal del bloque que lo
retenía; a partir de ahí actúan el movimiento, la colisión y la gravedad de
Minecraft. El máximo del prototipo es 0,22 bloques/tick.

`DinosaurBalanceController` acepta cualquier `Mob` y su
`DinosaurProceduralConfig`; no conoce la clase del prototipo. Cada especie
mantiene una instancia pequeña del controlador y la llama desde
`customServerAiStep`, conservando por entidad únicamente el estado de gracia
y la última evaluación.

La regla del polígono estático se pausa mientras la actividad de marcha
supera el umbral configurado. Un bípedo en movimiento pasa gran parte del
ciclo sobre una sola pata y necesita un modelo dinámico de momento/capture
point, no una regla estática que produciría caídas falsas. La geometría sigue
calculándose y las patas en vuelo continúan excluidas de la pendiente y del
apoyo.

`NoAI` es una congelación especial de Minecraft: `LivingEntity` deja de
ejecutar `travel`, que también integra gravedad y movimiento impuesto. Por
eso el análisis visual sigue disponible con `NoAI`, pero el contador y el
impulso autoritativo no progresan hasta volver a habilitar la entidad.

En servidor se sondea una vez cada dos ticks y se reparte el trabajo usando
el ID de entidad. Para cuatro patas la envolvente recibe como máximo 32
vértices; su coste es despreciable frente a las consultas de colisión y no
crece con el tamaño visual del dinosaurio, sino con su número de patas.

## Rig inspeccionado

El proyecto fuente de Blockbench y el `geo.json` exportado ya contienen las
cuatro cadenas del prototipo:

| Pata | Cadera | Rodilla | Pie | Flexión |
| --- | --- | --- | --- | --- |
| frontal izquierda | `leg_front_left` Y=12 | `shin_front_left` Y=6 | `foot_front_left` Y=1 | hacia atrás |
| frontal derecha | `leg_front_right` Y=12 | `shin_front_right` Y=6 | `foot_front_right` Y=1 | hacia atrás |
| trasera izquierda | `leg_back_left` Y=12 | `shin_back_left` Y=6 | `foot_back_left` Y=1 | hacia delante |
| trasera derecha | `leg_back_right` Y=12 | `shin_back_right` Y=6 | `foot_back_right` Y=1 | hacia delante |

La planta de cada cubo está en Y=0. El solver apunta el pivote del pie un
píxel por encima del contacto para que la planta quede sobre el bloque. La
revisión visual del rig no exigió modificar ni reexportar el `.bbmodel`.

## Integración con GeckoLib

`PrototypeDinosaurRenderer.addRenderData` muestrea y suaviza la pose.
`adjustModelBonesForRender` se ejecuta después de las animaciones JSON:

1. suma traslación Y, pitch y roll residual al hueso `body`;
2. suma rotaciones XYZ a cada cadera y rodilla;
3. suma la contrarrotación XYZ a cada pie.

El bob Y de las animaciones JSON también se incorpora al IK para no despegar
los pies. La pose suavizada conserva los objetivos sin suavizar, lo que
permite compensar la animación base cada frame.

## Depuración

Con F3 visible se muestran:

- contacto, altura, peso, ángulo de rodilla y extensión de cada pata;
- objetivo y punto alcanzado por IK;
- verde cuando es alcanzable y magenta cuando hubo límite;
- inclinación aplicada y pendiente medida por separado;
- conteos dinámicos `IK/total` y `ground/total`;
- fase y actividad de marcha.
- centro de masas y contorno de apoyo;
- margen firmado, número de patas sustentantes y dirección de caída cuando
  la postura es inestable.

Los colores se derivan del ID de pata, por lo que también funcionan con
especies que no tengan exactamente cuatro extremidades.

## Verificaciones

- `gradlew compileJava`: correcto.
- solver, muestreo y configuración no importan clases de cliente;
- la X de Blockbench se convierte explícitamente a la X horneada por
  GeckoLib;
- el modelo fuente conserva sus 23 huesos y no fue reexportado;
- no se usa `onGround()` ni estado de IA para calcular la pose.

La distribución de mirada entre cuello y cabeza y el avance horizontal de
cada paso permanecen para fases posteriores.
