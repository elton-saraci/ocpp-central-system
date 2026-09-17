package com.ocppcentralsystem.mapper;

import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.ChargeTransactionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChargeTransactionMapper {

    @Mapping(target = "cpId", expression = "java(chargeTransaction.getChargePoint() != null ? chargeTransaction.getChargePoint().getCpId() : null)")
    @Mapping(target = "idTag", expression = "java(chargeTransaction.getIdTag())")
    ChargeTransactionDTO toDto(ChargeTransaction chargeTransaction);

    List<ChargeTransactionDTO> toDtoList(List<ChargeTransaction> transactions);
}
