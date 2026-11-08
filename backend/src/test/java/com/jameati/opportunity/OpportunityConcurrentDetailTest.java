package com.jameati.opportunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jameati.subscription.ContentAccessService;
import com.jameati.subscription.SubscriptionPlanService;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.AdditionalAnswers;

@SpringBootTest
class OpportunityConcurrentDetailTest {
  @Autowired OpportunityRepository repository;
  @Autowired OpportunityMapper mapper;
  @Autowired PlatformTransactionManager transactions;

  @Test void concurrentReadsIncrementViewsWithoutVersionConflictsOrEditorialWrites() throws Exception {
    var tx = new TransactionTemplate(transactions);
    var item = new Opportunity();
    item.setSlug("concurrent-detail-regression"); item.setTitle("منحة اختبار التزامن");
    item.setShortDescription("ملخص"); item.setDescription("تفاصيل لا يجب تعديلها عند القراءة");
    item.setCategory("منحة"); item.setOrganization("جامعة"); item.setCountry("الأردن");
    item.setFunding(FundingType.FULLY_FUNDED); item.setPublished(true);
    var saved = tx.execute(s -> repository.saveAndFlush(item));
    try {
      var barrier = new CyclicBarrier(2);
      var coordinatingRepo = mock(OpportunityRepository.class, AdditionalAnswers.delegatesTo(repository));
      doAnswer(invocation -> {
        var loaded = repository.findBySlugAndDeletedFalseAndPublishedTrue(saved.getSlug());
        barrier.await(10, TimeUnit.SECONDS); // Both transactions have read the same entity version.
        return loaded;
      }).when(coordinatingRepo).findBySlugAndDeletedFalseAndPublishedTrue(saved.getSlug());
      var service = new OpportunityService(coordinatingRepo, mapper,
          mock(ContentAccessService.class), mock(SubscriptionPlanService.class), 14);
      try (var executor = Executors.newFixedThreadPool(2)) {
        Callable<OpportunityDtos.OpportunityResponse> request = () -> tx.execute(s -> service.detail(saved.getSlug(), null));
        var first = executor.submit(request);
        var second = executor.submit(request);
        assertThat(first.get(20, TimeUnit.SECONDS).id()).isEqualTo(saved.getId());
        assertThat(second.get(20, TimeUnit.SECONDS).id()).isEqualTo(saved.getId());
      }
      var reloaded = repository.findById(saved.getId()).orElseThrow();
      assertThat(reloaded.getViews()).isEqualTo(2);
      assertThat(reloaded.getVersion()).isEqualTo(saved.getVersion());
      assertThat(reloaded.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
      assertThat(reloaded.getDescription()).isEqualTo(item.getDescription());
    } finally {
      tx.executeWithoutResult(s -> repository.deleteById(saved.getId()));
    }
  }
}
