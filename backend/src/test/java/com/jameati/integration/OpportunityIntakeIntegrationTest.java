package com.jameati.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.OpportunityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.integration.api-key=test-intake-key")
@AutoConfigureMockMvc
class OpportunityIntakeIntegrationTest {
  @Autowired MockMvc mockMvc;
  @Autowired OpportunityRepository repository;
  @Autowired CatalogRepository catalog;

  private static final String BODY = """
      {"title":"منحة اختبار الاستقبال","organization":"جهة تجريبية","deadline":"2027-03-01",
       "link":"https://example.org/intake-test","type":"scholarship","funding":"ممولة بالكامل",
       "description":"تفاصيل المنحة"}
      """;

  @Test
  void rejectsMissingOrWrongKey() throws Exception {
    mockMvc.perform(post("/api/v1/integrations/opportunities").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/integrations/opportunities").header("X-API-Key", "wrong")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void savesDraftThenDetectsDuplicate() throws Exception {
    mockMvc.perform(post("/api/v1/integrations/opportunities").header("X-API-Key", "test-intake-key")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.created").value(1))
        .andExpect(jsonPath("$.data.results[0].status").value("created"));

    var saved = repository.findAllByOfficialUrlIgnoreCase("https://example.org/intake-test");
    assertThat(saved).hasSize(1);
    var o = saved.get(0);
    assertThat(o.isPublished()).isFalse();
    assertThat(o.getCategory()).isEqualTo("منح دراسية");
    assertThat(o.getFunding()).isEqualTo(FundingType.FULLY_FUNDED);
    assertThat(o.getDeadline()).hasToString("2027-03-01");

    mockMvc.perform(post("/api/v1/integrations/opportunities").header("X-API-Key", "test-intake-key")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.duplicates").value(1));
  }

  @Test
  void rejectsItemWithoutTitle() throws Exception {
    mockMvc.perform(post("/api/v1/integrations/opportunities").header("X-API-Key", "test-intake-key")
            .contentType(MediaType.APPLICATION_JSON).content("[{\"organization\":\"x\"}]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejected").value(1));
  }

  @Test
  @WithMockUser(username = "editor@example.com", roles = "EDITOR")
  void routesJobsAndCoursesToTheirSectionsAndPublishesFromDrafts() throws Exception {
    mockMvc.perform(post("/api/v1/integrations/opportunities").header("X-API-Key", "test-intake-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                [{"title":"تدريب للنشر","organization":"جهة","type":"internship"},
                 {"title":"مطور ويب مبتدئ","organization":"شركة س","type":"job","link":"https://example.org/job-1","deadline":"2027-01-01"},
                 {"title":"دورة بايثون","organization":"أكاديمية ع","type":"دورة","category":"تقنية وبرمجة"}]
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.created").value(3))
        .andExpect(jsonPath("$.data.results[0].section").value("opportunities"))
        .andExpect(jsonPath("$.data.results[1].section").value("jobs"))
        .andExpect(jsonPath("$.data.results[2].section").value("courses"));

    var job = catalog.findAllByTypeAndWebsiteIgnoreCase(CatalogType.JOB, "https://example.org/job-1").get(0);
    assertThat(job.isPublished()).isFalse();
    var course = catalog.findAll().stream().filter(c -> c.getTitle().equals("دورة بايثون")).findFirst().orElseThrow();
    assertThat(course.getType()).isEqualTo(CatalogType.COURSE);
    assertThat(course.getCategory()).isEqualTo("تقنية وبرمجة");

    // نفس الوظيفة مرة ثانية ← مكررة
    mockMvc.perform(post("/api/v1/integrations/opportunities").header("X-API-Key", "test-intake-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"مطور ويب مبتدئ\",\"organization\":\"شركة س\",\"type\":\"job\"}"))
        .andExpect(jsonPath("$.data.duplicates").value(1));

    mockMvc.perform(get("/api/v1/admin/intake/drafts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.kind=='JOB')]").exists())
        .andExpect(jsonPath("$.data[?(@.kind=='COURSE')]").exists());

    mockMvc.perform(post("/api/v1/admin/intake/drafts/JOB/" + job.getId() + "/publish")
            .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"تقنية\"}"))
        .andExpect(status().isOk());
    var published = catalog.findById(job.getId()).orElseThrow();
    assertThat(published.isPublished()).isTrue();
    assertThat(published.getCategory()).isEqualTo("تقنية");

    var opp = repository.findAll().stream().filter(o -> o.getTitle().equals("تدريب للنشر")).findFirst().orElseThrow();
    assertThat(opp.getCategory()).isEqualTo("تدريب");
    mockMvc.perform(post("/api/v1/admin/intake/drafts/OPPORTUNITY/" + opp.getId() + "/publish")
            .contentType(MediaType.APPLICATION_JSON).content("{\"funding\":\"FREE\"}"))
        .andExpect(status().isOk());
    assertThat(repository.findById(opp.getId()).orElseThrow().isPublished()).isTrue();
  }
}
