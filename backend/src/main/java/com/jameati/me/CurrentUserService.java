package com.jameati.me;

import com.jameati.common.NotFoundException;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.security.Principal;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {
  private final UserRepository users;

  public CurrentUserService(UserRepository users) {
    this.users = users;
  }

  public User require(Principal principal) {
    if (principal == null || principal.getName() == null) {
      throw new NotFoundException("المستخدم غير موجود");
    }
    return users
        .findByEmailIgnoreCaseAndDeletedFalse(principal.getName())
        .orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
  }
}
