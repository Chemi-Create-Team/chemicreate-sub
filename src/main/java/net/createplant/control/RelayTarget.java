package net.createplant.control;

import net.minecraft.core.BlockPos;

/** A machine the relay computer can hold at a set output: the computer finds the nearest formed target and calls it once per pass. */
public interface RelayTarget {
    /** One control pass on the computer's ALU, to hold the target at the given percentage; returns the computer's status key. */
    String step(RelayAlu alu, BlockPos computer, int targetPercent);

    /** Renews the hold the given computer has on this target. */
    void renewLease(BlockPos computer);

    /** Hands control back to the operator. */
    void release(BlockPos computer);
}
