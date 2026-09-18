package com.ocppcentralsystem.model;

/**
 * The plug standard a connector offers.
 *
 * <p>OCPP 1.6 does not model this, so it is descriptive only - it is not used by any protocol
 * exchange. The names follow the OCPI vocabulary, which keeps the door open to publish stations to
 * an OCPI hub later. OCPI defines more standards than the common European ones listed here; add
 * them as they are needed rather than guessing.</p>
 */
public enum ConnectorType {

    IEC_62196_T2,
    IEC_62196_T2_COMBO,
    IEC_62196_T1,
    IEC_62196_T1_COMBO,
    CHADEMO,
    TESLA_S,
    DOMESTIC_F,
    DOMESTIC_E,
    OTHER
}
