package com.tonywww.elder_bosses.network;

import com.tonywww.elder_bosses.item.EquipmentSettings;
import net.minecraft.network.FriendlyByteBuf;

/** Server-to-client only; independent of whether Curios is installed. */
public record EquipmentSettingsPacket(EquipmentSettings settings) {
    public EquipmentSettingsPacket {
        java.util.Objects.requireNonNull(settings);
    }

    public void write(FriendlyByteBuf buffer) {
        writeWeapon(buffer, settings.blade());
        writeWeapon(buffer, settings.greatsword());
        buffer.writeDouble(settings.needleRotCapacity());
        buffer.writeDouble(settings.bladeAttackSpeed());
        buffer.writeDouble(settings.greatswordAttackDamage());
        buffer.writeDouble(settings.greatswordKnockbackResistance());
        buffer.writeDouble(settings.circletArmor());
        buffer.writeDouble(settings.circletLuck());
    }

    public static EquipmentSettingsPacket read(FriendlyByteBuf buffer) {
        return new EquipmentSettingsPacket(new EquipmentSettings(readWeapon(buffer), readWeapon(buffer),
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
            buffer.readDouble(), buffer.readDouble()));
    }

    private static void writeWeapon(FriendlyByteBuf buffer, EquipmentSettings.Weapon values) {
        buffer.writeDouble(values.attackDamage());
        buffer.writeDouble(values.attackSpeed());
        buffer.writeVarInt(values.durability());
        buffer.writeVarInt(values.enchantability());
    }

    private static EquipmentSettings.Weapon readWeapon(FriendlyByteBuf buffer) {
        return new EquipmentSettings.Weapon(buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt());
    }
}
