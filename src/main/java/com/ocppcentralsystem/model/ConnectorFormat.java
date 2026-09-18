package com.ocppcentralsystem.model;

/**
 * Whether the connector has its own cable or expects the driver to bring one.
 */
public enum ConnectorFormat {

    /** You bring your own cable. */
    SOCKET,
    /** The cable is attached to the station. */
    CABLE
}
