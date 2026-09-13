# Immersive Aircraft catapult launcher

Date: 2026-09-12  
Repo: `patrck269/ImmersiveAircraft` (fork of `Luke100000/ImmersiveAircraft`), branch `1.20.1`

## Goal

A placeable **catapult** in Immersive Aircraft. Collision/outline is **1 wide × 1 long × 0.5 high** (block units). It sits on ordinary world ground and on a Eureka / Valkyrien Skies ship (a normal ship-assemblable block). One vehicle docks to the pad and stays locked until launch.

## Trigger

Two equivalent server-side fires:

1. **Redstone rising edge** — neighbor signal goes off → on. A held signal does not repeat-fire.
2. **Keybind while sitting in a docked vehicle** — registered in Controls (Immersive Aircraft category). Client sends a packet; the server launches only if that player is in the vehicle currently locked to a pad.

Cooldown **40 ticks (2 seconds)** after a successful launch. Empty pad: pulse or key does nothing.

## Dock / lock

- A vehicle **placed onto** the pad (item use) or **taxied onto** it docks: at most **one** vehicle per pad.
- While docked, the vehicle is **clamped** to the pad (position and yaw follow the block, including when the Eureka hull moves) until fire.
- Legal rest pose is **feet on the pad top** (`minY = 0.5`), not clipped into the 0.5-high collision. Detection volume is the collision box plus **1 block above** the top so Minecraft’s exclusive AABB test still sees that pose. A flyer at y=2 is not on-pad.
- Vehicle item placement **must be allowed** on the pad (vanilla `noCollision` would otherwise reject spawn on the top face).

## Launch impulse

On fire, unclamp then add velocity (blocks/tick):

- `FORWARD = 3.0` along the **pad’s facing** (horizontal direction property), not the plane’s look vector.
- `UP = 0.2` along world +Y.

Clear `onGround`. Play existing `woosh`. Occupied and empty `VehicleEntity` instances both launch (biplane, airship, hopper, Man of Many Planes add-on craft).

## Block

- Horizontal facing (four-way). Voxel **1×1×0.5**. No placement guard that forbids ships.
- Block entity owns: docked vehicle id, last redstone, cooldown.
- Creative tab: Immersive Aircraft. Block item drops itself.

Recipe (shaped, **no propeller**):

```
 I
IRI
 H
```

I = iron ingot, R = redstone, H = `immersive_aircraft:hull`.

## Eureka

The catapult does **not** load Eureka types. Empty-plane deck glue would cancel the impulse on the next tick, so Eureka must **not** glue a vehicle whose speed relative to the ship is already ≥ **0.25** blocks/tick.

## Tests

JVM tests drive the shipped functions:

- Collision box is 1×1×0.5.
- Rest-on-pad (`minY = HEIGHT`) is detected; flyer at y=2 is not.
- Rising edge fires once; held signal and cooldown do not.
- Launch impulse is non-zero along pad facing + up.

Forge + VS + IA live ship launch is not the verification bar.

## Out of scope

Animated arm, fuel, power tiers, Fabric-only distribution, copying into the local Technic instance, PRs to upstream `Luke100000/ImmersiveAircraft`.
