package com.lab.pbft.networkObjects.communique;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PrePrepare implements Serializable {
    private static final long serialVersionUID = 1L;

    private long currentView;

    private long sequenceNumber;

    private String requestDigest;

    private Request request;

}
