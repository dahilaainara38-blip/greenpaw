package com.greenpaw.chat.repository.mysql;

import com.greenpaw.chat.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByParentCategoryId(Long parentCategoryId);

    List<Category> findAllByOrderByParentCategoryIdAscIdAsc();

    List<Category> findByNameContaining(String name);
}