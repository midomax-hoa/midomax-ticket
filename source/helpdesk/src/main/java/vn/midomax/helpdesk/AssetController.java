package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý công cụ / dụng cụ (tài sản) của doanh nghiệp.
 * Route: /assets (mục "Công Cụ Dụng Cụ" trên sidebar).
 */
@Controller
@RequestMapping("/assets")
public class AssetController {

    /** Các trạng thái sử dụng cho phép chọn. */
    static final List<String> STATUSES = List.of(
            "Đang sử dụng", "Trong kho", "Đang sửa", "Hỏng", "Đã thanh lý");

    /** Tình trạng ghi nhận khi kiểm kê (bám theo giá trị đang dùng trong file Excel). */
    static final List<String> CONDITIONS = List.of(
            "Mới", "Sử dụng bình thường", "Cần bảo trì", "Hỏng", "Không tìm thấy");

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetCategoryRepository categoryRepository;

    @Autowired
    private AssetHandoverService handoverService;

    @Autowired
    private AssetUsageHistoryRepository usageHistoryRepository;

    @GetMapping("/history/{assetId}")
    @ResponseBody
    public ResponseEntity<List<AssetUsageHistory>> getAssetHistory(@PathVariable("assetId") Long assetId) {
        List<AssetUsageHistory> historyList = usageHistoryRepository.findByAssetIdOrderByAssignedDateDesc(assetId);
        if (historyList == null || historyList.isEmpty()) {
            Asset asset = assetRepository.findById(assetId).orElse(null);
            if (asset != null && asset.getAssignedToName() != null && !asset.getAssignedToName().isBlank()) {
                AssetUsageHistory initialHistory = new AssetUsageHistory(
                        asset.getId(),
                        asset.getInventoryCode(),
                        asset.getAssignedToName(),
                        asset.getAssignedToPosition(),
                        asset.getAssignedToDepartment(),
                        asset.getAssignedToLocation(),
                        asset.getCreatedAt() != null ? asset.getCreatedAt() : LocalDateTime.now(),
                        "Bàn giao / Cấp phát tài sản (Ghi nhận ban đầu)",
                        asset.getCreatedBy() != null ? asset.getCreatedBy() : "system"
                );
                usageHistoryRepository.save(initialHistory);
                historyList = List.of(initialHistory);
            }
        }
        return ResponseEntity.ok(historyList != null ? historyList : List.of());
    }

    /** Trang danh sách: lọc theo danh mục / trạng thái / từ khóa. */
    @GetMapping
    public String list(@RequestParam(value = "categoryId", required = false) Long categoryId,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       Model model) {

        List<AssetCategory> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        List<Asset> assets = assetRepository.search(categoryId, status, keyword);

        // Tên danh mục theo id, để template hiển thị mà không phải query lại từng dòng
        Map<Long, AssetCategory> categoryById = new LinkedHashMap<>();
        for (AssetCategory c : categories) {
            categoryById.put(c.getId(), c);
        }

        // Số lượng tài sản của từng danh mục, cho thanh lọc bên trên
        Map<Long, Long> countByCategory = new LinkedHashMap<>();
        for (AssetCategory c : categories) {
            countByCategory.put(c.getId(), assetRepository.countByCategoryId(c.getId()));
        }

        model.addAttribute("assets", assets);
        model.addAttribute("assetJson", buildAssetJson(assets));
        model.addAttribute("assetCount", assets.size());
        model.addAttribute("totalCount", assetRepository.count());
        model.addAttribute("categories", categories);
        model.addAttribute("categoryById", categoryById);
        model.addAttribute("countByCategory", countByCategory);
        model.addAttribute("statuses", STATUSES);
        model.addAttribute("conditions", CONDITIONS);
        model.addAttribute("inUseCount", assetRepository.countByStatus("Đang sử dụng"));
        model.addAttribute("brokenCount", assetRepository.countByStatus("Hỏng"));
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("suggestedCode", nextInventoryCode());
        model.addAttribute("activePage", "tools");

        return "asset-management";
    }

