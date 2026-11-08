package com.jameati.opportunity;

import static com.jameati.opportunity.OpportunityDtos.*;

import com.jameati.admin.AdminAuthorization;
import com.jameati.common.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpportunityAdminPolicyService {
  private final OpportunityService service;
  private final OpportunityRepository repository;

  public OpportunityAdminPolicyService(OpportunityService service, OpportunityRepository repository) {
    this.service = service;
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public OpportunityResponse detail(Long id) {
    return service.response(repository.findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new NotFoundException("الفرصة غير موجودة")));
  }

  @Transactional
  public OpportunityResponse create(UpsertRequest request, Authentication authentication) {
    if (AdminAuthorization.isDataEntryOnly(authentication)) request = asDraft(request);
    return service.create(request);
  }

  @Transactional
  public OpportunityResponse update(Long id, UpsertRequest request, Authentication authentication) {
    if (AdminAuthorization.isDataEntryOnly(authentication)) {
      var current = repository.findByIdAndDeletedFalse(id)
          .orElseThrow(() -> new NotFoundException("الفرصة غير موجودة"));
      if (current.isPublished()) {
        throw new AccessDeniedException("لا يستطيع مدخل البيانات تعديل فرصة منشورة");
      }
    }
    if (AdminAuthorization.isDataEntryOnly(authentication)) request = asDraft(request);
    return service.update(id, request);
  }

  private static UpsertRequest asDraft(UpsertRequest request) {
    return new UpsertRequest(request.slug(), request.title(), request.shortDescription(),
        request.description(), request.category(), request.organization(), request.country(),
        request.funding(), request.deadline(), request.deadlineText(), null, request.alwaysOpen(),
        false, false, request.coverImage(), request.officialUrl(), request.applyUrl(),
        request.serviceEnabled(), request.servicePrice(), request.serviceCurrency(),
        request.sourceName(), request.sourceUrl(), request.specialities(), request.nationalities(),
        request.degreeLevels());
  }
}
