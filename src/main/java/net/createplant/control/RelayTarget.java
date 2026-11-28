package net.createplant.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A machine the relay computer can hold at a set output. Each computer owns its own instances (see {@link RelayTargets}), so an
 * instance may keep that computer's control state.
 */
public interface RelayTarget {
    /** Looks for the nearest formed target within range of the computer; true when it is the one to control. */
    boolean acquire(Level level, BlockPos computer, int range);

    /** One control pass on the computer's ALU, to hold the acquired target at the given percentage; returns the computer's status key. */
    String step(RelayAlu alu, BlockPos computer, int targetPercent);

    /** Renews the hold the given computer has on its target. */
    void renewLease(Level level, BlockPos computer, int range);

    /** Hands control back to the operator. */
    void release(Level level, BlockPos computer, int range);
}
