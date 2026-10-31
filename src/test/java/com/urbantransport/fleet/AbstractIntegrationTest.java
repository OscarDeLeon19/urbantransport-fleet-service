package com.urbantransport.fleet;

import com.jayway.jsonpath.JsonPath;
import com.urbantransport.fleet.client.IdentityClient;
import com.urbantransport.fleet.client.TripClient;
import com.urbantransport.fleet.domain.BranchProjectionEntity;
import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.domain.DriverEntity;
import com.urbantransport.fleet.domain.LicenseType;
import com.urbantransport.fleet.domain.RouteEntity;
import com.urbantransport.fleet.repository.BranchProjectionRepository;
import com.urbantransport.fleet.repository.BusRepository;
import com.urbantransport.fleet.repository.DriverRepository;
import com.urbantransport.fleet.repository.RouteRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * PostgreSQL real embebido (Flyway aplica las migraciones) y Kafka embebido: sin Docker.
 * Cuando termina la JVM se cierra Postgres; los topics se crean al vuelo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    protected static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    protected static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected BranchProjectionRepository branchProjectionRepository;

    @Autowired
    protected BusRepository busRepository;

    @Autowired
    protected DriverRepository driverRepository;

    @Autowired
    protected RouteRepository routeRepository;

    @MockitoBean
    protected IdentityClient identityClient;

    @MockitoBean
    protected TripClient tripClient;

    @MockitoBean
    protected S3Client s3Client;

    @MockitoBean
    protected S3Presigner s3Presigner;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE license_alerts, drivers, buses, routes, outbox_events, processed_events, "
                + "branches_projection CASCADE");
    }

    protected static RequestPostProcessor branchAdmin(UUID branchId) {
        return userWithRole("BRANCH_ADMIN", branchId);
    }

    protected static RequestPostProcessor systemAdmin() {
        return userWithRole("SYSTEM_ADMIN", null);
    }

    protected static RequestPostProcessor userWithRole(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject(USER_ID.toString()).claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    protected static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    protected static UUID idAt(MvcResult result, String path) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), path));
    }

    protected UUID saveBranch(String name, boolean active) {
        return branchProjectionRepository.save(BranchProjectionEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .code(name.substring(0, 3).toUpperCase())
                .active(active)
                .build()).getId();
    }

    protected BusEntity saveBus(UUID branchId, String plate, BusStatus status) {
        return busRepository.save(BusEntity.builder()
                .plate(plate)
                .brand("Volvo")
                .model("B8R")
                .manufactureYear((short) 2020)
                .capacity(40)
                .seatRows((short) 10)
                .seatColumns((short) 4)
                .aisleAfterColumn((short) 2)
                .status(status)
                .odometerKm(new BigDecimal("1000.00"))
                .branchId(branchId)
                .build());
    }

    protected DriverEntity saveDriver(UUID branchId, String licenseNumber, LocalDate expiresOn, boolean active) {
        return driverRepository.save(DriverEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Chofer " + licenseNumber)
                .licenseNumber(licenseNumber)
                .licenseType(LicenseType.A)
                .licenseExpiresOn(expiresOn)
                .phone("55551234")
                .email(licenseNumber.toLowerCase() + "@mail.com")
                .baseSalaryPerTrip(new BigDecimal("150.00"))
                .branchId(branchId)
                .active(active)
                .build());
    }

    protected RouteEntity saveRoute(UUID originBranchId, UUID destinationBranchId, boolean deleted) {
        return routeRepository.save(RouteEntity.builder()
                .originBranchId(originBranchId)
                .destinationBranchId(destinationBranchId)
                .distanceKm(new BigDecimal("120.50"))
                .ticketPrice(new BigDecimal("45.00"))
                .createdBy(USER_ID)
                .deleted(deleted)
                .build());
    }

    protected List<String> outboxEventTypes(UUID aggregateId) {
        return jdbcTemplate.queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
                String.class, aggregateId);
    }

    protected int count(String sql, Object... args) {
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return total == null ? 0 : total;
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // se cierra al terminar la JVM
                }
            }));
            return postgres;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}