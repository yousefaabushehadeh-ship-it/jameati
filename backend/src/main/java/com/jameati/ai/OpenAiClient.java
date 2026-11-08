package com.jameati.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiClient {
  private final OpenAiConfig config;
  private final ObjectMapper mapper;
  private final RestClient http;
  @org.springframework.beans.factory.annotation.Autowired
  public OpenAiClient(OpenAiConfig config,ObjectMapper mapper) {
    this(config,mapper,restClient(config));
  }
  OpenAiClient(OpenAiConfig config,ObjectMapper mapper,RestClient http) {
    this.config=config; this.mapper=mapper; this.http=http;
  }
  private static RestClient restClient(OpenAiConfig config) {
    var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    return RestClient.builder().baseUrl("https://api.openai.com/v1").requestFactory(factory).build();
  }
  record Result(String disposition,String answer,List<String> evidence,int inputTokens,int outputTokens) {}
  record ExplanationResult(String summary,List<String> advantages,List<String> warnings) {}

  ExplanationResult explain(Map<String,Object> opportunityFacts,Map<String,Object> profileContext,
      int matchScore,List<Map<String,Object>> matchFactors) {
    if (config.getApiKey()==null || config.getApiKey().isBlank()) throw new AiException(503,"not_configured");
    try {
      var schema=Map.of("type","object","additionalProperties",false,
          "properties",Map.of(
              "summary",Map.of("type","string"),
              "advantages",Map.of("type","array","items",Map.of("type","string"),"maxItems",3),
              "warnings",Map.of("type","array","items",Map.of("type","string"),"maxItems",3)),
          "required",List.of("summary","advantages","warnings"));
      var input=new LinkedHashMap<String,Object>();
      input.put("opportunityFacts",opportunityFacts);
      input.put("minimumProfileContext",profileContext);
      input.put("deterministicMatchScore",matchScore);
      input.put("deterministicMatchFactors",matchFactors);
      var instructions="""
          أنت تشرح بالعربية سبب ملاءمة فرصة اختارها ورتبها نظام حتمي مسبقًا.
          لا تقرر الأهلية ولا تعيد الترتيب أو حساب النتيجة. استخدم حصراً الحقائق المرسلة.
          لا تخترع شروطاً أو ضمانات أو معلومات ناقصة، ولا تقل إن المستخدم مؤهل إلا إذا كانت
          الحقيقة مدعومة صراحة. اجعل الملخص قصيراً، والمزايا محددة، والتحذيرات فقط من حقائق
          الفرصة المرسلة أو عوامل المطابقة غير المتحققة. أعد JSON مطابقاً للمخطط فقط.
          """;
      var body=Map.of("model",config.getModel()==null || config.getModel().isBlank()?"gpt-4.1-mini":config.getModel(),
          "store",false,"max_output_tokens",Math.min(config.getMaxOutputTokens(),500),
          "instructions",instructions,"input",List.of(Map.of("role","user","content",mapper.writeValueAsString(input))),
          "text",Map.of("format",Map.of("type","json_schema","name","recommendation_explanation","strict",true,"schema",schema)));
      JsonNode response=http.post().uri("/responses").header("Authorization","Bearer "+config.getApiKey())
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
      if (response==null || !"completed".equals(response.path("status").asText())) throw new AiException(503,"incomplete_response");
      String text=null;
      for (JsonNode output:response.path("output")) if ("message".equals(output.path("type").asText()))
        for (JsonNode content:output.path("content")) {
          if ("refusal".equals(content.path("type").asText())) throw new AiException(503,"provider_refusal");
          if ("output_text".equals(content.path("type").asText())) text=content.path("text").asText();
        }
      if (text==null || text.length()>4000) throw new AiException(503,"invalid_response");
      JsonNode value=mapper.readTree(text);
      return new ExplanationResult(requiredText(value,"summary",600),texts(value,"advantages"),texts(value,"warnings"));
    } catch (AiException error) { throw error; }
    catch (RestClientResponseException error) { throw new AiException(503,"provider_http_"+error.getStatusCode().value()); }
    catch (Exception error) { throw new AiException(503,"provider_"+error.getClass().getSimpleName()); }
  }

  private static String requiredText(JsonNode value,String field,int maxLength) {
    var text=value.path(field).asText("").strip();
    if (text.isBlank() || text.length()>maxLength) throw new AiException(503,"invalid_response");
    return text;
  }

  private static List<String> texts(JsonNode value,String field) {
    if (!value.path(field).isArray()) throw new AiException(503,"invalid_response");
    var result=new ArrayList<String>();
    for (var item:value.path(field)) {
      if (!item.isTextual() || item.asText().isBlank() || item.asText().length()>300 || result.size()>=3)
        throw new AiException(503,"invalid_response");
      result.add(item.asText().strip());
    }
    return List.copyOf(result);
  }
  Result respond(OpportunityContextService.Context context,AiChatRequest request) {
    return respond(context,request,false);
  }
  Result respond(OpportunityContextService.Context context,AiChatRequest request,boolean quoteRetry) {
    if (config.getApiKey()==null || config.getApiKey().isBlank()) throw new AiException(503,"not_configured");
    try {
      var schema=Map.of("type","object","additionalProperties",false,
          "properties",Map.of("disposition",Map.of("type","string","enum",List.of("ANSWER","MISSING","CLARIFY","OUT_OF_SCOPE","INTERNAL")),
              "answer",Map.of("type","string"),"evidence",Map.of("type","array","items",Map.of("type","string"))),
          "required",List.of("disposition","answer","evidence"));
      String input=mapper.writeValueAsString(Map.of("context",context.fields(),"history",request.history(),"currentQuestion",request.message()));
      var body=Map.of("model",config.getModel()==null || config.getModel().isBlank()?"gpt-4.1-mini":config.getModel(),
          "store",false,"max_output_tokens",config.getMaxOutputTokens(),"instructions",AiPolicy.SYSTEM
              +(quoteRetry ? "\nYour previous answer could not be verified because its evidence was not an exact excerpt. "
                  +"Re-read the sourceText. Use 1-3 short quotes copied from sourceText with no translation or paraphrase. "
                  +"If you cannot find supporting quotes, classify MISSING and say what cannot be confirmed." : ""),
          "input",List.of(Map.of("role","user","content",input)),
          "text",Map.of("format",Map.of("type","json_schema","name","opportunity_answer","strict",true,"schema",schema)));
      JsonNode response=http.post().uri("/responses").header("Authorization","Bearer "+config.getApiKey())
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
      if (response==null || !"completed".equals(response.path("status").asText())) throw new AiException(503,"incomplete_response");
      String text=null;
      for (JsonNode output:response.path("output")) if ("message".equals(output.path("type").asText()))
        for (JsonNode content:output.path("content")) {
          if ("refusal".equals(content.path("type").asText())) return new Result("OUT_OF_SCOPE","",List.of(),0,0);
          if ("output_text".equals(content.path("type").asText())) text=content.path("text").asText();
        }
      if (text==null || text.length()>16000) throw new AiException(503,"invalid_response");
      JsonNode answer=mapper.readTree(text);
      if (!answer.path("disposition").isTextual() || !answer.path("answer").isTextual() || !answer.path("evidence").isArray())
        throw new AiException(503,"invalid_response");
      var evidence=new ArrayList<String>();
      for (var e:answer.path("evidence")) {
        if (!e.isTextual() || e.asText().isBlank() || evidence.size()>=20) throw new AiException(503,"invalid_evidence");
        evidence.add(e.asText());
      }
      return new Result(answer.path("disposition").asText(),answer.path("answer").asText(),evidence,
          response.path("usage").path("input_tokens").asInt(),response.path("usage").path("output_tokens").asInt());
    } catch (AiException e) { throw e; }
    catch (RestClientResponseException e) { throw new AiException(503,"provider_http_"+e.getStatusCode().value()); }
    catch (Exception e) { throw new AiException(503,"provider_"+e.getClass().getSimpleName()); }
  }
}
