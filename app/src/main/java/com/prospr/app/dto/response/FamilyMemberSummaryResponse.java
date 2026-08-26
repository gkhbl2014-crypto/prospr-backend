package com.prospr.app.dto.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * Deliberately minimal - this is returned from a PRE-login, unauthenticated endpoint (anyone with
 * a family's invite code can see it, before proving they're any specific member), so it carries
 * only what's needed to render a "pick yourself" picker. No email, phone, or anything else that
 * would let someone harvest contact details just by knowing an invite code.
 */
@Getter
@Builder
public class FamilyMemberSummaryResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String role;
}
