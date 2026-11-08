package com.jameati.ai;

import com.jameati.opportunity.OpportunityRepository;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.subscription.ContentAccessService;
import com.jameati.common.NotFoundException;
import java.security.Principal;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityContextService {
  private final OpportunityRepository opportunities;
  private final CatalogRepository catalog;
  private final ContentAccessService access;
  public OpportunityContextService(OpportunityRepository opportunities,CatalogRepository catalog,ContentAccessService access) {
    this.opportunities=opportunities; this.catalog=catalog; this.access=access;
  }
  public record Context(String type, Map<String,String> fields) {}
  @Transactional(readOnly=true)
  public Context load(long id, boolean job, Principal principal) {
    if (id<=0) throw new IllegalArgumentException("معرف الفرصة غير صالح");
    var fields=new LinkedHashMap<String,String>();
    if (job) {
      var item=catalog.findByIdAndDeletedFalse(id)
          .filter(c -> c.isPublished() && c.getType()==CatalogType.JOB)
          .orElseThrow(() -> new NotFoundException("الوظيفة غير موجودة"));
      boolean publicSource=item.getAttributes()!=null && Boolean.TRUE.equals(item.getAttributes().get("publicSourceLink"));
      if (!publicSource && access.isCatalogLocked(access.resolve(principal),CatalogType.JOB,id))
        throw new AiException(403,"access_locked");
      // The source documentation URL can be an API or job-board index. Prefer
      // the URL of this exact listing over any description stored in Jameati.
      Object official=item.getAttributes()==null ? null : item.getAttributes().get("officialUrl");
      put(fields,"sourceUrl",official == null ? item.getWebsite() : official);
      return new Context("JOB",Collections.unmodifiableMap(fields));
    }
    var item=opportunities.findByIdAndDeletedFalse(id).filter(o -> o.isPublished())
        .orElseThrow(() -> new NotFoundException("الفرصة غير موجودة"));
    if (access.isOpportunityLocked(access.resolve(principal),id)) throw new AiException(403,"access_locked");
    put(fields,"sourceUrl",item.getOfficialUrl()==null ? item.getApplyUrl() : item.getOfficialUrl());
    return new Context("OPPORTUNITY",Collections.unmodifiableMap(fields));
  }
  static void put(Map<String,String> fields,String key,Object value) {
    if (value==null || value instanceof Collection<?> c && c.isEmpty()) return;
    String raw=value.toString();
    // Bound parser work, retain the beginning and end of unusually large descriptions.
    boolean truncated=raw.length()>50000;
    if (truncated) raw=raw.substring(0,25000)+" [excerpt omitted] "+raw.substring(raw.length()-25000);
    var document=Jsoup.parse(raw); document.select("script,style,noscript,iframe").remove();
    String clean=document.text().trim();
    int remaining=16000-fields.entrySet().stream().mapToInt(e -> e.getKey().length()+e.getValue().length()).sum();
    int limit=Math.min(key.equals("description") || key.equals("content") || key.equals("sourceText") ? 10000 : 1500,remaining-100);
    if (clean.isEmpty()) return;
    if (limit<100) { fields.put("contextTruncated","true"); return; }
    if (clean.length()>limit) { clean=clean.substring(0,limit*2/3)+" [excerpt omitted] "+clean.substring(clean.length()-limit/3); truncated=true; }
    fields.put(key,clean);
    if (truncated) fields.put("contextTruncated","true");
  }
}
