package com.ocppcentralsystem.config;

import com.ocppcentralsystem.mcp.ChargePointMcpTools;
import com.ocppcentralsystem.mcp.ChargeTransactionMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider ocppToolCallbackProvider(
            ChargePointMcpTools chargePointMcpTools,
            ChargeTransactionMcpTools chargeTransactionMcpTools
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(chargePointMcpTools, chargeTransactionMcpTools)
                .build();
    }
}