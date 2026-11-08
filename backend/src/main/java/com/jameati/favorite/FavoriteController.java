package com.jameati.favorite;

import com.jameati.common.*;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.user.UserRepository;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/me/favorites") @Transactional
public class FavoriteController {
  private final FavoriteRepository favorites;private final UserRepository users;private final OpportunityRepository opportunities;
  public FavoriteController(FavoriteRepository f,UserRepository u,OpportunityRepository o){favorites=f;users=u;opportunities=o;}
  record FavoriteItem(Long id,Long opportunityId,String slug,String title,String category,String organization,String country,FundingType funding,LocalDate deadline,String deadlineText,boolean alwaysOpen,Instant savedAt){ }
  @GetMapping ApiResponse<List<FavoriteItem>> list(Principal p){var u=user(p);return ApiResponse.ok(favorites.list(u.getId()).stream().map(f->{var o=f.getOpportunity();return new FavoriteItem(f.getId(),o.getId(),o.getSlug(),o.getTitle(),o.getCategory(),o.getOrganization(),o.getCountry(),o.getFunding(),o.getDeadline(),o.getDeadlineText(),o.isAlwaysOpen(),f.getCreatedAt());}).toList());}
  @PostMapping("/{opportunityId}") ApiResponse<Void> add(@PathVariable Long opportunityId,Principal p){var u=user(p);if(favorites.findByUserIdAndOpportunityIdAndDeletedFalse(u.getId(),opportunityId).isEmpty()){var f=new Favorite();f.setUser(u);f.setOpportunity(opportunities.findByIdAndDeletedFalse(opportunityId).filter(com.jameati.opportunity.Opportunity::isPublished).orElseThrow(()->new NotFoundException("الفرصة غير موجودة")));favorites.save(f);}return ApiResponse.ok(null);}
  @DeleteMapping("/{opportunityId}") ApiResponse<Void> remove(@PathVariable Long opportunityId,Principal p){favorites.findByUserIdAndOpportunityIdAndDeletedFalse(user(p).getId(),opportunityId).ifPresent(f->f.setDeleted(true));return ApiResponse.ok(null);}
  private com.jameati.user.User user(Principal p){return users.findByEmailIgnoreCaseAndDeletedFalse(p.getName()).orElseThrow();}
}
