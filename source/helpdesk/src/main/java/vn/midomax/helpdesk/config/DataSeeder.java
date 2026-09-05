package vn.midomax.helpdesk.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.midomax.helpdesk.AppUser;
import vn.midomax.helpdesk.AppUserRepository;
import vn.midomax.helpdesk.Asset;
import vn.midomax.helpdesk.AssetRepository;
import vn.midomax.helpdesk.AssetCategory;
import vn.midomax.helpdesk.AssetCategoryRepository;
import vn.midomax.helpdesk.Shift;
import vn.midomax.helpdesk.ShiftRepository;
import org.springframework.beans.factory.annotation.Value;

@Component
public class DataSeeder implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AssetCategoryRepository assetCategoryRepository;
    private final AssetRepository assetRepository;
    private final ShiftRepository shiftRepository;

    /** Mật khẩu tài khoản admin lúc tạo mới. Production phải đặt qua biến môi trường. */
    @Value("${app.seed.admin-password:admin123}")
    private String adminPassword;

    /** Tài khoản "user" chỉ dùng để thử nghiệm, production nên tắt đi. */
    @Value("${app.seed.demo-user:true}")
    private boolean seedDemoUser;

    public DataSeeder(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                      AssetCategoryRepository assetCategoryRepository, AssetRepository assetRepository,
                      ShiftRepository shiftRepository) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.assetCategoryRepository = assetCategoryRepository;
        this.assetRepository = assetRepository;
        this.shiftRepository = shiftRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Tạo tài khoản Admin nếu chưa tồn tại
        if (!appUserRepository.existsByEmail("admin")) {
            AppUser admin = new AppUser();
            admin.setEmail("admin");
            admin.setFullName("Admin");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ROLE_ADMIN");
            admin.setAuthSource(AppUser.SOURCE_LOCAL);
            appUserRepository.save(admin);
            System.out.println("Tạo tài khoản Admin thành công.");
        }

        // Tạo tài khoản User nếu chưa tồn tại
        if (seedDemoUser && !appUserRepository.existsByEmail("user")) {
            AppUser user = new AppUser();
            user.setEmail("user");
            user.setFullName("User");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setRole("ROLE_USER");
            user.setAuthSource(AppUser.SOURCE_LOCAL);
            appUserRepository.save(user);
            System.out.println("Tạo tài khoản User thành công.");
        }

        backfillAuthSource();
        seedAssetCategories();
        seedDefaultShift();
    }

    /**
     * Không có ca nào thì phần chấm công không tính được công, nên tạo sẵn ca hành
     * chính. Chỉ chạy khi bảng ca đang trống để không đè cấu hình người dùng đã sửa.
     */
    private void seedDefaultShift() {
        if (shiftRepository.count() > 0) {
            return;
        }
        Shift shift = new Shift();
        shift.setCode("HC");
        shift.setName("Hành chính");
        shift.setStartTime(java.time.LocalTime.of(8, 0));
        shift.setEndTime(java.time.LocalTime.of(17, 30));
        shift.setBreakMinutes(90);
        shift.setLateGraceMinutes(5);
        shift.setEarlyGraceMinutes(5);
        shift.setOtStartAfterMinutes(30);
        shift.setCrossMidnight(false);
        shift.setWorkingWeekdays("2,3,4,5,6,7");
        shift.setIsDefault(true);
        shift.setActive(true);
        shiftRepository.save(shift);
        System.out.println("Tạo ca làm việc mặc định 'Hành chính'.");
    }

    /**
     * Các bản ghi tạo trước khi có cột auth_source đều là null. User đồng bộ từ 365
     * không bao giờ được đặt mật khẩu, nên sự có mặt của mật khẩu là dấu hiệu duy nhất
     * còn lại để phân biệt. Chỉ chạy một lần, các lần khởi động sau không còn bản ghi null.
     */
    private void backfillAuthSource() {
        java.util.List<AppUser> pending = appUserRepository.findAll().stream()
                .filter(u -> u.getAuthSource() == null)
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        for (AppUser u : pending) {
            boolean hasPassword = u.getPassword() != null && !u.getPassword().isEmpty();
            u.setAuthSource(hasPassword ? AppUser.SOURCE_LOCAL : AppUser.SOURCE_M365);
        }
        appUserRepository.saveAll(pending);
        System.out.println("Đã gán nguồn tài khoản cho " + pending.size() + " user cũ.");
    }

    /**
     * Danh mục công cụ dụng cụ mặc định, được tối ưu và thu gọn:
     * 1. Thiết bị CNTT & Mạng (gộp CNTT, mạng, điện thoại, camera, tivi)
     * 2. Bàn ghế & Nội thất (gộp bàn ghế)
     * 3. Thiết bị điện & Gia dụng (gộp máy nước nóng, quạt)
     * 4. Khác
     */
    private void seedAssetCategories() {
        java.util.List<AssetCategory> existingList = assetCategoryRepository.findAll();
        java.util.Map<String, AssetCategory> categoryMap = new java.util.HashMap<>();
        for (AssetCategory c : existingList) {
            if (c.getName() != null) {
                categoryMap.put(c.getName(), c);
            }
        }

        seedCategoryIfAbsent(categoryMap, "Thiết bị CNTT & Mạng", "fa-solid fa-laptop", true, 1);
        seedCategoryIfAbsent(categoryMap, "Bàn ghế & Nội thất", "fa-solid fa-chair", false, 2);
        seedCategoryIfAbsent(categoryMap, "Thiết bị điện & Gia dụng", "fa-solid fa-plug", false, 3);
        seedCategoryIfAbsent(categoryMap, "Khác", "fa-solid fa-box", false, 99);

        AssetCategory cnttCat = categoryMap.get("Thiết bị CNTT & Mạng");
        AssetCategory furnitureCat = categoryMap.get("Bàn ghế & Nội thất");
        AssetCategory applianceCat = categoryMap.get("Thiết bị điện & Gia dụng");

        Long cnttDestId = cnttCat != null ? cnttCat.getId() : null;
        Long furnitureDestId = furnitureCat != null ? furnitureCat.getId() : null;
        Long applianceDestId = applianceCat != null ? applianceCat.getId() : null;

        mergeCategory(categoryMap, "Thiết bị CNTT", cnttDestId);
        mergeCategory(categoryMap, "Thiết bị mạng", cnttDestId);
        mergeCategory(categoryMap, "Điện thoại", cnttDestId);
        mergeCategory(categoryMap, "Camera", cnttDestId);
        mergeCategory(categoryMap, "Tivi", cnttDestId);
        mergeCategory(categoryMap, "Bàn ghế", furnitureDestId);
        mergeCategory(categoryMap, "Máy nước nóng", applianceDestId);
        mergeCategory(categoryMap, "Quạt", applianceDestId);
    }

    private AssetCategory seedCategoryIfAbsent(java.util.Map<String, AssetCategory> categoryMap, String name, String icon, boolean itEquipment, int sortOrder) {
        if (!categoryMap.containsKey(name)) {
            AssetCategory cat = assetCategoryRepository.save(new AssetCategory(name, icon, itEquipment, sortOrder));
            categoryMap.put(name, cat);
            return cat;
        }
        return categoryMap.get(name);
    }

    private void mergeCategory(java.util.Map<String, AssetCategory> categoryMap, String oldName, Long newCategoryId) {
        if (newCategoryId == null) return;
        AssetCategory oldCat = categoryMap.get(oldName);
        if (oldCat != null) {
            if (oldCat.getId().equals(newCategoryId)) return;
            
            // Chuyển toàn bộ tài sản sang danh mục mới
            java.util.List<Asset> assets = assetRepository.findByCategoryIdOrderByCreatedAtDesc(oldCat.getId());
            for (Asset a : assets) {
                a.setCategoryId(newCategoryId);
            }
            assetRepository.saveAll(assets);
            
            // Xóa danh mục cũ
            try {
                assetCategoryRepository.delete(oldCat);
                categoryMap.remove(oldName);
                System.out.println("Đã gộp danh mục cũ '" + oldName + "' sang danh mục mới.");
            } catch (Exception e) {
                System.err.println("Không thể xóa danh mục cũ '" + oldName + "': " + e.getMessage());
            }
        }
    }
}
