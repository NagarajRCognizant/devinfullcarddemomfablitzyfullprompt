package com.carddemo.service;

import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;

/**
 * The three records the online programs read for one account: the cross reference entry reached
 * through the account alternate index, the account master record and the customer master record.
 */
public record AccountRecords(CardXrefEntity xref, AccountEntity account, CustomerEntity customer) {
}
