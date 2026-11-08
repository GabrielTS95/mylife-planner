package com.mylifeplanner.repository;

import com.mylifeplanner.model.UserOwnedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface UserOwnedRepository<T extends UserOwnedEntity> extends JpaRepository<T, Long> {
    List<T> findAllByUserId(Long userId);
    Optional<T> findByIdAndUserId(Long id, Long userId);
}
