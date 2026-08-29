package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private TicketService ticketService;

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        model.addAttribute("itStatusListGlobal", ticketService.getItWorkloadStatus());
    }
}
