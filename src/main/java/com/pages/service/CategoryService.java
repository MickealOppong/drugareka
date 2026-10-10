package com.pages.service;
import com.pages.dto.*;
import com.pages.exception.EntityNotFoundException;
import com.pages.model.Brand;
import com.pages.util.Media;
import com.pages.util.UtilService;
import com.pages.exception.InvalidOperationException;
import com.pages.model.Category;
import com.pages.repository.CategoryRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
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




    @Transactional
    public ResponseDto<Object> addCategory(CategoryRequest categoryDto) {
        try {
            String incomingName = categoryDto.getName() != null ? categoryDto.getName().trim() : "";
            String incomingParent = categoryDto.getParent() != null ? categoryDto.getParent().trim() : "";
            boolean hasParent = !incomingParent.isBlank() && !incomingParent.equalsIgnoreCase("null");

            if (hasParent && incomingName.equalsIgnoreCase(incomingParent)) {
                throw new IllegalArgumentException("Category cannot be assigned as its own parent container.");
            }

            // Isolate parent container node safely if present
            Category parentNode = null;
            if (hasParent) {
                // Find the specific parent by name (assuming parent names at the root level are unique)
                parentNode = categoryRepo.findByNameAndParentIsNull(incomingParent)
                        .orElseThrow(() -> new IllegalArgumentException("Parent category root node not found matching: " + incomingParent));
            }

            boolean isDuplicate;
            if (hasParent) {
                // Checks if "Shoes" already exists under the "Men" branch ONLY.
                isDuplicate = categoryRepo.existsByNameIgnoreCaseAndParent(incomingName, parentNode);
            } else {
                // Checks if the root category "Men" already exists globally
                isDuplicate = categoryRepo.existsByNameIgnoreCaseAndParentIsNull(incomingName);
            }

            if (isDuplicate) {
                return ResponseDto.builder()
                        .message("Category already exists inside this specific structural branch level")
                        .data(null)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            String computedPath = UtilService.toPath(incomingName, hasParent ? parentNode.getSlug() : null);

            Category category = Category.builder()
                    .name(incomingName)
                    .slug(UtilService.formatNameToSlug(incomingName))
                    .isActive(categoryDto.isActive())
                    .sortOrder(categoryDto.getSortOrder() != null ? categoryDto.getSortOrder() : 0)
                    .parent(parentNode)
                    .path(computedPath)
                    .build();

            Category savedCategory = categoryRepo.save(category);

            if (categoryDto.getImage() != null && !categoryDto.getImage().isEmpty()) {
                mediaService.uploadCategoryImage(categoryDto.getImage(), savedCategory);
            }

            return ResponseDto.builder()
                    .message("Created")
                    .data(savedCategory)
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {
            log.error("Category Insertion Collision Error: Fail to save taxonomy node.", e);
            return ResponseDto.builder()
                    .message(e.getMessage())
                    .data(false)
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
    }



    public Category getCategoryById(Long id){
        return categoryRepo.findById(id)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Category does not exist"
                ));
    }


    public Category getCategoryByPath(String path){
        return categoryRepo.findByPath(path)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Category does not exist"
                ));
    }


    public List<CategoryResponse> getAllParentCategories(){
        return categoryRepo.findAll().stream().filter(category -> category.getParent()==null).map(category->{
            return    CategoryResponse.builder()
                    .id(category.getId())
                    .active(category.getIsActive())
                    .name(category.getName())
                    .sortOrder(category.getSortOrder())
                    .slug(category.getSlug())
                    .build();
        }).toList();
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



    @Transactional(readOnly = true)
    public List<CategoryTreeDto> getCategoryTree() {
        // STEP 1: Fetch ALL categories from the database in a single query pass
        List<Category> allCategories = categoryRepo.findAll();

        // STEP 2: Isolate the main top-level parent categories (where parent is null)
        List<Category> parents = allCategories.stream()
                .filter(category -> category.getParent() == null)
                .toList();

        // STEP 3: Group subcategories by their parent ID in-memory to prevent N+1 queries
        Map<Long, List<Category>> subCategoriesByParentId = allCategories.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getId()));

        // STEP 4: Build the clean, type-safe DTO hierarchy tree structure
        return parents.stream()
                .map(parent -> {
                    // Fetch the pre-grouped child elements safely from memory map
                    List<Category> children = subCategoriesByParentId.getOrDefault(parent.getId(), Collections.emptyList());

                    List<CategoryTreeDto.SubCategoryDto> subCategoryDtos = children.stream()
                            .map(child -> CategoryTreeDto.SubCategoryDto.builder()
                                    .id(child.getId())
                                    .name(child.getName())
                                    .parent(child.getParent().getName())
                                    .path(child.getPath())
                                    .slug(child.getSlug())
                                    .build())
                            .toList();

                    return CategoryTreeDto.builder()
                            .id(parent.getId())
                            .name(parent.getName())
                            .slug(parent.getSlug())
                            .path(parent.getPath())
                            .subCategories(subCategoryDtos)
                            .build();
                })
                .toList();
    }


    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public CategoryResponse getCategory(Long id){
       Category category = categoryRepo.findById(id).orElse(null);
       if(category==null){
           return null;
       }
      MediaResponse media= mediaService.getCategoryImage(category);
       String image = media!=null?media.getImage():null;
       String parent = category.getParent()!=null?category.getParent().getName():null;

        return CategoryResponse.builder()
                .slug(category.getName())
                .active(category.getIsActive())
                .sortOrder(category.getSortOrder())
                .image(image)
                .parent(parent)
                .name(category.getName())
                .build();
    }

    public Category getCategoryByName(String name){
        return categoryRepo.findByName(name).orElseThrow(()->new EntityNotFoundException(name+" does not exist"));
    }

    public Category getCategoryBySlug(String slug){
        return categoryRepo.findBySlug(slug).orElseThrow(()->new EntityNotFoundException(slug+" does not exist"));
    }

    @Transactional
    public ResponseDto<Object> editCategory(CategoryRequest categoryDto){

        try{
            if(categoryDto.getName().equalsIgnoreCase(categoryDto.getParent())){
                throw new InvalidOperationException("Category cannot be same as parent");
            }
            Category retreivedCategory = categoryRepo.getReferenceById(categoryDto.getId());



            if(categoryDto.getName()!=null && !categoryDto.getName().isEmpty()){
                retreivedCategory.setName(categoryDto.getName());
                retreivedCategory.setSlug(UtilService.formatNameToSlug(categoryDto.getSlug()));
            }



            if(categoryDto.getParent()!=null && !categoryDto.getParent().isEmpty()){
                Category newParentCategory = categoryRepo.findByName(categoryDto.getParent()).orElse(null);
                retreivedCategory.setParent(newParentCategory);
            }
            retreivedCategory.setPath(UtilService.toPath(categoryDto.getParent(),categoryDto.getName()));
            retreivedCategory.setIsActive(categoryDto.isActive());
            retreivedCategory.setSortOrder(categoryDto.getSortOrder());

            categoryRepo.save(retreivedCategory);
            mediaService.updateCategoryImage(categoryDto.getImage(),retreivedCategory);

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
        try {
            Category targetCategory = categoryRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Taxonomy Node not found for ID: " + id));


            // We run a high-performance index check to see if any other categories point to this ID as their parent.
            boolean hasActiveChildren = categoryRepo.existsByParentId(targetCategory.getId());

            if (hasActiveChildren) {
                log.warn("Security Shield: Denied deletion request for Category [{} (ID: {})]. It still contains active subcategories.",
                        targetCategory.getName(), targetCategory.getId());

                return ResponseDto.builder()
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .data(false)
                        .message("Action Denied: This category cannot be deleted because it contains active subcategories. Please remove or re-assign all subcategories first.")
                        .build();
            }

            log.info("Taxonomy System: Guard checked clear. Purging isolated category node: [{} (ID: {})]",
                    targetCategory.getName(), targetCategory.getId());

         mediaService.deleteByCategory(targetCategory);

            // Sever parent links and safely drop the category row entry
            targetCategory.setParent(null);
            categoryRepo.delete(targetCategory);
            categoryRepo.flush();

            return ResponseDto.builder()
                    .httpStatus(HttpStatus.OK.value())
                    .data(true)
                    .message("Category successfully deleted.")
                    .build();

        } catch (Exception e) {
            log.error("Taxonomy System Failure: Aborting deletion workflow", e);
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()) // 500 for true unexpected runtime errors
                    .data(false)
                    .message(e.getLocalizedMessage())
                    .build();
        }
    }

    public List<CategoryResponse> top6Categories(){
      return  categoryRepo.findTop6ByParentIsNullAndIsActiveIsTrueOrderBySortOrder()
              .stream().map(category -> {

                  //CATEGORY IMAGE //COULD BE NULL
                  MediaResponse media = mediaService.getCategoryImage(category);
                  String image = media!=null? media.getImage():null;

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

    public List<CategoryResponse> top8Categories() {

        List<Category> categories = new ArrayList<>(
                categoryRepo.findByParentIsNullAndIsActiveIsTrue()
        );

        Collections.shuffle(categories);

        return categories.stream()
                .limit(8)
                .map(category -> {
                    // Category image could be null
                    MediaResponse media = mediaService.getCategoryImage(category);
                    String image = media != null ? media.getImage() : null;

                    return CategoryResponse.builder()
                            .image(image)
                            .name(category.getName())
                            .parent(category.getParent() != null
                                    ? category.getParent().getName()
                                    : null)
                            .sortOrder(category.getSortOrder())
                            .id(category.getId())
                            .slug(category.getSlug())
                            .build();
                })
                .toList();
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
