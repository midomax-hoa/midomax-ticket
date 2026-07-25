import os, re

files = [
    r'd:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\admin-home.html',
    r'd:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\dashboard.html',
    r'd:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\ticket-management.html',
    r'd:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\employee-management.html'
]

sidebar_template = """<!-- Menu Items -->
        <div class="sidebar-menu">
            <a href="/" title="Trang chủ" th:classappend="${#httpServletRequest.requestURI == '/' or #httpServletRequest.requestURI == '/admin-home'} ? 'active' : ''">
                <div class="icon-wrapper"><i class="fa-solid fa-house"></i></div>
                <span>Trang chủ</span>
            </a>
            <a href="/dashboard" title="Dashboard" th:classappend="${#httpServletRequest.requestURI == '/dashboard' or #httpServletRequest.requestURI == '/user-home'} ? 'active' : ''">
                <div class="icon-wrapper"><i class="fa-solid fa-chart-pie"></i></div>
                <span>Dashboard</span>
            </a>
            <a href="/ticket-management" title="Quản lý Ticket" th:classappend="${#httpServletRequest.requestURI == '/ticket-management'} ? 'active' : ''">
                <div class="icon-wrapper"><i class="fa-solid fa-ticket"></i></div>
                <span>Quản lý Ticket</span>
            </a>
            <a href="#" sec:authorize="hasRole('ADMIN')" title="Mạng">
                <div class="icon-wrapper"><i class="fa-solid fa-network-wired"></i></div>
                <span>Mạng</span>
            </a>
            <a href="#" sec:authorize="hasRole('ADMIN')" title="Công Cụ Dụng Cụ">
                <div class="icon-wrapper"><i class="fa-solid fa-boxes-stacked"></i></div>
                <span>Công Cụ Dụng Cụ</span>
            </a>
            <a href="/schedule" title="Lịch Trình" th:classappend="${#httpServletRequest.requestURI == '/schedule'} ? 'active' : ''">
                <div class="icon-wrapper"><i class="fa-solid fa-calendar-check"></i></div>
                <span>Lịch Trình</span>
            </a>
            <a href="/employees" sec:authorize="hasRole('ADMIN')" title="Quản Lý Nhân Sự" th:classappend="${#httpServletRequest.requestURI == '/employees'} ? 'active' : ''">
                <div class="icon-wrapper"><i class="fa-solid fa-user-tie"></i></div>
                <span>Quản Lý Nhân Sự</span>
            </a>
            <div sec:authorize="hasRole('ADMIN')" style="margin: 10px 25px; border-top: 1px solid #e5e7eb;"></div>
            <a href="#" sec:authorize="hasRole('ADMIN')" title="Quản Lý User">
                <div class="icon-wrapper"><i class="fa-solid fa-users-gear"></i></div>
                <span>Quản Lý User</span>
            </a>
            <div sec:authorize="hasRole('USER')" style="margin: 10px 25px; border-top: 1px solid #e5e7eb;"></div>
            <a href="/profile" sec:authorize="hasRole('USER')" title="Profile" th:classappend="${#httpServletRequest.requestURI == '/profile'} ? 'active' : ''">
                <div class="icon-wrapper"><i class="fa-solid fa-user"></i></div>
                <span>Profile</span>
            </a>
        </div>

        <!-- Bottom Account -->
        <div class="sidebar-bottom p-0" style="margin-top: auto; border-top: 1px solid #e5e7eb; cursor: default;">
             <form th:action="@{/logout}" method="post" class="m-0 w-100" style="display: flex; align-items: center; justify-content: space-between; padding: 15px 25px;">
                  <div class="d-flex align-items-center" style="overflow: hidden;">
                      <div class="sidebar-avatar text-white d-flex align-items-center justify-content-center fw-bold" style="width: 40px; height: 40px; border-radius: 12px; background: linear-gradient(135deg, #1e3a8a, #3b82f6);">
                          <span sec:authorize="hasRole('ADMIN')">IT</span>
                          <span sec:authorize="hasRole('USER')">US</span>
                      </div>
                      <div class="sidebar-user-info ms-3">
                          <div class="sidebar-user-name fw-bold" style="font-size: 14px; color: #1e3a8a; text-transform: capitalize;" sec:authentication="name">Super Admin</div>
                          <div class="sidebar-user-role" style="font-size: 12px; color: #64748b;">
                              <span sec:authorize="hasRole('ADMIN')">Quản trị viên</span>
                              <span sec:authorize="hasRole('USER')">Nhân viên</span>
                          </div>
                      </div>
                  </div>
                  <button type="submit" class="btn btn-link p-0 border-0 text-decoration-none sidebar-logout" style="font-size: 16px; margin-left: auto;" title="Đăng xuất">
                      <i class="fa-solid fa-arrow-right-from-bracket"></i>
                  </button>
             </form>
        </div>
    </div>"""

for f in files:
    if not os.path.exists(f): continue
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    # Replace sidebar
    content = re.sub(r'<!-- Menu Items -->\s*<div class="sidebar-menu">.*?</a>\s*</div>', sidebar_template, content, flags=re.DOTALL)
    
    # Add spring-security namespace if missing
    if 'xmlns:sec=' not in content:
        content = content.replace('<html lang="vi" xmlns:th="http://www.thymeleaf.org">', '<html lang="vi" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">')
        content = content.replace('<html xmlns:th="http://www.thymeleaf.org">', '<html xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">')
        
    with open(f, 'w', encoding='utf-8') as file:
        file.write(content)
    print('Updated ' + f)
