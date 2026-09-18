package com.ocppcentralsystem.mapper;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargePointDTO;
import com.ocppcentralsystem.model.Connector;
import com.ocppcentralsystem.model.ConnectorDTO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChargePointMapper {

    ChargePointDTO toDto(ChargePoint chargePoint);

    List<ChargePointDTO> toDtoList(List<ChargePoint> chargePoints);

    ConnectorDTO toDto(Connector connector);
}
