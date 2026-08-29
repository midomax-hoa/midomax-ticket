package vn.midomax.helpdesk;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AzureUserSyncService {

    public List<Map<String, String>> fetchUsersFromMicrosoft365(String accessToken) {
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
                    if (user.get("mail") != null) {
                        email = user.get("mail").toString();
                    } else if (user.get("userPrincipalName") != null) {
                        email = user.get("userPrincipalName").toString();
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

