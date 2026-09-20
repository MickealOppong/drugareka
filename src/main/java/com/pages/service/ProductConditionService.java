package com.pages.service;

import com.pages.dto.ConditionRequest;
import com.pages.dto.ConditionResponse;
import com.pages.dto.ResponseDto;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.ProductCondition;
import com.pages.repository.ProductConditionRepo;
import com.pages.util.UtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class ProductConditionService {

    private final ProductConditionRepo productConditionRepo;

    public ProductConditionService(ProductConditionRepo productConditionRepo) {
        this.productConditionRepo = productConditionRepo;
    }

    public ResponseDto<Object> addCondition(ConditionRequest conditionDto){
       try{

           ProductCondition productCondition =findByNameOrCreate(conditionDto);
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.OK.value())
                   .message("Created")
                   .data(productCondition)
                   .build();
       }catch (Exception e){
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.BAD_REQUEST.value())
                   .message(e.getMessage())
                   .data(false)
                   .build();
       }
    }

    public ProductCondition findByNameOrCreate(ConditionRequest dto){
        return productConditionRepo.findByName(dto.getName()).orElseGet(()->{
            ProductCondition productCondition = ProductCondition.builder()
                    .name(dto.getName())
                    .active(dto.isActive())
                    .description(dto.getDescription())
                    .sortOrder(dto.getSortOrder())
                    .slug(UtilService.formatNameToSlug(dto.getName()))
                    .build();
            return productConditionRepo.save(productCondition);
        });
    }
    public ProductCondition findByNameOrCreate(String name){
       return productConditionRepo.findByName(name).orElseGet(()->{
            ProductCondition productCondition = ProductCondition.builder()
                    .name(name)
                    .active(true)
                    .description(name)
                    .sortOrder(1)
                    .slug(UtilService.formatNameToSlug(name))
                    .build();
            return productConditionRepo.save(productCondition);
        });
    }

    public ResponseDto<Object> deleteCondition(Long id){
       try{
           if(productConditionRepo.existsById(id)){
               productConditionRepo.deleteById(id);
               return ResponseDto.builder()
                       .data(true)
                       .message("Delete")
                       .httpStatus(HttpStatus.OK.value())
                       .build();
           }
           return ResponseDto.builder()
                   .data(false)
                   .message("Condition does not exist")
                   .httpStatus(HttpStatus.OK.value())
                   .build();
       }catch (Exception e){
           return ResponseDto.builder()
                   .data(false)
                   .message(e.getMessage())
                   .httpStatus(HttpStatus.OK.value())
                   .build();
       }

    }

    public List<ConditionResponse> AllProductConditions(){
        return productConditionRepo.findAll().stream().map(productCondition -> {
            return ConditionResponse.builder()
                    .id(productCondition.getId())
                    .name(productCondition.getName())
                    .description(productCondition.getDescription())
                    .sortOrder(productCondition.getSortOrder())
                    .slug(productCondition.getSlug())
                    .active(productCondition.isActive())
                    .build();
        }).toList();
    }

    public ResponseDto<ConditionResponse> productCondition(Jwt jwt,Long id){
      if(jwt!=null){
          ProductCondition productCondition = productConditionRepo.findById(id).orElse(null);
          if(productCondition!=null){
              ConditionResponse response=   ConditionResponse.builder()
                      .id(productCondition.getId())
                      .name(productCondition.getName())
                      .description(productCondition.getDescription())
                      .sortOrder(productCondition.getSortOrder())
                      .slug(productCondition.getSlug())
                      .active(productCondition.isActive())
                      .build();
              return ResponseDto.<ConditionResponse>builder()
                      .data(response)
                      .httpStatus(HttpStatus.OK.value())
                      .build();
          }
          return ResponseDto.<ConditionResponse>builder()
                  .data(null)
                  .message(id+"does not exist")
                  .httpStatus(HttpStatus.BAD_REQUEST.value())
                  .build();
      }
        return ResponseDto.<ConditionResponse>builder()
                .data(null)
                .httpStatus(HttpStatus.FORBIDDEN.value())
                .build();
    }

    public ProductCondition getConditionByName(String name){
        return productConditionRepo.findByName(name).orElseThrow(()->new EntityNotFoundException(name+" does not exist"));
    }

    public ProductCondition findAndUpdate(String name){
        return  productConditionRepo.findByName(name).orElseThrow(()->new EntityNotFoundException("Product condition does not exist"));
    }

    public ResponseDto<Boolean> editCondition(Jwt jwt,ConditionRequest request){

        if(jwt==null){
            return ResponseDto.<Boolean>builder()
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .message("Unauthorised request")
                    .data(false)
                    .build();
        }
        try{
            ProductCondition productCondition = productConditionRepo.findById(request.getId()).orElse(null);
            if(productCondition !=null){
                if(StringUtils.hasText(request.getName().trim())){
                    productCondition.setName(request.getName());
                }
                if(StringUtils.hasText(request.getDescription().trim())){
                    productCondition.setDescription(request.getDescription());
                }
                if(StringUtils.hasText(request.getSlug().trim())){
                    productCondition.setSlug(UtilService.formatNameToSlug(request.getSlug()));
                }
                if(!request.isActive()){
                    productCondition.setActive(false);
                }
                if(request.getSortOrder()!= productCondition.getSortOrder()){
                    productCondition.setSortOrder(request.getSortOrder());
                }
                productConditionRepo.save(productCondition);
                return ResponseDto.<Boolean>builder()
                        .httpStatus(HttpStatus.OK.value())
                        .message("Updated")
                        .data(true)
                        .build();
            }
            return ResponseDto.<Boolean>builder()
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .message("")
                    .data(false)
                    .build();
        }catch (Exception e){
            return ResponseDto.<Boolean>builder()
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .message(e.getMessage())
                    .data(false)
                    .build();
        }
    }
}
