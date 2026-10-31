# Changelog

## 26.1.2-2.3.2

AdminShop 2.3.2 ported from Minecraft 1.21.1 to Minecraft 26.1.2 (NeoForge).

### Changed

- JEI support is built for JEI 29.53 (Minecraft 26.1.2) and needs that version or later; JEI stays optional. Its Shop Buy and Shop Sell categories list the Offers the server sends, and update after `/reload`. An Offer whose Permit is `0` shows no Permit line there, as in the shop screen.
- Patchouli is now optional. Without it, the Econ Handbook and its recipe are absent; with it, the handbook is crafted from a book and an emerald as before.
- The Econ Handbook's Permits page names the shop screen's "Permit: ..." line (it said "Requires: ...") and no longer speaks of permit tiers.
- The HUD Balance updates as soon as the server sends it (it was every 5 seconds).
- The shop screen's search updates as you type, and an open shop screen refreshes after `/reload`.
- The Seller and the Creative Seller sell once every 20 ticks, as the Seller's tooltip says (it was every 21).
- The T1, T2 and T3 Buyers buy once every 40, 20 and 10 ticks, as their tooltips say, and the Creative Buyer every 10 like the T3 (it was every 41, 21 and 11).
- A Buyer's block entity data keeps the 1.21.1 `recipe` and `lockedRecipe` fields, so presets made with commands or `minecraft:block_entity_data` still work; its contents are now saved under `inventory` (was `Items`).
- The Fluid Buyer and the Fluid Seller buy and sell once every 20 ticks, as their tooltips say (it was every 21).
- The Fluid Buyer's block entity data keeps the 1.21.1 `recipe` and `lockedRecipe` fields, and the Fluid Seller's its `lockedRecipe` field (a fluid Sell Offer's id, which it then takes and sells only through); their tanks are now saved under `tank`.
- The Fluid Buyer and the Fluid Seller face the player who placed them, like the other machines.
- Shift-clicking a fluid container in the shop screen sells as many trades as it holds; it sold one, so a bucket couldn't be sold to an Offer of less than 1000 mB.
- Buying a fluid into a stack of empty buckets puts the filled bucket elsewhere in the inventory, or drops it when the inventory is full, as filling a bucket in the world does; it was refused when no slot was free.
- The Basic and Advanced Detectors look at their Account's Balance once every 20 ticks (it was every 21), and as soon as they are placed or loaded; a Basic Detector no longer gives full power for its first second after being placed, whatever the Balance.
- A Detector keeps its signal when saved, so an Advanced Detector's signal no longer drops to 0 for a second after its chunk loads.
- A Detector's screen shows the Detector's threshold; it showed 0, or the last threshold set from that client.
- A Permit with no `adminshop.permit.<permit>` translation shows as its own name in the shop screen's tooltip and in JEI (it showed the raw translation key).
- The warning about an obsolete `config/adminshop/shop.csv` stops once the file is deleted and the server runs `/reload`; it lasted until the server restarted.
- The server no longer logs how many Offers of each kind it has at every login, nor AdminShop's recipe serializers at start.

### Fixed

- A crafted Econ Handbook opened no book: the recipe put the book's id in `minecraft:custom_data`, where Patchouli doesn't read it; it now sets Patchouli's `patchouli:book` component.
- After joining or leaving a party, the client kept showing and using the old team's Account; the server now resends the Account.
- For the first 5 seconds after joining, the HUD showed the whole Balance as a "+X/s" change.
- Buying into an inventory that couldn't hold everything charged the full price and lost the rest; buying is now capped to what fits.
- The Shop, the Seller, the Fluid Buyer and the Fluid Seller drop themselves when broken (their loot tables were in the pre-1.21 `loot_tables` folder); so do the Basic and Advanced Detectors, which had no loot table.
- A Detector nobody owns, as one placed by a command, crashed the server's tick; it now gives no signal until a player claims it.
- When a Detector's signal changes, the blocks next to the blocks it powers strongly update too, as with a redstone torch.
- A Detector's confirm button takes clicks only on its icon; it reached 38 pixels past it.
- AdminShop's blocks mine faster with a pickaxe; they still drop when broken by hand, as before (the mineable tags were empty, and in the pre-1.21 `tags/blocks` folder).
- Shift-clicking the item in the Seller's slot moves it to your inventory; it did nothing.
- A Seller locked to a Sell Offer sells only through that Offer; it could sell through another Offer that takes the same item.
- Hoppers could put any item into a Buyer's output slots; pipes and hoppers can now only take from them.
- Pipes could fill a Fluid Buyer's tank with any fluid; they can now only drain it.
- A tag Sell Offer with a `count` of 0 or less took no items for its price; such an Offer is now rejected at load.

### Security

- A client could make another team pay: trade requests no longer name a team, and quantities below one are refused.
- A creative player could lock any Seller anywhere to a Sell Offer by naming its position; the server now locks only the Seller whose screen the player has open.
- A creative player could set any Buyer or Fluid Buyer anywhere to a Buy Offer by naming its position; the server now sets only the one whose screen the player has open.
- A player could set the threshold of any of their team's Detectors anywhere by naming its position; the server now sets only the one whose screen the player has open, and refuses a negative threshold.
- A client could give any team any Permit, and destroy any item in its inventory, with the add-Permit request; the request now names only the inventory slot, and the server takes the Permit from the Trade Permit in it, for the player's own Account, while the shop screen is open.

### Offer datapack migration

AdminShop's own Offer fields keep their 1.21.1 names and shapes: `price`, `permit`, `result` (Buy Offers and fluid Offers), `item` (Sell Offers), and `tag` with `count` (tag Sell Offers). A 1.21.1 Offer that uses only these, with goods that carry no components, loads unchanged.

Fluid Offers (`adminshop:fluid_buying`, `adminshop:fluid_selling`) load unchanged: `result` is a NeoForge fluid stack, `{"id": "minecraft:water", "amount": 1000}`, with optional `components` as before. One difference: `result` may now be a bare fluid id, which means 1000 mB (`"result": "minecraft:water"`). A fluid Offer with no fluid, an unknown fluid or an `amount` below 1 is skipped at load, like a malformed item Offer.

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
