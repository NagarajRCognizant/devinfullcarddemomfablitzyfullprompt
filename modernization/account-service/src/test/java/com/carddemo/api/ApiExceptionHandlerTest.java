package com.carddemo.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.api.dto.ErrorResponse;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.exception.AdminOnlyOptionException;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.StorageAccessException;
import com.carddemo.exception.UpdateFailedException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * The status code each legacy outcome is reported with.
 *
 * <p>Three of these outcomes are reachable only from a file status the local database does not
 * produce (a failed rewrite, an abend status, and the admin-only refusal that the supplied option
 * table never triggers), so they are asserted here rather than through the API.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void aFieldEditIsAnInputError() {
        ResponseEntity<ErrorResponse> response = handler.validation(
                new ScreenValidationException("INVALID_DATA", ScreenMessages.PROMPT_FOR_ACCT,
                        Map.of("accountId", FieldFlag.BLANK)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo("INVALID_DATA");
        assertThat(response.getBody().message()).isEqualTo(ScreenMessages.PROMPT_FOR_ACCT);
        assertThat(response.getBody().fieldFlags()).containsEntry("accountId", FieldFlag.BLANK);
    }

    @Test
    void aMissingRecordIsNotFound() {
        ResponseEntity<ErrorResponse> response = handler.notFound(
                new RecordNotFoundException(
                        ScreenMessages.accountNotInCrossReference("00000000011"), Map.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo("NOT_FOUND");
    }

    @Test
    void anUnreadableBodyIsAnInputError() {
        ResponseEntity<ErrorResponse> response =
                handler.unreadable(new HttpMessageNotReadableException("truncated"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo("MALFORMED_REQUEST");
    }

    /** The refusal of PROCESS-ENTER-KEY in COMEN01C, unreachable with the supplied option table. */
    @Test
    void anAdminOnlyOptionIsForbidden() {
        ResponseEntity<ErrorResponse> response =
                handler.forbidden(new AdminOnlyOptionException(ScreenMessages.NO_ACCESS_ADMIN_ONLY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().status()).isEqualTo("NO_ACCESS");
        assertThat(response.getBody().message()).isEqualTo(ScreenMessages.NO_ACCESS_ADMIN_ONLY);
    }

    @Test
    void aFailedRewriteIsAServerFailure() {
        ResponseEntity<ErrorResponse> response =
                handler.updateFailed(new UpdateFailedException("rewrite failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(500));
        assertThat(response.getBody().status()).isEqualTo("UPDATE_FAILED");
    }

    /** Any file status other than '00' or '10' abended the transaction. */
    @Test
    void anUnexpectedFileStatusIsAServerFailure() {
        ResponseEntity<ErrorResponse> response =
                handler.storage(new StorageAccessException("Unable to lookup Account"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(500));
        assertThat(response.getBody().status()).isEqualTo("STORAGE_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Unable to lookup Account");
    }
}
