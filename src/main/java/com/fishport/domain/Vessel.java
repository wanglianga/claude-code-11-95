package com.fishport.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vessels")
public class Vessel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "vessel_no", nullable = false, unique = true)
    private String vesselNo;
    @Column(nullable = false)
    private String name;
    @Column(name = "owner_name", nullable = false)
    private String ownerName;
    @Column(name = "owner_phone")
    private String ownerPhone;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVesselNo() { return vesselNo; }
    public void setVesselNo(String vesselNo) { this.vesselNo = vesselNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
