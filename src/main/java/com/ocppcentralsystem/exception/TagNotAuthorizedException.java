package com.ocppcentralsystem.exception;

import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagAuthorization;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * A tag exists but is not allowed to charge, either because it was blocked or because it expired.
 * Unknown tags are reported as {@code TAG_NOT_FOUND} instead.
 */
public class TagNotAuthorizedException extends ApiException {

    public TagNotAuthorizedException(Tag tag) {
        super(HttpStatus.FORBIDDEN, "TAG_NOT_AUTHORIZED",
                "Tag " + tag.getIdTag() + " cannot be used for charging: " + reason(tag));
    }

    private static String reason(Tag tag) {
        TagAuthorization authorization = TagAuthorization.of(tag);
        return authorization.name().toUpperCase(Locale.ROOT);
    }
}
