package com.lab.pbft.networkObjects.communique;

import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String message;
}
