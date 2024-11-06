package com.lab.pbft.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateUserDTO {
    @NotNull(message = "User id cannot be null")
    private String username;
    @NotNull(message = "Password cannot be null")
    private String password;
}
