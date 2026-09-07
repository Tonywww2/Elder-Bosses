package com.tonywww.elder_bosses.platforms.registry;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.item.ConsecratedProstheticBladeItem;
import com.tonywww.elder_bosses.item.UnalloyedWingedHelmItem;
import com.tonywww.elder_bosses.player.GoldenNeedleItem;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.MaleniaConsecratedProstheticBladeValues;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.MaleniaUnalloyedWingedHelmValues;
import com.tonywww.elder_bosses.platforms.item.PlatformArmorMaterials;
import java.util.function.Supplier;
//? if neoforge {
/*import net.minecraft.core.Holder;
*///?}
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
/*import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class ModItems {
    private static final MaleniaConsecratedProstheticBladeValues DEFAULT_BLADE_VALUES =
        new MaleniaConsecratedProstheticBladeValues(9.0, 1.6, 2300, 15);
    private static final MaleniaUnalloyedWingedHelmValues DEFAULT_HELM_VALUES =
        new MaleniaUnalloyedWingedHelmValues(3.0, 3.5, 0.11, 450, 0.85);

    //? if forge {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ElderBosses.MOD_ID);
    //?} else {
    /*private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ElderBosses.MOD_ID);
    *///?}

    //? if forge {
    private static final ArmorMaterial UNALLOYED_GOLD =
            PlatformArmorMaterials.createUnalloyedGold(DEFAULT_HELM_VALUES);
    //?} else {
    /*private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ElderBosses.MOD_ID);
    private static final DeferredHolder<ArmorMaterial, ArmorMaterial> UNALLOYED_GOLD =
            ARMOR_MATERIALS.register(
                "unalloyed_gold",
                                () -> PlatformArmorMaterials.createUnalloyedGold(DEFAULT_HELM_VALUES)
            );
    *///?}

    //? if forge {
    public static final Supplier<GoldenNeedleItem> GOLDEN_NEEDLE = ITEMS.register(
            "golden_needle",
            () -> new GoldenNeedleItem(new Item.Properties())
    );
    //?} else {
    /*public static final Supplier<GoldenNeedleItem> GOLDEN_NEEDLE = ITEMS.registerItem(
            "golden_needle",
            GoldenNeedleItem::new
    );
    *///?}
    public static final Supplier<Item> ROT_GODDESS_REMEMBRANCE = register("rot_goddess_remembrance");
    public static final Supplier<ConsecratedProstheticBladeItem> CONSECRATED_PROSTHETIC_BLADE = ITEMS.register(
            "consecrated_prosthetic_blade",
            () -> new ConsecratedProstheticBladeItem(DEFAULT_BLADE_VALUES)
    );
    public static final Supplier<UnalloyedWingedHelmItem> UNALLOYED_WINGED_HELM = ITEMS.register(
            "unalloyed_winged_helm",
            () -> new UnalloyedWingedHelmItem(DEFAULT_HELM_VALUES)
    );
    public static final Supplier<Item> SCARLET_AEONIA_CORE = register("scarlet_aeonia_core");
    public static final Supplier<Item> HALIGTREE_ROOT_FRAGMENT = register("haligtree_root_fragment");

    private ModItems() {
    }

        //? if forge {
        public static ArmorMaterial unalloyedGoldMaterial() {
                return UNALLOYED_GOLD;
        }
        //?} else {
        /*public static Holder<ArmorMaterial> unalloyedGoldMaterial() {
                return UNALLOYED_GOLD;
        }
        *///?}

    private static Supplier<Item> register(String name) {
        //? if forge {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
        //?} else {
        /*return ITEMS.registerItem(name, Item::new);
        *///?}
    }

    public static void register(IEventBus modBus) {
        //? if neoforge {
        /*ARMOR_MATERIALS.register(modBus);
        *///?}
        ITEMS.register(modBus);
    }
}