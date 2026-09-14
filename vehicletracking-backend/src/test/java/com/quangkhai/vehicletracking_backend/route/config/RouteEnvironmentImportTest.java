package com.quangkhai.vehicletracking_backend.route.config;

import com.quangkhai.vehicletracking_backend.route.provider.HereRoutingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RouteEnvironmentImportTest {
    @TempDir Path temporaryDirectory;

    @EnableConfigurationProperties(HereRoutingProperties.class)
    static class Config {}

    // Read the real application's import list, but resolve it ONLY within a temporary
    // repository. Never read a developer's .env, launch the DB, or call HERE.
    private ApplicationContextRunner runner(Path workingDirectory) throws IOException {
        var yaml = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        String imports = (String) yaml.getFirst().getProperty("spring.config.import");
        assertThat(imports).startsWith("${APP_ENV_IMPORT:").endsWith("}");
        imports = imports.substring("${APP_ENV_IMPORT:".length(), imports.length() - 1);
        String isolatedImports = Arrays.stream(imports.split(","))
                .map(location -> {
                    assertThat(location).startsWith("optional:file:").endsWith("[.properties]");
                    String relative = location.substring("optional:file:".length(), location.indexOf('['));
                    Path resolved = workingDirectory.resolve(relative).normalize();
                    assertThat(resolved.startsWith(temporaryDirectory)).isTrue();
                    return "optional:" + resolved.toUri() + "[.properties]";
                }).collect(Collectors.joining(","));
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .withPropertyValues("spring.config.location=classpath:/application.yaml",
                        "APP_ENV_IMPORT=" + isolatedImports);
    }

    private Path repository() throws IOException {
        return Files.createDirectories(temporaryDirectory.resolve("repository"));
    }

    private void rootEnvironment(Path root) throws IOException {
        Files.writeString(root.resolve(".env"), """
                HERE_ROUTING_ENABLED=true
                HERE_API_KEY=fake-root-routing-key
                HERE_ROUTING_BASE_URL=https://router.hereapi.com
                """);
    }

    @Test
    void rootLaunch_loadsRootEnvironment() throws IOException {
        Path root = repository();
        rootEnvironment(root);
        runner(root).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(HereRoutingProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getApiKey()).isEqualTo("fake-root-routing-key");
            assertThat(properties.getBaseUrl()).isEqualTo("https://router.hereapi.com");
        });
    }

    @Test
    void backendLaunch_loadsParentRootEnvironment() throws IOException {
        Path root = repository();
        rootEnvironment(root);
        Path backend = Files.createDirectories(root.resolve("vehicletracking-backend"));
        runner(backend).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HereRoutingProperties.class).isEnabled()).isTrue();
            assertThat(context.getBean(HereRoutingProperties.class).getApiKey()).isEqualTo("fake-root-routing-key");
        });
    }

    @Test
    void backendEnvironment_overridesRootForBothLaunchDirectories() throws IOException {
        Path root = repository();
        rootEnvironment(root);
        Path backend = Files.createDirectories(root.resolve("vehicletracking-backend"));
        Files.writeString(backend.resolve(".env"), "HERE_ROUTING_ENABLED=false\nHERE_API_KEY=fake-backend-key\n");
        for (Path directory : new Path[]{root, backend}) {
            runner(directory).run(context -> {
                assertThat(context).hasNotFailed();
                var properties = context.getBean(HereRoutingProperties.class);
                assertThat(properties.isEnabled()).isFalse();
                assertThat(properties.getApiKey()).isEqualTo("fake-backend-key");
            });
        }
    }

    @Test
    void processEnvironment_overridesFiles() throws IOException {
        Path root = repository();
        rootEnvironment(root);
        runner(root).withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-process-environment", Map.<String, Object>of(
                        "HERE_ROUTING_ENABLED", "false", "HERE_API_KEY", "fake-process-key"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HereRoutingProperties.class).isEnabled()).isFalse();
                    assertThat(context.getBean(HereRoutingProperties.class).getApiKey()).isEqualTo("fake-process-key");
                });
    }

    @Test
    void missingFiles_stayDisabledAndDoNotLoadFrontendEnvironment() throws IOException {
        Path root = repository();
        Path frontend = Files.createDirectories(root.resolve("vehicletracking-frontend"));
        Files.writeString(frontend.resolve(".env"), "HERE_ROUTING_ENABLED=true\nHERE_API_KEY=fake-frontend-key\n");
        runner(root).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(HereRoutingProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getApiKey()).isEmpty();
        });
    }

    @Test
    void enabledWithoutKey_failsValidation() throws IOException {
        Path root = repository();
        Files.writeString(root.resolve(".env"), "HERE_ROUTING_ENABLED=true\n");
        runner(root).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("HERE API key không được để trống");
        });
    }
}
