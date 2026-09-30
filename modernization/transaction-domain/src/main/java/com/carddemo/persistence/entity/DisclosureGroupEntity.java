package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * DISCGRP VSAM KSDS record (app/cpy/CVTRA02Y.cpy, RECLN 50, KEYS(16 0)): the annual interest rate
 * CBACT04C applies to a category balance, with the {@code DEFAULT} group as the fallback its
 * paragraph 1200-A-GET-DEFAULT-INT-RATE reads when the account group has no row.
 */
@Entity
@Table(name = "disclosure_group")
public class DisclosureGroupEntity {

    @EmbeddedId
    private DisclosureGroupId id;

    @Column(name = "dis_int_rate", precision = 6, scale = 2, nullable = false)
    private BigDecimal disIntRate;

    protected DisclosureGroupEntity() {
    }

    public DisclosureGroupEntity(DisclosureGroupId id, BigDecimal disIntRate) {
        this.id = id;
        this.disIntRate = disIntRate;
    }

    public DisclosureGroupId getId() {
        return id;
    }

    public BigDecimal getDisIntRate() {
        return disIntRate;
    }
}
