package com.message.domain.blocklist.entity;

import com.message.global.crypto.StringCryptoConverter;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "blocklist")
public class Blocklist {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = StringCryptoConverter.class)
    private String email;

    @Convert(converter = StringCryptoConverter.class)
    private String cellPhone;

    private String clientIp;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private LocalDateTime createAt;

}
