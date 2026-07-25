package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CustomOidcUserService extends OidcUserService {

    @Autowired
    private AppUserRepository appUserRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        
        String email = oidcUser.getEmail();
        if (!StringUtils.hasText(email)) {
            email = (String) oidcUser.getAttributes().get("preferred_username");
        }
        
        if (StringUtils.hasText(email)) {
            String cleanEmail = email.trim().toLowerCase();
            String cleanPrefix = cleanEmail.split("@")[0];
            
            AppUser user = null;
            for (AppUser u : appUserRepository.findAll()) {
                if (u.getEmail() != null) {
                    String dbEmail = u.getEmail().trim().toLowerCase();
                    if (dbEmail.equals(cleanEmail) || dbEmail.split("@")[0].equals(cleanPrefix)) {
                        user = u;
                        break;
                    }
                }
            }
            if (user != null) {
                List<GrantedAuthority> mappedAuthorities = UserAuthorityMapper.authoritiesOf(user);

                String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName();
                if (!StringUtils.hasText(userNameAttributeName)) {
                    userNameAttributeName = "name";
                }
                
                return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), userNameAttributeName);
            }
        }
        
        return oidcUser;
    }
}
