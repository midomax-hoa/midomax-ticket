package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- THÔNG TIN CHUNG ---
    private String employeeCode; // Mã số lao động
    private String fullName; // Họ và tên
    private String department; // Phòng ban
    private String division; // Bộ phận
    private String position; // Chức vụ
    private String professionalTitle; // Chức danh chuyên môn
    private String gender; // Giới tính
    private String branch; // Chi nhánh

    // --- CÁ NHÂN & LIÊN HỆ ---
    private LocalDate dob; // Ngày sinh
    
    private String currentAddress; // Chỗ ở hiện tại
    private String permanentAddress; // Hộ khẩu thường trú đầy đủ
    private String hamlet; // Thôn/xóm
    private String ward; // Phường/xã
    private String district; // Quận/Huyện
    private String city; // Tỉnh/Thành phố
    
    private String educationLevel; // Trình độ
    private String major; // Chuyên ngành học
    private String personalEmail; // Gmail cá nhân
    private String companyEmail; // Email công ty
    
    private String idCardNumber; // Số CCCD
    private LocalDate idCardIssueDa
    
    public String getFatherYob() { return fatherYob; }
    public void setFatherYob(String fatherYob) { this.fatherYob = fatherYob; }
    
    public String getFatherPhone() { return fatherPhone; }
    public void setFatherPhone(String fatherPhone) { this.fatherPhone = fatherPhone; }
    
    public String getMotherName() { return motherName; }
    public void setMotherName(String motherName) { this.motherName = motherName; }
    
    public String getMotherYob() { return motherYob; }
    public void setMotherYob(String motherYob) { this.motherYob = motherYob; }
    
    public String getMotherPhone() { return motherPhone; }
    public void setMotherPhone(String motherPhone) { this.motherPhone = motherPhone; }
    
    public String getSpouseName() { return spouseName; }
    public void setSpouseName(String spouseName) { this.spouseName = spouseName; }
    
    public String getSpouseYob() { return spouseYob; }
    public void setSpouseYob(String spouseYob) { this.spouseYob = spouseYob; }
    
    public String getSpousePhone() { return spousePhone; }
    public void setSpousePhone(String spousePhone) { this.spousePhone = spousePhone; }
    
    public Integer getNumberOfChildren() { return numberOfChildren; }
    public void setNumberOfChildren(Integer numberOfChildren) { this.numberOfChildren = numberOfChildren; }

    public List<RelativeInfo> getChildren() { return children; }
    public void setChildren(List<RelativeInfo> children) { this.children = children; }

    public List<EmergencyContact> getEmergencyContacts() { return emergencyContacts; }
    public void setEmergencyContacts(List<EmergencyContact> emergencyContacts) { this.emergencyContacts = emergencyContacts; }
}

