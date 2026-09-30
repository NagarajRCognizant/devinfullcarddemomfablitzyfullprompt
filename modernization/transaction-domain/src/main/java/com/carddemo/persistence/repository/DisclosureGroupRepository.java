package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.DisclosureGroupEntity;
import com.carddemo.persistence.entity.DisclosureGroupId;
import org.springframework.data.jpa.repository.JpaRepository;

/** DISCGRP file access. */
public interface DisclosureGroupRepository extends JpaRepository<DisclosureGroupEntity, DisclosureGroupId> {
}
