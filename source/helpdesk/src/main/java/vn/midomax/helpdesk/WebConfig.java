package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private ModuleAccessInterceptor moduleAccessInterceptor;

    @Autowired
    private AssetDocumentService assetDocumentService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map /uploads/** to the absolute path of src/main/resources/static/uploads
        Path uploadDir = Paths.get("src/main/resources/static/uploads");

        // Dùng toUri() để ra đúng "file:///..." trên cả Windows lẫn Linux
        // ("file:/" + đường dẫn tuyệt đối Linux sẽ thành file://opt/... và không phục vụ được).
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDir.toAbsolutePath().toUri().toString());

        // Ảnh biên bản giao nhận/thu hồi: để ở thư mục dữ liệu riêng ngoài vùng build,
        // nên phải khai báo resource handler riêng mới xem được ảnh trên web.
        registry.addResourceHandler("/asset-docs/**")
                .addResourceLocations(assetDocumentService.getStorageDir().toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Chặn URL các phân hệ theo ma trận phòng ban (sidebar chỉ ẩn menu, đây mới là khóa thật)
        registry.addInterceptor(moduleAccessInterceptor)
                .addPathPatterns("/network/**", "/network", "/assets/**", "/expenses/**",
                                 "/work-reports/**", "/employees/**", "/attendance/**");
    }
}
