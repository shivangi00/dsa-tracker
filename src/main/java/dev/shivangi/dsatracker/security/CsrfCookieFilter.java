package dev.shivangi.dsatracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security creates the CSRF token lazily, only when something asks for it. Asking for it
 * on every response makes sure the XSRF-TOKEN cookie is always there for the page's JavaScript
 * to copy into its X-XSRF-TOKEN header.
 *
 * <p>Deliberately not a {@code @Component}: it only makes sense inside the security filter chain
 * (SecurityConfig adds it), after Spring's CsrfFilter has put the token on the request.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();   // loads the token, which writes the cookie
        }
        chain.doFilter(request, response);
    }
}
