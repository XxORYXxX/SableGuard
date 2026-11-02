# SableGuard 0.3.0

Mod para **Minecraft 1.21.1 / NeoForge** que corrige los sub-levels **huérfanos** de Sable (siguen en el storage pero nunca vuelven a cargarse: invisibles, no interactuables) y reúne en un solo mod los arreglos de otros mods que parchean Sable, revisados uno por uno contra el código fuente.

Escrito contra **Sable 2.0.6** (`ad8b4d3`), **Create 6.0.10** y **Create Aeronautics / Simulated 1.3.2**. En otra versión de Sable el mod queda **inactivo** a propósito (ver "Seguridad").

---

## Qué incluye

58 arreglos, cada uno se activa o desactiva por separado (`/sableguard fix …` o `config/sableguard/fixes.json`).

| Categoría | Por defecto | Qué es |
|---|---|---|
| `STORAGE` | activados | Evitan pérdida de datos, huérfanos o storage corrupto. |
| `CRASH_GUARD` | activados | Evitan un crash o congelamiento; no cambian el juego. |
| `COMPAT` | activados | Arreglan la interacción con otro mod. Solo actúan si ese mod está instalado. |
| `TOOL` | activados | Diagnóstico y herramientas de admin. |
| `BEHAVIOR` | **apagados** | Cambian el juego o imponen límites. El código se niega a registrar uno activado por defecto. |

### Núcleo de SableGuard (0.1.0)

