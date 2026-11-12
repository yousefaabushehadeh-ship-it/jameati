package com.jameati.opportunity;

import static com.jameati.opportunity.OpportunityDtos.*;
import org.mapstruct.*;

@Mapper(componentModel="spring")
public interface OpportunityMapper {
  @Mapping(target="status",ignore=true) @Mapping(target="daysRemaining",ignore=true) @Mapping(target="accessLocked",ignore=true) OpportunityResponse toResponse(Opportunity entity);
  @Mapping(target="requiredDocuments",ignore=true) @Mapping(target="requirements",ignore=true) @Mapping(target="id",ignore=true) @Mapping(target="createdAt",ignore=true) @Mapping(target="updatedAt",ignore=true) @Mapping(target="deleted",ignore=true) @Mapping(target="version",ignore=true) @Mapping(target="views",ignore=true) @Mapping(target="applyClicks",ignore=true) @Mapping(target="lastVerifiedAt",ignore=true) @Mapping(target="lastImportedAt",ignore=true) @Mapping(target="sourceExternalKey",ignore=true) @Mapping(target="sourceHash",ignore=true) @Mapping(target="verifiedBy",ignore=true) @Mapping(target="arabicTitle",ignore=true) Opportunity toEntity(UpsertRequest request);
  @BeanMapping(nullValuePropertyMappingStrategy=NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target="requiredDocuments",ignore=true) @Mapping(target="requirements",ignore=true) @Mapping(target="id",ignore=true) @Mapping(target="createdAt",ignore=true) @Mapping(target="updatedAt",ignore=true) @Mapping(target="deleted",ignore=true) @Mapping(target="version",ignore=true) @Mapping(target="views",ignore=true) @Mapping(target="applyClicks",ignore=true) @Mapping(target="lastVerifiedAt",ignore=true) @Mapping(target="lastImportedAt",ignore=true) @Mapping(target="sourceExternalKey",ignore=true) @Mapping(target="sourceHash",ignore=true) @Mapping(target="verifiedBy",ignore=true) @Mapping(target="arabicTitle",ignore=true) void update(UpsertRequest request,@MappingTarget Opportunity entity);
}
