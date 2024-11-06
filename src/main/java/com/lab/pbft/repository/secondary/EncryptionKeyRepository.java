package com.lab.pbft.repository.secondary;

import com.lab.pbft.model.secondary.EncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncryptionKeyRepository extends JpaRepository<EncryptionKey, Long> {
    EncryptionKey findById(long id);
}
