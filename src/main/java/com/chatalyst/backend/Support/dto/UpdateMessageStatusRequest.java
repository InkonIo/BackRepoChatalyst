package com.chatalyst.backend.Support.dto;

import com.chatalyst.backend.Support.Entity.MessageStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMessageStatusRequest {
    
    @NotNull(message = "Status cannot be null")
    private MessageStatus status;
}
