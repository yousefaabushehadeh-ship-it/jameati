package com.jameati.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds both known-length and chunked JSON before deserialization. Uses the socket peer,
 * never client-supplied forwarding headers. Configure trusted proxy handling at deployment. */
@Component
public class AiRequestFilter extends OncePerRequestFilter {
  private final AiRateLimiter limiter;
  private final ObjectMapper mapper;
  public AiRequestFilter(AiRateLimiter limiter,ObjectMapper mapper) { this.limiter=limiter; this.mapper=mapper; }
  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod()) || !request.getServletPath().matches(
        "/api/v1/public/(opportunities|catalog/JOB)/[^/]+/ai/chat");
  }
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
    response.setHeader("Cache-Control","no-store");
    try {
      limiter.consume("ip:"+request.getRemoteAddr(),false);
      if (request.getContentLengthLong()>65536) throw new AiException(413,"body_limit");
      byte[] body=request.getInputStream().readNBytes(65537);
      if (body.length>65536) throw new AiException(413,"body_limit");
      chain.doFilter(new HttpServletRequestWrapper(request) {
        @Override public ServletInputStream getInputStream() {
          var input=new ByteArrayInputStream(body);
          return new ServletInputStream() {
            public int read() { return input.read(); }
            public boolean isFinished() { return input.available()==0; }
            public boolean isReady() { return true; }
            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
          };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
      },response);
    } catch (AiException error) {
      response.setStatus(error.status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
      if(error.retryAfter>0) response.setHeader("Retry-After",Long.toString(error.retryAfter));
      mapper.writeValue(response.getOutputStream(),ApiResponse.error(error.status==429
          ? "وصلت إلى حد الرسائل المسموح، يرجى المحاولة لاحقًا." : "حجم الطلب أكبر من المسموح."));
    }
  }
}
