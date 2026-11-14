package com.arena.core.profile;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="BUSINESS_LOGO") @Getter @Setter
public class BusinessLogo {
 @Id private Long customerId;
 @Column(nullable=false,columnDefinition="bytea") private byte[] image;
}
