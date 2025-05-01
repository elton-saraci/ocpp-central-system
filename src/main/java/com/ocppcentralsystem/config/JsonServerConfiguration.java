package com.ocppcentralsystem.config;

import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.feature.profile.ServerCoreProfile;
import eu.chargetime.ocpp.feature.profile.ServerRemoteTriggerProfile;
import eu.chargetime.ocpp.feature.profile.ServerSmartChargingProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonServerConfiguration {

    /*
    Setting up the required server profiles: Smart Charging and Remote Trigger
     */
    @Bean
    public JSONServer jsonServer(ServerCoreProfile core) {
        JSONServer jsonServer = new JSONServer(core);
        ServerSmartChargingProfile serverSmartChargingProfile = new ServerSmartChargingProfile();
        ServerRemoteTriggerProfile serverRemoteTriggerProfile = new ServerRemoteTriggerProfile();
        jsonServer.addFeatureProfile(serverSmartChargingProfile);
        jsonServer.addFeatureProfile(serverRemoteTriggerProfile);
        return jsonServer;
    }

}
