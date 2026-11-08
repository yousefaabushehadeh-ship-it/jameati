package com.jameati.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

@Service
public class OfficialSourcePageService {
  private static final int MAX_BYTES = 2_000_000;
  private final HttpClient http;
  private final ObjectMapper mapper;

  @org.springframework.beans.factory.annotation.Autowired
  public OfficialSourcePageService(ObjectMapper mapper) {
    this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build());
  }

  OfficialSourcePageService(ObjectMapper mapper, HttpClient http) {
    this.mapper = mapper;
    this.http = http;
  }

  public OpportunityContextService.Context load(OpportunityContextService.Context context) {
    String url = context.fields().get("sourceUrl");
    if (url == null || url.isBlank()) throw new AiException(503,"official_source_missing");
    try {
      URI uri = URI.create(url);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getUserInfo() != null || uri.getPort() != -1) throw new AiException(503,"official_source_invalid");
      // Links come from stored records, but still must not reach private services.
      for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress() || isRestricted(address))
          throw new AiException(503,"official_source_invalid");
      }
      HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
          .header("Accept","text/html,application/xhtml+xml")
          .header("User-Agent","JameatiSourceReader/1.0")
          .GET().build();
      HttpResponse<InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode()!=200 || response.headers().firstValue("Content-Type")
          .map(value -> !value.toLowerCase(Locale.ROOT).contains("html")).orElse(true))
        throw new AiException(503,"official_source_unavailable");
      SourceHtml page;
      try (InputStream input=response.body(); var output=new ByteArrayOutputStream()) {
        byte[] buffer=new byte[8192]; int count;
        boolean truncated=false;
        while ((count=input.read(buffer))!=-1) {
          int remaining=MAX_BYTES-output.size();
          if (count>remaining) {
            output.write(buffer,0,remaining);
            truncated=true;
            break;
          }
          output.write(buffer,0,count);
        }
        page=new SourceHtml(output.toByteArray(),truncated);
      }
      Document document=Jsoup.parse(new String(page.bytes(),java.nio.charset.StandardCharsets.UTF_8),url);
      String sourceText=jobPosting(document);
      document.select("script,style,noscript,iframe,nav,footer,header,form").remove();
      Element content=document.selectFirst("main,article,[role=main]");
      String visible=(content==null ? document.body() : content).text();
      if (sourceText.isBlank()) sourceText=visible;
      // Structured job details already contain the listing. Appending the whole
      // page duplicates them and can push the requirements out of the excerpt.
      if (sourceText.isBlank()) throw new AiException(503,"official_source_empty");
      var fields=new LinkedHashMap<String,String>();
      fields.put("sourceUrl",url);
      OpportunityContextService.put(fields,"sourceText",sourceText);
      if (page.truncated()) fields.put("contextTruncated","true");
      return new OpportunityContextService.Context(context.type(),Map.copyOf(fields));
    } catch (AiException error) { throw error; }
    catch (Exception error) { throw new AiException(503,"official_source_fetch_failed"); }
  }

  private record SourceHtml(byte[] bytes, boolean truncated) {}

  private String jobPosting(Document document) {
    for (Element script:document.select("script[type=application/ld+json]")) {
      try {
        JsonNode root=mapper.readTree(script.data());
        JsonNode posting=findPosting(root);
        if (posting!=null) {
          var fields=new LinkedHashMap<String,String>();
          for (String key:new String[]{"title","description","datePosted","validThrough",
              "employmentType","jobLocation","baseSalary","hiringOrganization"}) {
            JsonNode value=posting.path(key);
            if (!value.isMissingNode() && !value.isNull())
              OpportunityContextService.put(fields,key,value.isTextual() ? value.asText() : value.toString());
          }
          if (!fields.isEmpty()) {
            var text=new StringBuilder();
            fields.forEach((key,value) -> text.append(key).append(": ").append(value).append('\n'));
            return text.toString();
          }
        }
      } catch (Exception ignored) { /* Try the visible page instead. */ }
    }
    return "";
  }

  private static JsonNode findPosting(JsonNode node) {
    if (node==null) return null;
    if (node.isObject()) {
      if ("JobPosting".equals(node.path("@type").asText())) return node;
      for (JsonNode child:node) { JsonNode found=findPosting(child); if (found!=null) return found; }
    } else if (node.isArray()) {
      for (JsonNode child:node) { JsonNode found=findPosting(child); if (found!=null) return found; }
    }
    return null;
  }

  private static boolean isRestricted(InetAddress address) {
    byte[] bytes=address.getAddress();
    if (bytes.length==4) {
      int first=bytes[0]&255, second=bytes[1]&255;
      return first==0 || first>=224 || first==10 || first==127 ||
          first==169 && second==254 || first==172 && second>=16 && second<=31 ||
          first==192 && second==168 || first==100 && second>=64 && second<=127;
    }
    return bytes.length==16 && (bytes[0]&0xfe)==0xfc;
  }
}
