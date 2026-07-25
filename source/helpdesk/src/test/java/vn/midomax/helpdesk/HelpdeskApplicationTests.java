package vn.midomax.helpdesk;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class HelpdeskApplicationTests {

	@Autowired
	private TicketService ticketService;

	@Test
	void contextLoads() {
	}

	@Test
	void testPastEstimatedCompletionTimeThrowsException() {
		Ticket ticket = new Ticket();
		ticket.setTitle("Test Ticket");
		ticket.setDescription("Test Description");
		ticket.setCategory("network");
		ticket.setLocation("Hà Nội");
		ticket.setReporterName("User");
		ticket.setEstimatedCompletionTime(LocalDateTime.now().minusHours(1));

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ticketService.createTicket(ticket);
		});
	}

}
