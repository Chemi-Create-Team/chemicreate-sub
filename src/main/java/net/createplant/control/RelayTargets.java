package net.createplant.control;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/** The relay target kinds addons register, in the order a computer tries them. */
public final class RelayTargets {
    private static final List<Supplier<RelayTarget>> KINDS = new CopyOnWriteArrayList<>();

    private RelayTargets() { }

    public static void register(Supplier<RelayTarget> kind) { KINDS.add(kind); }

    /** A fresh set of targets for one computer. */
    public static List<RelayTarget> create() { return KINDS.stream().map(Supplier::get).toList(); }
}
