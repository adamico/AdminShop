# Changelog

## 26.1.2-2.3.2

AdminShop 2.3.2 ported from Minecraft 1.21.1 to Minecraft 26.1.2 (NeoForge).

### Changed

- The HUD Balance updates as soon as the server sends it (it was every 5 seconds).
- The shop screen's search updates as you type, and an open shop screen refreshes after `/reload`.
- The Seller sells once every 20 ticks, as its tooltip says (it was every 21).

### Fixed

- After joining or leaving a party, the client kept showing and using the old team's Account; the server now resends the Account.
- For the first 5 seconds after joining, the HUD showed the whole Balance as a "+X/s" change.
- Buying into an inventory that couldn't hold everything charged the full price and lost the rest; buying is now capped to what fits.
- The Shop and the Seller drop themselves when broken (their loot tables were in the pre-1.21 `loot_tables` folder).
- AdminShop's blocks mine faster with a pickaxe; they still drop when broken by hand, as before (the mineable tags were empty, and in the pre-1.21 `tags/blocks` folder).
- Shift-clicking the item in the Seller's slot moves it to your inventory; it did nothing.
- A Seller locked to a Sell Offer sells only through that Offer; it could sell through another Offer that takes the same item.
- A tag Sell Offer with a `count` of 0 or less took no items for its price; such an Offer is now rejected at load.

### Security

- A client could make another team pay: trade requests no longer name a team, and quantities below one are refused.
- A creative player could lock any Seller anywhere to a Sell Offer by naming its position; the server now locks only the Seller whose screen the player has open.

### Offer datapack migration

AdminShop's own Offer fields keep their 1.21.1 names and shapes: `price`, `permit`, `result` (Buy Offers), `item` (Sell Offers), and `tag` with `count` (tag Sell Offers). A 1.21.1 Offer that uses only these, with goods that carry no components, loads unchanged.

The goods inside `result` and `item` are vanilla item stacks, and follow 26.1's format. Minecraft doesn't upgrade datapack files, so an Offer written for 1.21.1 may need these changes:

- **Text is an object, not a JSON string.** In `minecraft:custom_name`, `minecraft:item_name`, `minecraft:lore` and book pages, write `{"text": "Special Paper!", "italic": false}`, not `"{\"text\":\"Special Paper!\",\"italic\":false}"`. A string still loads, but as plain text: the item would be named `{"text":"Special Paper!","italic":false}`. Inside text, `hoverEvent` and `clickEvent` are now `hover_event` and `click_event`.
- **Components lost their wrapper objects and `show_in_tooltip`:**
  - `minecraft:enchantments` and `minecraft:stored_enchantments`: `{"levels": {"minecraft:efficiency": 3}}` becomes `{"minecraft:efficiency": 3}`.
  - `minecraft:attribute_modifiers`: `{"modifiers": [...]}` becomes the list itself.
  - `minecraft:can_break` and `minecraft:can_place_on`: `{"predicates": [...]}` becomes the list (or a single predicate).
  - `minecraft:dyed_color`: `{"rgb": 16711680}` becomes `16711680`.
  - `minecraft:jukebox_playable`: `{"song": "minecraft:cat"}` becomes `"minecraft:cat"`.
  - `minecraft:unbreakable`: `{"show_in_tooltip": false}` becomes `{}`.
  - `minecraft:trim`: drop `show_in_tooltip`.
- **Hiding tooltips:** `minecraft:hide_tooltip` and `minecraft:hide_additional_tooltip` are gone; use `minecraft:tooltip_display` with `hide_tooltip` or `hidden_components`.
- **`minecraft:food`** keeps `nutrition`, `saturation` and `can_always_eat`; eating time and effects moved to `minecraft:consumable`, and `using_converts_to` to `minecraft:use_remainder`.
- **`minecraft:fire_resistant`** is now `minecraft:damage_resistant` with `{"types": "#minecraft:is_fire"}`.
- **`minecraft:custom_model_data`** is an object of lists (`floats`, `flags`, `strings`, `colors`) instead of a single integer.
- **`minecraft:entity_data` and `minecraft:block_entity_data`** must name the entity or block entity type in `id`.
- **Item ids:** `minecraft:chain` is now `minecraft:iron_chain`.

Offers may now give their goods as a bare item id, such as `"result": "minecraft:apple"`. Item tag ids (`tag`) are written as before, without `#`, and a tag Sell Offer's `count` must be between 1 and 99.

A malformed Offer (no price or a negative one; no goods, or a Sell Offer naming both an `item` and a `tag`; an unknown item) is skipped at load with a log line naming the file, and the server carries on.
