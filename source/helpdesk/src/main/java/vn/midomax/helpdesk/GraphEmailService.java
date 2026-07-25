package vn.midomax.helpdesk;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class GraphEmailService {

    private static final Logger logger = LoggerFactory.getLogger(GraphEmailService.class);

    @Value("${azure.graph.client-id}")
    private String clientId;

    @Value("${azure.graph.tenant-id}")
    private String tenantId;

    @Value("${azure.graph.client-secret}")
    private String clientSecret;

    @Value("${azure.graph.sender-email:it01@midomax.vn}")
    private String senderEmail;

    private GraphServiceClient graphClient;

    @PostConstruct
    public void init() {
        try {
            // 1. Khởi tạo Credential từ thông số Azure App Registration
            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .clientId(clientId)
                    .tenantId(tenantId)
                    .clientSecret(clientSecret)
                    .build();

            // 2. Scope mặc định cho Application Permission trên Microsoft Graph
            String[] scopes = new String[] { "https://graph.microsoft.com/.default" };

            // 3. Khởi tạo GraphServiceClient
            this.graphClient = new GraphServiceClient(credential, scopes);
            logger.info("[GRAPH EMAIL CONFIG] Microsoft GraphServiceClient initialized successfully for sender: {}", senderEmail);
        } catch (Exception e) {
            logger.error("[GRAPH EMAIL CONFIG ERROR] Failed to initialize GraphServiceClient: {}", e.getMessage(), e);
        }
    }

    /**
     * Gửi email định dạng HTML qua Microsoft Graph API
     *
     * @param toEmail Địa chỉ email người nhận
     * @param subject Tiêu đề email
     * @param htmlBody Nội dung email (HTML)
     */
    public void sendEmail(String toEmail, String subject, String htmlBody) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            logger.warn("[GRAPH EMAIL] Recipient email is empty. Skip sending.");
            return;
        }

        if (graphClient == null) {
            logger.error("[GRAPH EMAIL ERROR] GraphServiceClient is not initialized. Cannot send email to {}", toEmail);
            return;
        }

        logger.info("\n================================================================================");
        logger.info(" [MIDOMAX GRAPH EMAIL SYSTEM] -> PREPARING TO SEND EMAIL");
        logger.info(" From (Mailbox): {}", senderEmail);
        logger.info(" To: {}", toEmail);
        logger.info(" Subject: {}", subject);
        logger.info(" Protocol: Microsoft Graph API (OAuth2 Client Credentials)");
        logger.info("================================================================================\n");

        try {
            // 1. Tạo đối tượng Message
            Message message = new Message();
            message.setSubject(subject);

            // 2. Thiết lập nội dung HTML
            ItemBody body = new ItemBody();
            body.setContentType(BodyType.Html);
            body.setContent(htmlBody);
            message.setBody(body);

            // 3. Thiết lập danh sách người nhận (To)
            LinkedList<Recipient> toRecipientsList = new LinkedList<>();
            Recipient recipient = new Recipient();
            EmailAddress emailAddress = new EmailAddress();
            emailAddress.setAddress(toEmail.trim());
            recipient.setEmailAddress(emailAddress);
            toRecipientsList.add(recipient);
            message.setToRecipients(toRecipientsList);

            // 4. Đóng gói vào Request Body & yêu cầu lưu vào Hộp thư đi (Sent Items)
            SendMailPostRequestBody sendMailPostRequestBody = new SendMailPostRequestBody();
            sendMailPostRequestBody.setMessage(message);
            sendMailPostRequestBody.setSaveToSentItems(true);

            // 5. Gọi Graph API phát lệnh gửi mail từ mailbox của senderEmail
            graphClient.users().byUserId(senderEmail).sendMail().post(sendMailPostRequestBody);

            logger.info("[GRAPH EMAIL SENT SUCCESS] Successfully sent HTML email via Microsoft Graph API to {}", toEmail);
        } catch (Exception e) {
            logger.error("[GRAPH EMAIL ERROR] Failed to send email via Microsoft Graph API to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
