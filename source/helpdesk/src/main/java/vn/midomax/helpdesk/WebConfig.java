package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình MVC chung.
 *
 * File upload không còn map từ ổ đĩa ở đây: file công khai đi qua UploadController (/uploads),
 * biên bản CCDC qua AssetDocumentFileController (/asset-docs), selfie chấm công qua
 * AttendanceController — tất cả đọc từ StorageService (MinIO khi production).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private ModuleAccessInterceptor moduleAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Chặn URL các phân hệ theo ma trận phòng ban (sidebar chỉ ẩn menu, đây mới là khóa thật)
        registry.addInterceptor(moduleAccessInterceptor)
                .addPathPatterns("/network/**", "/network", "/assets/**", "/expenses/**",
                                 "/work-reports/**", "/employees/**", "/attendance/**");
    }
}
