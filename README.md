# AdminShop
Admin Shop Minecraft Mod
Adminshop is a powerful mod that allows both server administrator and modpack developers to add their own "shop" into Minecraft, where players can buy and sell items at fixed prices. It uses a custom "bank account" system, in which you can create shared accounts with other players, allowing Co-Op play in servers or modpacks that rely on this mod. The mod was originally for 1.12 by Vnator, but it has been forked to newer versions and overhauled by Ammonium.

This branch (`26.1.2/2.x`) is a port of AdminShop Overhauled 2.3.2 from Minecraft 1.21.1 to **Minecraft 26.1.2 on NeoForge**, made on the [adamico/AdminShop](https://github.com/adamico/AdminShop) fork of [AmmoniumX/AdminShop](https://github.com/AmmoniumX/AdminShop). Releases are published as GitHub Releases on the fork. What changed since 1.21.1, including the notes for updating Offer datapacks, is in [CHANGELOG.md](CHANGELOG.md). Worlds saved with 1.21.1 are not migrated.

## Requirements

| | Version |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.109 or later |
| [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge) | 26.1.2.4 or later, required |
| [FTB Library](https://www.curseforge.com/minecraft/mc-mods/ftb-library-forge) | 26.1.2.11 or later, required |
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) | 29.53.0.114 or later, optional: Shop Buy and Shop Sell categories |
| EMI (the unofficial port for 26.1.2) | 1.1.24 or later, optional: the same categories; with JEI installed too they appear once |
| [Patchouli](https://modrinth.com/mod/patchouli) | 26.1-94 or later, optional: the Econ Handbook ("Economics 101"), crafted from a book and an emerald |

AdminShop is needed on both the server and the client.

# Information for Players:

AdminShop is a mod where you can buy and sell stuff manually or automatically.

To buy and sell items and fluids manually, right-click the Shop block, or use the Shopping Tablet from anywhere. Shift and Control buy or sell more at once; shift-clicking a stack in your inventory sells it.

To buy and sell automatically, use the machines. Each one belongs to the team that placed it (or the first team to use it) and trades with that team's Account:

- **Buyer** (tiers 1, 2 and 3): open its screen and click the item you want to buy; it buys it every 40, 20 or 10 ticks into its output slots, which pipes and hoppers can pull from.
- **Seller**: sells every item piped into it that the Shop buys.
- **Fluid Buyer** and **Fluid Seller**: the same for fluids, through a 64 000 mB tank.
- **Balance Detector** and **Advanced Balance Detector**: emit redstone from the team's Balance against a threshold you set in their screen (on or off, or proportional).
- **Creative Buyer** and **Creative Seller**: set up in creative mode, then claimed in survival; they bypass Permits.

Not all items are buyable, and some can be locked behind Trade Permits. Shift-click a Trade Permit in the shop screen to redeem it for your team.

Money belongs to your [FTB Team](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge): everyone in the same party shares one Account, its Balance and its Permits. Your Balance is shown on the HUD. Create or join a party with FTB Teams to play Co-Op.

Commands:

- `/adminshop listOwnedPermits`: list the Permits your team's Account has unlocked.
- `/adminshop removePermit <permit>`: remove a Permit from your team's Account.

# Information for Server Admins and Modpack Developers:

Adminshop is a "player-to-server" shop, not a "player-to-player" shop. What does this mean?

Adminshop allows you to:

Set up fixed prices at which to buy and sell items and fluids from datapacks.
The items are created/destroyed the moment that a player buys or sells them, there is no "limit" to the amount that can be bought or sold.

Adminshop does *not* allow you to:

Buy and sell items between players at a player's desired price.
Have players create their own shops with custom items and prices.
A player-to-server shop is ideal for server administrators that want to offer players the ability to buy and sell items at a specified price that will never change and will never run out of "stock", or for modpack developers that want to include a buying/selling mechanic in their modpack as a way to progress through it.

## Offers

The Shop's contents are Offers: recipes of four types in any datapack's `data/<namespace>/recipe/` folder. `/reload` updates the Shop, JEI and EMI for every connected player. A malformed Offer is skipped with a log line naming its file.

| Type | Fields |
|---|---|
| `adminshop:item_buying` | `price`, `result` (an item stack: `id`, optional `count` and `components`), optional `permit` |
| `adminshop:item_selling` | `price`, either `item` (an item stack) or `tag` with an optional `count` (1–99), optional `permit` |
| `adminshop:fluid_buying` | `price`, `result` (`id`, `amount` in mB), optional `permit` |
| `adminshop:fluid_selling` | `price`, `result` (`id`, `amount` in mB), optional `permit` |

Prices are in the smallest unit of money (with the default display, `100` shows as $1.00). For example:

```json
{
  "type": "adminshop:item_selling",
  "price": 100,
  "tag": "minecraft:wool",
  "count": 16
}
```

AdminShop's examples ship in the mod jar under `data/adminshop/recipe/example_*.json`. Offers written for 1.21.1 keep loading where they use AdminShop's own fields; the vanilla item stack format inside them changed, see the migration notes in [CHANGELOG.md](CHANGELOG.md). The obsolete `config/adminshop/shop.csv` is not read: while it exists, the server warns in its log and in chat.

## Trade Permits

For modpack developers, they can add their own Trade Permits as a "gatekeep" in progression in the shop. I.E, you can lock the ability to buy and sell specific items behind a craftable trade permit, and only once you craft and redeem it you can unlock said items in the shop.

An Offer with `"permit": "gems"` can only be traded by a team whose Account holds the Permit `gems`, by hand and by machines alike. A Trade Permit item carries its Permit in its custom data, so a crafting recipe or command can make one:

```
/give @p adminshop:permit[custom_data={key:"gems"}]
```

The Permit's display name comes from the translation key `adminshop.permit.<permit>`, and falls back to the Permit itself.

## Commands

- `/adminshop give <player> <amount>` and `/adminshop remove <player> <amount>`: change the Balance of a player's team (admins).
- `/adminshop getPermit <permit>`: get a Trade Permit item for testing (admins).

## Config

The server config (`serverconfig/adminshop-server.toml` in the world folder) sets `starting_money` for new Accounts. The client config (`config/adminshop-client.toml`) sets the Balance HUD: `Balance Display`, `Short mode `, `Ignore decimal offset` and `Balance Delta`.

# Building

Requires Java 25.

```bash
./gradlew build
```

builds the jar into `build/libs/` and runs the unit tests.

```bash
./gradlew runGameTestServer
```

runs the game tests on a headless server and writes `build/reports/gametest.xml`; the task fails if any test fails. CI runs both on every push and pull request.

`./gradlew runClient` and `./gradlew runServer` start a dev client or server with Patchouli and JEI. Choose the recipe viewers with `-Pviewers`: `jei` (the default), `emi`, `jei,emi` or `none`, for example:

```bash
./gradlew runClient -Pviewers=jei,emi
```

# License

See [LICENSE.md](LICENSE.md) (GNU LGPL 2.1).
