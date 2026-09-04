package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Theo dõi license Microsoft 365 — nằm trong phân hệ Công Cụ Dụng Cụ.
 *
 * Đặt dưới tiền tố /assets là có chủ đích: ModuleAccessInterceptor gán mọi URL
 * /assets** cho phân hệ ASSETS, nên cấp phân hệ Công Cụ Dụng Cụ cho phòng nào là
 * phòng đó xem được luôn, không phải thêm luật phân quyền riêng.
 *
 * Xem thì ai có phân hệ đều xem được (kế toán, nhân sự). Sửa số đã mua thì chỉ
 * Admin/IT — vì việc mua và cấp phát license do IT quản lý.
 */
@Controller
@RequestMapping("/assets/licenses")
public class License365Controller {

    @Autowired
    private License365Service licenseService;

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
                    "Chỉ IT/Admin mới sửa được thông tin license. Bạn chỉ có quyền xem.");
        }
    }

    @GetMapping
    public String list(@RequestParam(value = "showFree", required = false) Boolean showFree,
                       @RequestParam(value = "refresh", required = false) Boolean refresh,
                       Authentication authentication,
                       Model model) {
        boolean includeFree = Boolean.TRUE.equals(showFree);
        List<License365Service.LicenseRow> rows =
                licenseService.overview(!includeFree, Boolean.TRUE.equals(refresh));

        int totalPurchased = 0, totalAssigned = 0, totalDisabled = 0, overCount = 0;
        for (License365Service.LicenseRow r : rows) {
            totalPurchased += r.getPurchased();
            totalAssigned += r.getAssigned();
            totalDisabled += r.getAssignedToDisabled();
            if ("OVER".equals(r.getStatus())) overCount++;
        }

        model.addAttribute("rows", rows);
        model.addAttribute("showFree", includeFree);
        model.addAttribute("canEdit", canEdit(authentication));
        model.addAttribute("totalPurchased", totalPurchased);
        model.addAttribute("totalAssigned", totalAssigned);
        model.addAttribute("totalRemaining", totalPurchased - totalAssigned);
        model.addAttribute("totalDisabled", totalDisabled);
        model.addAttribute("overCount", overCount);
        model.addAttribute("lastSyncedAt", licenseService.getLastSyncedAt());
        model.addAttribute("syncError", licenseService.getLastError());
        model.addAttribute("activePage", "licenses");
        return "license-365";
    }

    @PostMapping("/save")
    public String save(@RequestParam("skuId") String skuId,
                       @RequestParam(value = "displayName", required = false) String displayName,
                       @RequestParam(value = "purchasedQty", required = false) Integer purchasedQty,
                       @RequestParam(value = "paid", required = false) Boolean paid,
                       @RequestParam(value = "note", required = false) String note,
                       @RequestParam(value = "showFree", required = false) Boolean showFree,
                       Authentication authentication,
                       RedirectAttributes ra) {
        assertCanEdit(authentication);
        licenseService.save(skuId, displayName, purchasedQty, paid, note,
                authentication == null ? null : authentication.getName());
        ra.addFlashAttribute("successMessage", "Đã lưu thông tin license.");
        return "redirect:/assets/licenses" + (Boolean.TRUE.equals(showFree) ? "?showFree=true" : "");
    }

    /** Bỏ cache, đọc lại từ Microsoft 365 ngay. */
    @PostMapping("/sync")
    public String sync(@RequestParam(value = "showFree", required = false) Boolean showFree,
                       Authentication authentication,
                       RedirectAttributes ra) {
        assertCanEdit(authentication);
        licenseService.clearCache();
        licenseService.overview(true, true);
        String err = licenseService.getLastError();
        if (err != null) {
            ra.addFlashAttribute("errorMessage", err);
        } else {
            ra.addFlashAttribute("successMessage", "Đã đồng bộ lại danh sách license từ Microsoft 365.");
        }
        return "redirect:/assets/licenses" + (Boolean.TRUE.equals(showFree) ? "?showFree=true" : "");
    }

    /** Danh sách người đang giữ một loại license — dùng cho popup. */
    @GetMapping("/holders/{skuId}")
    @ResponseBody
    public List<License365Service.Holder> holders(@PathVariable("skuId") String skuId) {
        License365Service.LicenseRow row = licenseService.rowOf(skuId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có loại license này.");
        }
        return row.getHolders();
    }
}
