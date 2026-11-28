package net.createplant.registry;

/** Puts the logistics addon's items into the base creative tab, where they stood when the tab listed them itself. */
public final class LogisticsCreativeTabs {
    private LogisticsCreativeTabs() { }

    public static void register() {
        CECreativeModeTabs.addGroupAfter("low_pressure_turbine_end", "anfo", "blasting_charge");
        CECreativeModeTabs.insertBefore("facility_constructor", "quarry_drive", "industrial_dispatch_board");
        CECreativeModeTabs.insertAfter("vault_hatch", "industrial_arm", "welding_torch");
    }
}
