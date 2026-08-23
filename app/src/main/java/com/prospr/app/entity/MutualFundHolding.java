package com.prospr.app.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "mutual_fund_holdings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class MutualFundHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Member member;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "consent_id")
    private String consentId;

    @Column(name = "account_ref")
    private String accountRef;

    @Column(name = "masked_account_number")
    private String maskedAccountNumber;

    @Column(name = "cost_value", precision = 15, scale = 2)
    private BigDecimal costValue;

    @Column(name = "current_value", precision = 15, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "amc")
    private String amc;

    @Column(name = "registrar")
    private String registrar;

    @Column(name = "scheme_code")
    private String schemeCode;

    @Column(name = "scheme_option")
    private String schemeOption;

    @Column(name = "isin", nullable = false)
    private String isin;

    @Column(name = "isin_description")
    private String isinDescription;

    @Column(name = "ucc")
    private String ucc;

    @Column(name = "folio_no")
    private String folioNo;

    @Column(name = "closing_units", precision = 18, scale = 4)
    private BigDecimal closingUnits;

    @Column(name = "lien_units", precision = 18, scale = 4)
    private BigDecimal lienUnits;

    @Column(name = "nav", precision = 18, scale = 4)
    private BigDecimal nav;

    @Column(name = "nav_date")
    private LocalDate navDate;

    @Column(name = "lockin_units", precision = 18, scale = 4)
    private BigDecimal lockinUnits;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
