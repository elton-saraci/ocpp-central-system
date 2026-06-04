package com.ocppcentralsystem.mcp;

import com.ocppcentralsystem.model.ChargeTransactionRequest;
import com.ocppcentralsystem.service.ChargeTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChargeTransactionMcpTools {

    private final ChargeTransactionService chargeTransactionService;
    private final McpToolResponse response;

    @Tool(description = "List all charging transactions known by the central system.")
    public String ocppListChargeTransactions() {
        return response.from(chargeTransactionService::findAllChargingTransactions);
    }

    @Tool(description = "Get a charging transaction by its transaction ID.")
    public String ocppGetChargeTransaction(
            @ToolParam(description = "The transaction ID") int transactionId
    ) {
        return response.from(() ->
                chargeTransactionService.findChargeTransactionById(transactionId)
        );
    }

    @Tool(description = "Start a charging transaction for a charge point connector. This is a high-risk remote operation and requires explicit user confirmation.")
    public String ocppStartChargeTransaction(
            @ToolParam(description = "The charge point ID, e.g. 'TEST_CP_ID'") String cpId,
            @ToolParam(description = "The connector number on the charge point, e.g. 1") int connectorId,
            @ToolParam(description = "The RFID authorization tag, e.g. 'tag1'") String idTag
    ) {
        return response.from(() -> {
            ChargeTransactionRequest request = new ChargeTransactionRequest(cpId, connectorId, idTag);
            return chargeTransactionService.startChargeTransaction(request);
        });
    }

    @Tool(description = "Stop an active charging transaction. This is a high-risk remote operation and requires explicit user confirmation.")
    public String ocppStopChargeTransaction(
            @ToolParam(description = "The transaction ID to stop") int transactionId
    ) {
        return response.successFrom(() ->
                chargeTransactionService.stopChargeTransaction(transactionId)
        );
    }
}