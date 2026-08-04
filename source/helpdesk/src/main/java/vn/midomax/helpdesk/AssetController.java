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

    /** Số dòng mỗi trang của danh sách tài sản. */
    private static final int PAGE_SIZE = 5;

    /** Các trạng thái sử dụng cho phép chọn. */
    static final List<String> STATUSES = List.of(
            "Mới", "Đang sử dụng", "Trong kho", "Đang sửa", "Hỏng", "Đã thanh lý");

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
    private ExcelService excelService;

    /** Tải file Excel mẫu để nhập công cụ dụng cụ hàng loạt. */
    @GetMapping("/sample-template")
    public ResponseEntity<InputStreamResource> downloadSampleTemplate() throws IOException {
        ByteArrayInputStream in = excelService.generateSampleAssetExcel();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=Mau_Nhap_Cong_Cu_Dung_Cu.xlsx");
        return ResponseEntity.ok().headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    /**
     * Nhập công cụ dụng cụ hàng loạt từ Excel. Mã kiểm kê đã có thì cập nhật,
     * chưa có thì tạo mới — nhập lại cùng file không sinh bản ghi trùng.
     */
    @PostMapping("/import")
    public String importAssets(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chưa chọn file Excel.");
            return "redirect:/assets";
        }

        List<ExcelService.AssetImportRow> rows;
        try {
            rows = excelService.parseAssetsFromExcel(file.getInputStream());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không đọc được file Excel: " + e.getMessage());
            return "redirect:/assets";
        }
        if (rows.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Không tìm thấy dòng nào có Mã kiểm kê. Kiểm tra lại dòng tiêu đề của file.");
            return "redirect:/assets";
        }

        // Tra danh mục theo tên (không phân biệt hoa thường) để khỏi bắt người dùng nhớ id
        Map<String, Long> categoryByName = new LinkedHashMap<>();
        for (AssetCategory c : categoryRepository.findAll()) {
            if (c.getName() != null) categoryByName.put(c.getName().trim().toLowerCase(), c.getId());
        }

        String actor = ReporterIdentity.of(authentication);
        int created = 0, updated = 0, noCategory = 0;

        for (ExcelService.AssetImportRow row : rows) {
            Asset asset = assetRepository.findByInventoryCode(row.inventoryCode).orElse(null);
            boolean isNew = asset == null;
            if (isNew) {
                asset = new Asset();
                asset.setInventoryCode(row.inventoryCode);
                asset.setCreatedBy(actor);
                asset.setCreatedAt(LocalDateTime.now());
            }

            if (row.categoryName != null && !row.categoryName.isBlank()) {
                Long categoryId = categoryByName.get(row.categoryName.trim().toLowerCase());
                if (categoryId != null) asset.setCategoryId(categoryId);
                else noCategory++;
            }
            if (row.officeLocation != null) asset.setOfficeLocation(row.officeLocation);
            if (row.quantity != null) asset.setQuantity(row.quantity);
            if (row.assignedToName != null) asset.setAssignedToName(row.assignedToName);
            if (row.assignedToPosition != null) asset.setAssignedToPosition(row.assignedToPosition);
            if (row.assignedToDepartment != null) asset.setAssignedToDepartment(row.assignedToDepartment);
            if (row.assignedToLocation != null) asset.setAssignedToLocation(row.assignedToLocation);
            if (row.assetType != null) asset.setAssetType(row.assetType);
            if (row.manufacturer != null) asset.setManufacturer(row.manufacturer);
            if (row.model != null) asset.setModel(row.model);
            if (row.details != null) asset.setDetails(row.details);
            if (row.serialNumber != null) asset.setSerialNumber(row.serialNumber);
            if (row.purchaseDate != null) asset.setPurchaseDate(row.purchaseDate);
            asset.setStatus(row.status != null && !row.status.isBlank() ? row.status.trim() : "Đang sử dụng");
            if (row.note != null) asset.setNote(row.note);
            asset.setUpdatedAt(LocalDateTime.now());

            assetRepository.save(asset);
            if (isNew) created++; else updated++;
        }

        StringBuilder msg = new StringBuilder("Đã nhập ").append(rows.size()).append(" dòng: ")
                .append(created).append(" tài sản mới, ").append(updated).append(" cập nhật.");
        if (noCategory > 0) {
            msg.append(" Có ").append(noCategory).append(" dòng ghi danh mục không có trong hệ thống nên bỏ trống danh mục.");
        }
        redirectAttributes.addFlashAttribute("successMessage", msg.toString());
        return "redirect:/assets?page=" + lastPage();
    }

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
                       @RequestParam(value = "page", required = false) Integer page,
                       Model model) {

        List<AssetCategory> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        List<Asset> allMatched = assetRepository.search(categoryId, status, keyword);

        // Phân trang trong bộ nhớ: danh sách tài sản không lớn, tránh phải đổi query/JPA
        int totalPages = Math.max(1, (int) Math.ceil(allMatched.size() / (double) PAGE_SIZE));
        int currentPage = Math.min(Math.max(page == null ? 1 : page, 1), totalPages);
        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, allMatched.size());
        List<Asset> assets = allMatched.subList(from, to);

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
        // JSON cho form sửa phải phủ TOÀN BỘ kết quả, không chỉ trang hiện tại, vì
        // các sản phẩm cùng đợt bàn giao có thể nằm ở trang khác.
        model.addAttribute("assetJson", buildAssetJson(allMatched));
        model.addAttribute("assetCount", allMatched.size());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageFrom", allMatched.isEmpty() ? 0 : from + 1);
        model.addAttribute("pageTo", to);
        // Mảng JSON toàn bộ tài sản khớp bộ lọc, để JS gom sản phẩm cùng đợt bàn giao
        // kể cả khi chúng nằm ở trang khác.
        model.addAttribute("allAssetsJson",
                "[" + String.join(",", buildAssetJson(allMatched).values()) + "]");
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

    /** Xuất danh sách công cụ dụng cụ (theo đúng bộ lọc đang chọn) ra file Excel. */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportExcel(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) throws IOException {

        List<Asset> allMatched = assetRepository.search(categoryId, status, keyword);
        List<AssetCategory> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();

        Map<Long, String> categoryNames = new LinkedHashMap<>();
        String categoryNameFilter = "Tất cả";
        for (AssetCategory c : categories) {
            categoryNames.put(c.getId(), c.getName());
            if (categoryId != null && categoryId.equals(c.getId())) {
                categoryNameFilter = c.getName();
            }
        }

        ByteArrayInputStream in = excelService.exportAssetsToExcel(
                allMatched, categoryNameFilter, status, keyword, categoryNames);

        String filename = "CongCuDungCu_Midomax_" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    /** Thêm mới hoặc cập nhật một tài sản. id rỗng -> thêm mới. */
    @PostMapping("/save")
    public String save(@RequestParam(value = "id", required = false) Long id,
                       @RequestParam(value = "inventoryCode", required = false) String inventoryCode,
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
                       @RequestParam(value = "handoverBy", required = false) String handoverBy,
                       @RequestParam(value = "handoverByPosition", required = false) String handoverByPosition,
                       @RequestParam(value = "handoverByDepartment", required = false) String handoverByDepartment,
                       @RequestParam(value = "handoverDate", required = false) String handoverDateRaw,
                       @RequestParam(value = "extraId", required = false) List<Long> extraIds,
                       @RequestParam(value = "extraCode", required = false) List<String> extraCodes,
                       @RequestParam(value = "extraType", required = false) List<String> extraTypes,
                       @RequestParam(value = "extraManufacturer", required = false) List<String> extraManufacturers,
                       @RequestParam(value = "extraModel", required = false) List<String> extraModels,
                       @RequestParam(value = "extraSerial", required = false) List<String> extraSerials,
                       @RequestParam(value = "extraQty", required = false) List<Integer> extraQtys,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {

        // Mã kiểm kê không bắt buộc: phụ kiện lẻ không dán mã thì để trống (lưu NULL).
        // Bỏ trống cũng không "tiêu" mất số gợi ý, lần sau vẫn gợi ý đúng số kế tiếp.
        String code = (inventoryCode == null || inventoryCode.isBlank()) ? null : inventoryCode.trim();

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

        // Mã kiểm kê nếu có nhập thì phải là duy nhất (bỏ qua chính bản ghi đang sửa)
        if (code != null && !code.equalsIgnoreCase(asset.getInventoryCode())
                && assetRepository.existsByInventoryCode(code)) {
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
        asset.setHandoverBy(trim(handoverBy));
        asset.setHandoverByPosition(trim(handoverByPosition));
        asset.setHandoverByDepartment(trim(handoverByDepartment));
        asset.setHandoverDate(parseDateTimeLocal(handoverDateRaw));
        asset.setUpdatedAt(LocalDateTime.now());

        assetRepository.save(asset);

        // Bàn giao 1 lần nhiều sản phẩm: các dòng "Thêm sản phẩm" dùng chung danh mục,
        // địa điểm, người nhận, người giao, thời gian và tình trạng với sản phẩm chính.
        int extraSaved = 0, extraUpdated = 0;
        if (extraCodes != null) {
            for (int i = 0; i < extraCodes.size(); i++) {
                String exCode = trim(extraCodes.get(i));
                if (exCode != null && exCode.isEmpty()) exCode = null;
                // Dòng trống hoàn toàn (không mã, không loại, không serial) thì bỏ qua;
                // còn chỉ thiếu mã kiểm kê thì vẫn lưu vì phụ kiện lẻ không dán mã.
                if (exCode == null
                        && isBlankValue(valueAt(extraTypes, i))
                        && isBlankValue(valueAt(extraManufacturers, i))
                        && isBlankValue(valueAt(extraModels, i))
                        && isBlankValue(valueAt(extraSerials, i))) {
                    continue;
                }

                // Dòng có extraId là sản phẩm ĐÃ CÓ trong cùng đợt bàn giao (mở form sửa
                // sẽ nạp sẵn) -> cập nhật; không có id thì mới là sản phẩm thêm mới.
                Long exId = extraIds != null && i < extraIds.size() ? extraIds.get(i) : null;
                Asset extra = (exId != null && exId > 0) ? assetRepository.findById(exId).orElse(null) : null;
                boolean isNewExtra = (extra == null);

                if (exCode != null && (isNewExtra || !exCode.equalsIgnoreCase(extra.getInventoryCode()))) {
                    if (assetRepository.existsByInventoryCode(exCode)) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "Mã kiểm kê \"" + exCode + "\" đã tồn tại — dòng này bị bỏ qua.");
                        continue;
                    }
                }
                if (isNewExtra) {
                    extra = new Asset();
                    extra.setCreatedBy(authentication != null ? authentication.getName() : "unknown");
                    extra.setCreatedAt(LocalDateTime.now());
                }
                extra.setInventoryCode(exCode);
                extra.setCategoryId(categoryId);
                extra.setOfficeLocation(asset.getOfficeLocation());
                extra.setAssignedToName(asset.getAssignedToName());
                extra.setAssignedToPosition(asset.getAssignedToPosition());
                extra.setAssignedToDepartment(asset.getAssignedToDepartment());
                extra.setAssignedToLocation(asset.getAssignedToLocation());
                extra.setAssetType(valueAt(extraTypes, i));
                extra.setManufacturer(valueAt(extraManufacturers, i));
                extra.setModel(valueAt(extraModels, i));
                extra.setSerialNumber(valueAt(extraSerials, i));
                Integer exQty = extraQtys != null && i < extraQtys.size() ? extraQtys.get(i) : null;
                extra.setQuantity(exQty == null || exQty < 1 ? 1 : exQty);
                extra.setStatus(asset.getStatus());
                extra.setPurchaseDate(asset.getPurchaseDate());
                extra.setHandoverBy(asset.getHandoverBy());
                extra.setHandoverByPosition(asset.getHandoverByPosition());
                extra.setHandoverByDepartment(asset.getHandoverByDepartment());
                extra.setHandoverDate(asset.getHandoverDate());
                extra.setNote(asset.getNote());
                extra.setUpdatedAt(LocalDateTime.now());
                assetRepository.save(extra);
                if (isNewExtra) extraSaved++; else extraUpdated++;

                if (isNewExtra && extra.getAssignedToName() != null && !extra.getAssignedToName().isBlank()) {
                    usageHistoryRepository.save(new AssetUsageHistory(
                            extra.getId(), extra.getInventoryCode(), extra.getAssignedToName(),
                            extra.getAssignedToPosition(), extra.getAssignedToDepartment(),
                            extra.getAssignedToLocation(), LocalDateTime.now(),
                            "Bàn giao / Cấp phát mới tài sản",
                            authentication != null ? authentication.getName() : "system"));
                }
            }
        }

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

        String okMsg = (id != null ? "Đã cập nhật tài sản " : "Đã thêm tài sản ")
                + (code != null ? code : "(chưa có mã kiểm kê)") + ".";
        if (extraSaved > 0) okMsg += " Thêm " + extraSaved + " sản phẩm cùng đợt bàn giao.";
        if (extraUpdated > 0) okMsg += " Cập nhật " + extraUpdated + " sản phẩm cùng đợt.";
        redirectAttributes.addFlashAttribute("successMessage", okMsg);
        // Danh sách xếp cũ->mới và chia 5 dòng/trang, nên tài sản vừa lưu thường nằm ở
        // trang cuối. Nhảy thẳng tới trang chứa nó để thấy ngay kết quả.
        return "redirect:/assets?page=" + pageOf(asset.getId());
    }

    /** Trang cuối của danh sách mặc định — nơi các tài sản vừa thêm nằm. */
    private int lastPage() {
        return Math.max(1, (int) Math.ceil(assetRepository.count() / (double) PAGE_SIZE));
    }

    /** Trang (1-based) chứa tài sản trong danh sách mặc định, để redirect về đúng chỗ. */
    private int pageOf(Long assetId) {
        if (assetId == null) return 1;
        List<Asset> all = assetRepository.search(null, null, null);
        for (int i = 0; i < all.size(); i++) {
            if (assetId.equals(all.get(i).getId())) return (i / PAGE_SIZE) + 1;
        }
        return Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
    }

    private static boolean isBlankValue(String v) {
        return v == null || v.isBlank();
    }

    private static String valueAt(List<String> list, int i) {
        return list != null && i < list.size() ? trim(list.get(i)) : null;
    }

    /** Parse chuỗi datetime-local "yyyy-MM-ddTHH:mm"; nhận cả "yyyy-MM-dd". */
    private static LocalDateTime parseDateTimeLocal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String v = raw.trim();
            return v.contains("T") ? LocalDateTime.parse(v) : LocalDate.parse(v).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
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
            @RequestParam(value = "accSerial", required = false) List<String> accSerials,
            @RequestParam(value = "accQuantity", required = false) List<String> accQuantities,
            @RequestParam(value = "accCondition", required = false) List<String> accConditions,
            @RequestParam(value = "saveAccessories", required = false, defaultValue = "false") boolean saveAccessories)
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
                buildAccessories(accNames, accSpecs, accSerials, accQuantities, accConditions);

        if (saveAccessories) {
            saveAccessoriesAsAssets(assets.get(0), accessories);
        }

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
    /**
     * Chỉ LƯU các dòng phụ kiện thành tài sản, không xuất file — nút "Tải biên bản"
     * lo phần tải. Phụ kiện kế thừa người nhận / người giao / đợt bàn giao của tài sản gốc.
     */
    @PostMapping("/handover/save-accessories")
    public String saveHandoverAccessories(
            @RequestParam("ids") List<Long> ids,
            @RequestParam(value = "accName", required = false) List<String> accNames,
            @RequestParam(value = "accSpec", required = false) List<String> accSpecs,
            @RequestParam(value = "accSerial", required = false) List<String> accSerials,
            @RequestParam(value = "accQuantity", required = false) List<String> accQuantities,
            @RequestParam(value = "accCondition", required = false) List<String> accConditions,
            RedirectAttributes redirectAttributes) {

        Asset base = (ids == null || ids.isEmpty()) ? null
                : assetRepository.findById(ids.get(0)).orElse(null);
        if (base == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài sản gốc của biên bản.");
            return "redirect:/assets";
        }

        List<AssetHandoverService.Accessory> accessories =
                buildAccessories(accNames, accSpecs, accSerials, accQuantities, accConditions);
        if (accessories.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Chưa có dòng phụ kiện nào để lưu. Bấm \"Thêm phụ kiện\" và nhập tên trước.");
            return "redirect:/assets";
        }

        int saved = saveAccessoriesAsAssets(base, accessories);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã lưu " + saved + " sản phẩm vào danh sách công cụ dụng cụ (cùng đợt bàn giao với "
                        + base.getInventoryCode() + ").");
        return "redirect:/assets?page=" + lastPage();
    }

    /** Tạo tài sản mới từ các dòng phụ kiện, trả về số dòng đã lưu. */
    private int saveAccessoriesAsAssets(Asset first, List<AssetHandoverService.Accessory> accessories) {
        int saved = 0;
        for (AssetHandoverService.Accessory acc : accessories) {
            Asset extra = new Asset();
            extra.setInventoryCode(nextInventoryCode());
            extra.setCategoryId(first.getCategoryId());
            extra.setOfficeLocation(first.getOfficeLocation());
            // Map đúng theo form sửa tài sản: Tên -> Loại tài sản, Thông số -> Thông tin
            // chi tiết, Serial -> Serial Number, để mở lại thấy khớp với biên bản.
            extra.setAssetType(acc.name());
            extra.setDetails(acc.spec());
            extra.setSerialNumber(acc.serial());
            try {
                extra.setQuantity(Math.max(1, Integer.parseInt(acc.quantity().replaceAll("[^0-9]", ""))));
            } catch (Exception e) {
                extra.setQuantity(1);
            }
            extra.setStatus(acc.condition() != null && !acc.condition().isBlank() ? acc.condition() : "Mới");
            extra.setAssignedToName(first.getAssignedToName());
            extra.setAssignedToPosition(first.getAssignedToPosition());
            extra.setAssignedToDepartment(first.getAssignedToDepartment());
            extra.setAssignedToLocation(first.getAssignedToLocation());
            extra.setHandoverBy(first.getHandoverBy());
            extra.setHandoverByPosition(first.getHandoverByPosition());
            extra.setHandoverByDepartment(first.getHandoverByDepartment());
            extra.setHandoverDate(first.getHandoverDate());
            extra.setCreatedBy(first.getHandoverBy() != null ? first.getHandoverBy() : "handover");
            extra.setCreatedAt(LocalDateTime.now());
            extra.setUpdatedAt(LocalDateTime.now());
            assetRepository.save(extra);
            saved++;

            if (extra.getAssignedToName() != null && !extra.getAssignedToName().isBlank()) {
                usageHistoryRepository.save(new AssetUsageHistory(
                        extra.getId(), extra.getInventoryCode(), extra.getAssignedToName(),
                        extra.getAssignedToPosition(), extra.getAssignedToDepartment(),
                        extra.getAssignedToLocation(), LocalDateTime.now(),
                        "Bàn giao / Cấp phát mới tài sản (lưu từ biên bản bàn giao)",
                        extra.getCreatedBy()));
            }
        }
        return saved;
    }

    private List<AssetHandoverService.Accessory> buildAccessories(List<String> names, List<String> specs,
                                                                  List<String> serials,
                                                                  List<String> quantities, List<String> conditions) {
        List<AssetHandoverService.Accessory> result = new ArrayList<>();
        if (names == null) return result;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) continue;
            result.add(new AssetHandoverService.Accessory(
                    name.trim(), at(specs, i), at(serials, i), at(quantities, i), at(conditions, i)));
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
            m.put("handoverBy", a.getHandoverBy());
            m.put("handoverByPosition", a.getHandoverByPosition());
            m.put("handoverByDepartment", a.getHandoverByDepartment());
            // input datetime-local cần dạng yyyy-MM-ddTHH:mm
            m.put("handoverDate", a.getHandoverDate() != null
                    ? a.getHandoverDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                    : null);
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

    private static String trim(String raw) {
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
