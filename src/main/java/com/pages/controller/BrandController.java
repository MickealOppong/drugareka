package com.pages.controller;

import com.pages.dto.BrandRequest;
import com.pages.dto.BrandResponse;
import com.pages.dto.ResponseDto;
import com.pages.service.BrandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final  BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @PostMapping("/new")
    public ResponseDto<Object> addBrand( @Valid  @RequestBody BrandRequest brand){
       return brandService.addBrand(brand);
    }
    @GetMapping("/all")
    public List<BrandResponse> all(){
        return brandService.allBrands();
    }

    @GetMapping("/{id}")
    public BrandResponse getBrand(Long id){
        return brandService.getBrandById(id);
    }
    @PutMapping("/edit")
    public ResponseDto<Object> editBrand(@RequestBody @Valid BrandResponse brandResponse){
        return brandService.editBrand(brandResponse);
    }

    @DeleteMapping("/delete")
    public ResponseDto<Object> delete(Long id){
        return brandService.deleteBrand(id);
    }

}
