package com.tonywww.elder_bosses.item;

import com.tonywww.elder_bosses.platforms.item.PlatformConfiguredSwordItem;
import com.tonywww.elder_bosses.platforms.config.PlatformEquipmentConfig;

/** Heavy, slow melee weapon; its Curios bonuses are registered separately. */
public final class YoungLionGreatswordItem extends PlatformConfiguredSwordItem {
    public YoungLionGreatswordItem() {
        super(EquipmentSettings.DEFAULT.greatsword(), () -> PlatformEquipmentConfig.current().greatsword());
    }
}
