package com.pages.controller;

import com.pages.dto.*;
import com.pages.service.SellerPayoutService;
import com.pages.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    public ListPageShipment myShipment(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "1")Integer page,
                                       @RequestParam(defaultValue = "10") Integer size,String type){
       if(type.equals("ACTUAL")){
           return shipmentService.actualShipments(jwt,page,size);
       }else{
           return shipmentService.returnShipments(jwt,page,size);
       }
    }

    @PutMapping("/shipment-status")
    public ResponseDto<Boolean> updateStatus(@AuthenticationPrincipal Jwt jwt,ShipmentRequest request) {
            return shipmentService.updateShippingStatus(jwt,request);
    }

    @PutMapping("/return-status")
    public ResponseDto<Boolean> updateReturnStatus(@AuthenticationPrincipal Jwt jwt,ShipmentRequest request) {
        return shipmentService.updateReturnShippingStatus(jwt,request);
    }


}
