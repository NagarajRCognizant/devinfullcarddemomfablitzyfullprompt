package com.carddemo.service;

import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.cobol.CobolText;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Port of COACTUPC paragraph 1205-COMPARE-OLD-NEW.
 *
 * <p>The paragraph compares the fetched values (ACUP-OLD-DETAILS) with the keyed values
 * (ACUP-NEW-DETAILS) and, when nothing differs, reports "no change detected" instead of running
 * the edits. It compares some fields with {@code FUNCTION UPPER-CASE(FUNCTION TRIM(...))} and
 * others literally, so the distinction is kept here field by field: names, addresses, group id,
 * government id and the indicator fields ignore case, while amounts, dates, phone parts, the SSN
 * and the FICO score are case sensitive. Fixed width padding has no equivalent in the JSON
 * request, so every comparison ignores surrounding spaces.
 *
 * <p>The COMMAREA that carried the fetched values between terminal turns is replaced by the
 * {@code original} block of the request, so the comparison is between two client supplied blocks
 * of the same shape.
 */
@Component
public class AccountChangeDetector {

    /** True when at least one value differs, which is CHANGE-HAS-OCCURRED in the source. */
    public boolean hasChanges(AccountUpdateForm original, AccountUpdateForm keyed) {
        return !unchanged(original, keyed);
    }

    private boolean unchanged(AccountUpdateForm old, AccountUpdateForm now) {
        // Account master block, checked first exactly as the source does.
        boolean accountUnchanged =
                exact(old, now, AccountUpdateForm::accountId)
                && upper(old, now, AccountUpdateForm::activeStatus)
                && exact(old, now, AccountUpdateForm::currentBalance)
                && exact(old, now, AccountUpdateForm::creditLimit)
                && exact(old, now, AccountUpdateForm::cashCreditLimit)
                && exact(old, now, AccountUpdateForm::openYear)
                && exact(old, now, AccountUpdateForm::openMonth)
                && exact(old, now, AccountUpdateForm::openDay)
                && exact(old, now, AccountUpdateForm::expiryYear)
                && exact(old, now, AccountUpdateForm::expiryMonth)
                && exact(old, now, AccountUpdateForm::expiryDay)
                && exact(old, now, AccountUpdateForm::reissueYear)
                && exact(old, now, AccountUpdateForm::reissueMonth)
                && exact(old, now, AccountUpdateForm::reissueDay)
                && exact(old, now, AccountUpdateForm::currentCycleCredit)
                && exact(old, now, AccountUpdateForm::currentCycleDebit)
                && upperTrim(old, now, AccountUpdateForm::groupId);
        if (!accountUnchanged) {
            return false;
        }

        return upperTrim(old, now, AccountUpdateForm::customerId)
                && upperTrim(old, now, AccountUpdateForm::firstName)
                && upperTrim(old, now, AccountUpdateForm::middleName)
                && upperTrim(old, now, AccountUpdateForm::lastName)
                && upperTrim(old, now, AccountUpdateForm::addressLine1)
                && upperTrim(old, now, AccountUpdateForm::addressLine2)
                && upperTrim(old, now, AccountUpdateForm::city)
                && upperTrim(old, now, AccountUpdateForm::state)
                && upperTrim(old, now, AccountUpdateForm::country)
                && upperTrim(old, now, AccountUpdateForm::zip)
                && exact(old, now, AccountUpdateForm::phone1Area)
                && exact(old, now, AccountUpdateForm::phone1Prefix)
                && exact(old, now, AccountUpdateForm::phone1Line)
                && exact(old, now, AccountUpdateForm::phone2Area)
                && exact(old, now, AccountUpdateForm::phone2Prefix)
                && exact(old, now, AccountUpdateForm::phone2Line)
                && exact(old, now, AccountUpdateForm::ssnPart1)
                && exact(old, now, AccountUpdateForm::ssnPart2)
                && exact(old, now, AccountUpdateForm::ssnPart3)
                && upperTrim(old, now, AccountUpdateForm::governmentIssuedId)
                && exact(old, now, AccountUpdateForm::dobYear)
                && exact(old, now, AccountUpdateForm::dobMonth)
                && exact(old, now, AccountUpdateForm::dobDay)
                && exact(old, now, AccountUpdateForm::eftAccountId)
                && upperTrim(old, now, AccountUpdateForm::primaryCardHolder)
                && exact(old, now, AccountUpdateForm::ficoScore);
    }

    private static boolean exact(AccountUpdateForm old, AccountUpdateForm now,
                                 Function<AccountUpdateForm, String> field) {
        return CobolText.trim(field.apply(old)).equals(CobolText.trim(field.apply(now)));
    }

    private static boolean upper(AccountUpdateForm old, AccountUpdateForm now,
                                 Function<AccountUpdateForm, String> field) {
        return CobolText.trim(field.apply(old)).toUpperCase()
                .equals(CobolText.trim(field.apply(now)).toUpperCase());
    }

    private static boolean upperTrim(AccountUpdateForm old, AccountUpdateForm now,
                                     Function<AccountUpdateForm, String> field) {
        return CobolText.upperTrim(field.apply(old)).equals(CobolText.upperTrim(field.apply(now)));
    }
}
