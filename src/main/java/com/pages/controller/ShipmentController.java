package com.pages.controller;

import com.pages.dto.ListPageShipment;
import com.pages.dto.ResponseDto;
import com.pages.dto.ShipmentRequest;
import com.pages.dto.ShipmentResponse;
import com.pages.service.SellerPayoutService;
import com.pages.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequestMapping("/api/shipment")
@RestController
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }


    @GetMapping("/shipments")
    public ListPageShipment myShipment(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "1")Integer page, @RequestParam(defaultValue = "10") Integer size){
        return shipmentService.shipments(jwt,page,size);
    }

    @PutMapping("/update-status")
    public ResponseDto<Boolean> updateStatus(@AuthenticationPrincipal Jwt jwt,ShipmentRequest request) {
            return shipmentService.updateShippingStatus(jwt,request);
    }

}
