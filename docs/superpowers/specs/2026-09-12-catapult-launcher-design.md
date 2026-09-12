# Immersive Aircraft catapult launcher

Date: 2026-09-12

Finer points were not answered in the brainstorming widget. This spec records the **recommended defaults** used for implementation.

## Goal

A placeable **catapult** block in Immersive Aircraft that launches IA vehicles from ordinary ground or a Eureka / Valkyrien Skies ship deck. Collision is **1 wide × 1 long × 0.5 high** (block units).

## Trigger

**Redstone rising edge.** A neighbor signal going from off to on fires the pad. A held signal does not repeat-fire. Cooldown **40 ticks** (2 seconds) after a successful launch.

No right-click fire, no auto-on-throttle.

## What launches

Any `immersive_aircraft.entity.VehicleEntity` whose AABB intersects the **detection** volume: the 1×1×0.5 collision box plus 1 block above the pad top. That includes a legal rest pose with feet on the pad (`minY = 0.5`). Occupied and empty vehicles both launch. A flyer at y=2 is outside the volume and is ignored. Collision/outline stays 1×1×0.5.

## Impulse

Pure transform on the vehicle’s current velocity (blocks/tick):

- Add `FORWARD` (1.5) along the vehicle’s current look/forward unit vector.
- Add `UP` (0.4) along world +Y.

So a pad on a rotated Eureka deck still throws the plane the way it is pointing. Ground pads behave the same.

After launch, `onGround` is cleared. Sound: existing `woosh`.

## Block

- Horizontal facing (like a furnace), 1×1×0.5 voxel, no placement guard that forbids ships or non-overworld ground.
- Normal world block: VS can assemble it onto a Eureka hull.
- Block item in the Immersive Aircraft creative tab.
- Recipe (shaped): iron ingots in the corners, redstone in the center, propeller on top, hull below:

```
 P
IRI
 H
```

P = `immersive_aircraft:propeller`, I = iron ingot, R = redstone, H = `immersive_aircraft:hull`.

## Ship glue (Eureka)

Eureka currently treats empty planes as parked and overwrites velocity to the ship. That would cancel the catapult impulse on the next tick.

**Required:** Eureka must **not** glue a vehicle whose speed relative to the ship is already above a small threshold (0.25 blocks/tick). The catapult itself does not call Eureka types.

## Out of scope

Animated arm, fuel, power tiers, Fabric-only jar, Technic local instance copies, PRs to upstream `Luke100000/ImmersiveAircraft`.
