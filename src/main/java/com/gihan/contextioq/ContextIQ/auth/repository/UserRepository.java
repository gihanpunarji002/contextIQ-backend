package com.gihan.contextioq.ContextIQ.auth.repository;

import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByEmail(String email);
  Optional<AppUser> findByGoogleSubject(String subject);
}
