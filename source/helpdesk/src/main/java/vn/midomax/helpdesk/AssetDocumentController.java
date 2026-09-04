package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ảnh biên bản giao nhận / thu hồi tài sản.
 *
 * Nằm dưới /assets nên tự thừa hưởng phân hệ Công Cụ Dụng Cụ qua ModuleAccessInterceptor:
 * phòng nào được cấp phân hệ là xem được biên bản. Thêm/xoá thì chỉ Admin/IT/Manager,
 * vì biên bản là giấy tờ tài sản, không để ai cũng sửa được.
 */
@Controller
@RequestMapping("/assets/documents")
public class AssetDocumentController {

    @Autowired
    private AssetDocumentService documentService;

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
                    "Chỉ IT/Admin mới được thêm hoặc xoá biên bản. Bạn chỉ có quyền xem.");
        }
    }

    /** Danh sách biên bản của một tài sản — modal gọi để vẽ. */
    @GetMapping("/{assetId}")
    @ResponseBody
    public Map<String, Object> list(@PathVariable("assetId") Long assetId, Authentication authentication) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AssetDocument d : documentService.listOf(assetId)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("docType", d.getDocType());
            m.put("typeLabel", d.getTypeLabel());
            m.put("filePath", d.getFilePath());
            m.put("originalName", d.getOriginalName());
            m.put("image", d.isImage());
            m.put("docDate", d.getDocDateStr());
            m.put("personName", d.getPersonName());
            m.put("note", d.getNote());
            m.put("uploadedAt", d.getUploadedAtStr());
            m.put("uploadedBy", d.getUploadedBy());
            m.put("size", d.getSizeStr());
            items.add(m);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("items", items);
        res.put("canEdit", canEdit(authentication));
        return res;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("assetId") Long assetId,
                         @RequestParam(value = "docType", required = false) String docType,
                         @RequestParam(value = "files", required = false) MultipartFile[] files,
                         @RequestParam(value = "docDate", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate docDate,
                         @RequestParam(value = "personName", required = false) String personName,
                         @RequestParam(value = "note", required = false) String note,
                         Authentication authentication,
                         RedirectAttributes ra) {
        assertCanEdit(authentication);

        AssetDocumentService.UploadResult result = documentService.upload(
                assetId, docType, files, docDate, personName, note,
                authentication == null ? null : authentication.getName());

        if (result.getSaved() > 0) {
            ra.addFlashAttribute("successMessage",
                    "Đã tải lên " + result.getSaved() + " ảnh biên bản.");
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage", String.join(" ", result.getErrors()));
        }
        if (result.getSaved() == 0 && !result.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Chưa chọn ảnh nào để tải lên.");
        }
        return "redirect:/assets";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") Long id,
                         Authentication authentication,
                         RedirectAttributes ra) {
        assertCanEdit(authentication);
        if (documentService.delete(id)) {
            ra.addFlashAttribute("successMessage", "Đã xoá ảnh biên bản.");
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy ảnh biên bản cần xoá.");
        }
        return "redirect:/assets";
    }
}
