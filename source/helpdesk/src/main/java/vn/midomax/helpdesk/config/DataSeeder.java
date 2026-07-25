package vn.midomax.helpdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.midomax.helpdesk.AppUser;
import vn.midomax.helpdesk.AppUserRepository;
import vn.midomax.helpdesk.Asset;
import vn.midomax.helpdesk.AssetRepository;
import vn.midomax.helpdesk.AssetCategory;
import vn.midomax.helpdesk.AssetCategoryRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    /** Mật khẩu tài khoản admin lúc tạo mới. Production phải đặt qua biến môi trường. */
    @Value("${app.seed.admin-password:admin123}")
    private String adminPassword;

    /** Tài khoản "user" chỉ dùng để thử nghiệm, production nên tắt đi. */
    @Value("${app.seed.demo-user:true}")
    private boolean seedDemoUser;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AssetCategoryRepository assetCategoryRepository;
    private final AssetRepository assetRepository;

    public DataSeeder(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                      AssetCategoryRepository assetCategoryRepository, AssetRepository assetRepository) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.assetCategoryRepository = assetCategoryRepository;
        this.assetRepository = assetRepository;
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
        seedCategory("Thiết bị CNTT & Mạng", "fa-solid fa-laptop", true, 1);
        seedCategory("Bàn ghế & Nội thất", "fa-solid fa-chair", false, 2);
        seedCategory("Thiết bị điện & Gia dụng", "fa-solid fa-plug", false, 3);
        seedCategory("Khác", "fa-solid fa-box", false, 99);

        // Lấy ID của danh mục đích
        Long cnttDestId = assetCategoryRepository.findByName("Thiết bị CNTT & Mạng").map(AssetCategory::getId).orElse(null);
        Long furnitureDestId = assetCategoryRepository.findByName("Bàn ghế & Nội thất").map(AssetCategory::getId).orElse(null);
        Long applianceDestId = assetCategoryRepository.findByName("Thiết bị điện & Gia dụng").map(AssetCategory::getId).orElse(null);

        // Gộp các danh mục cũ sang danh mục mới
        mergeCategory("Thiết bị CNTT", cnttDestId);
        mergeCategory("Thiết bị mạng", cnttDestId);
        mergeCategory("Điện thoại", cnttDestId);
        mergeCategory("Camera", cnttDestId);
        mergeCategory("Tivi", cnttDestId);
        mergeCategory("Bàn ghế", furnitureDestId);
        mergeCategory("Máy nước nóng", applianceDestId);
        mergeCategory("Quạt", applianceDestId);
    }

    private void seedCategory(String name, String icon, boolean itEquipment, int sortOrder) {
        if (!assetCategoryRepository.existsByName(name)) {
            assetCategoryRepository.save(new AssetCategory(name, icon, itEquipment, sortOrder));
        }
    }

    private void mergeCategory(String oldName, Long newCategoryId) {
        if (newCategoryId == null) return;
        assetCategoryRepository.findByName(oldName).ifPresent(oldCat -> {
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
                System.out.println("Đã gộp danh mục cũ '" + oldName + "' sang danh mục mới.");
            } catch (Exception e) {
                System.err.println("Không thể xóa danh mục cũ '" + oldName + "': " + e.getMessage());
            }
        });
    }
}