| id | Qué arregla |
|---|---|
| `orphan-resolver` | Sable descarta un grupo de dependencias si una pieza no está en el **mismo** holding chunk (`collectReadySubLevels` → `iter.remove()`). SableGuard reúne el grupo, carga el chunk donde está la pieza, lo retiene mientras la busca, quita referencias a piezas inexistentes o ya cargadas, y normaliza listas asimétricas. |
| `footprint-tickets` | Grupos más anchos que el área de block-ticking nunca cargan (#1619). Tickets temporales sobre su huella. |
| `save-guards` | `ConcurrentModificationException` y `null` que abortaban `saveAll()` a medias; duplicados tras un crash. |
| `velocity-sanitize` | Velocidades absurdas o NaN guardadas (#1387). |
| `heatmap-guard` | Crash del split sobre un sub-level ya removido (#1376). |
| `split-parent-guard` | El mismo crash cuando el padre se elimina **durante** el split: al sacar una isla, la masa del padre queda inválida (masa <= 0 o sin centro de masa) y Sable lo elimina (`updateMassDataFromBlockChange` → `destroyAllBlocks` + `markRemoved`); la isla siguiente reventaba en `assembleBlocks`. SableGuard corta el split ahí, conserva las islas ya separadas, lo cuenta en `/sableguard status` y deja un aviso con el UUID. No borra nada: los bloques que quedaban en el padre ya los destruyó Sable. |

### Integrado de SableCat / FuckSable (MIT)

- Storage: `chain-save-guard` (un sub-level que falla al guardarse ya no aborta el guardado del resto), `partial-header-write`, `storage-log-throttle`, `rescue-capture`, `manifest`, `scheduled-backup`.
- Crash guards: `panic-guard`, `constraint-self-fix`, `physics-ticket-guard`, `plot-holder-guard`, `udp-invalid-packet-guard`, `sublevel-entity-guard`, `entity-lookup-remove-guard`, `light-engine-bounds-guard`, `block-coordinate-guard`, `create-trackgraph-null-guard`, `create-train-detach-nulledge-guard`.
- Compat: Copycats (`copycats-lift-compat`), Linked Typewriter y Steam Vent en servidor dedicado, CarryOn, Create ThreadedTrains (`ctt-concurrent-fix`, `ctt-log-spam-fix`), Effortless Building, ScalableLux, y 7 arreglos de Scorched Guns (torretas y shock coil sobre sub-levels).
- Behaviour (apagados): `sublevel-volume-limit`, `command-block-sublevel-fix`, `player-position-guard`, `physics-staff-drag-clipfix`, `aeronautics-slime-bearfix`, `frogport-extract-limit`, `ctt-posttick-timeout-guard`, `scalablelux-incompat-bypass`.

### Integrado de Sable Fix Mod (MIT)

`assembly-drop-guard` (los bloques movidos al ensamblar no sueltan ítems: duplicación), `assembly-shape-skip`, `assembly-inventory-clear` (behaviour, apagado).

### Reimplementado sin código

- `chunk-guard`: la idea de **Sable Chunk Guard** (licencia All Rights Reserved, así que no se copió ni descompiló nada). La física de Sable ya no carga ni genera chunks de forma síncrona; si falta terreno, se salta ese tick y se retoma cuando el chunk carga en segundo plano. Evita congelamientos a 0 TPS cuando un sub-level entra en terreno nuevo.
- **Sable Holding Chunk Fixer** (su `mods.toml` dice All Rights Reserved): reimplementado a partir de su descripción, los nombres de sus métodos y el código de Sable, sin su código.
  - `keep-sublevels-near-players`: un sub-level no se descarga mientras alguna parte esté dentro de la distancia de visión de un jugador; Sable fuerza la carga del chunk que falta. Sin esto, Sable solo lo retiene si el centro del jugador está dentro de su caja. Grupos de más de `maxChunks` (256) se dejan a Sable para que unos bounds corruptos no carguen media región.
  - `death-grace`: durante `graceSeconds` (120) tras morir, el sitio donde murió el jugador cuenta como si siguiera allí.
  - Cargar los chunks que faltan cuando hay un jugador cerca ya lo hacía `footprint-tickets`.
  - Son `BEHAVIOR` (apagados por defecto), pero **se encienden solos una vez** cuando SableGuard sustituye a Holding Chunk Fixer, para que no pierdas lo que tenías. Si luego los apagas, no se vuelven a encender.

### CBC Ballistics / CBC Sable Rangefinder Fix (MIT)

- `cbc-rangefinder-sublevel-fix`: misma idea que **CBC Sable Rangefinder Fix** (`cbc_sable_fix`, de Yarik), escrita de nuevo. CBC Ballistics simula un proyectil virtual (`DetectingProjectile`) para el rangefinder **y** para las soluciones de tiro de la calculadora balística. Si impacta en un bloque de un sub-level, guarda coordenadas del plot (20-30 millones de bloques): la calculadora se queda calculando un tiro imposible y la radio envía objetivos absurdos. SableGuard guarda en su lugar el **punto exacto** del impacto en el mundo; el original usaba el centro de la caja del barco. La lógica de bloque de vanilla sigue viendo el bloque real del sub-level.
- `cbc-particle-cap`: máximo 200 partículas por línea y 100 por círculo. El original cortaba las líneas a 50 bloques; aquí se espacian las partículas y la línea conserva su longitud. Las formas absurdas (más de un millón de partículas) se omiten.

### Compatibilidad propia de SableGuard

- `airwars-plot-chunks` (cliente, activado si está **Create: The Air Wars**): Air Wars se queda cualquier chunk que llegue fuera de tu distancia de visión, para sus cámaras remotas (`ctaw$acceptRemoteChunk` al inicio de `ClientChunkCache.replaceWithPacketData`), y cancela el método. Los chunks del plot de Sable siempre están lejos, así que nunca llegaban a Sable: la estructura ensamblada era invisible, no se podía romper ni poner bloques en ella y `/sable remove` la quitaba igual (en el servidor sí existía). SableGuard le dice a Air Wars que los chunks de plot están "en rango", y Air Wars los devuelve al camino normal; sus cámaras siguen funcionando con el resto. Diagnóstico: `/sableguard track` mostraba el chunk de plot cargado en el servidor y `/sableguardc sublevels`, 0 en el cliente.
- `sabledynlights-thread-guard` (cliente, activado si está **Sable Dynamic Lights**): su mixin mete `VirtualLightManager.getVirtualEmission` al inicio de `BlockLightEngine.getEmission` de **todos** los motores de luz, no solo los del cliente. Sus mapas de luz son `HashMap` estáticos normales que el tick del cliente reescribe en el hilo de render; en un mundo de un jugador, el servidor integrado (por ejemplo `ServerLevelPlot.tick` de Sable) los recorre a la vez desde el hilo del servidor y el mundo cae con `ConcurrentModificationException`. Además metía luz dinámica (que solo es del cliente) en la luz del servidor. SableGuard hace que fuera del hilo de render la emisión virtual sea 0: el servidor ve la luz vanilla y el cliente sigue con sus luces dinámicas. Revisado contra el bytecode de la 2.0.1 (Apache 2.0); no se copió código.
- `heavy-blocks-natural-guard` (**behaviour, apagado**; solo actúa si está **Sable Heavy Blocks**): ese mod convierte en sub-level que cae todo bloque con masa de Sable ≥ `min_mass_to_break` (4,0 por defecto) sin soporte a menos de 20 bloques, en cualquier actualización de bloques vecinos (agua o lava que fluye, minar...). Los bloques de mena en bruto (`#c:storage_blocks`) pesan justo 4,0, así que las cuevas arrancaban trozos enteros de veta con deepslate y lava: decenas de cuerpos físicos en el fondo del mundo, splits que crasheaban y servidor a ~1 s por tick. Con este arreglo el terreno natural nunca dispara Heavy Blocks: menas, bloques de mena en bruto (también los de mods, por el prefijo `raw_`) y piedra base. Se amplía con un datapack en el tag `#sableguard:heavy_blocks_natural`. Contra: un bloque de mena en bruto que pongas tú tampoco caerá. Lo que ya se arrancó antes no vuelve solo. Activar: `/sableguard fix enable heavy-blocks-natural-guard`.

### Desactivar mods duplicados (`disable-duplicate-mods`, activado)

Al arrancar, si SableGuard está activo, renombra a `.jar.disabled` los jars de **Sable Fix Mod, Sable Chunk Guard, Sable Holding Chunk Fixer, CBC Sable Rangefinder Fix, SableCat y FuckSable**. No borra nada.
- Usa la carpeta `mods` desde la que arrancó el juego y el archivo que NeoForge dice que cargó, así que funciona igual con Prism, CurseForge, Modrinth, ATLauncher, el launcher oficial o un servidor.
- En esa primera sesión los originales siguen cargados y SableGuard omite sus propias versiones; nada se aplica dos veces. Desde el siguiente arranque queda solo SableGuard.
- Linux y macOS: el renombrado es inmediato. Windows bloquea los jars abiertos, así que se hace con un script al cerrar el juego (esta parte no la he podido probar en Windows).
- Nunca toca CBC Ballistics ni otros addons de Sable, y no hace nada si la versión de Sable no está probada (en ese caso los otros mods son los únicos arreglos activos).
- Los ops ven el resultado en el chat al entrar. Para conservar un mod, apágalo antes: `/sableguard fix disable disable-duplicate-mods`.

---

## Qué cambié respecto a los originales, y por qué

Revisé cada arreglo contra el código fuente. Estos **no** se portaron tal cual:

**Excluidos**
- *async-save* y *write-flush* de SableCat: el archivo de storage de Sable ya se abre con `DSYNC`; forzar flush no añade nada y guardar en otro hilo mientras Sable modifica las estructuras abre condiciones de carrera.
- *corrupted-cleanup* (borrado automático de punteros y "orphan prune"): borra datos sin que nadie lo revise. Aquí solo se borra con un comando explícito.
- El "arreglo" de transformaciones del entity getter: cambiaba el sentido de la transformación y era incorrecto.
- El wrap genérico de `Block.useItemOn`: tragaba excepciones de cualquier bloque de cualquier mod.
- La rama del ticket guard que detectaba `IllegalStateException` comparando texto del mensaje.
- El wrap de `Entity.teleportTo` de CarryOn compat: redundante (la proyección en `tryPlaceEntity` ya cubre el teleport) y reescribía teleports ajenos (comandos, otros mods) que apuntaran a coordenadas de plot.

**Reescritos**
- `panic-guard`: el original bloqueaba también cuerpos que no son sub-levels (`BoxPhysicsObject`). Ahora solo los sub-levels se comparan contra `activeSubLevels`.
- `sublevel-volume-limit`: el original cortaba dentro de `assembleBlocks` de Sable, que también se usa al **partir** un sub-level y para entonces Aeronautics ya había desmontado los contraptions de Create. Ahora corta donde `assembleFromSingleBlock` lee los bloques: si se rechaza, no se toca nada.
- `plot-holder-guard`, Steam Vent y Linked Typewriter: `@WrapMethod` en lugar de `@Overwrite`, para no romper inyecciones de otros mods. **SableGuard no usa ningún `@Overwrite` ni `@Redirect`.**
- `create-train-detach-nulledge-guard`: el original apuntaba a la lambda `lambda$detachFromTracks$22`, cuyo nombre depende del compilador. Ahora envuelve la llamada `forEachTravellingPoint`.
- `assembly-drop-guard` (Sable Fix Mod vaciaba los inventarios): se suprimen los drops en las posiciones movidas; vaciar inventarios queda como opción aparte y apagada.
- `frogport-extract-limit`: SableCat lo activaba por defecto, y eso impide a los frogports sacar de interfaces de **AE2** (inventarios de más de 256 slots). Apagado.
- `ctt-posttick-timeout-guard`: cancelar un worker de trenes a medias puede dejar ese tick a medio actualizar. Pasa a behaviour (apagado) y timeout de 30 s.
- `ctt-log-spam-fix`: repite cada tipo de error cada 10 minutos en vez de ocultarlo para siempre.
- `scheduled-backup`: copia la carpeta y comprime la copia, en lugar de comprimir la carpeta viva mientras Sable escribe.
- `scalablelux-compat`: toma los flags de luz de la dimensión (`hasSkyLight`) en vez de leer clases internas de ScalableLux que cambian entre versiones.
- `purge` / `forcepurge`: SableCat limpiaba a mano el slot de storage después de que Sable ya lo había puesto en cola para borrar. Eso libera el slot antes de tiempo: un sub-level que se descargue antes del siguiente guardado puede escribirse ahí, y el borrado en cola de Sable lo borra a él. Aquí se usa el `removeSubLevel(…, REMOVED)` de Sable seguido de su propio `saveAll()`.
- `import`: copia la carpeta externa a una temporal antes de leerla (las clases de storage de Sable pueden crear o podar archivos al abrir/cerrar, y un backup no se debe tocar), rechaza UUIDs que ya existan en el mundo (duplicados), y quita las dependencias que apuntan al otro mundo. El persistido de SableCat escribía sobre un puntero que siempre es `null` en un sub-level recién cargado, así que nunca ocurría.
- `rescue`: conserva la rotación del sub-level si es un cuaternión válido (SableCat la reiniciaba siempre).
- `aeronautics-slime-bearfix`: `instanceof SwivelBearingBlock` en vez de comparar el nombre de la clase.

---

## Comandos (en la dimensión donde se ejecutan)

| Comando | Permiso | Hace |
|---|---|---|
| `/sableguard status` | 2 | Estado, versión de Sable, contadores. |
| `/sableguard scan` | 2 | Diagnóstico **solo lectura** del storage + reporte en `<mundo>/sableguard_reports/`. |
| `/sableguard repair [confirm]` | 3 | Vista previa / aplica (con backup previo). |
| `/sableguard recover <uuid>` | 3 | Carga un sub-level guardado (y su grupo). |
| `/sableguard fix list [categoría]` | 2 | Lista los arreglos: `on`, `off` o `idle` (activado pero falta el mod que necesita). |
| `/sableguard fix info <id>` | 2 | Descripción, origen, opciones. |
| `/sableguard fix enable\|disable\|reset <id>` | 3 | Se guarda en `config/sableguard/fixes.json` y aplica al momento. |
| `/sableguard fix set <id> <opción> <valor>` | 3 | Cambia una opción (se valida el tipo; NaN/Infinity se rechazan). |
| `/sableguard rescue list` | 2 | Sub-levels cuya carga falló desde que arrancó el servidor. |
| `/sableguard rescue <uuid> [x y z]` | 3 | Lo mueve a esa posición y reintenta la carga. |
| `/sableguard rescue purge <uuid>` | 3 | Borra un registro fallido **sin bloques** (`CONTENT_EMPTY`). |
| `/sableguard purge <uuid>` | 3 | Borra un sub-level cargado **vacío**. |
| `/sableguard forcepurge <uuid> [motivo]` | 4 | Borra cualquier sub-level cargado. Se registra y se avisa a todos los ops. |
| `/sableguard import <carpeta> <uuid> [x y z]` | 4 | Trae un sub-level de otra copia del storage (un backup). |
| `/sableguard importoffset <offset> <x y z> <archivo>` | 4 | Igual, leyendo el NBT en un offset de un `.slvls`. |
| `/sableguard backup now\|list` | 3 / 2 | Backup del storage de la dimensión (`<mundo>/sableguard_backups/`). |
| `/sableguard manifest [filtro]` | 2 | Última posición conocida de cada sub-level; clic para teleport. |
| `/sableguard monitor <pos>\|stop <pos>\|clear\|list` | 2 | Avisa cada cambio de ese bloque (clic copia el stack). |
| `/sableguard savefailures list\|clear` | 2 | Sub-levels que fallaron al guardarse. |
| `/sableguard track [radio]` | 2 | Lo que **el servidor** sabe de los sub-levels cercanos y si te los está enviando. |
| `/sableguardc sublevels [radio]` | cliente | Lo que **tu cliente** sabe de ellos: si llegaron, si tienen chunks y datos de render. |

Rutas relativas de `import` se resuelven desde la carpeta del servidor.

---

## Compilar (Arch + NuShell)

```nu
sudo pacman -S jdk21-openjdk gradle
cd sableguard
mkdir libs
let mods = "~/ruta/a/tu/instancia/mods" | path expand
cp $"($mods)/sable-neoforge-1.21.1-2.0.6.jar" libs/
cp $"($mods)/create-1.21.1-6.0.10.jar" libs/
cp $"($mods)/cbc_ballistics-1.3.0a-neoforge-1.21.1.jar" libs/
ls $mods | where name =~ "(?i)aeronautics|simulated" | each { |f| cp $f.name libs/ }
gradle wrapper --gradle-version 8.10.2
./gradlew build
```

El jar queda en `build/libs/sableguard-neoforge-1.21.1-0.3.0.jar`.

- Sable, Create, Aeronautics y CBC Ballistics se compilan contra **tus** jars; los jar-in-jar que traen dentro se extraen solos. Nada de eso se incluye en el jar final.
- Scorched Guns, Moonlight y Framework se bajan de Modrinth/CurseMaven solo para compilar (no los tienes en el pack; los arreglos de Scorched Guns quedan `idle`).
- Si Gradle dice que `net.neoforged.moddev 2.0.107` no existe, pon la última 2.0.x de <https://projects.neoforged.net/neoforged/moddevgradle>.

---

## Seguridad

- Las mixins solo se aplican con Sable `2.0.6`. En otra versión el mod no hace nada y lo dice en el log y en `/sableguard status`. Para forzarlo: `config/sableguard-mixins.properties` → `allowUntestedSableVersions=true` (solo con backup).
- Si tienes instalado **SableCat/FuckSable, Sable Fix Mod o Sable Chunk Guard**, SableGuard desactiva sus propias versiones de esos arreglos para no aplicarlos dos veces. Desde la 0.3.0 los desactiva él mismo (ver arriba). Los mod IDs de SableCat (`sablecat`), FuckSable (`fucksable`), Sable Fix Mod (`sable_fix_mod`), Holding Chunk Fixer (`sablesublevelholdingchunkfix`) y CBC Sable Rangefinder Fix (`cbc_sable_fix`) están verificados; Sable Chunk Guard se reconoce también por el nombre del jar (`sable-chunk-guard-*`) o su nombre visible, porque su ID no es público.
- Los mixins de mods cuyo código no pude revisar (Create ThreadedTrains, Scorched Guns, Create: The Air Wars, Sable Dynamic Lights, Sable Heavy Blocks) o que cambian a menudo (CarryOn) están en una configuración **no obligatoria**: si ese mod cambió, ese mixin se salta con un error en el log en lugar de impedir que arranque el juego.
- Todos los borrados (`purge`, `forcepurge`, `rescue purge`) son explícitos; ningún arreglo borra datos por su cuenta.

### ScalableLux

Sable se declara incompatible con ScalableLux, así que FML no arranca si ambos están. `scalablelux-incompat-bypass` escribe en `config/fml.toml`:

```toml
[dependencyOverrides]
sable = ["-scalablelux"]
```

Limitación: FML lee ese archivo **antes** de que cargue cualquier mod. Si ya tienes ScalableLux instalado y el juego no arranca, SableGuard no puede escribirlo: añade esas dos líneas a mano. Al desactivar el arreglo, SableGuard solo quita la línea si la escribió él.

---

## Verificación hecha y pendiente

Hecho (en el entorno donde se escribió, sin Minecraft):
- Cada punto de inyección comprobado contra el código fuente de Sable 2.0.6, Create 6.0.10, Simulated/Aeronautics 1.3.2 y CarryOn 1.21.1 (método y llamada existen).
- Compilación con `-Xlint:all` sin errores ni warnings, contra Brigadier, Gson y NightConfig **reales** (compilados desde su fuente) y stubs del resto. Los stubs de Sable/Create/Simulated se escribieron a partir de su fuente; los de Minecraft/NeoForge, de memoria.
- 12 escenarios del resolver de huérfanos reproduciendo la lógica de carga de Sable: todos pasan.
- Pruebas de la detección de mods duplicados (los de tu pack, y que CBC Ballistics y otros addons de Sable no se tocan).
- Pruebas del registro de arreglos (JSON, defaults, behaviour apagados, opciones inválidas, archivo corrupto) y de la escritura de `fml.toml` con NightConfig real. Esta última encontró un bug real: NightConfig guarda en segundo plano por defecto y se perdía la escritura; corregido.

Hecho contra Minecraft real (1 nov 2026):
- Compila contra NeoForge 21.1.235 y los jars reales de Sable, Create, Aeronautics y CBC Ballistics.
- Servidor de desarrollo: 36 mixins aplicados sin errores; sub-levels ensamblados, guardados, descargados y recargados tras reiniciar.
- Copia del modpack completo (NeoForge 21.1.262, 505 mods): carga el mundo sin errores de mixins y desactiva los 4 mods duplicados.

Pendiente:
- Los mixins de Scorched Guns son un port fiel de SableCat, que compilaba contra Scorched Guns; yo no tuve su código para revisarlo.
- `keep-sublevels-near-players` y `death-grace` solo están probados en compilación; su lógica de proximidad no tiene escenarios simulados todavía.
- El script de renombrado diferido en Windows.

## Licencia

El código de SableGuard es GPL-3.0. Incluye código adaptado de SableCat, FuckSable y Sable Fix Mod (MIT); los avisos están en `THIRD_PARTY_NOTICES.md` y viajan dentro del jar. Sable es © RyanHCode bajo PolyForm Shield 1.0.0; SableGuard lo parchea en tiempo de carga y no redistribuye su código.
