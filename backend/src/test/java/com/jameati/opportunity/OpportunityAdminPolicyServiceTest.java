package com.jameati.opportunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OpportunityAdminPolicyServiceTest {
  private final OpportunityService opportunities = mock(OpportunityService.class);
  private final OpportunityRepository repository = mock(OpportunityRepository.class);
  private final OpportunityAdminPolicyService service = new OpportunityAdminPolicyService(opportunities, repository);
  private final UsernamePasswordAuthenticationToken dataEntry = new UsernamePasswordAuthenticationToken(
      "entry@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_DATA_ENTRY")));

  @Test
  void dataEntryCannotModifyAnAlreadyPublishedOpportunity() {
    var existing = new Opportunity();
    existing.setPublished(true);
    when(repository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.update(7L, request(false, null), dataEntry))
        .isInstanceOf(AccessDeniedException.class);
    verify(opportunities, never()).update(anyLong(), any());
  }

  @Test
  void dataEntryPublishFieldsAreForcedBackToDraft() {
    service.create(request(true, OpportunityStatus.UPCOMING), dataEntry);
    var requestCaptor = org.mockito.ArgumentCaptor.forClass(OpportunityDtos.UpsertRequest.class);
    verify(opportunities).create(requestCaptor.capture());
    assertThat(requestCaptor.getValue().published()).isFalse();
    assertThat(requestCaptor.getValue().featured()).isFalse();
    assertThat(requestCaptor.getValue().statusOverride()).isNull();
  }

  private static OpportunityDtos.UpsertRequest request(
      boolean published, OpportunityStatus statusOverride) {
    return new OpportunityDtos.UpsertRequest(
        "draft-opportunity", "Draft", "Short", "Description", "Scholarship", "Org",
        "Palestine", FundingType.FULLY_FUNDED, null, null, statusOverride, true, true,
        published, null, "https://example.com/official", "https://example.com/apply", null,
        null, Set.of(), Set.of(), Set.of());
  }
}
