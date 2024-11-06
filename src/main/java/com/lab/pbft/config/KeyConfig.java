package com.lab.pbft.config;

import com.lab.pbft.model.secondary.EncryptionKey;
import com.lab.pbft.repository.secondary.EncryptionKeyRepository;
import com.lab.pbft.service.ExitService;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.service.client.ClientService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@DependsOn("secondaryEntityManagerFactory")
@Getter
@Slf4j
public class KeyConfig {

    @Autowired
    private EncryptionKeyRepository encryptionKeyRepository;

    private Map<Long, PublicKey> publicKeyStore;

    private PrivateKey privateKey;

    @Autowired
    private SocketService socketService;

    @Autowired
    private ClientService clientService;
    @Autowired
    private ExitService exitService;

    @PostConstruct
    public void init() {
        Long id = (socketService.getAssignedPort()!=-1)?socketService.getAssignedPort():clientService.getUserId();

        if(id == null || id == -1) return;

        EncryptionKey currentKey = encryptionKeyRepository.findById(id).orElse(null);
        if(currentKey != null){
            try {
                privateKey = extractPrivateKey(currentKey.getPrivateKey());
                log.info("Private key for id: {} has been setup", id);
            } catch (Exception e) {
                log.error("Cannot convert to private key for id: {}", currentKey.getId());
                log.error("Exception: {}", e.getMessage());
                exitService.exitApplication(0);
            }
        }
        else{
            log.error("Unable to retrieve private key for {}", id);
            exitService.exitApplication(0);
        }

        List<EncryptionKey> keys = encryptionKeyRepository.findAll();

        publicKeyStore = new HashMap<>();

        for(EncryptionKey key : keys) {
            try {
                publicKeyStore.put(key.getId(), this.extractPublicKey(key.getPublicKey()));
            } catch (Exception e) {
                log.error("Cannot convert to public key for id: {}, key: {}", key.getId(), key.getPublicKey());
                log.error("Exception: {}", e.getMessage());
            }
        }

        log.info("Public key store setup");
    }

    public void reinit() {
        init();
    }

    private PublicKey extractPublicKey(String key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    public static PrivateKey extractPrivateKey(String base64PrivateKey) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
        ASN1Sequence sequence = ASN1Sequence.getInstance(keyBytes);
        RSAPrivateKey rsa = RSAPrivateKey.getInstance(sequence);
        RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(rsa.getModulus(), rsa.getPrivateExponent());

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }


}