    /** Thêm mới hoặc cập nhật một tài sản. id rỗng -> thêm mới. */
    @PostMapping("/save")
    public String save(@RequestParam(value = "id", required = false) Long id,
                       @RequestParam("inventoryCode") String inventoryCode,
                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                       @RequestParam(value = "officeLocation", required = false) String officeLocation,
                       @RequestParam(value = "quantity", required = false) Integer quantity,
                       @RequestParam(value = "assignedToName", required = false) String assignedToName,
                       @RequestParam(value = "assignedToPosition", required = false) String assignedToPosition,
                       @RequestParam(value = "assignedToDepartment", required = false) String assignedToDepartment,
                       @RequestParam(value = "assignedToLocation", required = false) String assignedToLocation,
                       @RequestParam(value = "assetType", required = false) String assetType,
                       @RequestParam(value = "manufacturer", required = false) String manufacturer,
                       @RequestParam(value = "model", required = false) String modelName,
                       @RequestParam(value = "details", required = false) String details,
                       @RequestParam(value = "serialNumber", required = false) String serialNumber,
                       @RequestParam(value = "purchaseDate", required = false) String purchaseDateRaw,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "note", required = false) String note,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {

        if (inventoryCode == null || inventoryCode.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập mã kiểm kê.");
            return "redirect:/assets";
        }
        String code = inventoryCode.trim();

        Asset asset;
        String oldAssignedName = null;
        if (id != null) {
            asset = assetRepository.findById(id).orElse(null);
            if (asset == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài sản cần sửa.");
                return "redirect:/assets";
            }
            oldAssignedName = asset.getAssignedToName();
        } else {
            asset = new Asset();
            asset.setCreatedBy(authentication != null ? authentication.getName() : "unknown");
            asset.setCreatedAt(LocalDateTime.now());
        }

        // Mã kiểm kê phải là duy nhất (bỏ qua chính bản ghi đang sửa)
        if (!code.equalsIgnoreCase(asset.getInventoryCode()) && assetRepository.existsByInventoryCode(code)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã kiểm kê \"" + code + "\" đã tồn tại.");
            return "redirect:/assets";
        }

        asset.setInventoryCode(code);
        asset.setCategoryId(categoryId);
        asset.setOfficeLocation(trim(officeLocation));
        asset.setQuantity(quantity == null || quantity < 1 ? 1 : quantity);
        
        String newAssignedName = trim(assignedToName);
        String newPosition = trim(assignedToPosition);
        String newDepartment = trim(assignedToDepartment);
        String newLocation = trim(assignedToLocation);

        asset.setAssignedToName(newAssignedName);
        asset.setAssignedToPosition(newPosition);
        asset.setAssignedToDepartment(newDepartment);
        asset.setAssignedToLocation(newLocation);
        asset.setAssetType(trim(assetType));
        asset.setManufacturer(trim(manufacturer));
        asset.setModel(trim(modelName));
        asset.setDetails(trim(details));
        asset.setSerialNumber(trim(serialNumber));
        asset.setPurchaseDate(parseDate(purchaseDateRaw));
        asset.setStatus(trim(status));
        asset.setNote(trim(note));
        asset.setUpdatedAt(LocalDateTime.now());

        assetRepository.save(asset);

        // Logic tự động lưu vết Lịch Sử Người Sử Dụng / Bàn Giao
        boolean isNew = (id == null);
        boolean assignmentChanged = false;

        if (isNew) {
            if (newAssignedName != null && !newAssignedName.isBlank()) {
                assignmentChanged = true;
            }
        } else {
            String oldClean = oldAssignedName != null ? oldAssignedName.trim() : "";
            String newClean = newAssignedName != null ? newAssignedName.trim() : "";
            if (!oldClean.equalsIgnoreCase(newClean)) {
                assignmentChanged = true;
            }
        }

        if (assignmentChanged) {
            String currentUser = authentication != null ? authentication.getName() : "system";

            // Đóng thời gian sử dụng của người cũ nếu là cập nhật
            if (!isNew && oldAssignedName != null && !oldAssignedName.isBlank()) {
                usageHistoryRepository.findFirstByAssetIdAndReturnedDateIsNullOrderByAssignedDateDesc(asset.getId())
                        .ifPresent(h -> {
                            h.setReturnedDate(LocalDateTime.now());
                            usageHistoryRepository.save(h);
                        });
            }

            // Ghi nhận lịch sử bàn giao mới cho người mới
            if (newAssignedName != null && !newAssignedName.isBlank()) {
                String reason = isNew ? "Bàn giao / Cấp phát mới tài sản" : "Điều chuyển người sử dụng (Bàn giao mới)";
                if (note != null && !note.isBlank()) {
                    reason += " - Ghi chú: " + note.trim();
                }
                AssetUsageHistory history = new AssetUsageHistory(
                        asset.getId(),
                        asset.getInventoryCode(),
                        newAssignedName,
                        newPosition,
                        newDepartment,
                        newLocation,
                        LocalDateTime.now(),
                        reason,
                        currentUser
                );
                usageHistoryRepository.save(history);
            }
        }

