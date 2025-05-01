package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.service.ChargePointService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/charge-point")
@AllArgsConstructor
public class ChargePointController {

    private final ChargePointService chargePointService;

    @GetMapping
    public ResponseEntity<List<ChargePoint>> findAllChargePoints() {
        return ResponseEntity.ok(chargePointService.getAllChargePoints());
    }

}
