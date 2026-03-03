package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(
        name = "sector",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sector_name", columnNames = {"name"}),
                @UniqueConstraint(name = "uk_sector_scheme_code", columnNames = {"scheme", "code"})
        }
)
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectorId;

    @Column(length = 30)
    private String scheme; // e.g., KRX_BZTP_M

    @Column(length = 50)
    private String code;   // e.g., idx_bztp_mcls_cd

    @Column(length = 100, nullable = false, unique = true)
    private String name;

    public void setScheme(String scheme) { this.scheme = scheme; }
    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
}
