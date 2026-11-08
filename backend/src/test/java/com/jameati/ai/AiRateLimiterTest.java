package com.jameati.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class AiRateLimiterTest {
  @Test void twentyRequestsAcrossOpportunitiesThenReset() {
    var clock=mock(Clock.class); when(clock.millis()).thenReturn(0L);
    var limiter=new AiRateLimiter(new OpenAiConfig(),clock);
    for(int i=0;i<20;i++) limiter.consume("ip:one",false);
    assertThatThrownBy(()->limiter.consume("ip:one",false)).isInstanceOfSatisfying(AiException.class,e->assertThat(e.retryAfter).isEqualTo(3600));
    limiter.consume("ip:two",false);
    when(clock.millis()).thenReturn(3600000L); limiter.consume("ip:one",false);
  }
  @Test void concurrentRequestsCannotBypassLimit() throws Exception {
    var limiter=new AiRateLimiter(new OpenAiConfig());
    try(var pool=Executors.newFixedThreadPool(8)) {
      var tasks=new java.util.ArrayList<Callable<Boolean>>();
      for(int i=0;i<80;i++) tasks.add(()->{try{limiter.consume("ip:one",false);return true;}catch(AiException e){return false;}});
      int accepted=0;for(var result:pool.invokeAll(tasks)) if(result.get()) accepted++;
      assertThat(accepted).isEqualTo(20);
    }
  }
}
