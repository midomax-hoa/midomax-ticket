package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AzureUserSyncService {

    @Value("${spring.security.oauth2.client.registration.microsoft.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-secret}")
    private String clientSecret;

    @Value("${azure.graph.tenant-id:}")
    private String tenantId;

    public String getAppAccessToken() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("scope", "https://graph.microsoft.com/.default");
        body.add("client_secret", clientSecret);
        body.add("grant_type", "client_credentials");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getBody() != null && response.getBody().containsKey("access_token")) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Map<String, String>> fetchUsersFromMicrosoft365() {
        String accessToken = getAppAccessToken();
        if (accessToken == null) {
            System.out.println("Cannot obtain App Access Token for Microsoft Graph.");
            return new ArrayList<>();
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = "https://graph.microsoft.com/v1.0/users?$select=displayName,mail,userPrincipalName,department&$top=999";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("value")) {
                List<Map<String, Object>> users = (List<Map<String, Object>>) body.get("value");
                List<Map<String, String>> resultList = new ArrayList<>();

                for (Map<String, Object> user : users) {
                    Map<String, String> userMap = new HashMap<>();
                    userMap.put("fullName", user.get("displayName") != null ? user.get("displayName").toString() : "");
                    
                    String email = "";
                    if (user.get("mail") != null && !user.get("mail").toString().trim().isEmpty()) {
                        email = user.get("mail").toString().trim();
                    } else if (user.get("userPrincipalName") != null && !user.get("userPrincipalName").toString().trim().isEmpty()) {
                        email = user.get("userPrincipalName").toString().trim();
                    }
                    userMap.put("email", email);
                    
                    userMap.put("department", user.get("department") != null ? user.get("department").toString() : "");
                    
                    if (!userMap.get("email").isEmpty()) {
                        resultList.add(userMap);
                    }
                }
                return resultList;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return new ArrayList<>();
    }
}

