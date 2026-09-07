package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, Long> {
    java.util.List<SoftwareLicense> findAllByOrderByNameAsc();
}
