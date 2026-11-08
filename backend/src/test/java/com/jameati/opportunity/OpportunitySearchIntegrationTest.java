package com.jameati.opportunity;

import static org.assertj.core.api.Assertions.assertThat;
import com.jameati.opportunity.OpportunityDtos.Filter;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OpportunitySearchIntegrationTest {
  @Autowired OpportunityRepository opportunities;

  private Opportunity save(String slug, String category, String country, FundingType funding, String degree, String speciality) {
    var o = new Opportunity();
    o.setSlug("search-" + slug);
    o.setTitle(slug);
    o.setShortDescription("opportunity");
    o.setDescription("details");
    o.setCategory(category);
    o.setOrganization("test organization");
    o.setCountry(country);
    o.setFunding(funding);
    o.setDegreeLevels(Set.of(degree));
    o.setSpecialities(Set.of(speciality));
    o.setPublished(true);
    return opportunities.save(o);
  }
  private org.springframework.data.domain.Page<Opportunity> search(String q, String country) {
    return opportunities.findAll(OpportunitySpecifications.publicFilter(new Filter(q,null,country,null,null,null,null,null),14),
        PageRequest.of(0,10,Sort.by("createdAt").descending()));
  }
  @Test void interpretsArabicCountryFundingAndCategoryInDatabase() {
    save("germany-scholarship","منح دراسية","ألمانيا",FundingType.FULLY_FUNDED,"ماجستير","الهندسة");
    save("jordan-scholarship","منح دراسية","الأردن",FundingType.FULLY_FUNDED,"بكالوريوس","الطب");
    save("germany-training","تدريب","ألمانيا",FundingType.FULLY_FUNDED,"ماجستير","الهندسة");
    opportunities.flush();
    assertThat(search("منحة ممولة بالكامل في المانيا",null).getContent()).extracting(Opportunity::getSlug)
        .contains("search-germany-scholarship").doesNotContain("search-jordan-scholarship","search-germany-training");
    assertThat(search("study in Jordan",null).getContent()).extracting(Opportunity::getSlug)
        .contains("search-jordan-scholarship").doesNotContain("search-germany-scholarship");
  }
  @Test void appliesFieldAndRemainingKeywordsInDatabase() {
    save("software-training","تدريب","الإمارات",FundingType.PAID,"بكالوريوس","البرمجة");
    save("medical-training","تدريب","الإمارات",FundingType.PAID,"بكالوريوس","الطب");
    opportunities.flush();
    assertThat(search("software internships UAE",null).getContent()).extracting(Opportunity::getSlug)
        .contains("search-software-training").doesNotContain("search-medical-training");
    assertThat(search("تدريب برمجة في الإمارات",null).getContent()).extracting(Opportunity::getSlug)
        .contains("search-software-training").doesNotContain("search-medical-training");
    assertThat(search("cybersecurity internships UAE",null).getContent()).extracting(Opportunity::getSlug)
        .doesNotContain("search-medical-training", "search-software-training");
  }
  @Test void explicitCountryOverridesInferredCountryAndPaginationRemainsInDatabase() {
    save("jordan-scholarship","منح دراسية","الأردن",FundingType.FULLY_FUNDED,"ماجستير","الهندسة");
    save("turkey-scholarship","منح دراسية","تركيا",FundingType.FULLY_FUNDED,"ماجستير","الهندسة");
    opportunities.flush();
    var page=opportunities.findAll(OpportunitySpecifications.publicFilter(new Filter("منح في تركيا",null,"الأردن",null,null,null,null,null),14),PageRequest.of(0,1));
    assertThat(page.getContent()).extracting(Opportunity::getSlug).contains("search-jordan-scholarship").doesNotContain("search-turkey-scholarship");
    assertThat(page.getSize()).isEqualTo(1);
  }
}
