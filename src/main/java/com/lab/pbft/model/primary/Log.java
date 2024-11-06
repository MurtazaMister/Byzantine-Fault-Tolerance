package com.lab.pbft.model.primary;

import com.lab.pbft.networkObjects.communique.PrePrepare;
import com.lab.pbft.util.ConverterUtil.MapConverter;
import com.lab.pbft.util.ConverterUtil.PrePrepareConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "log")
public class Log {

    public enum Type{
        PRE_PREPARE,
        PREPARED,
        COMMITED,
        EXECUTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long sequenceNumber;

    private long viewNumber;

    @Enumerated(EnumType.STRING)
    private Type type;

    private boolean approved;

    @Convert(converter = PrePrepareConverter.class)
    @Column(columnDefinition = "MEDIUMTEXT")
    private PrePrepare prePrepare;

    @Convert(converter = MapConverter.class)
    @Column(columnDefinition = "MEDIUMTEXT")
    // Will in the end, hold signatures of commit messages
    // Signatures of sent reply message will be held in reply log
    private Map<Long, String> signatures;
}