        redirectAttributes.addFlashAttribute("successMessage",
                (id != null ? "Đã cập nhật tài sản " : "Đã thêm tài sản ") + code + ".");
        return "redirect:/assets";
    }

    /** Ghi nhận kết quả kiểm kê cho một tài sản. */
    @PostMapping("/inventory/{id}")
    public String recordInventory(@PathVariable("id") Long id,
                                  @RequestParam(value = "lastInventoryDate", required = false) String dateRaw,
                                  @RequestParam(value = "inventoryBy", required = false) String inventoryBy,
                                  @RequestParam(value = "inventoryCondition", required = false) String condition,
                                  @RequestParam(value = "note", required = false) String note,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {

        Asset asset = assetRepository.findById(id).orElse(null);
        if (asset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài sản.");
            return "redirect:/assets";
        }

        LocalDate date = parseDate(dateRaw);
        asset.setLastInventoryDate(date != null ? date : LocalDate.now());
        String who = trim(inventoryBy);
        if (who == null || who.isBlank()) {
            who = authentication != null ? authentication.getName() : null;
        }
        asset.setInventoryBy(who);
        asset.setInventoryCondition(trim(condition));
        if (note != null && !note.isBlank()) {
            asset.setNote(note.trim());
        }
        asset.setUpdatedAt(LocalDateTime.now());

        assetRepository.save(asset);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã ghi nhận kiểm kê cho " + asset.getInventoryCode() + ".");
        return "redirect:/assets";
    }

    /** Xóa một tài sản. */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Asset asset = assetRepository.findById(id).orElse(null);
        if (asset != null) {
            assetRepository.delete(asset);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xóa tài sản " + asset.getInventoryCode() + ".");
        }
        return "redirect:/assets";
    }

    /**
     * Xuất biên bản bàn giao (.docx) cho một hoặc nhiều tài sản giao cùng một người,
     * kèm các dòng phụ kiện nhập tay (không lưu DB).
     *
     * Các mảng acc* là dữ liệu song song theo từng dòng phụ kiện trên giao diện;
     * dòng không có tên phụ kiện sẽ bị bỏ qua.
     */
    @PostMapping("/handover")
    public ResponseEntity<InputStreamResource> handover(
            @RequestParam("ids") List<Long> ids,
            @RequestParam(value = "accName", required = false) List<String> accNames,
            @RequestParam(value = "accSpec", required = false) List<String> accSpecs,
            @RequestParam(value = "accQuantity", required = false) List<String> accQuantities,
            @RequestParam(value = "accCondition", required = false) List<String> accConditions)
            throws IOException {

        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Giữ đúng thứ tự người dùng chọn thay vì thứ tự findAllById trả về
        List<Asset> assets = new ArrayList<>();
        for (Long id : ids) {
            assetRepository.findById(id).ifPresent(assets::add);
        }
        if (assets.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Biên bản chỉ có một mục "Bên nhận" nên mọi tài sản phải cùng người nhận
        String receiver = normalize(assets.get(0).getAssignedToName());
        for (Asset a : assets) {
            if (!receiver.equals(normalize(a.getAssignedToName()))) {
                return ResponseEntity.badRequest().build();
            }
        }

        Map<Long, AssetCategory> categoryById = new LinkedHashMap<>();
        for (AssetCategory c : categoryRepository.findAll()) {
            categoryById.put(c.getId(), c);
        }

        List<AssetHandoverService.Accessory> accessories =
                buildAccessories(accNames, accSpecs, accQuantities, accConditions);

        ByteArrayInputStream in = handoverService.buildHandoverDoc(assets, categoryById, accessories);

        String filename = "BienBanBanGiao_" + safeFileName(assets.get(0).getInventoryCode()) + ".docx";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition",
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''"
                        + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(new InputStreamResource(in));
    }

    /** Gộp các mảng song song từ form thành danh sách phụ kiện, bỏ dòng trống. */
    private List<AssetHandoverService.Accessory> buildAccessories(List<String> names, List<String> specs,
                                                                  List<String> quantities, List<String> conditions) {
        List<AssetHandoverService.Accessory> result = new ArrayList<>();
        if (names == null) return result;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) continue;
            result.add(new AssetHandoverService.Accessory(
                    name.trim(), at(specs, i), at(quantities, i), at(conditions, i)));
        }
        return result;
    }

    /** Phần tử thứ i của mảng song song; thiếu phần tử -> rỗng. */
    private String at(List<String> list, int i) {
        if (list == null || i >= list.size() || list.get(i) == null) return "";
        return list.get(i).trim();
    }

    /** Chuẩn hóa tên người nhận để so sánh: rỗng nếu null, bỏ khoảng trắng thừa. */
    private String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    // ===== Danh mục =====

    /** Thêm mới hoặc đổi tên / thuộc tính một danh mục. */
    @PostMapping("/category/save")
    public String saveCategory(@RequestParam(value = "id", required = false) Long id,
                               @RequestParam("name") String name,
                               @RequestParam(value = "icon", required = false) String icon,
                               @RequestParam(value = "itEquipment", required = false) Boolean itEquipment,
                               @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                               RedirectAttributes redirectAttributes) {

        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập tên danh mục.");
            return "redirect:/assets";
        }
        String cleanName = name.trim();

        AssetCategory category;
        if (id != null) {
            category = categoryRepository.findById(id).orElse(null);
            if (category == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy danh mục.");
                return "redirect:/assets";
            }
        } else {
            category = new AssetCategory();
            category.setCreatedAt(LocalDateTime.now());
        }

        if (!cleanName.equalsIgnoreCase(category.getName()) && categoryRepository.existsByName(cleanName)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Danh mục \"" + cleanName + "\" đã tồn tại.");
            return "redirect:/assets";
        }

        category.setName(cleanName);
        category.setIcon(icon != null && !icon.isBlank() ? icon.trim() : "fa-solid fa-box");
        category.setItEquipment(Boolean.TRUE.equals(itEquipment));
        category.setSortOrder(sortOrder != null ? sortOrder : 99);
        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute("successMessage",
                (id != null ? "Đã cập nhật danh mục " : "Đã thêm danh mục ") + cleanName + ".");
        return "redirect:/assets";
    }

    /** Xóa danh mục. Chặn nếu vẫn còn tài sản thuộc danh mục đó. */
    @PostMapping("/category/delete/{id}")
    public String deleteCategory(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        AssetCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return "redirect:/assets";
        }
        long used = assetRepository.countByCategoryId(id);
        if (used > 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Không thể xóa \"" + category.getName() + "\": còn " + used
                            + " tài sản đang thuộc danh mục này.");
            return "redirect:/assets";
        }
        categoryRepository.delete(category);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa danh mục " + category.getName() + ".");
        return "redirect:/assets";
    }

    // ===== Helper =====

    /**
     * JSON của từng tài sản để modal "Sửa" đổ lại vào form (nhúng qua data-json).
     * Ngày để dạng "yyyy-MM-dd" cho khớp input type=date.
     */
    private Map<Long, String> buildAssetJson(List<Asset> assets) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Asset a : assets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("inventoryCode", a.getInventoryCode());
            m.put("categoryId", a.getCategoryId());
            m.put("officeLocation", a.getOfficeLocation());
            m.put("quantity", a.getQuantity());
            m.put("assignedToName", a.getAssignedToName());
            m.put("assignedToPosition", a.getAssignedToPosition());
            m.put("assignedToDepartment", a.getAssignedToDepartment());
            m.put("assignedToLocation", a.getAssignedToLocation());
            m.put("assetType", a.getAssetType());
            m.put("manufacturer", a.getManufacturer());
            m.put("model", a.getModel());
            m.put("details", a.getDetails());
            m.put("serialNumber", a.getSerialNumber());
            m.put("purchaseDate", a.getPurchaseDateInput());
            m.put("status", a.getStatus());
            m.put("note", a.getNote());
            result.put(a.getId(), toJson(m));
        }
        return result;
    }

    /** JSON tối giản cho map String -> (String|Number|null). */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(v))).append("\"");
            }
        }
        return sb.append("}").toString();
    }

    /** Escape chuỗi trong JSON: dấu nháy, backslash và ký tự xuống dòng. */
    private String escapeJson(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Gợi ý mã kiểm kê kế tiếp dạng MDM-00275 dựa trên mã lớn nhất hiện có.
     * Mã cố định 5 chữ số nên so sánh chuỗi trùng với so sánh số.
     */
    private String nextInventoryCode() {
        String prefix = "MDM-";
        String max = assetRepository.findMaxInventoryCodeByPrefix(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length()).trim()) + 1;
            } catch (NumberFormatException ignored) {
                // Mã không theo định dạng số -> bắt đầu lại từ 1, người dùng có thể sửa tay
            }
        }
        return String.format("%s%05d", prefix, next);
    }

    /** Bỏ ký tự không hợp lệ trong tên file tải về. */
    private String safeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "TaiSan";
        return raw.replaceAll("[^a-zA-Z0-9\\-_]", "_");
    }

    private String trim(String raw) {
        return raw == null ? null : raw.trim();
    }

    /** Parse ngày từ input HTML type=date ("yyyy-MM-dd"); rỗng/không hợp lệ -> null. */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
