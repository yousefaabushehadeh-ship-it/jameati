package com.jameati.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.opportunity.OpportunityService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OfficialOpportunityImportServiceTest {
  private static final int OFFICIAL_OPPORTUNITY_COUNT = 12_550;

  @Autowired private OfficialOpportunityImportService service;
  @Autowired private OpportunityRepository opportunities;
  @Autowired private OpportunityService publicOpportunities;

  @Test
  void importsTheOfficialCatalogOnceAndSkipsAnIdenticalSecondRun() {
    var preview = service.preview();

    assertThat(preview.total()).isEqualTo(OFFICIAL_OPPORTUNITY_COUNT);
    assertThat(preview.createCount()).isEqualTo(OFFICIAL_OPPORTUNITY_COUNT);
    assertThat(preview.updateCount()).isZero();
    assertThat(preview.skipCount()).isZero();

    var firstRun = service.run();

    assertThat(firstRun.totalRows()).isEqualTo(OFFICIAL_OPPORTUNITY_COUNT);
    assertThat(firstRun.createdRows()).isEqualTo(OFFICIAL_OPPORTUNITY_COUNT);
    assertThat(firstRun.updatedRows()).isZero();
    assertThat(firstRun.skippedRows()).isZero();
    assertThat(firstRun.failedRows()).isZero();
    assertThat(firstRun.status()).isEqualTo(ImportStatus.COMPLETED);

    var homepage = publicOpportunities.homepage(4, null);
    assertThat(homepage.getTotalElements()).isEqualTo(OFFICIAL_OPPORTUNITY_COUNT);
    assertThat(homepage.getContent()).hasSize(4);

    var secondRun = service.run();

    assertThat(secondRun.totalRows()).isEqualTo(OFFICIAL_OPPORTUNITY_COUNT);
    assertThat(secondRun.createdRows()).isZero();
    assertThat(secondRun.updatedRows()).isZero();
    assertThat(secondRun.skippedRows()).isEqualTo(OFFICIAL_OPPORTUNITY_COUNT);
    assertThat(secondRun.failedRows()).isZero();
    assertThat(
            opportunities.findBySourceNameAndSourceExternalKey(
                "Chevening", "chevening-scholarships-2027-28"))
        .isPresent();
  }

  @Test
  void reportsConflictsWithoutTakingOverManualRecordsAndPersistsTheFailedRun() {
    var manual = new Opportunity();
    manual.setSlug("chevening-scholarships-2027-2028");
    manual.setTitle("سجل يدوي");
    manual.setShortDescription("سجل يدوي لا ينبغي أن يستولي عليه المستورد الرسمي.");
    manual.setDescription("محتوى السجل اليدوي");
    manual.setCategory("منح دراسية");
    manual.setOrganization("مؤسسة يدوية");
    manual.setCountry("فلسطين");
    manual.setFunding(FundingType.FULLY_FUNDED);
    manual.setPublished(false);
    opportunities.saveAndFlush(manual);

    var preview = service.preview();

    assertThat(preview.conflictCount()).isEqualTo(1);
    assertThat(preview.records())
        .filteredOn(row -> row.externalKey().equals("chevening-scholarships-2027-28"))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.action()).isEqualTo(OfficialOpportunityDtos.ImportAction.CONFLICT);
              assertThat(row.conflictReason()).contains("الرابط المختصر");
            });

    assertThatThrownBy(service::run).isInstanceOf(IllegalStateException.class);

    var latest =
        service
            .history(
                PageRequest.of(
                    0, 1, Sort.by(Sort.Direction.DESC, "createdAt")))
            .getContent()
            .getFirst();
    assertThat(latest.status()).isEqualTo(ImportStatus.FAILED);
    assertThat(latest.errorMessage()).contains("تعارضت الدفعة");
    assertThat(manual.getSourceExternalKey()).isNull();
  }
}
