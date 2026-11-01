# SableGuard — guía para Claude Code

Mod NeoForge 1.21.1 que corrige bugs de **Sable 2.0.6** (sub-levels huérfanos, guardado, física) y absorbe los arreglos de otros mods que parchean Sable. Lo uso en mi modpack "Mega Tech Pack" (Prism Launcher, Arch Linux, ~400 mods).

## Cómo quiero que trabajes

- Responde en español. Sé honesto y directo; basa todo en hechos, pros y contras. Si no sabes algo, dilo. Si algo no tiene solución, busca alternativas conmigo.
- Piensa como desarrollador Java de mods de Minecraft y cuida la calidad del código.
- Uso NuShell, Kitty y Nvim. Si me das comandos, dalos para NuShell.

## Estructura

- `src/main/java/dev/sableguard/`
  - `SableGuard.java`: entrada del mod. `SableVersionGate.java`: plugin de mixins (solo aplica en Sable 2.0.6 y desactiva los duplicados de mods originales instalados). `DuplicateMods.java`: cómo se reconocen los mods que SableGuard reemplaza.
  - `fix/`: `Fix`, `FixRegistry` (config `config/sableguard/fixes.json`) y `Fixes` (todos los arreglos, con id, categoría, origen y opciones).
  - `core/`: resolución de huérfanos y diagnóstico/reparación del storage (lo original de SableGuard).
  - `tools/`: herramientas de admin (rescue, purge, import, backup, manifest, monitor, desactivador de duplicados, proximidad de jugadores...).
  - `mixin/`: por áreas (`storage`, `physics`, `plot`, `guard`, `assembly`, `accessor`, `compat/<mod>`).
  - `command/`: `/sableguard ...` (servidor). `client/`: `/sableguardc ...` (cliente).
- `src/main/resources/`: `sableguard.mixins.json` (obligatoria) y `sableguard.thirdparty.mixins.json` (no obligatoria, para mods cuyo código no se pudo verificar o que cambian a menudo).
- `devtools/`: verificación sin Minecraft (ver abajo).
- `README.md`: documentación para usuarios. Explica qué se integró de cada mod, qué se cambió y por qué.
- `THIRD_PARTY_NOTICES.md`: licencias del código adaptado (obligatorio por MIT). Viaja dentro del jar.

## Reglas del proyecto (no romper)

1. **Ningún `@Overwrite` ni `@Redirect`.** Usa `@Inject`, `@WrapOperation`, `@WrapMethod`, `@ModifyArg`, `@ModifyVariable`, para no romper a otros mods que parcheen lo mismo.
2. Cada arreglo es un `Fix` en `Fixes.java`. Mira su `on()` antes de actuar. Las categorías son `STORAGE`, `CRASH_GUARD`, `COMPAT`, `TOOL` y `BEHAVIOR`. **`BEHAVIOR` siempre va apagado por defecto**; el constructor de `Fix` lanza una excepción si no.
3. Si un mixin solo aplica cuando hay otro mod, va en `mixin/compat/<mod>/` y se registra en `REQUIRED_BY_PACKAGE` de `SableVersionGate`.
4. Si un mixin duplica a un mod original, añádelo a `INTEGRATED_FROM` (con su id en `DuplicateMods`) para que no se aplique dos veces.
5. Los mixins nuevos van en el JSON que corresponda: en la configuración no obligatoria si el target no se puede verificar contra código fuente.
6. Las licencias importan:
   - **MIT** (SableCat, FuckSable, Sable Fix Mod, CBC Ballistics, CBC Sable Rangefinder Fix): se puede adaptar código con aviso en `THIRD_PARTY_NOTICES.md`.
   - **All Rights Reserved** (Sable Chunk Guard, Sable Holding Chunk Fixer): solo reimplementar la idea. No copiar ni descompilar el código.
7. Nada borra datos del jugador sin un comando explícito. Los mods duplicados se renombran a `.jar.disabled`; nunca se borran.
8. Antes de afirmar que algo funciona, verifícalo y di qué parte quedó sin probar.

## Compilar y probar

```nu
# una vez: copia los jars del modpack (solo para compilar, no se incluyen en el jar)
let mods = "~/.local/share/PrismLauncher/instances/Mega Tech Pack/minecraft/mods" | path expand
mkdir libs
for j in [sable-neoforge-1.21.1-2.0.6.jar create-1.21.1-6.0.10.jar create-aeronautics-bundled-1.21.1-1.3.2.jar cbc_ballistics-1.3.0a-neoforge-1.21.1.jar] { cp $"($mods)/($j)" libs/ }
./gradlew build
```

- Build: NeoGradle userdev 7.1.38 + Gradle 9.2.1 (wrapper incluido), con configuration cache activada. En las acciones de tareas no leas propiedades del proyecto (`mod_id`...): calcúlalas en configuración.
- `libs/*.jar` no se sube a git (Sable es PolyForm Shield), así que el CI de GitHub no puede compilar sin esos jars.
- Prueba en servidor de desarrollo: copia en `run/<run>/mods/` Create, Aeronautics bundled, Create Big Cannons, Ritchie's Projectile Lib y CBC Ballistics (Sable ya entra por `localRuntime`). `./gradlew runGameTestServer` aplica los mixins de arranque y se cierra (no hay gametests); los de storage/accessors solo se aplican al cargar un mundo (`runServer`).
- El nombre exacto del jar de Aeronautics puede variar. El build avisa si falta algún jar.
- Pruebas de lógica sin Minecraft: `bash devtools/harness/run-tests.sh` (después de un build, para que Gson y NightConfig estén en la caché de Gradle). Son 12 escenarios de huérfanos más las pruebas del registro de arreglos, de la detección de duplicados y de `fml.toml`.
- `python3 devtools/check_targets.py src/main/java/dev/sableguard` comprueba que los métodos y llamadas que apuntan los mixins existen en el código fuente de Sable/Create/Simulated. Edita `ROOTS` en el script con las rutas a tus clones (ver `HANDOFF.md`).
- `devtools/stubgen/` tiene los generadores de stubs usados para compilar sin Minecraft. Solo hacen falta si no puedes usar Gradle.

## Código de referencia

Sable es PolyForm Shield (no redistribuir su código). Para revisarlo, clona las versiones exactas:
- Sable 2.0.6: `git clone https://github.com/ryanhcode/sable` y `git checkout ad8b4d3`.
- Create 6.0.10: `https://github.com/Creators-of-Create/Create`, tag `mc1.21.1-6.0.10`.
- Simulated/Aeronautics 1.3.2: `https://github.com/Creators-of-Aeronautics/Simulated-Project`.
- SableCat (MIT): `https://github.com/eviesaurr/SableCat`. FuckSable: `https://github.com/XSY-HYH/fuck-sable`. Sable Fix Mod: `https://github.com/finkkk/sable-fix-mod`.
