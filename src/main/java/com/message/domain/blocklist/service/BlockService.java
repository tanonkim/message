package com.message.domain.blocklist.service;

import com.message.domain.blocklist.query.BlockCheckQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BlockService {

    private final DetailBlockService detailBlockService;

    public boolean isBlocked(BlockCheckQuery blockCheckQuery) {
        return detailBlockService.isBlocked(blockCheckQuery);
    }
}
