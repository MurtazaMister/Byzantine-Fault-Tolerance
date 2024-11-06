package com.lab.pbft.networkObjects.acknowledgements;

import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AckServerStatusUpdate implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean serverFailed;
}
