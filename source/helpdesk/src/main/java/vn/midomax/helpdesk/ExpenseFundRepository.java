package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExpenseFundRepository extends JpaRepository<ExpenseFund, Long> {

    List<ExpenseFund> findAllByOrderByCreatedAtDesc();

    ExpenseFund findFirstByActiveTrueOrderByCreatedAtDesc();
}
