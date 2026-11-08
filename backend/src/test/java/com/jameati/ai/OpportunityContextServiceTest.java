package com.jameati.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jameati.catalog.*;
import com.jameati.opportunity.*;
import com.jameati.subscription.ContentAccessService;
import com.jameati.common.NotFoundException;
import java.util.*;
import org.junit.jupiter.api.Test;

class OpportunityContextServiceTest {
  final OpportunityRepository opportunities=mock(OpportunityRepository.class);
  final CatalogRepository catalog=mock(CatalogRepository.class);
  final ContentAccessService access=mock(ContentAccessService.class);
  final OpportunityContextService service=new OpportunityContextService(opportunities,catalog,access);
  @Test void scholarshipUsesOriginalPageInsteadOfLocalDescription() {
    var o=new Opportunity(); o.setId(1L); o.setPublished(true); o.setTitle("منحة");
    o.setDescription("<p>الوثائق: شهادة &amp; كشف علامات</p><script>steal()</script>"); o.setSourceHash("internal-hash");
    o.setVerifiedBy("private-staff"); o.setFunding(FundingType.FULLY_FUNDED);
    o.setOfficialUrl("https://source.example/listing");
    when(opportunities.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(o));
    var context=service.load(1,false,null);
    assertThat(context.fields()).containsOnlyKeys("sourceUrl")
        .containsEntry("sourceUrl","https://source.example/listing");
    assertThat(context.fields().toString()).doesNotContain("internal-hash","private-staff","شهادة","FULLY_FUNDED");
    verifyNoInteractions(catalog);
  }
  @Test void jobContextSelectsExactListingNotAggregatorOrLocalContent() {
    var j=new CatalogItem(); j.setId(1L); j.setType(CatalogType.JOB); j.setTitle("Engineer"); j.setContent("Requires Java");
    j.setAttributes(Map.of("salary","1000 JOD","remote",true,"internalNote","secret",
        "officialUrl","https://jobs.example/listing","sourceUrl","https://jobs.example/api"));
    when(catalog.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(j));
    var context=service.load(1,true,null);
    assertThat(context.fields()).containsOnlyKeys("sourceUrl")
        .containsEntry("sourceUrl","https://jobs.example/listing");
    assertThat(context.fields().toString()).doesNotContain("1000 JOD","Requires Java","secret");
    verifyNoInteractions(opportunities);
  }
  @Test void rejectsLockedOpportunityBeforeExposingContext() {
    var o=new Opportunity(); o.setPublished(true);
    when(opportunities.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(o));
    when(access.isOpportunityLocked(null,1L)).thenReturn(true);
    assertThatThrownBy(()->service.load(1,false,null)).isInstanceOf(AiException.class).hasMessage("access_locked");
  }
  @Test void rejectsUnpublishedAndNonJobRecords() {
    var o=new Opportunity();
    when(opportunities.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(o));
    assertThatThrownBy(()->service.load(1,false,null)).isInstanceOf(NotFoundException.class);
    var j=new CatalogItem(); j.setType(CatalogType.COURSE);
    when(catalog.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(j));
    assertThatThrownBy(()->service.load(1,true,null)).isInstanceOf(NotFoundException.class);
  }
  @Test void publicJobPortalFollowsExistingAccessException() {
    var j=new CatalogItem(); j.setType(CatalogType.JOB); j.setAttributes(Map.of("publicSourceLink",true));
    when(catalog.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(j));
    service.load(1,true,null); verifyNoInteractions(access);
  }
  @Test void largeContextIsBoundedAndTruncationExplicit() {
    var fields=new LinkedHashMap<String,String>();
    OpportunityContextService.put(fields,"description","start "+"x".repeat(100000)+" deadline 2027");
    OpportunityContextService.put(fields,"salary",null);
    assertThat(fields.toString().length()).isLessThan(16000);
    assertThat(fields).containsEntry("contextTruncated","true").doesNotContainKey("salary");
    assertThat(fields.get("description")).startsWith("start").endsWith("deadline 2027");
  }
}
