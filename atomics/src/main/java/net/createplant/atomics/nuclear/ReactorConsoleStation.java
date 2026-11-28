package net.createplant.atomics.nuclear;

/** The master unit of a reactor desk or display board, told when its units form, change or break. */
public interface ReactorConsoleStation {
    boolean assembled();
    void stationChanged();
    void invalidateStation();
}
