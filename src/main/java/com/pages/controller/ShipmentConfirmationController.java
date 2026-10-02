package com.pages.controller;

import com.pages.dto.AddressForm;
import com.pages.dto.ShipmentConfirmationRequest;
import com.pages.dto.ShipmentItemResponse;
import com.pages.model.SellerShipmentToken;
import com.pages.service.SellerShipmentTokenService;
import com.pages.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.io.DataInput;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/seller/shipment")
public class ShipmentConfirmationController {

    private final SellerShipmentTokenService sellerShipmentTokenService;
    private final ShipmentService shipmentService;

    public ShipmentConfirmationController(SellerShipmentTokenService sellerShipmentTokenService, ShipmentService shipmentService) {
        this.sellerShipmentTokenService = sellerShipmentTokenService;
        this.shipmentService = shipmentService;
    }

    @PostMapping(value = "/ship",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void executeShipment(ShipmentConfirmationRequest request){
        log.info("Data {}",request);

        SellerShipmentToken validatedToken= sellerShipmentTokenService.validateToken(request.getToken());
        //shipmentService.createInPostShipment(request);
        shipmentService.createDpdShipment(request);
    }

    @GetMapping("/token")
    public List<ShipmentItemResponse> getShipmentItems(String token){
        log.info("token: {}",token);
        SellerShipmentToken validatedToken= sellerShipmentTokenService.validateToken(token);
        return shipmentService.getShipment(validatedToken);
    }
}
