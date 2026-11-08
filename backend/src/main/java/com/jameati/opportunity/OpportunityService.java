package com.jameati.opportunity;

import static com.jameati.opportunity.OpportunityDtos.*;
import com.jameati.common.ArabicTitleSupport;
import com.jameati.common.NotFoundException;
import com.jameati.subscription.ContentAccessService;
import com.jameati.subscription.SubscriptionPlanService;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class OpportunityService {
  private final OpportunityRepository repo; private final OpportunityMapper mapper; private final ContentAccessService access; private final SubscriptionPlanService subscriptionPlan; private final int closingDays;
  public OpportunityService(OpportunityRepository repo,OpportunityMapper mapper,ContentAccessService access,SubscriptionPlanService subscriptionPlan,@Value("${app.opportunity.closing-soon-days}") int closingDays){this.repo=repo;this.mapper=mapper;this.access=access;this.subscriptionPlan=subscriptionPlan;this.closingDays=closingDays;}
  public Page<OpportunityResponse> search(Filter filter,Pageable pageable,Principal principal){var viewer=access.resolve(principal);return repo.findAll(OpportunitySpecifications.publicFilter(filter,closingDays),ArabicTitleSupport.arabicFirst(pageable)).map(o->publicResponse(o,viewer));}
  @Transactional public OpportunityResponse detail(String slug,Principal principal){
    var o=repo.findBySlugAndDeletedFalseAndPublishedTrue(slug).orElseThrow(()->new NotFoundException("الفرصة غير موجودة"));
    // Only the atomic counter query writes. Dirtying this versioned entity would
    // flush a full editorial update and conflict with another detail request.
    repo.incrementViews(o.getId());
    return publicResponse(o,access.resolve(principal));
  }
  public List<OpportunityResponse> featured(int limit,Principal principal){var viewer=access.resolve(principal);var pageable=PageRequest.of(0,Math.max(1,Math.min(limit,20)),Sort.by(Sort.Direction.DESC,"createdAt"));return repo.featured(ArabicTitleSupport.arabicFirst(pageable)).stream().map(o->publicResponse(o,viewer)).toList();}
  public Page<OpportunityResponse> homepage(int requestedLimit,Principal principal){
    var limit=Math.max(1,Math.min(requestedLimit,12));
    var viewer=access.resolve(principal);
    var selectedIds=viewer.plan()==null
        ? List.<Long>of()
        : viewer.plan().freeContent().getOrDefault(SubscriptionPlanService.OPPORTUNITY,List.of());
    var selectedById=selectedIds.isEmpty()
        ? Map.<Long,Opportunity>of()
        : repo.findByIdInAndPublishedTrueAndDeletedFalse(selectedIds).stream()
            .collect(java.util.stream.Collectors.toMap(Opportunity::getId,o->o));
    var latestPageable=ArabicTitleSupport.arabicFirst(
        PageRequest.of(0,limit,Sort.by(Sort.Direction.DESC,"createdAt")));
    var latest=repo.findByPublishedTrueAndDeletedFalse(latestPageable);
    var merged=new LinkedHashMap<Long,Opportunity>();
    selectedIds.stream().map(selectedById::get).filter(Objects::nonNull)
        .forEach(o->merged.put(o.getId(),o));
    latest.getContent().forEach(o->merged.putIfAbsent(o.getId(),o));
    var content=merged.values().stream()
        .sorted(Comparator.comparing(Opportunity::isArabicTitle).reversed())
        .limit(limit).map(o->publicResponse(o,viewer)).toList();
    return new PageImpl<>(content,latestPageable,latest.getTotalElements());
  }
  public Page<OpportunityResponse> adminList(Pageable pageable){return repo.findAll(pageable).map(this::response);}
  @Transactional public OpportunityResponse create(UpsertRequest r){if(repo.existsBySlugIgnoreCase(r.slug()))throw new IllegalArgumentException("الرابط المختصر مستخدم");return response(repo.save(mapper.toEntity(r)));}
  @Transactional public OpportunityResponse update(Long id,UpsertRequest r){var o=repo.findByIdAndDeletedFalse(id).orElseThrow(()->new NotFoundException("الفرصة غير موجودة"));preventRemovingFreeSelection(id,r.published());mapper.update(r,o);return response(o);}
  @Transactional public void delete(Long id){var o=repo.findByIdAndDeletedFalse(id).orElseThrow(()->new NotFoundException("الفرصة غير موجودة"));preventRemovingFreeSelection(id,false);o.setDeleted(true);o.setPublished(false);}
  OpportunityResponse response(Opportunity o){return response(o,false);}
  private OpportunityResponse publicResponse(Opportunity o,ContentAccessService.AccessContext viewer){return response(o,access.isOpportunityLocked(viewer,o.getId()));}
  private OpportunityResponse response(Opportunity o,boolean locked){var base=mapper.toResponse(o);var s=status(o);long days=o.getDeadline()==null?0:ChronoUnit.DAYS.between(LocalDate.now(),o.getDeadline());return new OpportunityResponse(base.id(),base.slug(),base.title(),base.shortDescription(),locked?null:base.description(),base.category(),base.organization(),base.country(),base.funding(),base.deadline(),base.deadlineText(),base.alwaysOpen(),base.featured(),base.published(),base.coverImage(),locked?null:base.officialUrl(),locked?null:base.applyUrl(),locked?false:base.serviceEnabled(),locked?null:base.servicePrice(),locked?null:base.serviceCurrency(),locked?null:base.sourceName(),locked?null:base.sourceUrl(),locked?null:base.lastVerifiedAt(),base.views(),locked?0:base.applyClicks(),locked?Set.of():base.specialities(),locked?Set.of():base.nationalities(),locked?Set.of():base.degreeLevels(),s,days,base.createdAt(),base.updatedAt(),locked);}
  OpportunityStatus status(Opportunity o){if(o.getStatusOverride()!=null)return o.getStatusOverride();if(o.isAlwaysOpen())return OpportunityStatus.ALWAYS_OPEN;if(o.getDeadline()==null)return OpportunityStatus.UNKNOWN;var d=ChronoUnit.DAYS.between(LocalDate.now(),o.getDeadline());if(d<0)return OpportunityStatus.EXPIRED;if(d<=closingDays)return OpportunityStatus.CLOSING_SOON;return OpportunityStatus.OPEN;}
  private void preventRemovingFreeSelection(Long id,boolean remainsPublished){if(!remainsPublished&&subscriptionPlan.isSelectedFree(SubscriptionPlanService.OPPORTUNITY,id))throw new IllegalArgumentException("استبدل هذه الفرصة من اختيارات العضوية المجانية قبل إلغاء نشرها أو حذفها");}
}
