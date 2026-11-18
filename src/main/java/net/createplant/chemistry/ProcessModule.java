package net.createplant.chemistry;

/** One reusable mechanical operation, rather than one block per chemical process. */
public enum ProcessModule {
    PROCESS_HEATER, REACTION_VESSEL,
    FRACTION_SEPARATOR, GAS_LIQUID_SEPARATOR, CATALYST_UNIT, GAS_CENTRIFUGE, STIRRED_REACTOR, ABSORPTION_TRAYS;
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
