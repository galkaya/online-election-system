package edu.comp5100.election.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application context with the real services, repositories and an isolated in-memory H2
 * database, but without a real HTTP server. Test methods are NOT wrapped in a rollback
 * transaction: every service call commits or rolls back exactly as in production.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
public @interface IntegrationTest {
}
