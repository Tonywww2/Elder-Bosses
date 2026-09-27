package com.tonywww.elder_bosses.item;

import com.tonywww.elder_bosses.platforms.item.PlatformConfiguredSwordItem;
import com.tonywww.elder_bosses.platforms.config.PlatformEquipmentConfig;

public final class ConsecratedProstheticBladeItem extends PlatformConfiguredSwordItem {
    public ConsecratedProstheticBladeItem() {
        super(EquipmentSettings.DEFAULT.blade(), () -> PlatformEquipmentConfig.current().blade());
    }
}
