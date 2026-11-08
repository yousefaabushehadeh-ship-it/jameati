package com.jameati.ai;

import java.security.Principal;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpportunityAiService {
  private static final Logger log=LoggerFactory.getLogger(OpportunityAiService.class);
  private final OpportunityContextService contexts;
  private final OfficialSourcePageService sources;
  private final OpenAiClient client;
  private final AiRateLimiter limiter;
  public OpportunityAiService(OpportunityContextService contexts,OfficialSourcePageService sources,OpenAiClient client,AiRateLimiter limiter) {
    this.contexts=contexts; this.sources=sources; this.client=client; this.limiter=limiter;
  }
  public AiChatResponse chat(long id,boolean job,AiChatRequest request,Principal principal) {
    long start=System.nanoTime();
    String type=job?"JOB":"OPPORTUNITY";
    try {
      if (principal!=null) limiter.consume("user:"+principal.getName(),false);
      var context=sources.load(contexts.load(id,job,principal));
      String blocked=AiPolicy.blocked(request.message());
      if (blocked!=null) {
        log.info("ai id={} type={} status=blocked ms={}",id,type,(System.nanoTime()-start)/1000000);
        return new AiChatResponse(AiPolicy.refusal(request.message(),blocked));
      }
      limiter.consume("global",true);
      var result=client.respond(context,request);
      if (!Set.of("ANSWER","MISSING","CLARIFY","OUT_OF_SCOPE","INTERNAL").contains(result.disposition()))
        throw new AiException(503,"invalid_disposition");
      if (!Set.of("OUT_OF_SCOPE","INTERNAL").contains(result.disposition()) && !verified(result,context)) {
        log.info("ai id={} type={} status=retry_quote ms={}",id,type,(System.nanoTime()-start)/1000000);
        try {
          limiter.consume("global",true);
          result=client.respond(context,request,true);
        } catch (AiException error) {
          if (error.status==429) return unverified(id,type,start,request.message());
          throw error;
        }
        if (!Set.of("OUT_OF_SCOPE","INTERNAL").contains(result.disposition()) && !verified(result,context))
          return unverified(id,type,start,request.message());
      }
      String answer;
      if (Set.of("OUT_OF_SCOPE","INTERNAL").contains(result.disposition())) answer=AiPolicy.refusal(request.message(),result.disposition());
      else answer=result.answer();
      log.info("ai id={} type={} status={} ms={} inputTokens={} outputTokens={}",id,type,result.disposition(),
          (System.nanoTime()-start)/1000000,result.inputTokens(),result.outputTokens());
      return new AiChatResponse(answer);
    } catch (RuntimeException e) {
      log.warn("ai id={} type={} status=error ms={} error={}",id,type,(System.nanoTime()-start)/1000000,
          e instanceof AiException ? e.getMessage() : e.getClass().getSimpleName());
      throw e;
    }
  }

  private boolean verified(OpenAiClient.Result result,OpportunityContextService.Context context) {
    if (!Set.of("ANSWER","MISSING","CLARIFY","OUT_OF_SCOPE","INTERNAL").contains(result.disposition())
        || result.answer().isBlank() || result.answer().length()>2000) return false;
    if ("ANSWER".equals(result.disposition()) && result.evidence().isEmpty()) return false;
    String source=context.fields().getOrDefault("sourceText","");
    return result.evidence().stream().allMatch(quote -> AiEvidence.matches(source,quote));
  }

  private AiChatResponse unverified(long id,String type,long start,String message) {
    log.info("ai id={} type={} status=unverified ms={}",id,type,(System.nanoTime()-start)/1000000);
    return new AiChatResponse(message.matches("(?s).*[\u0600-\u06ff].*")
        ? "لم أستطع تأكيد الإجابة من صفحة المصدر الرسمية الآن. افتح الإعلان الأصلي للتحقق من هذه المعلومة."
        : "I couldn't verify that answer on the original listing. Please check the source page.");
  }
}
