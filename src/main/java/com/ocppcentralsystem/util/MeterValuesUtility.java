package com.ocppcentralsystem.util;

import eu.chargetime.ocpp.model.core.MeterValue;
import eu.chargetime.ocpp.model.core.MeterValuesRequest;
import eu.chargetime.ocpp.model.core.SampledValue;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class MeterValuesUtility {

    public static final String ENERGY_KEYWORD = "Energy.Active.Import.Register";
    public static final String POWER_KEYWORD = "Power.Active.Import";
    public static final String KWH_UNIT = "kWh";

    public static Integer fetchEnergyValue(MeterValuesRequest meterValuesRequest) {
        return findEnergySampledValue(meterValuesRequest)
                .map(SampledValue::getValue)
                .flatMap(MeterValuesUtility::parseFloatSafely)
                .map(value -> isInKWhUnit(meterValuesRequest) ? value * 1000f : value)
                .map(Math::round)
                .orElse(null);
    }

    public static Integer fetchPowerValue(MeterValuesRequest meterValuesRequest) {
        return findPowerSampledValue(meterValuesRequest)
                .map(SampledValue::getValue)
                .flatMap(MeterValuesUtility::parseFloatSafely)
                .map(value -> isInKWhUnit(meterValuesRequest) ? value * 1000f : value)
                .map(Math::round)
                .orElse(null);
    }

    private static Optional<SampledValue> findEnergySampledValue(MeterValuesRequest request) {
        for (MeterValue meterValue : request.getMeterValue()) {
            for (SampledValue sampledValue : meterValue.getSampledValue()) {
                if (ENERGY_KEYWORD.equals(sampledValue.getMeasurand())) {
                    return Optional.of(sampledValue);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<SampledValue> findPowerSampledValue(MeterValuesRequest request) {
        for (MeterValue meterValue : request.getMeterValue()) {
            for (SampledValue sampledValue : meterValue.getSampledValue()) {
                if (POWER_KEYWORD.equals(sampledValue.getMeasurand())) {
                    return Optional.of(sampledValue);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isInKWhUnit(MeterValuesRequest request) {
        return findEnergySampledValue(request)
                .map(SampledValue::getUnit)
                .map(unit -> unit.equalsIgnoreCase(KWH_UNIT))
                .orElse(false);
    }

    private static Optional<Float> parseFloatSafely(String value) {
        try {
            return Optional.of(Float.parseFloat(value));
        } catch (NumberFormatException ex) {
            log.warn("Could not parse float from value: {}", value);
            return Optional.empty();
        }
    }

}
