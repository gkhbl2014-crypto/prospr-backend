/*
 * package com.prospr.app.service;
 * 
 * import java.time.Instant; import java.util.ArrayList; import
 * java.util.HashMap; import java.util.List; import java.util.Map;
 * 
 * import org.springframework.beans.factory.annotation.Autowired; import
 * org.springframework.beans.factory.annotation.Value; import
 * org.springframework.http.HttpEntity; import
 * org.springframework.http.HttpHeaders; import
 * org.springframework.http.HttpMethod; import
 * org.springframework.http.MediaType; import
 * org.springframework.http.ResponseEntity; import
 * org.springframework.stereotype.Service; import
 * org.springframework.web.client.RestTemplate;
 * 
 * import com.prospr.app.dto.ConsentResponse;
 * 
 * @Service public class SetuConsentService {
 * 
 * @Value("${setu.base-url}") private String baseUrl;
 * 
 * @Value("${setu.client-id}") private String clientId;
 * 
 * @Value("${setu.client-secret}") private String clientSecret;
 * 
 * @Value("${setu.product-instance-id}") private String productId;
 * 
 * @Autowired RestTemplate restTemplate;
 * 
 * public ConsentResponse createConsent(String mobile){
 * 
 * HttpHeaders headers = new HttpHeaders();
 * 
 * headers.setBasicAuth(clientId, clientSecret);
 * 
 * headers.setContentType(MediaType.APPLICATION_JSON);
 * 
 * headers.set("x-product-instance-id", productId);
 * 
 * Map<String,Object> body = new HashMap<>();
 * 
 * body.put("vua", mobile + "@onemoney");
 * 
 * Map<String,String> duration = new HashMap<>();
 * 
 * duration.put("unit","MONTH");
 * 
 * duration.put("value","4");
 * 
 * body.put("consentDuration",duration);
 * 
 * Map<String,String> range = new HashMap<>();
 * 
 * range.put("from","2024-01-01T00:00:00Z");
 * 
 * range.put("to",Instant.now().toString());
 * 
 * body.put("dataRange",range);
 * 
 * body.put("context",new ArrayList<>());
 * 
 * Map<String,Object> params=new HashMap<>();
 * 
 * params.put("tags", List.of("PROSPR"));
 * 
 * body.put("additionalParams",params);
 * 
 * String token = webClient.post()
 * .uri("https://orgservice-prod.setu.co/v1/users/login") .header("client",
 * "bridge") .bodyValue(requestBody) .retrieve() .bodyToMono(JsonNode.class)
 * .map(json -> json.get("access_token").asText()) .block();
 * 
 * }
 * 
 * }
 */