package com.jameati.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.jameati.subscription.ContentAccessService;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CatalogConcurrentDetailTest {
  @Autowired CatalogRepository repository;

  @Test void concurrentJobReadsCountEveryViewWithoutChangingEditorialVersion() throws Exception {
    var item = new CatalogItem();
    item.setType(CatalogType.JOB);
    item.setSlug("concurrent-job-detail-regression");
    item.setTitle("وظيفة تجريبية");
    item.setSummary("ملخص");
    item.setContent("تفاصيل الوظيفة");
    item.setCategory("وظيفة");
    var saved = repository.saveAndFlush(item);
    try {
      var barrier = new CyclicBarrier(2);
      var coordinated = mock(CatalogRepository.class, AdditionalAnswers.delegatesTo(repository));
      doAnswer(invocation -> {
        var loaded = repository.findByTypeAndSlugAndPublishedTrueAndDeletedFalse(CatalogType.JOB, saved.getSlug());
        barrier.await(10, TimeUnit.SECONDS);
        return loaded;
      }).when(coordinated).findByTypeAndSlugAndPublishedTrueAndDeletedFalse(CatalogType.JOB, saved.getSlug());
      var controller = new CatalogController(coordinated, mock(ContentAccessService.class));
      try (var pool = Executors.newFixedThreadPool(2)) {
        Callable<Long> read = () -> controller.detail(CatalogType.JOB, saved.getSlug(), null).data().id();
        var first = pool.submit(read);
        var second = pool.submit(read);
        assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(saved.getId());
        assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(saved.getId());
      }
      var reloaded = repository.findById(saved.getId()).orElseThrow();
      assertThat(reloaded.getViews()).isEqualTo(2);
      assertThat(reloaded.getVersion()).isEqualTo(saved.getVersion());
      assertThat(reloaded.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
      assertThat(reloaded.getContent()).isEqualTo(saved.getContent());
    } finally {
      repository.deleteById(saved.getId());
    }
  }
}
