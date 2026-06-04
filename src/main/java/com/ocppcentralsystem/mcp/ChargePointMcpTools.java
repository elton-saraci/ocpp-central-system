package com.ocppcentralsystem.mcp;

import com.ocppcentralsystem.service.ChargePointService;
import eu.chargetime.ocpp.model.core.ResetType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChargePointMcpTools {

    private final ChargePointService chargePointService;
    private final McpToolResponse response;

    @Tool(description = "List all connected or known OCPP charge points.")
    public String ocppListChargePoints() {
        return response.from(chargePointService::getAllChargePoints);
    }

    @Tool(description = "Send a hard reset request to a charge point. This is a high-risk remote operation and requires explicit user confirmation.")
    public String ocppHardResetChargePoint(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId
    ) {
        return response.successFrom(() ->
                chargePointService.sendResetRequestToChargePoint(ResetType.Hard, cpId)
        );
    }

    @Tool(description = "Send a soft reset request to a charge point. This is a high-risk remote operation and requires explicit user confirmation.")
    public String ocppSoftResetChargePoint(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId
    ) {
        return response.successFrom(() ->
                chargePointService.sendResetRequestToChargePoint(ResetType.Soft, cpId)
        );
    }

    @Tool(description = "Unlock a connector on a charge point. This is a high-risk remote operation and requires explicit user confirmation.")
    public String ocppUnlockConnector(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId,
            @ToolParam(description = "The connector number on the charge point, e.g. 1") int connectorId
    ) {
        return response.successFrom(() ->
                chargePointService.sendConnectorUnlockToChargePoint(cpId, connectorId)
        );
    }

    @Tool(description = "Ask a charge point to send a StatusNotification for a connector.")
    public String ocppTriggerStatusNotification(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId,
            @ToolParam(description = "The connector number on the charge point, e.g. 1") int connectorId
    ) {
        return triggerMessage(cpId, connectorId, TriggerMessageRequestType.StatusNotification);
    }

    @Tool(description = "Ask a charge point to send a BootNotification.")
    public String ocppTriggerBootNotification(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId,
            @ToolParam(description = "The connector number on the charge point, e.g. 1") int connectorId
    ) {
        return triggerMessage(cpId, connectorId, TriggerMessageRequestType.BootNotification);
    }

    @Tool(description = "Ask a charge point to send a Heartbeat.")
    public String ocppTriggerHeartbeat(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId,
            @ToolParam(description = "The connector number on the charge point, e.g. 1") int connectorId
    ) {
        return triggerMessage(cpId, connectorId, TriggerMessageRequestType.Heartbeat);
    }

    @Tool(description = "Ask a charge point to send MeterValues for a connector.")
    public String ocppTriggerMeterValues(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId,
            @ToolParam(description = "The connector number on the charge point, e.g. 1") int connectorId
    ) {
        return triggerMessage(cpId, connectorId, TriggerMessageRequestType.MeterValues);
    }

    @Tool(description = "Change a configuration key on a charge point. This is a high-risk remote operation and requires explicit user confirmation.")
    public String ocppChangeConfiguration(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId,
            @ToolParam(description = "The OCPP configuration key to change") String key,
            @ToolParam(description = "The new value for the OCPP configuration key") String value
    ) {
        return response.successFrom(() ->
                chargePointService.sendChangeConfigurationRequestToChargePoint(cpId, key, value)
        );
    }

    private String triggerMessage(
            String cpId,
            int connectorId,
            TriggerMessageRequestType requestType
    ) {
        return response.successFrom(() ->
                chargePointService.sendTriggerMessageRequestToChargePoint(
                        cpId,
                        connectorId,
                        requestType
                )
        );
    }
}