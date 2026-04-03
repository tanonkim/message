package com.message.domain.blocklist.repository;

import com.message.domain.blocklist.entity.Blocklist;
import org.springframework.data.repository.CrudRepository;

public interface BlocklistRepository extends CrudRepository<Blocklist, Long> {

    boolean existsByEmail(String email);

    boolean existsByCellPhone(String cellPhone);
}
