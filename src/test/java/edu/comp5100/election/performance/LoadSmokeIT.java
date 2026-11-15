package edu.comp5100.election.performance;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.service.ReceiptStatus;
import edu.comp5100.election.service.ReceiptVerificationService;
import edu.comp5100.election.service.UserAdminService;
import edu.comp5100.election.support.HttpTestClient;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.ServerTest;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * A small local HTTP load smoke test (not a benchmark and not a capacity claim). A fixed number
 * of fictional voters, each with a real session, bcrypt login and CSRF tokens, vote through the
 * real embedded server with a fixed number of client threads. The test fails when any request
 * fails or when the stored votes do not match what was sent; response times are only reported.
 *
 * <p>Tunable with -Dload.voters, -Dload.concurrency and -Dload.warmup. The report is written to
 * target/performance/load-smoke-report.md.
 */
@ServerTest
@DisplayName("Performance: local HTTP load smoke test")
class LoadSmokeIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;
    private static final String PASSWORD = "Load-Smoke-Pass-1";
    private static final Pattern RECEIPT = Pattern.compile("id=\"receipt-code\">([0-9A-Z-]{39})<");

    /** What happened to one simulated voter. Times are in nanoseconds; failure is null on success. */
    private record Flow(String failure, long loginNanos, long ballotNanos, long voteNanos, long totalNanos,
            String receiptCode) {
        boolean succeeded() {
            return failure == null;
        }
    }

    @LocalServerPort
    private int port;
    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int connectionPoolSize;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ReceiptVerificationService receiptVerificationService;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private int nextVoterNumber;

    private List<UserAccount> createVoters(UserAccount admin, Election election, int count) {
        List<UserAccount> voters = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            // Real accounts with bcrypt hashes, created through the production service.
            voters.add(userAdminService.createVoter(admin.getUsername(), "load.voter." + (++nextVoterNumber),
                    "Load Voter " + nextVoterNumber, PASSWORD));
        }
        data.makeEligible(election, voters.toArray(UserAccount[]::new));
        return voters;
    }

    private Flow voteOverHttp(UserAccount voter, Election election, Long candidateId) {
        long started = System.nanoTime();
        try {
            HttpTestClient client = new HttpTestClient(port);
            client.get("/login");
            long loginStarted = System.nanoTime();
            HttpResponse<String> login = client.postForm("/login",
                    Map.of("username", voter.getUsername(), "password", PASSWORD));
            long loginNanos = System.nanoTime() - loginStarted;
            if (login.statusCode() != 302 || login.headers().firstValue("Location").orElse("").contains("error")) {
                return failed("login answered " + login.statusCode(), started);
            }

            long ballotStarted = System.nanoTime();
            HttpResponse<String> ballot = client.get("/voter/elections/" + election.getId() + "/ballot");
            long ballotNanos = System.nanoTime() - ballotStarted;
            if (ballot.statusCode() != 200) {
                return failed("ballot answered " + ballot.statusCode(), started);
            }

            long voteStarted = System.nanoTime();
            HttpResponse<String> vote = client.postForm("/voter/elections/" + election.getId() + "/vote",
                    Map.of("candidateId", String.valueOf(candidateId)));
            long voteNanos = System.nanoTime() - voteStarted;
            if (vote.statusCode() != 302) {
                return failed("vote answered " + vote.statusCode(), started);
            }
            Matcher code = RECEIPT.matcher(client.follow(vote).body());
            if (!code.find()) {
                return failed("confirmation page without a code", started);
            }
            return new Flow(null, loginNanos, ballotNanos, voteNanos, System.nanoTime() - started, code.group(1));
        } catch (RuntimeException failure) {
            return failed(failure.getClass().getSimpleName(), started);
        }
    }

    private static Flow failed(String reason, long started) {
        return new Flow(reason, 0, 0, 0, System.nanoTime() - started, null);
    }

    private List<Flow> run(List<UserAccount> voters, Election election, List<Candidate> candidates, int concurrency)
            throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<Flow>> futures = new ArrayList<>();
            for (int index = 0; index < voters.size(); index++) {
                UserAccount voter = voters.get(index);
                Long candidateId = candidates.get(index % candidates.size()).getId();
                futures.add(threads.submit(() -> voteOverHttp(voter, election, candidateId)));
            }
            List<Flow> flows = new ArrayList<>();
            for (Future<Flow> future : futures) {
                flows.add(future.get(120, TimeUnit.SECONDS));
            }
            return flows;
        } finally {
            threads.shutdownNow();
        }
    }

    /** Nearest-rank percentile in milliseconds with one decimal place. */
    private static double percentile(List<Long> nanos, double percentile) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return Math.round(sorted.get(Math.max(rank, 1) - 1) / 100_000.0) / 10.0;
    }

    private static String row(String label, List<Long> nanos) {
        return String.format(Locale.US, "| %s | %d | %.1f | %.1f | %.1f |", label, nanos.size(),
                percentile(nanos, 50), percentile(nanos, 95), percentile(nanos, 100));
    }

    @Test
    @DisplayName("Fixed voters vote over real HTTP; every vote is stored exactly once and times are reported")
    void fixedLoadIsHandledCorrectly() throws Exception {
        int voterCount = Integer.getInteger("load.voters", 60);
        int concurrency = Integer.getInteger("load.concurrency", 12);
        int warmupCount = Integer.getInteger("load.warmup", 12);
        clock.setInstant(BASE);
        UserAccount admin = data.admin("load-admin");

        Election warmupElection = data.openElection("Load warm-up", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Warm A", "Warm B");
        List<Flow> warmup = run(createVoters(admin, warmupElection, warmupCount), warmupElection,
                data.candidatesOf(warmupElection), concurrency);

        Election election = data.openElection("Load measured", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Jordan Lee", "Priya Nair", "Marcus Hill");
        List<Candidate> candidates = data.candidatesOf(election);
        List<UserAccount> voters = createVoters(admin, election, voterCount);

        long measuredStarted = System.nanoTime();
        List<Flow> flows = run(voters, election, candidates, concurrency);
        double measuredSeconds = (System.nanoTime() - measuredStarted) / 1_000_000_000.0;

        // A second, sequential round: ten voters try to vote again and must be refused with 409.
        int duplicateAttempts = Math.min(10, voterCount);
        List<Long> rejectionNanos = new ArrayList<>();
        int rejected = 0;
        for (int index = 0; index < duplicateAttempts; index++) {
            HttpTestClient client = new HttpTestClient(port);
            client.login(voters.get(index).getUsername(), PASSWORD);
            long started = System.nanoTime();
            HttpResponse<String> again = client.postForm("/voter/elections/" + election.getId() + "/vote",
                    Map.of("candidateId", String.valueOf(candidates.get(0).getId())));
            rejectionNanos.add(System.nanoTime() - started);
            if (again.statusCode() == 409) {
                rejected++;
            }
        }

        List<Flow> succeeded = flows.stream().filter(Flow::succeeded).toList();
        List<String> failures = flows.stream().filter(flow -> !flow.succeeded()).map(Flow::failure).toList();
        List<Long> stored = ballotRepository.findCandidateIdsByElectionId(election.getId());
        long participations = participationRepository.countByElectionId(election.getId());
        boolean tallyMatches = true;
        StringBuilder tally = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            int candidateIndex = index;
            long expected = java.util.stream.IntStream.range(0, voterCount)
                    .filter(voter -> voter % candidates.size() == candidateIndex).count();
            long actual = Collections.frequency(stored, candidates.get(index).getId());
            tallyMatches &= expected == actual;
            tally.append(String.format(Locale.US, "| %s | %d | %d |%n", candidates.get(index).getName(), expected,
                    actual));
        }

        Runtime runtime = Runtime.getRuntime();
        List<String> report = new ArrayList<>(List.of(
                "# Local HTTP load smoke test",
                "",
                "Run at " + OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " by `LoadSmokeIT`"
                        + " (`mvnw -Pperformance verify`).",
                "",
                "This is a smoke test on one developer machine with client and server in the same JVM."
                        + " It is not a benchmark, a capacity statement or a service-level claim.",
                "",
                "## Environment",
                "",
                "- Operating system: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")",
                "- Logical processors: " + runtime.availableProcessors() + ", JVM maximum heap: "
                        + runtime.maxMemory() / (1024 * 1024) + " MB",
                "- Java: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version"),
                "- Server: embedded Tomcat on a random local port, H2 in-memory database, connection pool size "
                        + connectionPoolSize,
                "",
                "## Load",
                "",
                "- Warm-up (not measured): " + warmupCount + " voters on a separate election, "
                        + warmup.stream().filter(Flow::succeeded).count() + " succeeded",
                "- Measured: " + voterCount + " voters, " + concurrency + " concurrent client threads, one vote each",
                "- Per voter: GET /login, POST /login (bcrypt), GET ballot, POST vote with CSRF token,"
                        + " GET confirmation",
                "- Then " + duplicateAttempts + " sequential duplicate attempts (expected answer: 409)",
                "",
                "## Correctness",
                "",
                "| Check | Result |",
                "|---|---|",
                "| Voters whose whole flow succeeded | " + succeeded.size() + " of " + voterCount + " |",
                "| Failed flows | " + failures.size() + (failures.isEmpty() ? "" : " " + failures) + " |",
                "| Participation records stored | " + participations + " |",
                "| Ballots stored | " + stored.size() + " |",
                "| Duplicate attempts rejected with 409 | " + rejected + " of " + duplicateAttempts + " |",
                "| Tally equals the votes that were sent | " + (tallyMatches ? "yes" : "NO") + " |",
                "",
                "| Candidate | Votes sent | Votes stored |",
                "|---|---|---|"));
        report.add(tally.toString().stripTrailing());
        report.addAll(List.of(
                "",
                "## Response times of successful requests (milliseconds)",
                "",
                "Failed requests, if any, are excluded from this table and listed above instead.",
                "",
                "| Request | Count | p50 | p95 | max |",
                "|---|---|---|---|---|",
                row("POST /login (bcrypt check, new session)", succeeded.stream().map(Flow::loginNanos).toList()),
                row("GET ballot page", succeeded.stream().map(Flow::ballotNanos).toList()),
                row("POST vote (accepted, 302)", succeeded.stream().map(Flow::voteNanos).toList()),
                row("Whole voter flow (5 requests)", succeeded.stream().map(Flow::totalNanos).toList()),
                row("POST vote (duplicate, 409, sequential)", rejectionNanos),
                "",
                String.format(Locale.US, "Measured phase wall-clock time: %.2f s for %d voters (%.1f completed voter"
                        + " flows per second at this concurrency).", measuredSeconds, voterCount,
                        succeeded.size() / measuredSeconds)));
        writeReport(report);

        assertThat(failures).as("failed voter flows").isEmpty();
        assertThat(participations).isEqualTo(voterCount);
        assertThat(stored).hasSize(voterCount);
        assertThat(tallyMatches).as("stored tally equals the votes sent").isTrue();
        assertThat(rejected).isEqualTo(duplicateAttempts);
        assertThat(succeeded).extracting(Flow::receiptCode).doesNotHaveDuplicates();
        for (Flow flow : succeeded) {
            assertThat(receiptVerificationService.verify(flow.receiptCode())).isEqualTo(ReceiptStatus.ACCEPTED);
        }
    }

    private static void writeReport(List<String> lines) throws IOException {
        Path file = Path.of("target", "performance", "load-smoke-report.md");
        Files.createDirectories(file.getParent());
        Files.write(file, lines);
        lines.forEach(System.out::println);
    }
}
