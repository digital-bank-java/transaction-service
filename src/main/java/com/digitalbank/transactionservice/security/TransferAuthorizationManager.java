package com.digitalbank.transactionservice.security;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class TransferAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String REQUIRED_AUTHORITY = "SCOPE_transfer.internal";

    private final Set<String> authorizedSubjects;

    TransferAuthorizationManager(
            @Value("${transaction.transfer.authorization.allowed-subjects:}") String configuredSubjects) {
        authorizedSubjects = Arrays.stream(configuredSubjects.split(","))
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var current = authentication.get();
        if (!(current instanceof JwtAuthenticationToken jwtAuthentication)) {
            return new AuthorizationDecision(false);
        }

        var subject = jwtAuthentication.getToken().getSubject();
        var hasRequiredAuthority = current.getAuthorities().stream()
                .anyMatch(authority -> REQUIRED_AUTHORITY.equals(authority.getAuthority()));
        return new AuthorizationDecision(
                current.isAuthenticated() && hasRequiredAuthority && authorizedSubjects.contains(subject));
    }
}
