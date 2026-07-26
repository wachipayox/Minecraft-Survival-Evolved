# Minecraft Survival Evolved

Mod para Minecraft 26.1.2 sobre NeoForge 26.1.2.84, Java 25 y GeckoLib 5.

## Estado actual

- ID: `mc_evolved`
- paquete base: `com.wachi.mse`
- clase principal: `MseMod`
- Mixins configurados en `mc_evolved.mixins.json`
- entidad GeckoLib inicial: `mc_evolved:prototype_dinosaur`

El prototipo incluye un modelo temporal, textura pixel art, animaciones
`idle` y `walk`, atributos, una IA mínima de paseo y la primera capa de
adaptación visual al terreno. Se puede invocar con:

```mcfunction
/summon mc_evolved:prototype_dinosaur ~ ~ ~
```

Al mostrar F3 se visualizan sus muestras de apoyo, el desnivel de cada una,
los valores suavizados de pitch/roll y el polígono de estabilidad. Si el
centro de masas queda fuera de apoyos alcanzables durante el tiempo de
recuperación, el servidor desplaza al dinosaurio fuera del borde para que la
gravedad normal complete la caída.

![Dinosaurio prototipo](docs/images/prototype_dinosaur.png)

## Desarrollo

Compila y ejecuta las comprobaciones con:

```powershell
.\gradlew.bat clean build
```

El proyecto editable de Blockbench está en
`art/blockbench/prototype_dinosaur/prototype_dinosaur.bbmodel`. Los recursos
exportados que carga GeckoLib están bajo `src/main/resources/assets/mc_evolved/`.
Los `.bbmodel` se excluyen deliberadamente del JAR.

La arquitectura y el orden de implementación del sistema procedural están en
`docs/dinosaur_procedural_animation_plan.md`; la fase de terreno está detallada
en `docs/dinosaur_procedural_terrain_phase.md`.
