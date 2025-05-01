package com.ocppcentralsystem.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

@Converter
public class ConnectorStatusMapConverter implements AttributeConverter<Map<Integer, ChargePointStatus>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<Integer, ChargePointStatus> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert connector map to JSON", e);
        }
    }

    @Override
    public Map<Integer, ChargePointStatus> convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert JSON to connector map", e);
        }
    }
}

