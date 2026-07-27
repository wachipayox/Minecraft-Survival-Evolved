# Minecraft Survival Evolved

Mod para Minecraft 26.1.2 sobre NeoForge 26.1.2.84, Java 25 y GeckoLib 5.

## Estado actual

- ID: `mc_evolved`
- paquete base: `com.wachi.mse`
- clase principal: `MseMod`
- Mixins configurados en `mc_evolved.mixins.json`
- entidad GeckoLib inicial: `mc_evolved:prototype_dinosaur`

El prototipo incluye un modelo temporal, textura pixel art, animaciones
`idle`, `walk` y `bite`, adaptación visual al terreno, cuello procedural,
navegación con giro por desplazamiento, hitboxes anatómicas multipartes y
ataques ligados a la parte animada del cuerpo. Se puede invocar con:

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
`docs/dinosaur_procedural_animation_plan.md`; las fases terminadas se detallan
en `docs/dinosaur_procedural_terrain_phase.md` y
`docs/dinosaur_orientation_phase.md`. El rediseño de anatomía lógica,
navegación y combate se documenta en
`docs/dinosaur_anatomy_navigation_combat.md`.
