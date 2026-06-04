package org.example.group03.repository;

import org.example.group03.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE " +
            "(:keyword IS NULL OR p.title LIKE %:keyword%) AND " +
            "(:category IS NULL OR :category = '' OR p.category = :category) AND " +
            "(:status IS NULL OR :status = '' OR p.status = :status)")
    List<Post> searchPosts(@Param("keyword") String keyword,
                           @Param("category") String category,
                           @Param("status") String status);
}
