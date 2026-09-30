package com.carddemo.batch.transaction;

/**
 * A validated daily transaction on its way to either the transaction file or the reject file.
 *
 * <p>WS-VALIDATION-FAIL-REASON of CBTRN02C is carried rather than an exception because a failed
 * edit is a normal outcome of the run: the record is written to DALYREJS, the reject count is
 * raised and the job continues with the next record.
 *
 * @param transaction the record as read from DALYTRAN
 * @param accountId the account the cross reference resolved the card to, absent when reason 100
 * @param failReason 0 when every edit passed, otherwise the source reason code
 * @param failReasonDescription the source description that accompanies the reason code
 */
public record PostingCandidate(
        DailyTransaction transaction,
        Long accountId,
        int failReason,
        String failReasonDescription) {

    /** Reason 100 of 1500-A-LOOKUP-XREF: the card has no cross reference record. */
    public static final int INVALID_CARD_NUMBER = 100;
    /** Reason 101 of 1500-B-LOOKUP-ACCT: the cross referenced account is not on file. */
    public static final int ACCOUNT_NOT_FOUND = 101;
    /** Reason 102 of 1500-B-LOOKUP-ACCT: the cycle amounts plus this one exceed the credit limit. */
    public static final int OVERLIMIT = 102;
    /** Reason 103 of 1500-B-LOOKUP-ACCT: the account expired before the transaction was taken. */
    public static final int AFTER_EXPIRATION = 103;

    public static PostingCandidate valid(DailyTransaction transaction, Long accountId) {
        return new PostingCandidate(transaction, accountId, 0, "");
    }

    public static PostingCandidate rejected(DailyTransaction transaction, Long accountId, int reason,
            String description) {
        return new PostingCandidate(transaction, accountId, reason, description);
    }

    public boolean postable() {
        return failReason == 0;
    }
}
