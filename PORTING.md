# Porting 1.21.1 → 26.1.2: parity audit

AdminShop 2.3.2 was ported from Minecraft 1.21.1 (branch `1.21.1/2.x`) to Minecraft 26.1.2 (branch `26.1.2/2.x`) in tickets #3–#17 of [#2](https://github.com/adamico/AdminShop/issues/2). Ticket #3 moved the 1.21.1 `src/main` into an uncompiled `legacy-1.21.1/` folder; each later ticket rewrote what it ported into `src/main` and deleted it from there. This file records, for every file under `src/main` on `1.21.1/2.x`, where it is on `26.1.2/2.x`, or why it was dropped. Ticket #18 removed the emptied folder.

The "Ticket" column names the ticket and commit that retired the 1.21.1 file. Behaviour changes and fixes are in [CHANGELOG.md](CHANGELOG.md); world data from 1.21.1 is not migrated.

## Summary

221 files under `src/main` on 1.21.1:

| Outcome | Files |
|---|---|
| Same path, unchanged | 56 |
| Same path, ported in place | 78 |
| Moved or renamed | 13 |
| Merged into another file | 34 |
| Replaced by a 26.1 equivalent | 14 |
| Split across several files | 1 |
| Dropped | 25 |

Every dropped file was unused on 1.21.1 (checked by searching the 1.21.1 tree for references), an empty tag, or a shell whose content moved elsewhere.

## Registry ids

Every 1.21.1 id is registered on 26.1.2 under the same name, except where noted.

