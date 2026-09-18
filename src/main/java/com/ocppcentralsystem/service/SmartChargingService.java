package com.ocppcentralsystem.service;

import com.ocppcentralsystem.config.ApplicationConfiguration;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargingProfileResultDTO;
import com.ocppcentralsystem.model.ChargingScheduleDTO;
import com.ocppcentralsystem.model.ChargingSchedulePeriodDTO;
import eu.chargetime.ocpp.model.core.ChargingProfile;
import eu.chargetime.ocpp.model.core.ChargingProfileKindType;
import eu.chargetime.ocpp.model.core.ChargingProfilePurposeType;
import eu.chargetime.ocpp.model.core.ChargingRateUnitType;
import eu.chargetime.ocpp.model.core.ChargingSchedule;
import eu.chargetime.ocpp.model.core.ChargingSchedulePeriod;
import eu.chargetime.ocpp.model.smartcharging.ChargingProfileStatus;
import eu.chargetime.ocpp.model.smartcharging.ClearChargingProfileConfirmation;
import eu.chargetime.ocpp.model.smartcharging.ClearChargingProfileRequest;
import eu.chargetime.ocpp.model.smartcharging.ClearChargingProfileStatus;
import eu.chargetime.ocpp.model.smartcharging.GetCompositeScheduleConfirmation;
import eu.chargetime.ocpp.model.smartcharging.GetCompositeScheduleRequest;
import eu.chargetime.ocpp.model.smartcharging.SetChargingProfileConfirmation;
import eu.chargetime.ocpp.model.smartcharging.SetChargingProfileRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Sets, clears and reads back the charging power limit of a charge point.
 *
 * <p>Callers work in watts and know nothing else: the OCPP charging profile (purpose, kind, stack
 * level, rate unit and schedule) is built here. A limit applies to a connector, where connector
 * {@code 0} means the whole charge point - which is what a caller gets when it only supplies a
 * cpId.</p>
 */
@Service
@AllArgsConstructor
@Slf4j
public class SmartChargingService {

    /** connectorId 0 addresses the charge point itself rather than one connector. */
    private static final int WHOLE_CHARGE_POINT = 0;
    private static final int START_PERIOD_SECONDS = 0;
    private static final int SECONDS_PER_MINUTE = 60;
    /** How far ahead a read-back looks, unless the caller asks for a shorter window. */
    private static final int DEFAULT_READ_WINDOW_MINUTES = 15;
    /**
     * Fixed, so that setting a power limit twice replaces the previous one instead of stacking a
     * second profile on top of it. ClearChargingProfile targets the same id.
     */
    private static final int PROFILE_ID = 1;

    private final ChargePointService chargePointService;
    private final ChargePointCommunicator chargePointCommunicator;
    private final ApplicationConfiguration applicationConfiguration;

    /**
     * @param connectorId     null means the whole charge point
     * @param durationMinutes how long the limit stays in effect; null means until it is cleared
     */
    public ChargingProfileResultDTO setPowerLimit(String tenant, String cpId, Integer connectorId, int powerW, Integer durationMinutes) {
        ChargePoint chargePoint = chargePointService.requireChargePoint(tenant, cpId);
        int target = connectorId == null ? WHOLE_CHARGE_POINT : connectorId;
        double limitAmps = toAmperes(powerW);

        ChargingProfile profile = getChargingProfile(durationMinutes, limitAmps);

        SetChargingProfileConfirmation confirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(),
                new SetChargingProfileRequest(target, profile),
                SetChargingProfileConfirmation.class);

        log.info("SetChargingProfile for cpId {} connector {} ({} W = {} A) -> {}",
                cpId, target, powerW, limitAmps, confirmation.getStatus());

