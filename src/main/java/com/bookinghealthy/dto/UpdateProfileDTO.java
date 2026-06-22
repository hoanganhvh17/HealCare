package com.bookinghealthy.dto;

import lombok.Data;

@Data
public class UpdateProfileDTO {
    private String fullName;
    private String email;
    private String phone;
}
