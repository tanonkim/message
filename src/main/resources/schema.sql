-- 통합 발송 이력
CREATE TABLE IF NOT EXISTS notification_log (
    notification_log_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key       VARCHAR(64) UNIQUE COMMENT '중복 발송 방지 키',
    service_id            VARCHAR(50) NOT NULL,
    channel               VARCHAR(20) NOT NULL COMMENT 'SMS|ALIMTALK|EMAIL|PUSH',
    priority              VARCHAR(10) NOT NULL COMMENT 'CRITICAL|HIGH|NORMAL|LOW',
    recipient             VARCHAR(500) NOT NULL COMMENT 'AES 암호화',
    template_id           VARCHAR(100),
    variables             TEXT COMMENT 'JSON',
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count           INT DEFAULT 0,
    fallback_channel      VARCHAR(20),
    provider_message_id   VARCHAR(100),
    error_message         TEXT,
    cost                  DECIMAL(10, 4) DEFAULT 0,
    create_at             DATETIME NOT NULL,
    sent_at               DATETIME,
    INDEX idx_idempotency (idempotency_key),
    INDEX idx_service_channel (service_id, channel),
    INDEX idx_status (status),
    INDEX idx_create_at (create_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 수신 차단 목록
CREATE TABLE IF NOT EXISTS blocklist (
    blocklist_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(500) COMMENT 'AES 암호화',
    cell_phone    VARCHAR(500) COMMENT 'AES 암호화',
    client_ip     VARCHAR(50),
    memo          TEXT,
    create_at     DATETIME NOT NULL,
    INDEX idx_email (email),
    INDEX idx_cell_phone (cell_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 알림 템플릿
CREATE TABLE IF NOT EXISTS notification_template (
    notification_template_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code             VARCHAR(100) NOT NULL UNIQUE,
    channel                   VARCHAR(20) NOT NULL,
    template_id               VARCHAR(100),
    pf_id                     VARCHAR(100),
    title                     VARCHAR(200),
    description               VARCHAR(500),
    is_active                 BOOLEAN NOT NULL DEFAULT TRUE,
    create_at                 DATETIME NOT NULL,
    update_at                 DATETIME NOT NULL,
    INDEX idx_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
