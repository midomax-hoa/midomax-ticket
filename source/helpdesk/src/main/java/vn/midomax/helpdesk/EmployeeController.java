package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final ExcelService excelService;

    @Autowired
    public EmployeeController(EmployeeService employeeService, ExcelService excelService) {
        this.employeeService = employeeService;
        this.excelService = excelService;
    }

    @GetMapping
    public String viewEmployeeManagement(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "id", required = false) Long id,
            Model model) {
        
        List<Employee> employees = employeeService.searchAndFilter(search, department, status);
        
        Employee emp = new Employee();
        boolean openModal = false;
        boolean viewMode = false;
        
        if (id != null) {
            java.util.Optional<Employee> opt = employeeService.getEmployeeById(id);
            if (opt.isPresent()) {
                emp = opt.get();
                openModal = true;
                if ("view".equals(action)) {
                    viewMode = true;
                }
            }
        }
        
        model.addAttribute("employees", employees);
        model.addAttribute("newEmployee", emp);
        model.addAttribute("openModal", openModal);
        model.addAttribute("viewMode", viewMode);
        model.addAttribute("departments", employeeService.findAllDepartments());
        model.addAttribute("statuses", employeeService.findAllStatuses());
        model.addAttribute("searchQuery", search);
        model.addAttribute("selectedDept", department);
        model.addAttribute("selectedStatus", status);
        
        return "employee-management";
    }

    @PostMapping("/save")
    public String saveEmployee(@ModelAttribute("newEmployee") Employee employee, RedirectAttributes redirectAttributes) {
        employeeService.saveEmployee(employee);
        redirectAttributes.addFlashAttribute("successMessage", "Đã lưu thông tin nhân viên thành công!");
        return "redirect:/employees";
    }

    @GetMapping("/delete/{id}")
    public String deleteEmployee(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        employeeService.deleteEmployee(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa nhân viên!");
        return "redirect:/employees";
    }

    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportExcel() throws IOException {
        List<Employee> employees = employeeService.getAllEmployees();
        ByteArrayInputStream in = excelService.exportEmployeesToExcel(employees);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=NhanSu.xlsx");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn file Excel!");
            return "redirect:/employees";
        }
        
        try {
            List<Employee> employees = excelService.importEmployeesFromExcel(file);
            for (Employee emp : employees) {
                employeeService.saveEmployee(emp);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Đã Import thành công " + employees.size() + " nhân viên!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Có lỗi xảy ra khi đọc file Excel!");
        }
        return "redirect:/employees";
    }
}
