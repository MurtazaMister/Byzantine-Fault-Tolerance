package com.lab.pbft.model.primary;

import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.util.ConverterUtil.PrePrepareConverter;
import com.lab.pbft.util.ConverterUtil.ReplyConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "replyLog")
public class ReplyLog {

    @Id
    private String timestamp;

    private long clientId;

    @Convert(converter = ReplyConverter.class)
    @Column(columnDefinition = "MEDIUMTEXT")
    private Reply reply;

}
