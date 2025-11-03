# ClientsCustomWeapons

## Commands
- `/ccw give <weaponId> [player]`: Creates the configured weapon and hands it to you or the named player. Drops the item at their feet if the inventory is full.
- `/ccw reload`: Re-reads `weapons.yml` and rebuilds the registry so changes take effect without restarting the server.

## Adding A New Weapon
1. Open `plugins/ClientsCustomWeapons/weapons.yml` (the file is generated after the first server start).
2. Copy an existing weapon block and paste it under the `weapons:` section, then give it a unique id:
   ```yaml
   weapons:
     zweihander:
       # ... existing config ...
     my_new_weapon:      # choose a lowercase id with no spaces
       name: "&bMy New Weapon"
       modelName: ""     # optional custom model namespaced key
       material: IRON_SWORD
       attackDamage: 6.0
       attackSpeed: 1.8
       maxDurability: 350
       twoHanded: false
       range: 3.0         # omit or delete this line to use default reach
       special: []        # or e.g. [DISMOUNT_MOUNTED]
   ```
3. Adjust the values:
   - `name`: Display name (supports `&` colour codes).
   - `material`: Bukkit material backing the item, e.g. `NETHERITE_AXE`.
   - `attackDamage` / `attackSpeed`: Numbers that define the combat attributes.
   - `maxDurability`: Item durability cap; must be a positive integer.
   - `twoHanded`: Set to `true` to block shield usage in the offhand.
   - `range`: Optional reach in blocks (requires ProtocolLib).
   - `special`: List of effect flags; currently supports `DISMOUNT_MOUNTED`.
4. Save the file and run `/ccw reload` in-game or from the console to apply the new weapon definition.
