package com.message.domain.blocklist.service.query.usecase;

import com.message.domain.blocklist.query.BlockCheckQuery;

public interface BlockQueryUseCase {
    boolean isBlocked(BlockCheckQuery blockCheckQuery);
}
