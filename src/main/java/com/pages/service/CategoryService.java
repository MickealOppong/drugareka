package com.pages.service;
import com.pages.dto.*;
import com.pages.exception.EntityNotFoundException;
import com.pages.model.Brand;
import com.pages.util.Media;
import com.pages.util.UtilService;
import com.pages.exception.InvalidOperationException;
import com.pages.model.Category;
import com.pages.repository.CategoryRepo;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.pages.util.UtilService.formatNameToSlug;

@Slf4j

@Service
public class CategoryService {

    private final CategoryRepo categoryRepo;
    private final MediaService mediaService;

    public CategoryService(CategoryRepo categoryRepo, MediaService mediaService) {
        this.categoryRepo = categoryRepo;
        this.mediaService = mediaService;
    }


    public ResponseDto<Object> addCategory(CategoryRequest categoryDto){
        try{

            if(categoryDto.getName().equalsIgnoreCase(categoryDto.getParent())){
                throw new InvalidOperationException("Category cannot be same as parent");
            }

            if(categoryRepo.existsByName(categoryDto.getName())){
                return ResponseDto.builder()
                        .message("Category already exists")
                        .data(null)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }
            Category parent = categoryRepo.findByName(categoryDto.getParent()).orElse(null);

            Category category = Category.builder()
                    .slug(formatNameToSlug(categoryDto.getName()))
                    .isActive(categoryDto.isActive())
                    .sortOrder(categoryDto.getSortOrder())
                    .parent(parent)
                    .name(categoryDto.getName())
                    .build();
           Category savedCategory= categoryRepo.save(category);

           /*
                CATEGORY IMAGE
            */
           mediaService.saveMedia(categoryDto.getImage(),savedCategory);

            return ResponseDto.builder()
                    .message("Created")
                    .data(savedCategory)
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        }catch (Exception e){
            return ResponseDto.builder()
                    .message(e.getMessage())
                    .data(false)
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .build();
        }
    }


    public Category findByNameOrCreate(String name){
        return categoryRepo.findByName(name).orElseGet(()->{
            Category newCategory = Category.builder()
                    .name(name)
                    .parent(null)
                    .sortOrder(1)
                    .isActive(true)
                    .slug(UtilService.formatNameToSlug(name))
                    .build();
         return  categoryRepo.save(newCategory);

        });
    }

    public List<String> getAllParentCategories(){
        return categoryRepo.findAll().stream().map(Category::getName).toList();
    }

    public List<CategoryResponse> getAllCategories(){
        return categoryRepo.findAll().stream().map(category -> {
            MediaResponse media = mediaService.getCategoryImage(category);
         return    CategoryResponse.builder()
                    .id(category.getId())
                    .active(category.getIsActive())
                    .name(category.getName())
                    .image(media!=null? media.getImage():null)
                    .parent(category.getParent()!=null?category.getParent().getName():null)
                    .sortOrder(category.getSortOrder())
                    .slug(category.getSlug())
                    .build();
        }).toList();

    }



    public CategoryResponse getCategory(Long id){
       Category category = categoryRepo.getReferenceById(id);
        return CategoryResponse.builder()
                .slug(category.getName())
                .active(category.getIsActive())
                .sortOrder(category.getSortOrder())
                .parent(category.getParent()!=null?category.getParent().getName():null)
                .name(category.getName())
                .build();
    }

    public Category getCategoryByName(String name){
        return categoryRepo.findByName(name).orElseThrow(()->new EntityNotFoundException(name+" does not exist"));
    }

    public Category getCategoryBySlug(String slug){
        return categoryRepo.findBySlug(slug).orElseThrow(()->new EntityNotFoundException(slug+" does not exist"));
    }

    public ResponseDto<Object> editCategory(CategoryRequest categoryDto){

        try{
            if(categoryDto.getName().equalsIgnoreCase(categoryDto.getParent())){
                throw new InvalidOperationException("Category cannot be same as parent");
            }
            Category retreivedCategory = categoryRepo.getReferenceById(categoryDto.getId());

            if(categoryDto.getName()!=null && !categoryDto.getName().isEmpty()){
                retreivedCategory.setName(categoryDto.getName());
                retreivedCategory.setSlug(categoryDto.getName());
            }
            Category newParentCategory = categoryRepo.findByName(categoryDto.getParent()).orElse(null);
            retreivedCategory.setParent(newParentCategory);
            retreivedCategory.setIsActive(categoryDto.isActive());
            retreivedCategory.setSortOrder(categoryDto.getSortOrder());

            categoryRepo.save(retreivedCategory);

            return ResponseDto.builder()
                    .message("Updated")
                    .data(true)
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        }catch (Exception e){
            return ResponseDto.builder()
                    .message(e.getMessage())
                    .data(false)
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .build();
        }


    }


    @Transactional
    public ResponseDto<Object> deleteCategory(Long id) {
        try{
            categoryRepo.findById(id).ifPresent(cat->{

                try {
                    mediaService.deleteAllByCategory(id);
                    categoryRepo.deleteById(id);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            });
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
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

    public List<CategoryResponse> top6Categories(){
      return  categoryRepo.findTop6AllByAndIsActiveIsTrue().stream().map(category -> {
         String image= mediaService.getCategoryImage(category).getImage();
            return CategoryResponse.builder()
                    .image(image)
                    .name(category.getName())
                    .parent(category.getParent()!=null?category.getName():null)
                    .sortOrder(category.getSortOrder())
                    .id(category.getId())
                    .slug(category.getSlug())
                    .build();
        }).toList();
    }

    public List<CategoryResponse> allCategories(){
        return  categoryRepo.findAllByAndIsActiveIsTrue().stream().map(category -> {
            String image= mediaService.getCategoryImage(category).getImage();
            return CategoryResponse.builder()
                    .image(image)
                    .name(category.getName())
                    .parent(category.getParent()!=null?category.getName():null)
                    .sortOrder(category.getSortOrder())
                    .id(category.getId())
                    .slug(category.getSlug())
                    .build();
        }).toList();
    }
}
