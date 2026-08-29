package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop20ByRecipientInOrderByCreatedAtDesc(List<String> recipients);
    long countByRecipientInAndReadStatusFalse(List<String> recipients);
    List<Notification> findByRecipientInAndReadStatusFalse(List<String> recipients);
}
