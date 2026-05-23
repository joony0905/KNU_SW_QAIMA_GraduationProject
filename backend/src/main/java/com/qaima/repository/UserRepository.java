package com.qaima.repository;

import com.qaima.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findAllByNameAndBirthdate(String name, String birthdate);
    @Query("select u from User u where u.name = :name and (u.birthdate = :birthdate or u.birthdate like concat(:birthdate, '%'))")
    List<User> findAllByNameAndBirthdatePrefix(@Param("name") String name, @Param("birthdate") String birthdate);
    List<User> findAllByPhoneIsNotNull();
    Optional<User> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
