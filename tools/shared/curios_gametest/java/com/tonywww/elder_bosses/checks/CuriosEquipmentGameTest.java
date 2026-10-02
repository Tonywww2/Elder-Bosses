package com.tonywww.elder_bosses.checks;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.registry.ModAttributes;
import com.tonywww.elder_bosses.platforms.registry.ModItems;
import com.tonywww.elder_bosses.item.EquipmentSettings;
import com.tonywww.elder_bosses.network.EquipmentSettingsPacket;
import com.tonywww.elder_bosses.platforms.config.PlatformEquipmentConfig;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/** Exercises the real Curios mixins, registry, slot data and server equip/unequip events. */
@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class CuriosEquipmentGameTest {
    @GameTest(template = "curios_empty", timeoutTicks = 100)
    public static void equipmentAndRecipes(GameTestHelper helper) {
        Player player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "CuriosTest"));
        EquipmentSettings settings = expectedSettings();
        check(PlatformEquipmentConfig.current().equals(settings), "TOML values did not reach the startup snapshot");
        weapon(player, new ItemStack(ModItems.CONSECRATED_PROSTHETIC_BLADE.get()), settings.blade());
        weapon(player, new ItemStack(ModItems.YOUNG_LION_GREATSWORD.get()), settings.greatsword());
        checkConfigSync(settings);
        if (Boolean.getBoolean("elder_bosses.expectNoCurios")) {
            check(!net.minecraftforge.fml.ModList.get().isLoaded("curios"), "Curios is still loaded in absence test");
            try {
                Class.forName("top.theillusivec4.curios.api.CuriosApi");
                throw new AssertionError("Curios API is still present in the runtime");
            } catch (ClassNotFoundException expected) {
                // The mod has really started with no Curios classes available.
            }
            check(ModItems.YOUNG_LION_GREATSWORD.get() instanceof SwordItem, "Greatsword requires Curios to work");
            check(ModItems.CONSECRATED_PROSTHETIC_BLADE.get() instanceof SwordItem, "Blade requires Curios to work");
            recipe(helper, player, "young_lion_greatsword", ModItems.GOD_AND_LORD_REMEMBRANCE.get(), Items.DIAMOND_SWORD,
                ModItems.YOUNG_LION_GREATSWORD.get());
            recipe(helper, player, "circlet_of_fading_light", ModItems.GOD_AND_LORD_REMEMBRANCE.get(), Items.GOLDEN_HELMET,
                ModItems.CIRCLET_OF_FADING_LIGHT.get());
            helper.succeed();
            return;
        }
        var inventory = CuriosApi.getCuriosInventory(player).resolve().orElseThrow();
        var charm = inventory.getCurios().get("charm").getStacks();
        var head = inventory.getCurios().get("head").getStacks();
        check(charm.getSlots() == 2 && head.getSlots() == 1, "Player slot data was not loaded");
        SlotContext first = new SlotContext("charm", player, 0, false, true);
        SlotContext second = new SlotContext("charm", player, 1, false, true);
        SlotContext headContext = new SlotContext("head", player, 0, false, true);
        Attribute rot = ModAttributes.SCARLET_ROT_CAPACITY.get();
        value(player, rot, 100);
        value(player, Attributes.ATTACK_DAMAGE, 1);
        value(player, Attributes.ATTACK_SPEED, 4);

        // Materials are not accessories. Neither slot can accept them.
        for (Item material : new Item[]{ModItems.ROT_GODDESS_REMEMBRANCE.get(),
                ModItems.GOD_AND_LORD_REMEMBRANCE.get(), ModItems.SCARLET_AEONIA_CORE.get(),
                ModItems.HALIGTREE_ROOT_FRAGMENT.get(), ModItems.GATE_FRAGMENT.get(), ModItems.RUNE_FRAGMENT.get()}) {
            ItemStack stack = new ItemStack(material);
            check(!CuriosApi.isStackValid(first, stack) && !CuriosApi.isStackValid(headContext, stack),
                "Material accepted as a curio: " + stack);
        }

        ItemStack needle = new ItemStack(ModItems.GOLDEN_NEEDLE.get(), 64);
        check(charm.isItemValid(0, needle), "Needle cannot enter charm slot");
        check(!head.isItemValid(0, needle), "Needle entered head slot");
        check(!CuriosApi.getCurio(needle).resolve().orElseThrow().canEquipFromUse(first),
            "Right-click equipping intercepted cleansing");
        charm.setStackInSlot(0, needle);
        tick(player);
        value(player, rot, 100 + settings.needleRotCapacity()); // Stack size cannot multiply the bonus.
        check(needle.getCount() == 64, "Wearing a needle consumed it");
        check(!charm.isItemValid(1, new ItemStack(ModItems.GOLDEN_NEEDLE.get())), "Duplicate needle accepted");
        check(charm.isItemValid(0, needle), "Existing slot rejected itself");
        charm.setStackInSlot(0, ItemStack.EMPTY);
        tick(player);
        value(player, rot, 100);

        ItemStack blade = new ItemStack(ModItems.CONSECRATED_PROSTHETIC_BLADE.get());
        ItemStack sword = new ItemStack(ModItems.YOUNG_LION_GREATSWORD.get());
        ItemStack crown = new ItemStack(ModItems.CIRCLET_OF_FADING_LIGHT.get());
        check(blade.getItem() instanceof SwordItem && sword.getItem() instanceof SwordItem,
            "Both weapon items must really be swords");
        check(sword.getMaxStackSize() == 1 && sword.getMaxDamage() == settings.greatsword().durability(), "Greatsword durability/stack size");
        check(charm.isItemValid(0, blade) && charm.isItemValid(1, sword) && head.isItemValid(0, crown),
            "Equipment slot tags do not match their roles");
        check(!charm.isItemValid(0, crown) && !head.isItemValid(0, sword), "Incorrect slot accepted equipment");
        charm.setStackInSlot(0, blade);
        charm.setStackInSlot(1, sword);
        head.setStackInSlot(0, crown);
        tick(player);
        value(player, Attributes.ATTACK_SPEED, 4 * (1 + settings.bladeAttackSpeed()));
        value(player, Attributes.ATTACK_DAMAGE, 1 + settings.greatswordAttackDamage());
        value(player, Attributes.KNOCKBACK_RESISTANCE, settings.greatswordKnockbackResistance());
        value(player, Attributes.ARMOR, settings.circletArmor());
        value(player, Attributes.LUCK, settings.circletLuck());
        check(!charm.isItemValid(1, blade) && !charm.isItemValid(0, sword), "Duplicate weapon accepted");
        // A weapon in Curios must not apply its main-hand damage/speed modifiers.
        check(sword.getDamageValue() == 0, "Wearing a sword consumed durability");
        SlotContext cosmetic = new SlotContext("charm", player, 0, true, true);
        check(CuriosApi.getAttributeModifiers(cosmetic, CuriosApi.getSlotUuid(cosmetic), sword).isEmpty(),
            "Cosmetic slot granted attributes");

        // Repeat event ticks, then save/reload the equipment and verify modifiers don't accumulate.
        for (int i = 0; i < 10; i++) tick(player);
        value(player, Attributes.ATTACK_DAMAGE, 1 + settings.greatswordAttackDamage());
        var saved = inventory.saveInventory(false);
        charm.setStackInSlot(0, ItemStack.EMPTY);
        charm.setStackInSlot(1, ItemStack.EMPTY);
        head.setStackInSlot(0, ItemStack.EMPTY);
        tick(player);
        value(player, Attributes.ATTACK_SPEED, 4);
        value(player, Attributes.ATTACK_DAMAGE, 1);
        value(player, Attributes.KNOCKBACK_RESISTANCE, 0);
        value(player, Attributes.ARMOR, 0);
        value(player, Attributes.LUCK, 0);
        inventory.loadInventory(saved);
        tick(player);
        value(player, Attributes.ATTACK_DAMAGE, 1 + settings.greatswordAttackDamage());
        value(player, Attributes.ATTACK_SPEED, 4 * (1 + settings.bladeAttackSpeed()));

        // Main-hand stats still work, independently of the optional Curios modifiers.
        var mainHand = sword.getAttributeModifiers(EquipmentSlot.MAINHAND);
        player.getAttributes().addTransientAttributeModifiers(mainHand);
        value(player, Attributes.ATTACK_DAMAGE, settings.greatsword().attackDamage() * (1 + settings.greatswordAttackDamage()));
        value(player, Attributes.ATTACK_SPEED, settings.greatsword().attackSpeed() * (1 + settings.bladeAttackSpeed()));
        player.getAttributes().removeAttributeModifiers(mainHand);
        value(player, Attributes.ATTACK_DAMAGE, 1 + settings.greatswordAttackDamage());

        recipe(helper, player, "consecrated_prosthetic_blade", ModItems.ROT_GODDESS_REMEMBRANCE.get(), Items.IRON_SWORD,
            ModItems.CONSECRATED_PROSTHETIC_BLADE.get());
        recipe(helper, player, "young_lion_greatsword", ModItems.GOD_AND_LORD_REMEMBRANCE.get(), Items.DIAMOND_SWORD,
            ModItems.YOUNG_LION_GREATSWORD.get());
        recipe(helper, player, "circlet_of_fading_light", ModItems.GOD_AND_LORD_REMEMBRANCE.get(), Items.GOLDEN_HELMET,
            ModItems.CIRCLET_OF_FADING_LIGHT.get());
        helper.succeed();
    }

    private static void recipe(GameTestHelper helper, Player player, String id, Item remembrance, Item base, Item output) {
        var grid = new TransientCraftingContainer(player.inventoryMenu, 2, 2);
        grid.setItem(0, new ItemStack(remembrance));
        grid.setItem(1, new ItemStack(base));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        check(recipe.getId().equals(PlatformResourceLocation.id(id)), "Ambiguous or missing remembrance recipe " + id);
        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        check(result.is(output) && result.getCount() == 1 && result.getDamageValue() == 0, "Invalid crafted item " + id);
    }

    private static void tick(Player player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static EquipmentSettings expectedSettings() {
        return switch (System.getProperty("elder_bosses.equipmentConfigCase", "default")) {
            case "custom" -> new EquipmentSettings(new EquipmentSettings.Weapon(11.75, 2.25, 5000, 30),
                new EquipmentSettings.Weapon(16.5, 1.35, 6000, 35), 70, 0.25, 0.35, 0.30, 5, 3);
            case "zero" -> new EquipmentSettings(new EquipmentSettings.Weapon(1, 0.01, 1, 0),
                new EquipmentSettings.Weapon(1, 0.01, 1, 0), 0, 0, 0, 0, 0, 0);
            default -> EquipmentSettings.DEFAULT;
        };
    }

    private static void weapon(Player player, ItemStack stack, EquipmentSettings.Weapon expected) {
        check(stack.getMaxDamage() == expected.durability(), "Configured durability ignored");
        check(stack.getEnchantmentValue() == expected.enchantability(), "Configured enchantability ignored");
        var modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        player.getAttributes().addTransientAttributeModifiers(modifiers);
        value(player, Attributes.ATTACK_DAMAGE, expected.attackDamage());
        value(player, Attributes.ATTACK_SPEED, expected.attackSpeed());
        check(stack.getAttributeModifiers(EquipmentSlot.OFFHAND).isEmpty(), "Weapon stats leaked into offhand");
        player.getAttributes().removeAttributeModifiers(modifiers);
        stack.setDamageValue(expected.durability() - 1);
        ItemStack restored = ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
        check(restored.getMaxDamage() == expected.durability() && restored.getDamageValue() == expected.durability() - 1,
            "Configured durability lost after saving an existing weapon");
    }

    private static void checkConfigSync(EquipmentSettings settings) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new EquipmentSettingsPacket(settings).write(buffer);
            check(EquipmentSettingsPacket.read(buffer).settings().equals(settings) && !buffer.isReadable(), "Config packet round-trip");
        } finally {
            buffer.release();
        }
        // An integrated server must ignore the remote client override, even in the same JVM.
        PlatformEquipmentConfig.receive(EquipmentSettings.DEFAULT);
        check(PlatformEquipmentConfig.current().equals(settings), "Client config changed server authority");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread client = new Thread(new ThreadGroup("equipment-client-check"), () -> {
            try {
                check(PlatformEquipmentConfig.current().equals(EquipmentSettings.DEFAULT), "Client did not use received config");
                PlatformEquipmentConfig.disconnect();
                check(PlatformEquipmentConfig.current().equals(settings), "Disconnect did not restore local equipment config");
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        client.start();
        try {
            client.join(5000);
        } catch (InterruptedException error) {
            throw new AssertionError(error);
        }
        check(!client.isAlive() && failure.get() == null, "Client snapshot lifecycle: " + failure.get());
    }

    private static void value(Player player, Attribute attribute, double expected) {
        double actual = player.getAttributeValue(attribute);
        check(Math.abs(actual - expected) < 0.00001, attribute.getDescriptionId() + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
