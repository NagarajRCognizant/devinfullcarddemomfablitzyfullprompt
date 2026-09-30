package com.carddemo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.yaml.snakeyaml.Yaml;

/**
 * Every call a screen makes has to reach the service that answers it: a path no route matches is
 * answered 404 by the edge, which the operator sees as a screen that cannot be reached at all.
 */
class GatewayRouteTest {

    private static final PathPatternParser PARSER = new PathPatternParser();

    @ParameterizedTest
    @CsvSource({
        // COSGN00C, COADM01C and the COUSR screens.
        "/api/me,identity",
        "/api/users,identity",
        "/api/users/USER0001,identity",
        "/api/admin-menu,identity",
        // COMEN01C, COACTVWC and COACTUPC.
        "/api/menu,account",
        "/api/accounts/11,account",
        "/api/accounts/11/balance-adjustments,account",
        // COCRDLIC, COCRDSLC and COCRDUPC.
        "/api/cards,account",
        "/api/cards/4111111111111111,account",
        "/api/cards/4111111111111111/update,account",
        "/api/cards/validate,account",
        // The cardholder lookup of COBIL00C and COTRN02C, and the statement parties of CBSTM03A.
        "/api/cardholders/4111111111111111,account",
        "/api/statement-parties,account",
        // CPVS and CPVD.
        "/api/authorizations/11,authorization",
        // COTRN00C, COTRN01C, COTRN02C, COBIL00C and CORPT00C.
        "/api/transactions,transaction",
        "/api/transactions/0000000000000001,transaction",
        "/api/bill-payments/11,transaction",
        "/api/transaction-reports,transaction",
    })
    void routesTheCallOfEveryScreenToTheServiceThatAnswersIt(String path, String routeId) {
        assertThat(routeOf(path)).isEqualTo(routeId);
    }

    @Test
    void leavesAPathNoServiceAnswersUnrouted() {
        assertThat(routeOf("/api/unknown")).isNull();
    }

    private static String routeOf(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return routes().entrySet().stream()
                .filter(route -> route.getValue().stream().anyMatch(pattern -> pattern.matches(container)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<PathPattern>> routes() {
        Map<String, Object> document;
        try (InputStream yaml = GatewayRouteTest.class.getResourceAsStream("/application.yml")) {
            document = new Yaml().load(yaml);
        } catch (Exception failure) {
            throw new IllegalStateException("the gateway configuration cannot be read", failure);
        }
        Map<String, Object> gateway = (Map<String, Object>) nested(document, "spring", "cloud", "gateway");
        Map<String, List<PathPattern>> routes = new LinkedHashMap<>();
        for (Map<String, Object> route : (List<Map<String, Object>>) gateway.get("routes")) {
            List<PathPattern> patterns = ((List<String>) route.get("predicates")).stream()
                    .filter(predicate -> predicate.startsWith("Path="))
                    .flatMap(predicate -> List.of(predicate.substring("Path=".length()).split(",")).stream())
                    .map(PARSER::parse)
                    .toList();
            routes.put((String) route.get("id"), patterns);
        }
        return routes;
    }

    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> document, String... keys) {
        Object value = document;
        for (String key : keys) {
            value = ((Map<String, Object>) value).get(key);
        }
        return value;
    }
}