        ChargingProfileResultDTO result = new ChargingProfileResultDTO();
        result.setCpId(cpId);
        result.setConnectorId(target);
        result.setRequestedPowerW(powerW);
        result.setLimitAmps(limitAmps);
        result.setStatus(confirmation.getStatus().name());
        result.setApplied(ChargingProfileStatus.Accepted == confirmation.getStatus());
        return result;
    }

    private @NonNull ChargingProfile getChargingProfile(Integer durationMinutes, double limitAmps) {
        ChargingSchedulePeriod period = new ChargingSchedulePeriod(START_PERIOD_SECONDS, limitAmps);
        ChargingSchedule schedule = new ChargingSchedule(ChargingRateUnitType.A, new ChargingSchedulePeriod[]{period});
        if (durationMinutes != null && durationMinutes > 0) {
            // OCPP expresses the schedule duration in seconds.
            schedule.setDuration(durationMinutes * SECONDS_PER_MINUTE);
        }

        return new ChargingProfile(PROFILE_ID,
                applicationConfiguration.getSmartChargingStackLevel(),
                // Applies to the connector for every transaction, not just a running one, so no
                // transaction id is needed and the limit survives a transaction ending.
                ChargingProfilePurposeType.TxDefaultProfile,
                ChargingProfileKindType.Absolute,
                schedule);
    }

    public ChargingProfileResultDTO clearPowerLimit(String tenant, String cpId, Integer connectorId) {
        ChargePoint chargePoint = chargePointService.requireChargePoint(tenant, cpId);
        int target = connectorId == null ? WHOLE_CHARGE_POINT : connectorId;

        ClearChargingProfileRequest request = new ClearChargingProfileRequest();
        request.setId(PROFILE_ID);
        request.setConnectorId(target);
        request.setChargingProfilePurpose(ChargingProfilePurposeType.TxDefaultProfile);
        request.setStackLevel(applicationConfiguration.getSmartChargingStackLevel());

        ClearChargingProfileConfirmation confirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(), request, ClearChargingProfileConfirmation.class);

        log.info("ClearChargingProfile for cpId {} connector {} -> {}", cpId, target, confirmation.getStatus());

        ChargingProfileResultDTO result = new ChargingProfileResultDTO();
        result.setCpId(cpId);
        result.setConnectorId(target);
        result.setStatus(confirmation.getStatus().name());
        result.setApplied(ClearChargingProfileStatus.Accepted == confirmation.getStatus());
        return result;
    }

    /** Reads back the limit the charge point currently has in effect. */
    public ChargingScheduleDTO getPowerLimit(String tenant, String cpId, Integer connectorId, Integer durationMinutes) {
        ChargePoint chargePoint = chargePointService.requireChargePoint(tenant, cpId);
        int target = connectorId == null ? WHOLE_CHARGE_POINT : connectorId;
        int windowSeconds = durationMinutes != null && durationMinutes > 0
                ? durationMinutes * SECONDS_PER_MINUTE
                : DEFAULT_READ_WINDOW_MINUTES * SECONDS_PER_MINUTE;

        GetCompositeScheduleConfirmation confirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(),
                new GetCompositeScheduleRequest(target, windowSeconds),
                GetCompositeScheduleConfirmation.class);

        ChargingScheduleDTO result = new ChargingScheduleDTO();
        result.setCpId(cpId);
        result.setConnectorId(target);
        result.setStatus(confirmation.getStatus().name());

        ChargingSchedule schedule = confirmation.getChargingSchedule();
        if (schedule != null) {
            ChargingRateUnitType unit = schedule.getChargingRateUnit();
            result.setUnit(unit == null ? null : unit.name());
            result.setDurationSeconds(schedule.getDuration());
            result.setPeriods(toPeriods(schedule, unit));
        }
        return result;
    }

    private List<ChargingSchedulePeriodDTO> toPeriods(ChargingSchedule schedule, ChargingRateUnitType unit) {
        ChargingSchedulePeriod[] periods = schedule.getChargingSchedulePeriod();
        if (periods == null) {
            return List.of();
        }
        return Arrays.stream(periods).map(period -> {
            ChargingSchedulePeriodDTO dto = new ChargingSchedulePeriodDTO();
            dto.setStartPeriodSeconds(period.getStartPeriod());
            dto.setLimit(period.getLimit());
            dto.setPowerW(toWatts(period.getLimit(), unit));
            return dto;
        }).toList();
    }

    /**
     * Charge points expect the limit in amperes, where a three phase charger draws
     * {@code phases x voltage x I} - so 11 kW is 15.9 A on three phases of 230 V.
     */
    private double toAmperes(int powerW) {
        int phases = applicationConfiguration.getSmartChargingPhases();
        int voltage = applicationConfiguration.getSmartChargingVoltage();
        return roundToOneDecimal(powerW / (double) (phases * voltage));
    }

    private Double toWatts(Double limit, ChargingRateUnitType unit) {
        if (limit == null || unit == null) {
            return null;
        }
        if (ChargingRateUnitType.W == unit) {
            return limit;
        }
        int phases = applicationConfiguration.getSmartChargingPhases();
        int voltage = applicationConfiguration.getSmartChargingVoltage();
        return roundToOneDecimal(limit * phases * voltage);
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
