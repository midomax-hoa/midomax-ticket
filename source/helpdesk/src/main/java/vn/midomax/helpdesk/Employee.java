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
    private LocalDate idCardIssueDate; // Ngày cấp
    private String idCardIssuePlace; // Nơi cấp
    
    private String personalPhoneNumber; // Số điện thoại cá nhân
    private String companyPhoneNumber; // Số điện thoại cty cấp
    private String taxCode; // Mã số thuế

    // --- HỢP ĐỒNG & BẢO HIỂM ---
    private LocalDate joinDate; // Ngày vào công ty
    private LocalDate officialSignDate; // Ngày ký chính thức
    private Integer signMonth; // Tháng ký
    private Integer signYear; // Năm ký
    private Double yearsOfWork; // Số năm làm việc
    private String contractNumber; // Số HĐLĐ
    private String workStatus; // Tình trạng làm việc
    
    private String socialInsuranceNumber; // Sổ BH
    private Boolean isInsurancePaidAtCompany; // Đóng BH tại công ty
    private String insurancePremium; // Mức đóng bảo hiểm
    
    private String bankAccountNumber; // STK
    private String bankName; // Ngân hàng
    private String bankBranch; // Chi nhánh
    
    private LocalDate maternityLeaveFrom; // Thai sản từ ngày
    private LocalDate returnToWorkDate; // Ngày đi làm lại
    
    private LocalDate resignDate; // Ngày nghỉ việc
    private String resignReason; // Lý do nghỉ
    
    private String note; // Ghi chú

    // --- HỒ SƠ ĐÍNH KÈM (Checkboxes) ---
    private Boolean docResume; // Sơ yếu lý lịch
    private Boolean docIdCard; // CCCD
    private Boolean docHealthCert; // Giấy KSK
    private Boolean docDegree; // Bằng cấp
    private Boolean docPhoto; // Ảnh 3x4
    private Boolean docCv; // CV cá nhân
    private Boolean docSocialInsurance; // Sổ BH & Tờ rời
    private Boolean docParentId; // CCCD Bố/Mẹ
    private Boolean docHrConfirmation; // Xác nhận nhân sự
    private Boolean docDriverLicense; // Bằng lái xe
    private Boolean docBirthCert; // Giấy KS
    private Boolean docHouseholdRegistration; // Photo hộ khẩu
    
    private String recruitmentSource; // Nguồn tuyển
    private String maritalStatus; // Tình trạng hôn nhân

    // --- THÔNG TIN GIA ĐÌNH ---
    private String fatherName;
    private String fatherYob;
    private String fatherPhone;
    
    private String motherName;
    private String motherYob;
    private String motherPhone;
    
    private String spouseName;
    private String spouseYob;
    private String spousePhone;
    
    private Integer numberOfChildren;

    // Relatives list stored as JSON string to simplify for now, or just map them dynamically if needed.
    // For simplicity, we will store them as a large text block of JSON since the UI can build it up,
    // or as element collections.
    @ElementCollection
    @CollectionTable(name = "employee_children", joinColumns = @JoinColumn(name = "employee_id"))
    private List<RelativeInfo> children = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "employee_emergency_contacts", joinColumns = @JoinColumn(name = "employee_id"))
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    // --- CONSTRUCTORS, GETTERS, SETTERS ---
    
    public Employee() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
    
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    
    public String getProfessionalTitle() { return professionalTitle; }
    public void setProfessionalTitle(String professionalTitle) { this.professionalTitle = professionalTitle; }
    
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    
    public String getPermanentAddress() { return permanentAddress; }
    public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }
    
    public String getHamlet() { return hamlet; }
    public void setHamlet(String hamlet) { this.hamlet = hamlet; }
    
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }
    
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    
    public String getCompanyEmail() { return companyEmail; }
    public void setCompanyEmail(String companyEmail) { this.companyEmail = companyEmail; }
    
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    
    public LocalDate getIdCardIssueDate() { return idCardIssueDate; }
    public void setIdCardIssueDate(LocalDate idCardIssueDate) { this.idCardIssueDate = idCardIssueDate; }
    
    public String getIdCardIssuePlace() { return idCardIssuePlace; }
    public void setIdCardIssuePlace(String idCardIssuePlace) { this.idCardIssuePlace = idCardIssuePlace; }
    
    public String getPersonalPhoneNumber() { return personalPhoneNumber; }
    public void setPersonalPhoneNumber(String personalPhoneNumber) { this.personalPhoneNumber = personalPhoneNumber; }
    
    public String getCompanyPhoneNumber() { return companyPhoneNumber; }
    public void setCompanyPhoneNumber(String companyPhoneNumber) { this.companyPhoneNumber = companyPhoneNumber; }
    
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    
    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    
    public LocalDate getOfficialSignDate() { return officialSignDate; }
    public void setOfficialSignDate(LocalDate officialSignDate) { this.officialSignDate = officialSignDate; }
    
    public Integer getSignMonth() { return signMonth; }
    public void setSignMonth(Integer signMonth) { this.signMonth = signMonth; }
    
    public Integer getSignYear() { return signYear; }
    public void setSignYear(Integer signYear) { this.signYear = signYear; }
    
    public Double getYearsOfWork() { return yearsOfWork; }
    public void setYearsOfWork(Double yearsOfWork) { this.yearsOfWork = yearsOfWork; }
    
    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }
    
    public String getWorkStatus() { return workStatus; }
    public void setWorkStatus(String workStatus) { this.workStatus = workStatus; }
    
    public String getSocialInsuranceNumber() { return socialInsuranceNumber; }
    public void setSocialInsuranceNumber(String socialInsuranceNumber) { this.socialInsuranceNumber = socialInsuranceNumber; }
    
    public Boolean getIsInsurancePaidAtCompany() { return isInsurancePaidAtCompany; }
    public void setIsInsurancePaidAtCompany(Boolean isInsurancePaidAtCompany) { this.isInsurancePaidAtCompany = isInsurancePaidAtCompany; }
    
    public String getInsurancePremium() { return insurancePremium; }
    public void setInsurancePremium(String insurancePremium) { this.insurancePremium = insurancePremium; }
    
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
    
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    
    public String getBankBranch() { return bankBranch; }
    public void setBankBranch(String bankBranch) { this.bankBranch = bankBranch; }
    
    public LocalDate getMaternityLeaveFrom() { return maternityLeaveFrom; }
    public void setMaternityLeaveFrom(LocalDate maternityLeaveFrom) { this.maternityLeaveFrom = maternityLeaveFrom; }
    
    public LocalDate getReturnToWorkDate() { return returnToWorkDate; }
    public void setReturnToWorkDate(LocalDate returnToWorkDate) { this.returnToWorkDate = returnToWorkDate; }
    
    public LocalDate getResignDate() { return resignDate; }
    public void setResignDate(LocalDate resignDate) { this.resignDate = resignDate; }
    
    public String getResignReason() { return resignReason; }
    public void setResignReason(String resignReason) { this.resignReason = resignReason; }
    
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    
    public Boolean getDocResume() { return docResume; }
    public void setDocResume(Boolean docResume) { this.docResume = docResume; }
    
    public Boolean getDocIdCard() { return docIdCard; }
    public void setDocIdCard(Boolean docIdCard) { this.docIdCard = docIdCard; }
    
    public Boolean getDocHealthCert() { return docHealthCert; }
    public void setDocHealthCert(Boolean docHealthCert) { this.docHealthCert = docHealthCert; }
    
    public Boolean getDocDegree() { return docDegree; }
    public void setDocDegree(Boolean docDegree) { this.docDegree = docDegree; }
    
    public Boolean getDocPhoto() { return docPhoto; }
    public void setDocPhoto(Boolean docPhoto) { this.docPhoto = docPhoto; }
    
    public Boolean getDocCv() { return docCv; }
    public void setDocCv(Boolean docCv) { this.docCv = docCv; }
    
    public Boolean getDocSocialInsurance() { return docSocialInsurance; }
    public void setDocSocialInsurance(Boolean docSocialInsurance) { this.docSocialInsurance = docSocialInsurance; }
    
    public Boolean getDocParentId() { return docParentId; }
    public void setDocParentId(Boolean docParentId) { this.docParentId = docParentId; }
    
    public Boolean getDocHrConfirmation() { return docHrConfirmation; }
    public void setDocHrConfirmation(Boolean docHrConfirmation) { this.docHrConfirmation = docHrConfirmation; }
    
    public Boolean getDocDriverLicense() { return docDriverLicense; }
    public void setDocDriverLicense(Boolean docDriverLicense) { this.docDriverLicense = docDriverLicense; }
    
    public Boolean getDocBirthCert() { return docBirthCert; }
    public void setDocBirthCert(Boolean docBirthCert) { this.docBirthCert = docBirthCert; }
    
    public Boolean getDocHouseholdRegistration() { return docHouseholdRegistration; }
    public void setDocHouseholdRegistration(Boolean docHouseholdRegistration) { this.docHouseholdRegistration = docHouseholdRegistration; }
    
    public String getRecruitmentSource() { return recruitmentSource; }
    public void setRecruitmentSource(String recruitmentSource) { this.recruitmentSource = recruitmentSource; }
    
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    
    public String getFatherName() { return fatherName; }
    public void setFatherName(String fatherName) { this.fatherName = fatherName; }
    
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
