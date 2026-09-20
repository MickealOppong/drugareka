package com.pages.controller;

import com.pages.dto.CategoryDto;
import com.pages.dto.CategoryRequest;
import com.pages.dto.CategoryResponse;
import com.pages.dto.ResponseDto;
import com.pages.exception.PhotoNotFoundException;
import com.pages.service.CategoryService;
import jakarta.validation.Valid;
import jdk.jfr.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }



    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping(value = "/new",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> newCategory(@ModelAttribute @Valid  CategoryRequest dataToSend){
        if(dataToSend.getImage()==null){
            throw new PhotoNotFoundException( "Please add category image");
        }
        return categoryService.addCategory(dataToSend);
    }

    @GetMapping("/parent-categories")
    public List<String> allParentCategories(){
        return categoryService.getAllParentCategories();
    }

    @GetMapping("/categories")
    public List<CategoryResponse> allCategories(){
        return categoryService.getAllCategories();
    }

    @GetMapping("/{id}")
    public CategoryResponse category(Long id){
        return categoryService.getCategory(id);
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/edit",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> editCategory(@ModelAttribute @Valid CategoryRequest categoryDto){
        return categoryService.editCategory(categoryDto);
    }

    @DeleteMapping("/delete")
    public ResponseDto<Object> deleteCategory(Long id){
        return categoryService.deleteCategory(id);
    }
}
