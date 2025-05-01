package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargeTransactionDTO;
import com.ocppcentralsystem.model.ChargeTransactionRequest;
import com.ocppcentralsystem.service.ChargeTransactionService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/charge-transaction")
@AllArgsConstructor
@Validated
public class ChargeTransactionController {

    private ChargeTransactionService chargeTransactionService;

    @GetMapping("/{transactionId}")
    public ResponseEntity<ChargeTransactionDTO> fetchTransactionById(@PathVariable int transactionId) {
        log.info("Fetching transaction with id -> {}", transactionId);
        return ResponseEntity.ok(chargeTransactionService.findChargeTransactionById(transactionId));
    }

    @GetMapping
    public ResponseEntity<List<ChargeTransactionDTO>> fetchAllTransactions() {
        return ResponseEntity.ok(chargeTransactionService.findAllChargingTransactions());
    }

    @PostMapping("/start")
    public ResponseEntity<ChargeTransactionDTO> startChargeTransaction(@RequestBody @Valid ChargeTransactionRequest chargeTransactionRequest) {
        log.info("ChargeTransaction Request: -> {}", chargeTransactionRequest);
        return ResponseEntity.ok(chargeTransactionService.startChargeTransaction(chargeTransactionRequest));
    }

    @PostMapping("/stop/{transactionId}")
    public ResponseEntity<Boolean> stopChargeTransaction(@PathVariable int transactionId) {
        return ResponseEntity.ok(chargeTransactionService.stopChargeTransaction(transactionId));
    }

}
