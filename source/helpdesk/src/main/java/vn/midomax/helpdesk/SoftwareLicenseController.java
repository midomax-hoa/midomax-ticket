package vn.midomax.helpdesk;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * License phần mềm khác (ngoài Microsoft 365) — admin/IT tự tạo, sửa, xóa.
 * Nằm dưới /assets nên tự ăn phân hệ Công Cụ Dụng Cụ (ModuleAccessInterceptor);
 * xem thì ai có phân hệ đều xem được, sửa thì chỉ Admin/IT/Manager —
 * cùng luật với trang License 365.
 */
@Controller
@RequestMapping("/assets/software-licenses")
public class SoftwareLicenseController {

    private final SoftwareLicenseRepository repo;

    public SoftwareLicenseController(SoftwareLicenseRepository repo) {
        this.repo = repo;
    }

    private boolean canEdit(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_IT")
                        || a.getAuthority().equals("ROLE_MANAGER"));
    }

    private void assertCanEdit(Authentication auth) {
        if (!canEdit(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ IT/Admin mới sửa được license. Bạn chỉ có quyền xem.");
        }
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        List<SoftwareLicense> rows = repo.findAllByOrderByNameAsc();

        int totalPurchased = 0, totalAssigned = 0, alertCount = 0;
        for (SoftwareLicense r : rows) {
            totalPurchased += r.getPurchasedQty();
            totalAssigned += r.getAssignedQty();
            if (!"OK".equals(r.getStatus())) alertCount++;
        }

        model.addAttribute("rows", rows);
        model.addAttribute("canEdit", canEdit(authentication));
        model.addAttribute("totalPurchased", totalPurchased);
        model.addAttribute("totalAssigned", totalAssigned);
        model.addAttribute("totalRemaining", totalPurchased - totalAssigned);
        model.addAttribute("alertCount", alertCount);
        model.addAttribute("activePage", "software-licenses");
        return "software-licenses";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String name,
                       @RequestParam(required = false) String vendor,
                       @RequestParam(required = false) Integer purchasedQty,
                       @RequestParam(required = false) Integer assignedQty,
                       @RequestParam(required = false) String expiryDate,
                       @RequestParam(required = false) String note,
                       Authentication authentication,
                       RedirectAttributes ra) {
        assertCanEdit(authentication);
        if (name == null || name.isBlank()) {
            ra.addFlashAttribute("errorMessage", "Tên license không được để trống.");
            return "redirect:/assets/software-licenses";
        }
        SoftwareLicense lic = id == null ? new SoftwareLicense()
                : repo.findById(id).orElseGet(SoftwareLicense::new);
        lic.setName(name.trim());
        lic.setVendor(vendor == null || vendor.isBlank() ? null : vendor.trim());
        lic.setPurchasedQty(purchasedQty == null || purchasedQty < 0 ? 0 : purchasedQty);
        lic.setAssignedQty(assignedQty == null || assignedQty < 0 ? 0 : assignedQty);
        lic.setExpiryDate(expiryDate == null || expiryDate.isBlank() ? null : LocalDate.parse(expiryDate));
        lic.setNote(note == null || note.isBlank() ? null : note.trim());
        lic.setUpdatedAt(LocalDateTime.now());
        lic.setUpdatedBy(authentication == null ? null : authentication.getName());
        repo.save(lic);
        ra.addFlashAttribute("successMessage",
                (id == null ? "Đã tạo license " : "Đã cập nhật license ") + lic.getName() + ".");
        return "redirect:/assets/software-licenses";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, Authentication authentication, RedirectAttributes ra) {
        assertCanEdit(authentication);
        repo.findById(id).ifPresent(lic -> {
            repo.delete(lic);
            ra.addFlashAttribute("successMessage", "Đã xóa license " + lic.getName() + ".");
        });
        return "redirect:/assets/software-licenses";
    }
}
