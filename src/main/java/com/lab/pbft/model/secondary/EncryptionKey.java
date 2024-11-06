package com.lab.pbft.model.secondary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "encryption_keys")
public class EncryptionKey {
    @Id
    @Column(name = "id")
    private Long id;
    @Column(name = "public_key")
    private String publicKey;
    @Column(name = "private_key")
    private String privateKey;

}
