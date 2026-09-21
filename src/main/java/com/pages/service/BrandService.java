package com.pages.service;

import com.pages.dto.BrandRequest;
import com.pages.dto.BrandResponse;
import com.pages.dto.ListPageBrand;
import com.pages.dto.ResponseDto;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.Brand;
import com.pages.repository.BrandRepo;
import com.pages.util.UtilService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.pages.util.UtilService.formatNameToSlug;

@Slf4j
@Transactional
@Service
public class BrandService {

    private final BrandRepo brandRepo;
    private UtilService utilService;

    public BrandService(BrandRepo brandRepo) {
        this.brandRepo = brandRepo;
    }

    public BrandResponse getBrandById(Long id){
        Brand brand = brandRepo.getReferenceById(id);
        return BrandResponse.builder()
                .name(brand.getName())
                .slug(brand.getSlug())
                .id(brand.getId())
                .active(brand.isActive())
                .sortOrder(brand.getSortOrder())
                .build();
    }

    public Brand getBrandByName(String brandName){
        return brandRepo.findByName(brandName).orElseThrow(()->new EntityNotFoundException(brandName+" does not exist"));
    }


    public ResponseDto<Object> addBrand(BrandRequest brand){
       try{
           if(brandRepo.existsByName(brand.getName())){
               throw new InvalidOperationException("Brand already exists");
           }
           Brand newBrand = new Brand(brand.getName(),formatNameToSlug(brand.getSlug())
                   , brand.isActive(), brand.getSortOrder());
           brandRepo.save(newBrand);
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.OK.value())
                   .data(true)
                   .message("Created")
                   .build();
       }catch (Exception e){
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.BAD_REQUEST.value())
                   .data(false)
                   .message(e.getMessage())
                   .build();
       }
    }


    public Brand findByNameOrCreate(String name){
        return brandRepo.findByName(name).orElseGet(()->{
            Brand newBrand = Brand.builder()
                    .name(name)
                    .active(true)
                    .sortOrder(1)
                    .slug(UtilService.formatNameToSlug(name))
                    .build();
            return brandRepo.save(newBrand);
        });
    }
    public ListPageBrand allBrands(Jwt jwt, int page , int size){
        if(jwt==null){
                ListPageBrand.builder().build();
        }

        /*
         * =====================================================
         * PAGINATION
         * =====================================================
         */
        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

      Page<BrandResponse> brands= brandRepo.findAll(pageable).map(brand->{
          return BrandResponse.builder()
                 .id(brand.getId())
                 .name(brand.getName())
                 .slug(brand.getSlug())
                  .sortOrder(brand.getSortOrder())
                 .build();
        });
      return ListPageBrand.builder()
              .brands(brands.getContent())
              .page(brands.getNumber())
              .pageSize(brands.getSize())
              .totalElements(brands.getTotalElements())
              .totalPages(brands.getTotalPages())
              .build();
    }

    public List<BrandResponse> allBrands(Jwt jwt){
        if(jwt==null){
            ListPageBrand.builder().build();
        }


       return  brandRepo.findAll().stream().map(brand->{
            return BrandResponse.builder()
                    .id(brand.getId())
                    .name(brand.getName())
                    .slug(brand.getSlug())
                    .sortOrder(brand.getSortOrder())
                    .build();
        }).toList();
    }
    public ResponseDto<Object> editBrand(BrandResponse brandResponse) {

        try{
            Brand brand = brandRepo.getReferenceById(brandResponse.getId());
            if(!brandResponse.getName().isEmpty()){
                brand.setName(brandResponse.getName());
            }
            if(!brandResponse.getSlug().isEmpty()){
                brand.setSlug(formatNameToSlug(brandResponse.getSlug()));
            }
            if(brandResponse.getSortOrder()>0){
                brand.setSortOrder(brandResponse.getSortOrder());
            }
            brand.setActive(brand.isActive());
            brandRepo.save(brand);
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.OK.value())
                    .data(true)
                    .message("Updated")
                    .build();
        }catch (Exception e){
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .data(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    public ResponseDto<Object> deleteBrand(Long id) {
       try{
           brandRepo.findById(id).ifPresent(br ->{
               brandRepo.deleteById(id);
           });
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.OK.value())
                   .data(true)
                   .message("Deleted")
                   .build();
       }catch (Exception e){
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.BAD_REQUEST.value())
                   .data(false)
                   .message(e.getMessage())
                   .build();
       }
    }

    public Brand findByName(String name){
        return brandRepo.findByName(name).orElseThrow(()->new EntityNotFoundException("Brand does not exist"));
    }
}
