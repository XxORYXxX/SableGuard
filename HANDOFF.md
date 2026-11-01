# Traspaso: estado de SableGuard 0.3.0 (1 nov 2026)

Resumen de lo hecho en la conversación de Claude en la nube, para seguir en Claude Code.

## Hecho

- **0.1.0, núcleo:**
  - La causa de los huérfanos está en `SubLevelHoldingChunk.collectReadySubLevels`: si falta una dependencia en el mismo holding chunk, hace `iter.remove()`. `OrphanResolver` reúne el grupo, carga el chunk donde está la pieza, retiene el grupo, poda referencias imposibles y normaliza las listas asimétricas.
  - Además: tickets de huella, guardas de guardado, saneado de velocidades, guarda del heat-map, y `scan`/`repair`/`recover`.
- **0.2.0:**
  - Integrados SableCat/FuckSable y Sable Fix Mod (MIT) tras revisarlos uno por uno. Lo que se excluyó o reescribió, y por qué, está en `README.md`.
  - Chunk Guard reimplementado.
  - Herramientas: rescue, purge, forcepurge, import, importoffset, backup, manifest, monitor y savefailures.
- **0.3.0:**
  - Holding Chunk Fixer reimplementado (`keep-sublevels-near-players`, `death-grace`).
  - CBC Sable Rangefinder Fix reimplementado (`cbc-rangefinder-sublevel-fix` en `DetectingProjectile`, `cbc-particle-cap`).
  - `disable-duplicate-mods` renombra a `.jar.disabled` los mods reemplazados, desde `FMLPaths.MODSDIR`, así que funciona con cualquier launcher.
  - Diagnóstico `/sableguard track` (servidor) y `/sableguardc sublevels` (cliente).
- **Verificado sin Minecraft:**
  - Compila con `-Xlint:all` sin errores ni warnings, contra Brigadier, Gson y NightConfig reales y stubs del resto.
  - Los targets de los mixins se comprobaron contra el código de Sable 2.0.6, Create 6.0.10, Simulated 1.3.2, CarryOn 1.21.1 y el bytecode de CBC Ballistics 1.3.0a.
  - Todas las pruebas de `devtools/harness` pasan.
- **Bugs que salieron al probar, ya corregidos:** `CHUNK_GUARD` duplicado en `Fixes`; NightConfig guardaba de forma asíncrona y se perdía la escritura de `fml.toml` (ahora usa `.sync()`).

## Pendiente, por orden

1. **[HECHO 1 nov 2026: compila a la primera contra NeoForge 21.1.235 y los jars reales del pack, sin errores de firmas; jar generado. En `runGameTestServer` se detectó Sable 2.0.6, se aplicaron sin errores los 14 mixins que se cargan al arrancar, se desactivaron los 9 mixins duplicados y se renombraron los 4 mods reemplazados a `.jar.disabled`. En `runServer` (sin los duplicados): 36 mixins aplicados sin errores; se ensamblaron 2 sub-levels por RCON (`/sable assemble area`, `/sable spawn platform`), se descargaron al storage y se recargaron tras reiniciar; `/sableguard scan` sin problemas. Sin aplicar todavía, porque sus clases no se cargaron en la prueba: `SteamVentValueBoxTransformMixin`, `DimensionalCarriageEntityMixin`, `TrackGraphMixin`, `SimAssemblyContraptionMixin` y `cbc.ParticleHelperMixin` (necesitan trenes, assembler de Simulated o CBC en juego). Los de CarryOn, CTT, Scorched Guns, ScalableLux y Effortless no se pudieron probar porque esos mods no están instalados.]** Primer build real (`./gradlew build`) y arreglar los errores de firmas de Minecraft/NeoForge. Los stubs de MC se escribieron de memoria; es lo más probable que falle. Puntos a revisar:
   - `ServerPlayer.requestedViewDistance()`.
   - `MinecraftServer.getServerDirectory()` (devuelve `Path` en 1.21.1).
   - `RegisterClientCommandsEvent`.
   - `IModInfo.getOwningFile().getFile().getFilePath()`.
   - `Level.getCapability`.
   - Las coordenadas de Scorched Guns/Moonlight/Framework en `build.gradle` (Modrinth/CurseMaven).
