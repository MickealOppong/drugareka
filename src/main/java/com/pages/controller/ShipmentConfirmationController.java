package com.pages.controller;

import com.pages.dto.*;
import com.pages.model.SellerShipment;
import com.pages.model.SellerShipmentToken;
import com.pages.service.OrderReturnService;
import com.pages.service.SellerShipmentTokenService;
import com.pages.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.io.DataInput;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/seller/shipment")
public class ShipmentConfirmationController {


    private final ShipmentService shipmentService;


    public ShipmentConfirmationController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping(value = "/ship",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Boolean> executeShipment(ShipmentConfirmationRequest request){
        return ResponseEntity.ok(shipmentService.updateAndOrderCourierConfirmation(request));
    }

    @GetMapping("/token")
    public ResponseDto<Object> getShipmentItems(String token){
        return shipmentService.getShipment(token);
    }
}