| Registry | Ids | 26.1.2 |
|---|---|---|
| Blocks and block items | `shop`, `seller`, `buyer_1`, `buyer_2`, `buyer_3`, `fluid_buyer`, `fluid_seller`, `creative_buyer`, `creative_seller`, `detector`, `adv_detector` | all present (`blocks/ModBlocks.java`) |
| Items | `permit`, `tablet` | both present (`item/ModItems.java`) |
| Block entity types | the same 11 ids as the blocks | all present (`blocks/entity/ModBlockEntities.java`) |
| Menu types | `shop_menu`, `seller_menu`, `buyer_menu`, `buyer_2_menu`, `buyer_3_menu`, `fluid_buyer_menu`, `fluid_seller_menu`, `detector_menu`, `advanced_detector_menu` | all present (`screen/ModMenuTypes.java`) |
| Menu types | `creative_buyer_menu`, `creative_seller_menu` | dropped (#11): the Creative Buyer opens `buyer_3_menu`, the Creative Seller `seller_menu`; menu ids are never saved |
| Recipe types and serializers | `item_buying`, `item_selling`, `fluid_buying`, `fluid_selling` | all present (`recipes/ModRecipeTypes.java`, `recipes/ModRecipeSerializers.java`) |
| Creative tab | `creativetab` (title `item_group.adminshop.creativetab`) | present (`setup/CreativeTab.java`) |
| Payloads | `sync_money_to_client`, `buy_request`, `sell_request`, `set_seller_recipe`, `set_item_buyer_recipe`, `set_fluid_buyer_recipe`, `set_detector_threshold`, `account_add_permit` | all present (`setup/Messages.java`); several carry less than on 1.21.1 (no team id or position), see the changelog's Security section |
| Payloads | `update_request` | dropped (#7, #12): machine screens get their state through menu data slots |
| JEI recipe types | `jei_buy_recipe`, `jei_sell_recipe` | present (`compat/jei/AdminShopJeiPlugin.java`, #15) |
| Saved data | 1.21.1's Account storage | rewritten as `adminshop:accounts` codec saved data (#4); 1.21.1 data is not read |
| Commands | `/adminshop give`, `remove`, `getPermit`, `removePermit`, `listOwnedPermits` | all present with the same arguments and permission levels (`commands/AdminShopCommand.java`, #4, #9) |
| Config | `server_config.starting_money`; `display_config."Balance Display"`, `"Short mode "`, `"Ignore decimal offset"`, `"Balance Delta"` | `setup/Config.java` is byte-identical to 1.21.1 (#4, checked in #13) |
| Key bindings | none registered on 1.21.1 (both registrations commented out) | none (#13) |

## Translation keys

`assets/adminshop/lang/en_us.json` keeps every 1.21.1 key and text except these, none of which 1.21.1's code or assets read:

| Key | Reason |
|---|---|
| `key.categories.adminshop` | no key binding was registered (#13) |
| `be.adminshop.buyer` | the Buyer's menu title is `container.adminshop.buyer`, same text "Buyer" (#8) |
| `screen.adminshop.buyer`, `screen.adminshop.seller` | only named by the blocks' `getMenuProvider`, which nothing called: 1.21.1 opened menus with the block entity's own title (#10, #13) |
| `gui.item_tag`, `gui.fluid_tag`, `gui.adminshop.tank_gauge`, `itemGroup.adminshop` | unused (#13) |

The 1.21.1 file also held `gui.adminshop.tank_empty` and `sellitem.recipe.tag` twice; each is kept once. One key was added: `container.adminshop.buyer`.

## Outside `src/main`

| 1.21.1 path | 26.1.2 |
|---|---|
| `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat` | rewritten for ModDevGradle, Java 25 and Gradle 9.5.1 (#3) |
| `commons-csv-1.9.0.jar`, `src/test/java/CSVParserTest.java`, `src/test/java/shop.csv` | dropped (#3): 1.21.1 no longer read `shop.csv` (Offers are datapack recipes), so the bundled CSV library and the CSV parser test had nothing left to do |
| `runs/client/usernamecache.json` | dropped: NeoGradle run output; ModDevGradle runs use the ignored `run/` (#3) |
| `.gitattributes`, `.github/ISSUE_TEMPLATE/*`, `LICENSE.md` | kept unchanged |
| `.gitignore` | kept, plus `mcmodsrepo` (#3) |
| `README.md` | rewritten for 26.1.2 (#18) |

## Files

| 1.21.1 path (`src/main/`) | 26.1.2 | Ticket |
|---|---|---|
| `java/…/AdminShop.java` | rewritten for 26.1.2 (#3); the 1.21.1 copy was retired in #13 | #13 (573efb6) |
| `java/…/blocks/AbstractBuyerBlock.java` | merged into `blocks/BuyerBlock.java` + `blocks/BuyerTier.java` | #8 (e586bbf) |
| `java/…/blocks/AdvancedDetector.java` | merged into `blocks/DetectorBlock.java` (`DetectorKind.ADVANCED`) | #12 (698d855) |
| `java/…/blocks/BasicDetector.java` | merged into `blocks/DetectorBlock.java` (`DetectorKind.BASIC`) | #12 (698d855) |
| `java/…/blocks/BuyerBlock1.java` | merged into `blocks/BuyerBlock.java` (tier `BuyerTier.ONE`) | #8 (e586bbf) |
| `java/…/blocks/BuyerBlock2.java` | merged into `blocks/BuyerBlock.java` (tier `BuyerTier.TWO`) | #8 (e586bbf) |
| `java/…/blocks/BuyerBlock3.java` | merged into `blocks/BuyerBlock.java` (tier `BuyerTier.THREE`) | #8 (e586bbf) |
| `java/…/blocks/CreativeBuyerBlock.java` | ported in place | #11 (94cf19a) |
| `java/…/blocks/CreativeSellerBlock.java` | ported in place | #11 (94cf19a) |
| `java/…/blocks/FluidBuyerBlock.java` | ported in place | #10 (f2c02b6) |
| `java/…/blocks/FluidSellerBlock.java` | ported in place | #10 (f2c02b6) |
| `java/…/blocks/ModBlocks.java` | ported in place | #13 (573efb6) |
| `java/…/blocks/SellerBlock.java` | ported in place | #7 (71d9300) |
| `java/…/blocks/ShopBlock.java` | ported in place | #5 (45a00fb) |
| `java/…/blocks/entity/AbstractBuyerEntity.java` | merged into `blocks/entity/BuyerEntity.java` | #8 (e586bbf) |
| `java/…/blocks/entity/AdvancedDetectorEntity.java` | merged into `blocks/entity/DetectorEntity.java` | #12 (698d855) |
| `java/…/blocks/entity/BasicDetectorEntity.java` | merged into `blocks/entity/DetectorEntity.java` | #12 (698d855) |
| `java/…/blocks/entity/BuyerEntity1.java` | merged into `blocks/entity/BuyerEntity.java` | #8 (e586bbf) |
| `java/…/blocks/entity/BuyerEntity2.java` | merged into `blocks/entity/BuyerEntity.java` | #8 (e586bbf) |
| `java/…/blocks/entity/BuyerEntity3.java` | merged into `blocks/entity/BuyerEntity.java` | #8 (e586bbf) |
| `java/…/blocks/entity/CreativeBuyerEntity.java` | ported in place | #11 (94cf19a) |
| `java/…/blocks/entity/CreativeSellerEntity.java` | ported in place | #11 (94cf19a) |
| `java/…/blocks/entity/FluidBuyerEntity.java` | ported in place | #10 (f2c02b6) |
| `java/…/blocks/entity/FluidHandlerBlockEntity.java` | replaced by `blocks/entity/MachineTank.java` (NeoForge resource handlers) | #10 (f2c02b6) |
| `java/…/blocks/entity/FluidSellerEntity.java` | ported in place | #10 (f2c02b6) |
| `java/…/blocks/entity/ModBlockEntities.java` | ported in place | #13 (573efb6) |
| `java/…/blocks/entity/SellerEntity.java` | ported in place | #7 (71d9300) |
| `java/…/blocks/entity/ShopEntity.java` | ported in place | #5 (45a00fb) |
| `java/…/blocks/interfaces/Detector.java` | merged into `blocks/DetectorKind.java` and `blocks/entity/DetectorEntity.java` | #12 (698d855) |
| `java/…/blocks/interfaces/FluidBuyerMachine.java` | merged into `blocks/entity/FluidBuyerEntity.java` and `blocks/interfaces/ShopMachine.java` | #10 (f2c02b6) |
| `java/…/blocks/interfaces/FluidSellerMachine.java` | merged into `blocks/entity/FluidSellerEntity.java` and `blocks/interfaces/ShopMachine.java` | #10 (f2c02b6) |
| `java/…/blocks/interfaces/ItemBuyerMachine.java` | merged into `blocks/entity/BuyerEntity.java` and `blocks/interfaces/ShopMachine.java` | #8 (e586bbf) |
| `java/…/blocks/interfaces/ItemSellerMachine.java` | dropped: an interface the 1.21.1 Seller implemented only for RecipeManager/SellItemRecipe; machine selling lives in `shop/ShopTrades.sellFrom` | #7 (71d9300) |
| `java/…/blocks/interfaces/ShopMachine.java` | ported in place | #7 (71d9300) |
| `java/…/client/KeyInit.java` | dropped: registered no key on 1.21.1 (both registrations commented out) | #13 (83b9b82) |
| `java/…/client/events/ClientForgeEvents.java` | dropped: its only handler (the key binding tick) was empty | #13 (83b9b82) |
| `java/…/client/events/ServerEventListeners.java` | split: login Account sync → `money/AccountEvents.java` (#4); command registration → `commands/AdminShopCommand.java` (#4, #9); `shop.csv` warning → `setup/ObsoleteShopCsv.java` (#13); debug Offer-count logging dropped (#13) | #13 (b4e004d) |
| `java/…/client/gui/BuySellButton.java` | ported in place | #5 (bab1329) |
| `java/…/client/gui/ProgressBar.java` | ported in place | #7 (812b8db) |
| `java/…/client/gui/ScrollButton.java` | dropped: unused stub on 1.21.1 | #5 (bab1329) |
| `java/…/client/gui/ShopButton.java` | ported in place | #10 (209f08b) |
| `java/…/client/gui/TankGauge.java` | ported in place | #10 (f2c02b6) |
| `java/…/client/gui/TextConfirmButton.java` | ported in place | #12 (698d855) |
| `java/…/client/jei/AdminShopJEI.java` | moved to `compat/jei/AdminShopJeiPlugin.java` | #15 (ff7a16a) |
| `java/…/client/jei/ShopBuyCategory.java` | merged into `compat/jei/ShopJeiCategory.java` + `compat/ShopCategory.java` (`BUY`) | #15 (ff7a16a) |
| `java/…/client/jei/ShopSellCategory.java` | merged into `compat/jei/ShopJeiCategory.java` + `compat/ShopCategory.java` (`SELL`) | #15 (ff7a16a) |
| `java/…/commands/AdminShopCommand.java` | ported in place | #9 (82a444c) |
| `java/…/item/LoreBlockItem.java` | ported in place | #5 (45a00fb) |
| `java/…/item/LoreItem.java` | ported in place | #5 (45a00fb) |
| `java/…/item/ModItems.java` | ported in place | #9 (82a444c) |
| `java/…/item/ShopTablet.java` | ported in place | #5 (45a00fb) |
| `java/…/money/BalanceDisplay.java` | ported in place | #4 (5378fd9) |
| `java/…/money/ClientCache.java` | ported in place | #4 (8f47594) |
| `java/…/money/MoneyFormat.java` | ported in place | #4 (33b8dee) |
| `java/…/money/MoneyHelper.java` | ported in place | #4 (8f47594) |
| `java/…/network/PacketAccountAddPermit.java` | ported in place | #9 (82a444c) |
| `java/…/network/PacketBuyRequest.java` | ported in place | #10 (6d5f2f9) |
| `java/…/network/PacketSellRequest.java` | ported in place | #10 (6d5f2f9) |
| `java/…/network/PacketSetDetectorThreshold.java` | ported in place | #12 (698d855) |
| `java/…/network/PacketSetFluidBuyerRecipe.java` | ported in place | #10 (f2c02b6) |
| `java/…/network/PacketSetItemBuyerRecipe.java` | renamed `network/PacketSetBuyerRecipe.java` (payload id `set_item_buyer_recipe` kept) | #8 (e586bbf) |
| `java/…/network/PacketSetSellerRecipe.java` | ported in place | #7 (812b8db) |
| `java/…/network/PacketSyncMoneyToClient.java` | ported in place | #4 (8f47594) |
| `java/…/network/PacketUpdateRequest.java` | dropped: machine screens get their state through menu data slots, no client BE sync (#7, #12) | #12 (698d855) |
| `java/…/recipes/BuyFluidRecipe.java` | ported in place | #10 (209f08b) |
| `java/…/recipes/BuyItemRecipe.java` | ported in place | #5 (780aa7e) |
| `java/…/recipes/ModRecipeSerializers.java` | ported in place | #10 (209f08b) |
| `java/…/recipes/ModRecipeTypes.java` | ported in place | #10 (209f08b) |
| `java/…/recipes/RecipeManager.java` | dropped: server lookups use `server.getRecipeManager()` by recipe key in `shop/ShopTrades.java`; the client reads `shop/ShopCache.java` / `shop/ShopDisplay.java` (#5); its last users were the JEI categories (#15) | #15 (ff7a16a) |
| `java/…/recipes/SellFluidRecipe.java` | ported in place | #10 (209f08b) |
| `java/…/recipes/SellItemRecipe.java` | ported in place | #7 (71d9300) |
| `java/…/recipes/interfaces/BuyRecipe.java` | merged into `recipes/ShopRecipe.java` (`isBuy()`) | #5 (780aa7e) |
| `java/…/recipes/interfaces/FluidRecipe.java` | merged into `recipes/BuyFluidRecipe.java` / `recipes/SellFluidRecipe.java` and `shop/ShopGoods.java` (`Fluid`) | #10 (209f08b) |
| `java/…/recipes/interfaces/ItemRecipe.java` | merged into `recipes/ShopRecipe.java` (`goods()`) and `shop/ShopGoods.java` | #5 (780aa7e) |
| `java/…/recipes/interfaces/SellRecipe.java` | merged into `recipes/ShopRecipe.java` (`isBuy()`) | #5 (780aa7e) |
| `java/…/recipes/interfaces/ShopRecipe.java` | moved to `recipes/ShopRecipe.java` | #5 (780aa7e) |
| `java/…/screen/AbstractBuyerMenu.java` | merged into `screen/BuyerMenu.java` | #8 (e586bbf) |
| `java/…/screen/AbstractBuyerScreen.java` | merged into `screen/BuyerScreen.java` | #8 (d79ddbc) |
| `java/…/screen/AdvancedDetectorMenu.java` | merged into `screen/DetectorMenu.java` (menu id `advanced_detector_menu` kept) | #12 (698d855) |
| `java/…/screen/AdvancedDetectorScreen.java` | merged into `screen/DetectorScreen.java` | #12 (698d855) |
| `java/…/screen/BasicDetectorMenu.java` | merged into `screen/DetectorMenu.java` (menu id `detector_menu` kept) | #12 (698d855) |
| `java/…/screen/BasicDetectorScreen.java` | merged into `screen/DetectorScreen.java` | #12 (698d855) |
| `java/…/screen/BuyerMenu1.java` | merged into `screen/BuyerMenu.java` (menu id `buyer_menu` kept) | #8 (e586bbf) |
| `java/…/screen/BuyerMenu2.java` | merged into `screen/BuyerMenu.java` (menu id `buyer_2_menu` kept) | #8 (e586bbf) |
| `java/…/screen/BuyerMenu3.java` | merged into `screen/BuyerMenu.java` (menu id `buyer_3_menu` kept) | #8 (e586bbf) |
| `java/…/screen/BuyerScreen1.java` | merged into `screen/BuyerScreen.java` | #8 (d79ddbc) |
| `java/…/screen/BuyerScreen2.java` | merged into `screen/BuyerScreen.java` | #8 (d79ddbc) |
| `java/…/screen/BuyerScreen3.java` | merged into `screen/BuyerScreen.java` | #8 (d79ddbc) |
| `java/…/screen/CreativeBuyerMenu.java` | dropped: the Creative Buyer opens the T3 Buyer menu (`screen/BuyerMenu.java`, `buyer_3_menu`); menu ids are never saved | #11 (94cf19a) |
| `java/…/screen/CreativeSellerMenu.java` | dropped: the Creative Seller opens the Seller menu (`screen/SellerMenu.java`, `seller_menu`); menu ids are never saved | #11 (94cf19a) |
| `java/…/screen/DetectorMenu.java` | ported in place | #12 (698d855) |
| `java/…/screen/DetectorScreen.java` | ported in place | #12 (698d855) |
| `java/…/screen/FluidBuyerMenu.java` | ported in place | #10 (f2c02b6) |
| `java/…/screen/FluidBuyerScreen.java` | ported in place | #10 (f2c02b6) |
| `java/…/screen/FluidSellerMenu.java` | ported in place | #10 (f2c02b6) |
| `java/…/screen/FluidSellerScreen.java` | ported in place | #10 (f2c02b6) |
| `java/…/screen/ModMenuTypes.java` | ported in place | #13 (573efb6) |
| `java/…/screen/SellerMenu.java` | ported in place | #7 (71d9300) |
| `java/…/screen/SellerScreen.java` | ported in place | #7 (812b8db) |
| `java/…/screen/ShopMenu.java` | ported in place | #5 (45a00fb) |
| `java/…/screen/ShopScreen.java` | ported in place | #10 (6d5f2f9) |
| `java/…/screen/slot/ResultSlot.java` | replaced by `ResourceHandlerSlot`s over `MachineInventory.extractOnly()` in `screen/BuyerMenu.java` | #8 (e586bbf) |
| `java/…/screen/slot/ShopItemInputSlot.java` | replaced by a `ResourceHandlerSlot` over the filtering `blocks/entity/MachineInventory.java` in `screen/SellerMenu.java` | #7 (71d9300) |
| `java/…/setup/ClientSetup.java` | ported in place | #13 (83b9b82) |
| `java/…/setup/Config.java` | unchanged | #4 (211c168) |
| `java/…/setup/CreativeTab.java` | ported in place | #5 (45a00fb) |
| `java/…/setup/Messages.java` | ported in place | #13 (573efb6) |
| `java/…/setup/ModSetup.java` | dropped: held only commented-out code; the tab is `setup/CreativeTab.java` | #13 (83b9b82) |
| `resources/META-INF/neoforge.mods.toml` | rewritten for 26.1.2 (#3): FTB Teams and FTB Library required, Patchouli (#14), JEI (#15) and EMI (#16) optional | #15 (e9cf848) |
| `resources/assets/adminshop/blockstates/adv_detector.json` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/blockstates/buyer_1.json` | unchanged | #8 (e586bbf) |
| `resources/assets/adminshop/blockstates/buyer_2.json` | unchanged | #8 (e586bbf) |
| `resources/assets/adminshop/blockstates/buyer_3.json` | unchanged | #8 (e586bbf) |
| `resources/assets/adminshop/blockstates/creative_buyer.json` | unchanged | #11 (94cf19a) |
| `resources/assets/adminshop/blockstates/creative_seller.json` | unchanged | #11 (94cf19a) |
| `resources/assets/adminshop/blockstates/detector.json` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/blockstates/fluid_buyer.json` | ported in place | #10 (f2c02b6) |
| `resources/assets/adminshop/blockstates/fluid_seller.json` | ported in place | #10 (f2c02b6) |
| `resources/assets/adminshop/blockstates/seller.json` | unchanged | #7 (71d9300) |
| `resources/assets/adminshop/blockstates/shop.json` | unchanged | #5 (45a00fb) |
| `resources/assets/adminshop/lang/en_us.json` | ported in place, key by key as each feature moved; the dropped keys are listed below | #15 (ff7a16a) |
| `resources/assets/adminshop/models/block/adv_detector.json` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/models/block/adv_detector_off.json` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/models/block/buyer_1.json` | unchanged | #8 (e586bbf) |
| `resources/assets/adminshop/models/block/buyer_2.json` | unchanged | #8 (e586bbf) |
| `resources/assets/adminshop/models/block/buyer_3.json` | unchanged | #8 (e586bbf) |
| `resources/assets/adminshop/models/block/detector.json` | ported in place | #12 (698d855) |
| `resources/assets/adminshop/models/block/detector_off.json` | ported in place | #12 (698d855) |
| `resources/assets/adminshop/models/block/fluid_buyer.json` | unchanged | #10 (f2c02b6) |
| `resources/assets/adminshop/models/block/fluid_seller.json` | unchanged | #10 (f2c02b6) |
| `resources/assets/adminshop/models/block/seller.json` | unchanged | #7 (71d9300) |
| `resources/assets/adminshop/models/block/shop.json` | unchanged | #5 (45a00fb) |
| `resources/assets/adminshop/models/item/adv_detector.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/adv_detector.json` (it only pointed at the block model) | #12 (698d855) |
| `resources/assets/adminshop/models/item/buyer_1.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/buyer_1.json` (it only pointed at the block model) | #8 (e586bbf) |
| `resources/assets/adminshop/models/item/buyer_2.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/buyer_2.json` (it only pointed at the block model) | #8 (e586bbf) |
| `resources/assets/adminshop/models/item/buyer_3.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/buyer_3.json` (it only pointed at the block model) | #8 (e586bbf) |
| `resources/assets/adminshop/models/item/creative_buyer.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/creative_buyer.json` (it only pointed at the block model) | #11 (94cf19a) |
| `resources/assets/adminshop/models/item/creative_seller.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/creative_seller.json` (it only pointed at the block model) | #11 (94cf19a) |
| `resources/assets/adminshop/models/item/detector.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/detector.json` (it only pointed at the block model) | #12 (698d855) |
| `resources/assets/adminshop/models/item/econ_handbook.json` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/models/item/fluid_buyer.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/fluid_buyer.json` (it only pointed at the block model) | #10 (f2c02b6) |
| `resources/assets/adminshop/models/item/fluid_seller.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/fluid_seller.json` (it only pointed at the block model) | #10 (f2c02b6) |
| `resources/assets/adminshop/models/item/permit.json` | unchanged | #9 (82a444c) |
| `resources/assets/adminshop/models/item/seller.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/seller.json` (it only pointed at the block model) | #7 (71d9300) |
| `resources/assets/adminshop/models/item/shop.json` | replaced by the 26.1 client item definition `resources/assets/adminshop/items/shop.json` (it only pointed at the block model) | #5 (45a00fb) |
| `resources/assets/adminshop/models/item/tablet.json` | unchanged | #5 (45a00fb) |
| `resources/assets/adminshop/patchouli_books/econ_handbook/en_us/categories/introduction.json` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/patchouli_books/econ_handbook/en_us/entries/guides/buyer.json` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/patchouli_books/econ_handbook/en_us/entries/guides/permits.json` | ported in place | #14 (b636393) |
| `resources/assets/adminshop/patchouli_books/econ_handbook/en_us/entries/guides/seller.json` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/patchouli_books/econ_handbook/en_us/entries/guides/shop.json` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/patchouli_books/econ_handbook/en_us/entries/guides/whatis.json` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/textures/block/adv_detector.png` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/textures/block/detector.png` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/textures/block/detector_off.png` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/textures/block/ender.png` | unchanged | #5 (45a00fb) |
| `resources/assets/adminshop/textures/block/glass.jpg` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/block/hopper_inside.png` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/block/hopper_outside.png` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/block/hopper_top.png` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/block/ironblock.png` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/block/machines_generic.png` | unchanged | #5 (45a00fb) |
| `resources/assets/adminshop/textures/block/sellertexture.png` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/block/shopfront.png` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/block/shopside.png` | dropped: no model referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/gui/adv_detector.png` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/textures/gui/atm.png` | dropped: no screen or book page referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/gui/base_gui.png` | dropped: no screen or book page referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/gui/buyer.png` | unchanged | #8 (d79ddbc) |
| `resources/assets/adminshop/textures/gui/buyer_2.png` | unchanged | #8 (d79ddbc) |
| `resources/assets/adminshop/textures/gui/buyer_3.png` | unchanged | #8 (d79ddbc) |
| `resources/assets/adminshop/textures/gui/detector.png` | unchanged | #12 (698d855) |
| `resources/assets/adminshop/textures/gui/entries/buyergui.png` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/textures/gui/entries/sellergui.png` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/textures/gui/entries/shopgui.png` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/textures/gui/fluid_buyer.png` | unchanged | #10 (f2c02b6) |
| `resources/assets/adminshop/textures/gui/fluid_seller.png` | unchanged | #10 (f2c02b6) |
| `resources/assets/adminshop/textures/gui/jei_buy_category.png` | unchanged | #15 (ff7a16a) |
| `resources/assets/adminshop/textures/gui/jei_sell_category.png` | unchanged | #15 (ff7a16a) |
| `resources/assets/adminshop/textures/gui/pedestal.png` | dropped: no screen or book page referenced it on 1.21.1 | #13 (573efb6) |
| `resources/assets/adminshop/textures/gui/progress_right.png` | unchanged | #7 (812b8db) |
| `resources/assets/adminshop/textures/gui/seller.png` | unchanged | #7 (812b8db) |
| `resources/assets/adminshop/textures/gui/shop_gui.png` | unchanged | #5 (45a00fb) |
| `resources/assets/adminshop/textures/gui/shopjei.png` | dropped: referenced by nothing on 1.21.1 | #15 (ff7a16a) |
| `resources/assets/adminshop/textures/item/econ_handbook.png` | unchanged | #14 (b636393) |
| `resources/assets/adminshop/textures/item/permit.png` | unchanged | #9 (82a444c) |
| `resources/assets/adminshop/textures/item/tablet.png` | unchanged | #5 (45a00fb) |
| `resources/data/adminshop/loot_tables/blocks/buyer_1.json` | moved to `resources/data/adminshop/loot_table/blocks/buyer_1.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #8 (e586bbf) |
| `resources/data/adminshop/loot_tables/blocks/buyer_2.json` | moved to `resources/data/adminshop/loot_table/blocks/buyer_2.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #8 (e586bbf) |
| `resources/data/adminshop/loot_tables/blocks/buyer_3.json` | moved to `resources/data/adminshop/loot_table/blocks/buyer_3.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #8 (e586bbf) |
| `resources/data/adminshop/loot_tables/blocks/creative_buyer.json` | moved to `resources/data/adminshop/loot_table/blocks/creative_buyer.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #11 (94cf19a) |
| `resources/data/adminshop/loot_tables/blocks/creative_seller.json` | moved to `resources/data/adminshop/loot_table/blocks/creative_seller.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #11 (94cf19a) |
| `resources/data/adminshop/loot_tables/blocks/fluid_buyer.json` | moved to `resources/data/adminshop/loot_table/blocks/fluid_buyer.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #10 (f2c02b6) |
| `resources/data/adminshop/loot_tables/blocks/fluid_seller.json` | moved to `resources/data/adminshop/loot_table/blocks/fluid_seller.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #10 (f2c02b6) |
| `resources/data/adminshop/loot_tables/blocks/seller.json` | moved to `resources/data/adminshop/loot_table/blocks/seller.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #7 (71d9300) |
| `resources/data/adminshop/loot_tables/blocks/shop.json` | moved to `resources/data/adminshop/loot_table/blocks/shop.json`, unchanged (26.1 folder name; 1.21.1 ignored the pre-1.21 `loot_tables` folder, so the block dropped nothing) | #5 (45a00fb) |
| `resources/data/adminshop/patchouli_books/econ_handbook/book.json` | ported in place | #14 (b636393) |
| `resources/data/adminshop/recipe/adv_detector.json` | ported in place | #12 (698d855) |
| `resources/data/adminshop/recipe/buyer_1.json` | ported in place | #8 (e586bbf) |
| `resources/data/adminshop/recipe/buyer_2.json` | ported in place | #8 (e586bbf) |
| `resources/data/adminshop/recipe/buyer_3.json` | ported in place | #8 (e586bbf) |
| `resources/data/adminshop/recipe/detector.json` | ported in place | #12 (698d855) |
| `resources/data/adminshop/recipe/econ_handbook.json` | ported in place | #14 (b636393) |
| `resources/data/adminshop/recipe/example_buy_fluid.json` | unchanged | #10 (209f08b) |
| `resources/data/adminshop/recipe/example_buy_item.json` | unchanged | #5 (780aa7e) |
| `resources/data/adminshop/recipe/example_buy_nbt.json` | ported in place | #6 (e08e85a) |
| `resources/data/adminshop/recipe/example_permits.json` | unchanged | #9 (98cf185) |
| `resources/data/adminshop/recipe/example_sell_fluid.json` | unchanged | #10 (209f08b) |
| `resources/data/adminshop/recipe/example_sell_item.json` | unchanged | #5 (780aa7e) |
| `resources/data/adminshop/recipe/example_sell_item_tag.json` | unchanged | #6 (3dbd61b) |
| `resources/data/adminshop/recipe/example_sell_nbt.json` | ported in place | #6 (e08e85a) |
| `resources/data/adminshop/recipe/fluid_buyer.json` | ported in place | #10 (f2c02b6) |
| `resources/data/adminshop/recipe/fluid_seller.json` | ported in place | #10 (f2c02b6) |
| `resources/data/adminshop/recipe/seller.json` | ported in place | #7 (71d9300) |
| `resources/data/adminshop/recipe/shop.json` | ported in place | #5 (45a00fb) |
| `resources/data/adminshop/recipe/tablet.json` | ported in place | #5 (45a00fb) |
| `resources/data/minecraft/tags/blocks/mineable/axe.json` | dropped: empty tag | #7 (e931bf7) |
| `resources/data/minecraft/tags/blocks/mineable/hoe.json` | dropped: empty tag | #7 (e931bf7) |
| `resources/data/minecraft/tags/blocks/mineable/pickaxe.json` | moved to `resources/data/minecraft/tags/block/mineable/pickaxe.json` (26.1 folder name), now listing every breakable AdminShop block | #7 (e931bf7) |
| `resources/data/minecraft/tags/blocks/mineable/shovel.json` | dropped: empty tag | #7 (e931bf7) |
| `resources/pack.mcmeta` | dropped: NeoForge generates the mod's pack metadata (1.21.1's said pack_format 9) | #13 (573efb6) |
