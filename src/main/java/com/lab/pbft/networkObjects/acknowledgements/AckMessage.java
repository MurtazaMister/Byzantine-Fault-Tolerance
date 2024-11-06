package com.lab.pbft.networkObjects.acknowledgements;
import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AckMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String message;
}
