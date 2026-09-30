package com.carddemo.exception;

import com.carddemo.domain.validation.FieldFlag;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A keyed read returned NOTFND: COACTVWC/COACTUPC paragraphs 9200, 9300 and 9400 treat a missing
 * cross reference, account or customer record as an input error rather than a failure.
 *
 * <p>Those paragraphs also set an edit flag, so the redisplayed map highlights the filter that
 * produced the miss: 9200 and 9300 set WS-EDIT-ACCT-FLAG and 9400 sets WS-EDIT-CUST-FLAG. The flags
 * travel with the exception so the response highlights the same field.
 */
public class RecordNotFoundException extends BusinessRuleException {

    private final Map<String, FieldFlag> fieldFlags;

    public RecordNotFoundException(String message, Map<String, FieldFlag> fieldFlags) {
        super(message);
        this.fieldFlags = new LinkedHashMap<>(fieldFlags);
    }

    public Map<String, FieldFlag> getFieldFlags() {
        return fieldFlags;
    }
}
