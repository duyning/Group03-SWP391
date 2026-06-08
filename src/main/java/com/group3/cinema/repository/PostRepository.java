package com.group3.cinema.repository;

import com.group3.cinema.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE " +
            "(:keyword IS NULL OR p.title LIKE %:keyword%) AND " +
            "(:category IS NULL OR :category = '' OR p.category = :category) AND " +
            "(:status IS NULL OR :status = '' OR p.status = :status)")
    List<Post> searchPosts(@Param("keyword") String keyword,
                           @Param("category") String category,
                           @Param("status") String status);

    List<Post> findByStatusOrderByPublishedAtDescCreatedAtDesc(String status);

    List<Post> findTop3ByStatusOrderByPublishedAtDescCreatedAtDesc(String status);

    Optional<Post> findByIdAndStatus(Long id, String status);
}
