# RaindanceMC/worldutils

- **`worldutils-module`** — Rain's Flexible Modules — World Utils: Moving between worlds and dimensions (/w, /dim — names and selectors), and making, resetting and deleting worlds with a chosen seed and a history of every seed a world has had. The hard parts — regenerating a group of worlds without stranding anybody, carrying game rules across, the seed history, portal linking and who may enter a world — are RainsCore's; this module is the commands, the worlds it made, and where each player last stood in each world.
- **`worldutils-bundle`** — Rain's World Utils: Everything about worlds in one jar: the world gate (lock, drain or close the Nether and the End) and world utils (/w, /dim, and creating, resetting and deleting worlds with a seed history), with RainsCore installed alongside. The two do not know about each other — the gate answers RainsCore's world entry rules and /w and /dim ask them — so a locked End is locked to a portal and to a command alike.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `worldutils-bundle/target/RainsWorldUtils-0.5.0.jar` — also published as `de.raindancer.modules:worldutils-bundle:0.5.0`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
