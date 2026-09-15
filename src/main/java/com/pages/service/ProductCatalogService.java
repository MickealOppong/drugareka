package com.pages.service;

import com.pages.dto.ProductDto;
import com.pages.dto.ProductRequest;
import com.pages.model.Brand;
import com.pages.model.Category;
import com.pages.model.ProductCatalog;
import com.pages.repository.ProductCatalogRepo;
import com.pages.util.UtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProductCatalogService {

    private final ProductCatalogRepo productCatalogRepo;
    private final BrandService brandService;
    private final CategoryService categoryService;

    public ProductCatalogService(ProductCatalogRepo productCatalogRepo, BrandService brandService, CategoryService categoryService) {
        this.productCatalogRepo = productCatalogRepo;

        this.brandService = brandService;
        this.categoryService = categoryService;
    }

    public ProductCatalog findOrCreateProduct(ProductRequest productRequest){

        /*
                BRAND
         */
        Brand brand =brandService.findByNameOrCreate(productRequest.getBrand());

        /*
                CATEGORY
         */
        Category category = categoryService.findByNameOrCreate(productRequest.getCategory());

        /*
            PRODUCT
         */


        ProductDto dto = ProductDto.builder()
                .name(productRequest.getName())
                .slug(UtilService.formatNameToSlug(productRequest.getName()))
                .description(productRequest.getDescription())
                .category(category)
                .brand(brand)
                .build();

            return productCatalogRepo.findByNameIgnoreCaseAndBrandNameIgnoreCase(productRequest.getName(), productRequest.getBrand().trim())
                    .orElseGet(()->{
                        ProductCatalog newProductCatalog = new ProductCatalog(dto);
                      return   productCatalogRepo.save(newProductCatalog);
                    });
    }


    public void save(ProductCatalog productCatalog){
        productCatalogRepo.save(productCatalog);
    }
}
