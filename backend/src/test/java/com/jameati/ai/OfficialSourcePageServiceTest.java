package com.jameati.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfficialSourcePageServiceTest {
  @Test void usesJobPostingFromOriginalHtmlAndIgnoresJameatiContent() throws Exception {
    var http=mock(HttpClient.class);
    @SuppressWarnings("unchecked") var response=(HttpResponse<java.io.InputStream>) mock(HttpResponse.class);
    String html="""
        <html><body><script type="application/ld+json">
        {"@context":"https://schema.org","@graph":[{"@type":"JobPosting",
        "title":"Current original title","description":"<p>Requires three years of experience</p>"}]}
        </script><main>Other page content</main></body></html>
        """;
    when(http.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    when(response.statusCode()).thenReturn(200);
    when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
        Map.of("Content-Type",java.util.List.of("text/html; charset=UTF-8")),(key,value)->true));
    when(response.body()).thenReturn(new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var source=new OfficialSourcePageService(new ObjectMapper(),http);
    var context=new OpportunityContextService.Context("JOB",Map.of(
        "sourceUrl","https://example.com/listing","content","Old local summary"));
    var loaded=source.load(context);
    assertThat(loaded.fields().get("sourceText")).contains("title: Current original title","Requires three years of experience")
        .doesNotContain("Old local summary","Other page content","<p>");
    assertThat(loaded.fields()).containsOnlyKeys("sourceUrl","sourceText");
  }

  @Test void neverFallsBackToStoredDataWhenSourceIsMissing() {
    var source=new OfficialSourcePageService(new ObjectMapper(),mock(HttpClient.class));
    assertThatThrownBy(() -> source.load(new OpportunityContextService.Context("JOB",
        Map.of("content","Old local summary"))))
        .isInstanceOf(AiException.class).hasMessage("official_source_missing");
  }

  @Test void extractsJobDetailsAfterLargePageAssets() throws Exception {
    String html="<html><head><style>"+"x".repeat(530_000)+"</style>"
        +"<script type=\"application/ld+json\">"
        +"{\"@type\":\"JobPosting\",\"title\":\"Education manager\","
        +"\"description\":\"Supports university customers\"}"
        +"</script></head><body><main>Job details</main></body></html>";
    var source=sourceFor(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
    var loaded=source.load(new OpportunityContextService.Context("JOB",
        Map.of("sourceUrl","https://example.com/listing")));

    assertThat(loaded.fields().get("sourceText"))
        .contains("Education manager","Supports university customers");
    assertThat(loaded.fields()).doesNotContainKey("contextTruncated");
  }

  @Test void keepsReadingBoundedWhenTheOfficialPageIsMuchLarger() throws Exception {
    String html="<html><head><script type=\"application/ld+json\">"
        +"{\"@type\":\"JobPosting\",\"title\":\"Remote developer\"}"
        +"</script><style>"+"x".repeat(3_000_000)+"</style></head></html>";
    var input=new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    var loaded=sourceFor(input).load(new OpportunityContextService.Context("JOB",
        Map.of("sourceUrl","https://example.com/listing")));

    assertThat(loaded.fields().get("sourceText")).contains("Remote developer");
    assertThat(loaded.fields()).containsEntry("contextTruncated","true");
    assertThat(input.available()).isGreaterThan(0);
  }

  private static OfficialSourcePageService sourceFor(InputStream input) throws Exception {
    var http=mock(HttpClient.class);
    @SuppressWarnings("unchecked") var response=(HttpResponse<InputStream>) mock(HttpResponse.class);
    when(http.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    when(response.statusCode()).thenReturn(200);
    when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
        Map.of("Content-Type",java.util.List.of("text/html; charset=UTF-8")),(key,value)->true));
    when(response.body()).thenReturn(input);
    return new OfficialSourcePageService(new ObjectMapper(),http);
  }
}
