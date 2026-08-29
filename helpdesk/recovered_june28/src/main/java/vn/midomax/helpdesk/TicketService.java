package vn.midomax.helpdesk;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Map;

public interface TicketService {

    Page<Ticket> getTickets(String tab, String search, int page);

    Ticket getTicketById(Long id);

    Ticket createTicket(Ticket ticket);

    Ticket updateTicket(Long id, Ticket updatedTicket);

    Ticket assignTicket(Long id, String assignee);

    void deleteTicket(Long id);

    // Get statistics for the dashboard/ticket widgets
    Map<String, Long> getStatistics();

    // Count tickets for a specific reporter filtered by status
    long countTicketsByReporterAndStatus(String reporterName, String status);

    // Count all tickets for a specific reporter
    long countTicketsByReporter(String reporterName);

    Page<Ticket> getTicketsForUser(String username, String tab, String search, int page);

    Map<String, Long> getStatisticsForUser(String username);
}
