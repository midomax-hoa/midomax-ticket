package vn.midomax.helpdesk;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TicketServiceImpl implements TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    @PostConstruct
    @SuppressWarnings("null")
    public void seedData() {
        if (ticketRepository.count() == 0) {
            // Seed sample data to matches mockups in HTML templates

            // Ticket 1
            Ticket t1 = new Ticket();
            t1.setTitle("Cập phát màn hình rời");
            t1.setDescription("Cần màn hình 24 inch rời để làm việc với bảng tính Excel lớn.");
            t1.setCategory("hardware");
            t1.setReporterName("C");
            t1.setReporterDepartment("Hành chính");
            t1.setPriority("LOW");
            t1.setStatus("OPEN");
            t1.setCreatedAt(LocalDateTime.now().minusDays(1));
            t1.setSlaDeadline(LocalDateTime.now().plusDays(2));
            ticketRepository.save(t1);

            // Ticket 2
            Ticket t2 =
            switch (tab.toLowerCase()) {
                case "closed":
                case "resolved":
                    status = "RESOLVED";
                    break;
                default:
                    break;
            }
        }

        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String searchId = null;

        if (cleanSearch != null) {
            if (cleanSearch.toLowerCase().startsWith("t")) {
                try {
                    long idVal = Long.parseLong(cleanSearch.substring(1));
                    searchId = String.valueOf(idVal);
                } catch (NumberFormatException e) {}
            } else {
                try {
                    Long.parseLong(cleanSearch);
                    searchId = cleanSearch;
                } catch (NumberFormatException e) {}
            }
        }

        Pageable pageable = PageRequest.of(page, 5);
        return ticketRepository.filterAndSearchTicketsForUser(username, status, cleanSearch, searchId, pageable);
    }

    @Override
    public Map<String, Long> getStatisticsForUser(String username) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", ticketRepository.countByReporterName(username));
        stats.put("open", ticketRepository.countByReporterNameAndStatus(username, "OPEN"));
        stats.put("inProgress", ticketRepository.countByReporterNameAndStatus(username, "PROGRESS"));
        stats.put("resolved", ticketRepository.countByReporterNameAndStatus(username, "RESOLVED"));
        stats.put("mine", ticketRepository.countByReporterName(username)); // mine với user cũng chính là total của họ
        return stats;
    }
}
