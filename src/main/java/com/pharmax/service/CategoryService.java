package com.pharmax.service;

import com.pharmax.database.Repository.CategoryRepository;
import com.pharmax.model.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CategoryService {
    private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);
    private final CategoryRepository categoryRepository;
    
    public CategoryService() {
        this.categoryRepository = new CategoryRepository();
    }
    
    public Category createCategory(Category category) {
        logger.info("Creating new category: {}", category.getName());
        validateCategory(category);
        return categoryRepository.save(category);
    }
    
    public Category updateCategory(Category category) {
        logger.info("Updating category: {}", category.getId());
        validateCategory(category);
        return categoryRepository.save(category);
    }
    
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }
    
    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }
    
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
    
    public List<Category> getActiveCategories() {
        return categoryRepository.findAll().stream()
                .filter(Category::getIsActive)
                .toList();
    }
    
    public void deleteCategory(Long id) {
        logger.info("Deleting category: {}", id);
        categoryRepository.deleteById(id);
    }
    
    public void deleteCategory(Category category) {
        logger.info("Deleting category: {}", category.getId());
        categoryRepository.delete(category);
    }
    
    private void validateCategory(Category category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("اسم الفئة مطلوب");
        }
        
        // Check for duplicate name (for new categories)
        if (category.getId() == null) {
            Optional<Category> existing = categoryRepository.findByName(category.getName());
            if (existing.isPresent()) {
                throw new IllegalArgumentException("اسم الفئة مستخدم بالفعل");
            }
        } else {
            // Check for duplicate name (for updates)
            Optional<Category> existing = categoryRepository.findByName(category.getName());
            if (existing.isPresent() && !existing.get().getId().equals(category.getId())) {
                throw new IllegalArgumentException("اسم الفئة مستخدم بالفعل");
            }
        }
    }
}
