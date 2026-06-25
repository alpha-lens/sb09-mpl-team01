package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.Content;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, UUID> {
}