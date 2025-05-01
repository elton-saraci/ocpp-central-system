package com.ocppcentralsystem.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Data
public class ApplicationConfiguration {

    //we will be able to initiate the charging process only from these tags
    @Value("${tags}")
    private List<String> whitelistedIdTags;

    @Value("${websocket.port}")
    private int websocketPort;

}
