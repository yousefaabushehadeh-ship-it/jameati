package com.jameati.ai;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiRateLimiter {
  private final OpenAiConfig config;
  private final Clock clock;
  private final Map<String, Window> windows = new HashMap<>();
  @org.springframework.beans.factory.annotation.Autowired
  public AiRateLimiter(OpenAiConfig config) { this(config, Clock.systemUTC()); }
  AiRateLimiter(OpenAiConfig config, Clock clock) { this.config=config; this.clock=clock; }
  public synchronized void consume(String key, boolean global) {
    long now=clock.millis();
    windows.entrySet().removeIf(e -> e.getValue().resetAt <= now);
    var window=windows.get(key);
    if (window==null) {
      if (windows.size()>=20000) throw new AiException(429,"limiter_capacity",60);
      window=new Window(now+3600000,0);
    }
    if (window.count >= (global ? config.getGlobalMessagesPerHour() : config.getMessagesPerHour()))
      throw new AiException(429,"rate_limit",Math.max(1,(window.resetAt-now+999)/1000));
    windows.put(key,new Window(window.resetAt,window.count+1));
  }
  private record Window(long resetAt,int count) {}
}
