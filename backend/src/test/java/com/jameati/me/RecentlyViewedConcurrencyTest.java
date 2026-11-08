package com.jameati.me;

import static org.assertj.core.api.Assertions.*;
import com.jameati.opportunity.*;
import com.jameati.user.*;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class RecentlyViewedConcurrencyTest {
  @Autowired RecentlyViewedOpportunityRepository views;
  @Autowired OpportunityRepository opportunities;
  @Autowired UserRepository users;
  @Autowired UserSpaceService service;
  @Autowired PlatformTransactionManager transactions;

  @Test void concurrentFirstVisitsCreateOneRowAndClearedHistoryCanBeRestored() throws Exception {
    var tx = new TransactionTemplate(transactions);
    var user = users.saveAndFlush(new User("recent-concurrency@test.example", "test-hash", "Test visitor"));
    var opportunity = new Opportunity();
    opportunity.setSlug("recent-concurrency-test"); opportunity.setTitle("منحة اختبار");
    opportunity.setShortDescription("ملخص"); opportunity.setDescription("تفاصيل");
    opportunity.setCategory("منحة"); opportunity.setOrganization("جامعة"); opportunity.setCountry("الأردن");
    opportunity.setFunding(FundingType.FULLY_FUNDED); opportunity.setPublished(true);
    var saved = opportunities.saveAndFlush(opportunity);
    try {
      var gate = new CyclicBarrier(6);
      try (var pool = Executors.newFixedThreadPool(6)) {
        var results = new java.util.ArrayList<Future<UserSpaceDtos.RecentlyViewedResponse>>();
        for (int i=0;i<6;i++) results.add(pool.submit(() -> {
          gate.await(10, TimeUnit.SECONDS);
          return service.recordView(user, new UserSpaceDtos.RecentlyViewedRequest(saved.getId()));
        }));
        var ids = new java.util.HashSet<Long>();
        for (var result : results) ids.add(result.get(20, TimeUnit.SECONDS).id());
        assertThat(ids).hasSize(1);
      }
      var view = views.findByUserIdAndOpportunityId(user.getId(), saved.getId()).orElseThrow();
      assertThat(views.findByUserIdAndDeletedFalse(user.getId())).hasSize(1);
      var createdAt = view.getCreatedAt();
      var viewedAt = view.getViewedAt();
      service.clearRecentlyViewed(user);
      assertThat(views.findByUserIdAndDeletedFalse(user.getId())).isEmpty();
      // An older, delayed write must neither regress the latest time nor create another row.
      tx.executeWithoutResult(s -> views.recordView(user.getId(), saved.getId(), Instant.EPOCH));
      var restored = views.findByUserIdAndOpportunityId(user.getId(), saved.getId()).orElseThrow();
      assertThat(restored.getId()).isEqualTo(view.getId());
      assertThat(restored.getCreatedAt()).isEqualTo(createdAt);
      assertThat(restored.getViewedAt()).isEqualTo(viewedAt);
      assertThat(restored.isDeleted()).isFalse();
    } finally {
      tx.executeWithoutResult(s -> {
        views.findByUserIdAndOpportunityId(user.getId(), saved.getId()).ifPresent(views::delete);
        views.flush(); opportunities.deleteById(saved.getId()); users.deleteById(user.getId());
      });
    }
  }
}
