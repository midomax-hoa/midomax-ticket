package vn.midomax.helpdesk;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Chặn truy cập trực tiếp bằng URL vào các phân hệ mà user không được cấp
 * (ẩn menu thôi chưa đủ — gõ thẳng /assets vẫn vào được nếu không chặn ở đây).
 * Không được cấp thì đưa về trang chủ.
 */
@Component
public class ModuleAccessInterceptor implements HandlerInterceptor {

    @Autowired
    private ModuleAccessService moduleAccessService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        AppModule module = moduleOf(request.getRequestURI());
        if (module == null) {
            return true; // URL không thuộc phân hệ giới hạn nào
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (moduleAccessService.canAccess(auth, module)) {
            return true;
        }
        response.sendRedirect("/");
        return false;
    }

    /** Ánh xạ tiền tố URL -> phân hệ. Thêm phân hệ mới thì bổ sung tại đây. */
    private AppModule moduleOf(String uri) {
        if (uri == null) return null;
        // Lịch chấm công CÁ NHÂN: ai đăng nhập cũng xem được của chính mình, không cần phân hệ HR
        if (uri.startsWith("/attendance/my")) return null;
        // Trang duyệt đơn nghỉ/đi trễ: trưởng phòng + nhân sự vào được dù không có phân hệ HR
        // (AttendanceController tự kiểm tra quyền duyệt bên trong)
        if (uri.startsWith("/attendance/requests")) return null;
        if (uri.startsWith("/network")) return AppModule.NETWORK;
        if (uri.startsWith("/assets")) return AppModule.ASSETS;
        if (uri.startsWith("/expenses")) return AppModule.FINANCE;
        if (uri.startsWith("/work-reports")) return AppModule.REPORTS;
        if (uri.startsWith("/employees") || uri.startsWith("/attendance")) return AppModule.HR;
        return null;
    }
}