2. **Probar en un mundo de prueba** (con backup). Comprobar en el log:
   - Que aparece `[SableGuard] Sable 2.0.6 detected`.
   - Que los mixins aplican sin errores.
   - Que `disable-duplicate-mods` renombró `sable_fix_mod-1.1.0`, `sable-chunk-guard-1.0.0`, `sablesublevelholdingchunkfix-1.5.3` y `cbc-sable-fix-1.0.3`. Reiniciar y confirmar que no vuelven.
3. **[RESUELTO 1 nov 2026 con `airwars-plot-chunks`]** Causa: Create: The Air Wars (`hi.mixin.camera.ClientChunkCacheMixin.ctaw$acceptRemoteChunk`, HEAD de `ClientChunkCache.replaceWithPacketData`) se quedaba todo chunk fuera del radio de visión para sus cámaras y cancelaba, así que los chunks de plot nunca llegaban a Sable (servidor: 1 chunk de plot cargado; cliente: 0). Con el arreglo, en la copia del pack el cliente marca `plot chunks loaded 1`. **Bug del usuario (historial):** con el assembler de Aeronautics, la estructura desaparece, no se puede romper ni poner bloques ahí, y `/sable remove` con rango la quita.
   - El log del 1 nov (instancia Prism "Mega Tech Pack", mundo "Test") muestra que el servidor creó bien el sub-level en (145.5, 90.8, 299.5), sin errores.
   - Que no se pueda romper indica que **el cliente probablemente nunca recibió el sub-level** (red o seguimiento), no un fallo de render.
   - Siguiente paso: reproducir y ejecutar `/sableguard track` y `/sableguardc sublevels`:
     - Servidor "sent to you yes" y el cliente no lo lista → paquete perdido. Sospechosos por orden: Krypton FNP (`KryptonFNP` en el log), Waystones Sable (mixins en `ClientboundStart/StopTrackingSubLevelPacket`), e4all.
     - El cliente lo lista → render. Sospechosos: Sodium 0.8.13 + Iris + ThreatenGL + Ixeris + Immersive Optimization + smoothchunk. **Chunks Fade In Extra ya se descartó.**
     - Servidor "NO" → fallo de seguimiento de Sable. Revisar `SubLevelTrackingSystem.tick/shouldLoad`.
4. Escenarios simulados para `keep-sublevels-near-players` y `death-grace` (solo están probados en compilación).
5. El renombrado diferido en Windows (script `.cmd` al cerrar) no está probado. El usuario está en Linux, donde el renombrado es inmediato.
6. Opcional: `/sableguard fix list` muestra como `on` los arreglos que el plugin desactivó por estar instalado el original; solo queda en el log.

## Decisiones tomadas con el usuario

- Integrar **todos** los arreglos, también los de mods que no están en el pack (CTT, CarryOn, Effortless, ScalableLux, Scorched Guns).
- Los arreglos que cambian el juego se incluyen **apagados por defecto**. Excepción acordada: al reemplazar Holding Chunk Fixer, sus equivalentes se encienden una sola vez (marcador en `config/sableguard/migrated.txt`).
- Reimplementar los mods sin código público e integrar también las herramientas de SableCat.
- El desactivado de duplicados lo hace el propio mod y debe funcionar en todos los launchers.

## Datos útiles del pack

- NeoForge 21.1.262, Java 21.0.7, Arch Linux, Prism 11.1.1.
- Mods relacionados con Sable que **no** son duplicados y no se tocan: sable_heavy_blocks, sable_pathfinder, sable-cool-rain, sable-deployer, sable-dynamic-lights, sable-photomancy, sablebeyond, sabledestructive, sableexplosionfix, sablejade, sablephysicscompat, sablepowerloadercompat, waystonessable, cc_sable, cbc_ballistics, aeronauticscompat.
- Avisos del log que no tienen que ver con el bug:
  - `copycats:copycat_step`/`catwalk` desconocidos en las tags de física de Sable.
  - El conflicto de `getChunks` entre BetterF3 y el accessor de depuración de Sable.
