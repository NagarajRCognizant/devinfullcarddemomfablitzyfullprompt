package com.carddemo.api;

import com.carddemo.api.dto.ErrorResponse;
import com.carddemo.domain.UserMessages;
import com.carddemo.exception.DuplicateUserException;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.SignOnFailedException;
import com.carddemo.exception.StorageAccessException;
import com.carddemo.exception.UpdateFailedException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the outcomes the sign-on and user administration screens reported in WS-MESSAGE onto HTTP
 * status codes, keeping the message text of the source program.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** The required field checks of COSGN00C, COUSR01C and COUSR02C, and the no-change path. */
    @ExceptionHandler(ScreenValidationException.class)
    public ResponseEntity<ErrorResponse> validation(ScreenValidationException failure) {
        return ResponseEntity.badRequest().body(new ErrorResponse(failure.getStatus(),
                failure.getMessage(), failure.getFieldFlags()));
    }

    /** DFHRESP(NOTFND) of READ-USER-SEC-FILE in COUSR02C and COUSR03C. */
    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(RecordNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", failure.getMessage(), failure.getFieldFlags()));
    }

    /** DFHRESP(DUPKEY)/DFHRESP(DUPREC) of WRITE-USER-SEC-FILE in COUSR01C. */
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> duplicate(DuplicateUserException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_USER", failure.getMessage(), Map.of()));
    }

    /**
     * The two rejected sign-on paths of COSGN00C. The program redisplays the map with a different
     * message for a missing user and a wrong password; both are unauthenticated here and the
     * distinct texts are preserved.
     */
    @ExceptionHandler(SignOnFailedException.class)
    public ResponseEntity<ErrorResponse> signOnFailed(SignOnFailedException failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("SIGNON_FAILED", failure.getMessage(), Map.of()));
    }

    /** The WHEN OTHER branches of the rewrite and delete paragraphs. */
    @ExceptionHandler(UpdateFailedException.class)
    public ResponseEntity<ErrorResponse> updateFailed(UpdateFailedException failure) {
        LOG.error("USRSEC update failed, unit of work rolled back", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("UPDATE_FAILED", failure.getMessage(), Map.of()));
    }

    /** The abend paths of the browse paragraphs of COUSR00C. */
    @ExceptionHandler(StorageAccessException.class)
    public ResponseEntity<ErrorResponse> storage(StorageAccessException failure) {
        LOG.error("USRSEC access failure", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("STORAGE_ERROR", failure.getMessage(), Map.of()));
    }

    /**
     * A body the mapper cannot read has no counterpart in the source, where the terminal delivered a
     * fixed length map; it is reported as a missing first field.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException failure) {
        return ResponseEntity.badRequest().body(new ErrorResponse("MALFORMED_REQUEST",
                UserMessages.USER_ID_REQUIRED, Map.of()));
    }
}
