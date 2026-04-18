package com.message.domain.blocklist.service.query.service;

import com.message.domain.blocklist.query.BlockCheckQuery;
import com.message.domain.blocklist.repository.BlocklistRepository;
import com.message.domain.blocklist.service.query.usecase.BlockQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BlockQueryService implements BlockQueryUseCase {

    private final BlocklistRepository blocklistRepository;

    public boolean isBlocked(BlockCheckQuery blockCheckQuery) {
        if (blockCheckQuery.phone() != null && blocklistRepository.existsByCellPhone(blockCheckQuery.phone())) {
            return true;
        }
        return blockCheckQuery.email() != null && blocklistRepository.existsByEmail(blockCheckQuery.email());
    }
}
