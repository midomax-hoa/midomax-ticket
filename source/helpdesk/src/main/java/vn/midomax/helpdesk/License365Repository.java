package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface License365Repository extends JpaRepository<License365, Long> {

    Optional<License365> findBySkuId(String skuId);

    List<License365> findAllByOrderByDisplayNameAsc();
}
