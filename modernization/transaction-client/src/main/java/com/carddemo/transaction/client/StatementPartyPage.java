package com.carddemo.transaction.client;

import java.util.List;

/** One page of the cross reference browse, in ascending card number order. */
public record StatementPartyPage(List<StatementParty> rows, boolean lastPage) {
}
