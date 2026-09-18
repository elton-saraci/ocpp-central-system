package com.ocppcentralsystem.model;

import lombok.Getter;

/**
 * How a connector delivers power.
 *
 * <p>This is the piece smart charging needs: charge points expect a limit in amperes, so the
 * number of phases is what turns a requested wattage into a current. {@code DC} carries a single
 * voltage, so it counts as one phase.</p>
 */
@Getter
public enum PowerType {

    AC_1_PHASE(1),
    AC_3_PHASE(3),
    DC(1);

    private final int phases;

    PowerType(int phases) {
        this.phases = phases;
    }
}
