package com.pages.controller;

import com.pages.dto.ConditionRequest;
import com.pages.dto.ConditionResponse;
import com.pages.dto.ResponseDto;
import com.pages.service.ProductConditionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/conditions")
public class ConditionController {

    private final ProductConditionService productConditionService;

    public ConditionController(ProductConditionService productConditionService) {
        this.productConditionService = productConditionService;
    }


    @PostMapping("/new")
    public ResponseDto<Object> add(@RequestBody @Valid ConditionRequest conditionDto){
      return productConditionService.addCondition(conditionDto);
    }

    @DeleteMapping("/delete")
    public ResponseDto<Object> deleteCondition(Long id){
        return productConditionService.deleteCondition(id);
    }

    @PutMapping("/edit")
    public ResponseDto<Boolean> editCondition(@AuthenticationPrincipal Jwt jwt, ConditionRequest dto){
        log.info("{}",dto);
        return productConditionService.editCondition(jwt,dto);
    }


    @GetMapping("/all")
    public List<ConditionResponse> all(){
        return productConditionService.AllProductConditions();
    }

    @GetMapping("/{id}")
    public ResponseDto<ConditionResponse> condition(@AuthenticationPrincipal Jwt jwt, Long id){
        return productConditionService.productCondition(jwt,id);
    }
}

