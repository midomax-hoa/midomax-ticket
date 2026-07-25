package vn.midomax.helpdesk;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GraphApiService {

    private final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0";

    public String getUserEmails(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL + "/me/messages", 
                    HttpMethod.GET, 
                    entity, 
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"Could not fetch emails. Ensure Mail.Read scope is granted and token is valid.\"}";
        }
    }
}
