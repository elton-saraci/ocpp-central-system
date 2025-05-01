package com.ocppcentralsystem.service;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.repository.ChargePointRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ChargePointService {

    private final ChargePointRepository chargePointRepository;

    public List<ChargePoint> getAllChargePoints() {
        return chargePointRepository.findAll();
    }

}
