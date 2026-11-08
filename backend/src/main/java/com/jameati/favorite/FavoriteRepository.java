package com.jameati.favorite;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface FavoriteRepository extends JpaRepository<Favorite,Long>{
  @Query("select f from Favorite f join fetch f.opportunity where f.user.id=:userId and f.deleted=false order by f.createdAt desc") List<Favorite> list(Long userId);
  Optional<Favorite> findByUserIdAndOpportunityIdAndDeletedFalse(Long userId,Long opportunityId);
}
