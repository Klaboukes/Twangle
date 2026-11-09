// Read-only looks at Twangle on Google Play, for scripts/release.sh status | health | reviews
// (docs/release.md; docs/architecture/performance.md, *Release build*), and the edit that
// scripts/release.sh listing sends the store listing in.
//
// Plain JDK, run as a single source file by Android Studio's Java (java PlayReports.java ...), so it
// needs nothing installed beyond what builds the app, and nothing of it goes into the app. It signs in
// with the robot user's key (a service-account JSON key) the OAuth way for service accounts: a JWT
// signed with the key, swapped for an access token (RFC 7523; developers.google.com/identity/protocols/
// oauth2/service-account), then calls Google's REST APIs:
//   status  - Android Publisher API v3: opens an edit, reads the tracks and store listings, deletes the
//             edit again (never commits it), so nothing changes on Play.
//   health  - Play Developer Reporting API v1beta1: vitals.crashrate and vitals.anrrate, DAILY per
//             versionCode over the last N days up to Play's newest data (freshnessInfo).
//   reviews - Android Publisher API v3 reviews.list: the reviews with text of the last week.
//   edit-commit-unsent <id>, edit-delete <id> - an edit Gradle Play Publisher filled but didn't commit
//             (--no-commit): committed with changesNotSentForReview, so it waits in the Play Console's
//             Publishing overview until the owner sends it for review (the plugin's own commit sends it
//             at once), or deleted when a send stopped half way.
//   track-started <track> - "yes" when the track has a release that went out (live, a staged rollout
//             or halted), "no" when it never had one: an app's first production release can't be a
//             staged rollout (Play Console Help, Release app updates with staged rollouts). Read only,
//             as status.
//
// Usage: java PlayReports.java status|health [days]|reviews [language]|edit-commit-unsent <id>|
//        edit-delete <id>|track-started <track> <key.json> <application id>
// Errors go to stderr as "release: ...", like scripts/release.sh, with exit code 1.

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class PlayReports {
    static final String PUBLISHER = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/";
    static final String REPORTING = "https://playdeveloperreporting.googleapis.com/v1beta1/apps/";
    static final String SCOPES = "https://www.googleapis.com/auth/androidpublisher "
        + "https://www.googleapis.com/auth/playdeveloperreporting";
    // Play's bad-behaviour thresholds (Android vitals, "Core vitals"): user-perceived crash rate and
    // user-perceived ANR rate, over all devices. Above them Play may show the app less and warn people.
    static final double CRASH_LINE = 1.09;
    static final double ANR_LINE = 0.47;
    // Daily vitals are only in Pacific time (vitals.crashrate reference, Supported aggregation periods).
    static final String PACIFIC = "America/Los_Angeles";

    static final PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    static final HttpClient http = HttpClient.newBuilder()
        .proxy(ProxySelector.getDefault())
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    static String token;
    static String app;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) fail("usage: java PlayReports.java status|health [days]|reviews [language]|edit-commit-unsent <id>|edit-delete <id>|track-started <track> <key.json> <application id>");
        String command = args[0];
        String extra = args.length > 3 ? args[1] : null;
        Path key = Path.of(args[args.length - 2]);
        app = args[args.length - 1];
        signIn(key);
        switch (command) {
            case "status" -> status();
            case "health" -> health(extra == null ? 7 : Integer.parseInt(extra));
            case "reviews" -> reviews(extra == null ? "en" : extra);
            case "edit-commit-unsent" -> editCommitUnsent(needed(extra, "the edit's id"));
            case "edit-delete" -> editDelete(needed(extra, "the edit's id"));
            case "track-started" -> trackStarted(needed(extra, "the track's name"));
            default -> fail("unknown command " + command);
        }
    }

    static void fail(String message) {
        System.err.println("release: " + message);
        System.exit(1);
    }

    static String needed(String value, String what) {
        if (value == null) fail("missing " + what);
        return value;
    }

    // ---- Sign-in -------------------------------------------------------------------------------------

    static void signIn(Path keyFile) throws Exception {
        Map<String, Object> key = asMap(Json.parse(Files.readString(keyFile)));
        String email = str(key, "client_email");
        String pem = str(key, "private_key");
        String tokenUri = key.get("token_uri") instanceof String s ? s : "https://oauth2.googleapis.com/token";
        if (email == null || pem == null) fail("the Google Play key (" + keyFile + ") is not a service-account key");
        byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));

        long now = Instant.now().getEpochSecond();
        String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claims = b64("{\"iss\":\"" + email + "\",\"scope\":\"" + SCOPES + "\",\"aud\":\"" + tokenUri
            + "\",\"iat\":" + now + ",\"exp\":" + (now + 600) + "}");
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        String jwt = header + "." + claims + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());

        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&assertion=" + enc(jwt)))
            .build();
        Reply reply = send(request);
        Object accessToken = reply.json instanceof Map<?, ?> m ? m.get("access_token") : null;
        if (reply.code != 200 || !(accessToken instanceof String)) {
            fail("Google refused the robot user's key (" + email + "): " + reply.raw.strip()
                + "\nIs the key still valid in Google Cloud (project Twangle Play, Service accounts)? Nothing was read.");
        }
        token = (String) accessToken;
    }

    static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ---- HTTP ----------------------------------------------------------------------------------------

    record Reply(int code, Object json, String raw) {
        String errorMessage() {
            Object message = path(json, "error", "message");
            return message != null ? message.toString() : raw.strip();
        }

        String errorStatus() {
            Object status = path(json, "error", "status");
            return status != null ? status.toString() : "";
        }

        /** Google's "the API is switched off in the Cloud project" answer (SERVICE_DISABLED). */
        boolean apiDisabled() {
            String text = raw.toLowerCase(Locale.ROOT);
            return code == 403 && (text.contains("service_disabled") || text.contains("has not been used in project")
                || text.contains("it is disabled"));
        }
    }

    static Reply send(HttpRequest request) throws Exception {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            fail("no connection to Google (" + request.uri().getHost() + "): " + e + ". Is this PC online?");
            return null;
        }
        Object json = null;
        try {
            if (!response.body().isBlank()) json = Json.parse(response.body());
        } catch (RuntimeException ignored) {
            // Not JSON (a proxy's page, say): the raw text is in the error message.
        }
        return new Reply(response.statusCode(), json, response.body());
    }

    static Reply call(String method, String url, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + token);
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return send(builder.build());
    }

    /** The reply, or a plain stop naming what Google said; [what] says what was being read. */
    static Reply expect(Reply reply, String what) {
        if (reply.code / 100 == 2) return reply;
        if (reply.code == 401 || reply.code == 403) {
            fail("Google Play refused to show " + what + " to the robot user (HTTP " + reply.code + " "
                + reply.errorStatus() + "): " + reply.errorMessage()
                + "\nIts permissions are in the Play Console: Users and permissions. Nothing was changed on Play.");
        }
        fail("Google Play could not show " + what + " (HTTP " + reply.code + " " + reply.errorStatus() + "): "
            + reply.errorMessage() + "\nNothing was changed on Play.");
        return reply;
    }

    // ---- The store listing's edit --------------------------------------------------------------------

    /**
     * Commits the edit without sending it for review (edits.commit, changesNotSentForReview): its changes
     * join the Publishing overview's "Changes not yet sent for review", for the owner to send. Without it
     * a commit sends them to Google's review at once, managed publishing or not (2026-11-09).
     */
    static void editCommitUnsent(String edit) throws Exception {
        Reply reply = call("POST", PUBLISHER + app + "/edits/" + enc(edit) + ":commit?changesNotSentForReview=true", null);
        if (reply.code / 100 != 2) {
            fail("Google Play did not take the store listing (HTTP " + reply.code + " " + reply.errorStatus() + "): "
                + reply.errorMessage() + "\nThis part was not committed: it is not on Play.");
        }
    }

    /** Deletes an edit that was never committed (a send that stopped half way): nothing of it reaches Play. */
    static void editDelete(String edit) throws Exception {
        Reply reply = call("DELETE", PUBLISHER + app + "/edits/" + enc(edit), null);
        if (reply.code / 100 != 2 && reply.code != 404) {
            out.println("(The edit could not be deleted: " + reply.errorMessage()
                + ". It was never committed, so nothing changed; Play drops it by itself.)");
        }
    }

    // ---- status --------------------------------------------------------------------------------------

    static void status() throws Exception {
        String base = PUBLISHER + app + "/edits";
        // An edit is Play's scratch copy of the app's setup. This one is only read and then deleted,
        // never committed, so it changes nothing (and leaves any draft in the Play Console alone).
        Reply opened = expect(call("POST", base, null), "the app's setup");
        String edit = str(asMap(opened.json), "id");
        Object tracks;
        Object listings;
        try {
            tracks = expect(call("GET", base + "/" + edit + "/tracks", null), "the tracks").json;
            listings = expect(call("GET", base + "/" + edit + "/listings", null), "the store listing").json;
        } finally {
            Reply deleted = call("DELETE", base + "/" + edit, null);
            if (deleted.code / 100 != 2) {
                out.println("(The temporary edit could not be deleted: " + deleted.errorMessage()
                    + ". It was never committed, so nothing changed; Play drops it by itself.)");
            }
        }

        out.println("Twangle on Google Play (" + app + "), as it is right now:");
        out.println();
        out.println("Tracks");
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Object t : list(tracks, "tracks")) byName.put(str(asMap(t), "track"), asMap(t));
        List<String> order = new ArrayList<>(List.of("internal", "alpha", "beta", "production"));
        for (String name : byName.keySet()) if (!order.contains(name)) order.add(name);
        for (String name : order) {
            Map<String, Object> track = byName.get(name);
            List<Object> releases = track == null ? List.of() : list(track, "releases");
            String label = trackLabel(name);
            if (releases.isEmpty()) {
                out.println("  " + label + ": nothing there");
                continue;
            }
            out.println("  " + label + ":");
            for (Object r : releases) {
                Map<String, Object> release = asMap(r);
                List<Object> codes = list(release, "versionCodes");
                String codeText = codes.isEmpty() ? "no build" : "versionCode " + String.join(", ", codes.stream().map(Object::toString).toList());
                String releaseName = release.get("name") instanceof String s ? s : "(no name)";
                out.println("    " + releaseName + ", " + codeText + ": " + statusText(release));
            }
        }
        out.println();
        List<String> languages = new ArrayList<>();
        for (Object l : list(listings, "listings")) languages.add(str(asMap(l), "language"));
        languages.sort(null);
        out.println("Store listing: " + languages.size() + (languages.size() == 1 ? " language" : " languages"));
        if (!languages.isEmpty()) out.println("  " + String.join(", ", languages));
        out.println();
        out.println("Read only: a temporary edit was opened, read and deleted; nothing was changed on Google Play.");
        out.println("(Google's review of a release shows only in the Play Console, under Publishing overview.)");
    }

    // Whether a track ever had a release go out. Only its current releases are there to read, and once
    // one went out a track always keeps a live (or halted) one, so that's enough.
    static void trackStarted(String name) throws Exception {
        String base = PUBLISHER + app + "/edits";
        Reply opened = expect(call("POST", base, null), "the app's setup");
        String edit = str(asMap(opened.json), "id");
        Object tracks;
        try {
            tracks = expect(call("GET", base + "/" + edit + "/tracks", null), "the tracks").json;
        } finally {
            call("DELETE", base + "/" + edit, null);
        }
        boolean started = false;
        for (Object t : list(tracks, "tracks")) {
            if (!name.equals(str(asMap(t), "track"))) continue;
            for (Object r : list(asMap(t), "releases")) {
                Object state = asMap(r).get("status");
                if ("completed".equals(state) || "inProgress".equals(state) || "halted".equals(state)) started = true;
            }
        }
        out.println(started ? "yes" : "no");
    }

    static String trackLabel(String name) {
        return switch (name) {
            case "internal" -> "Internal testing (internal)";
            case "alpha" -> "Closed testing (alpha)";
            case "beta" -> "Open testing (beta)";
            case "production" -> "Production";
            // The watch app's own Wear OS tracks (docs/release.md, *The watch app*).
            case "wear:qa", "wear:internal" -> "Watch, internal testing (" + name + ")";
            case "wear:beta" -> "Watch, open testing (" + name + ")";
            case "wear:production" -> "Watch, production (" + name + ")";
            default -> name.startsWith("wear:") ? "Watch, closed testing (" + name + ")"
                : name.contains(":") ? "Track " + name + " (another device type)" : "Closed testing (" + name + ")";
        };
    }

    static String statusText(Map<String, Object> release) {
        String status = String.valueOf(release.get("status"));
        String fraction = release.get("userFraction") instanceof BigDecimal f
            ? f.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + " %"
            : null;
        return switch (status) {
            case "draft" -> "draft (not started yet: start it in the Play Console)";
            case "inProgress" -> "staged rollout to " + (fraction != null ? fraction : "part") + " of people";
            case "completed" -> "live (for everyone on this track)";
            case "halted" -> "halted" + (fraction != null ? " at " + fraction : "") + ": nobody new gets it";
            default -> status;
        };
    }

    // ---- health --------------------------------------------------------------------------------------

    /** One version's rate over the days: daily rates weighted by that day's users, as Play's 7- and
     *  28-day figures are (vitals.crashrate reference, crashRate7dUserWeighted). */
    static final class Rate {
        double weighted;
        double users;
        double plainSum;
        int days;
        double mostUsers;

        void add(double rate, double dayUsers) {
            weighted += rate * dayUsers;
            users += dayUsers;
            plainSum += rate;
            days++;
            mostUsers = Math.max(mostUsers, dayUsers);
        }

        /** In percent: [add] takes each day's rate in percent too. */
        double percent() {
            return users > 0 ? weighted / users : plainSum / days;
        }
    }

    record Window(LocalDate start, LocalDate end) {}

    static void health(int days) throws Exception {
        Window crashWindow = window("crashRateMetricSet", days);
        Window anrWindow = window("anrRateMetricSet", days);
        out.println("Crash and ANR rates of Twangle on Google Play (" + app + "), last " + days
            + (days == 1 ? " day." : " days."));
        if (crashWindow == null && anrWindow == null) {
            noVitals();
            return;
        }
        Map<Long, Rate> crashes = crashWindow == null ? Map.of()
            : rates("crashRateMetricSet", crashWindow, "userPerceivedCrashRate");
        Map<Long, Rate> anrs = anrWindow == null ? Map.of()
            : rates("anrRateMetricSet", anrWindow, "userPerceivedAnrRate");
        Window shown = crashWindow != null ? crashWindow : anrWindow;
        out.println("Days " + shown.start + " to " + shown.end.minusDays(1) + " (Pacific time; Play's newest data is from "
            + shown.end.minusDays(1) + "; usually a day or two ago).");
        if (crashes.isEmpty() && anrs.isEmpty()) {
            noVitals();
            return;
        }
        out.println("Play's bad-behaviour lines: user-perceived crash rate " + pct(CRASH_LINE)
            + ", user-perceived ANR rate " + pct(ANR_LINE) + " (share of users a day hit at least once).");
        out.println();

        TreeSet<Long> codes = new TreeSet<>(java.util.Comparator.reverseOrder());
        codes.addAll(crashes.keySet());
        codes.addAll(anrs.keySet());
        List<String> bad = new ArrayList<>();
        List<String> close = new ArrayList<>();
        for (long code : codes) {
            Rate crash = crashes.get(code);
            Rate anr = anrs.get(code);
            String name = versionName(code) + " (" + code + ")";
            double users = Math.max(crash == null ? 0 : crash.mostUsers, anr == null ? 0 : anr.mostUsers);
            out.println("  " + name + ", up to about " + (long) users + " users a day:");
            out.println("    crashes " + judge(crash, CRASH_LINE));
            out.println("    ANRs (app not responding) " + judge(anr, ANR_LINE));
            if (users > 0 && users < 100) {
                out.println("    (few users: one unlucky person moves these rates a lot)");
            }
            boolean over = (crash != null && crash.percent() > CRASH_LINE) || (anr != null && anr.percent() > ANR_LINE);
            boolean near = (crash != null && crash.percent() > 0.75 * CRASH_LINE) || (anr != null && anr.percent() > 0.75 * ANR_LINE);
            if (over) bad.add(name); else if (near) close.add(name);
        }
        out.println();
        if (!bad.isEmpty()) {
            out.println("Verdict: " + String.join(" and ", bad) + (bad.size() == 1 ? " is" : " are")
                + " above Play's bad-behaviour line. Don't raise the"
                + " rollout; look at Android vitals in the Play Console, and think about halting it"
                + " (scripts/release.sh halt).");
        } else if (!close.isEmpty()) {
            out.println("Verdict: below Play's lines, but " + String.join(" and ", close)
                + (close.size() == 1 ? " is" : " are") + " close to them. Look at"
                + " Android vitals in the Play Console before raising the rollout.");
        } else {
            out.println("Verdict: every version is well below Play's bad-behaviour lines: healthy, fine to raise the rollout.");
        }
        out.println("Read only: nothing was changed on Google Play.");
    }

    static void noVitals() {
        out.println();
        out.println("No crash or ANR data on Google Play yet. Play shows these only once enough people use the app");
        out.println("every day (while it is not public, or has few users, there are none). That is not a fault.");
        out.println("Read only: nothing was changed on Google Play.");
    }

    static String judge(Rate rate, double line) {
        if (rate == null) return "no data";
        double p = rate.percent();
        String verdict = p > line ? "ABOVE Play's line of " + pct(line)
            : p > 0.75 * line ? "below Play's line of " + pct(line) + ", but close"
            : "fine (Play's line: " + pct(line) + ")";
        return pct(p) + ": " + verdict;
    }

    static String pct(double p) {
        return String.format(Locale.ROOT, "%.2f %%", p);
    }

    /** Twangle's versionCode back to its version: major * 10000 + minor * 100 + patch (scripts/release.sh). */
    static String versionName(long code) {
        return (code / 10000) + "." + (code / 100 % 100) + "." + (code % 100);
    }

    /** The last [days] full days Play has data for, or null when it has none at all. */
    static Window window(String metricSet, int days) throws Exception {
        Reply reply = call("GET", REPORTING + app + "/" + metricSet, null);
        reportingError(reply, metricSet);
        for (Object f : list(path(reply.json, "freshnessInfo"), "freshnesses")) {
            Map<String, Object> freshness = asMap(f);
            if (!"DAILY".equals(freshness.get("aggregationPeriod"))) continue;
            Map<String, Object> end = asMap(freshness.get("latestEndTime"));
            if (end.get("year") == null) return null;
            LocalDate endDay = LocalDate.of(num(end, "year"), num(end, "month"), num(end, "day"));
            return new Window(endDay.minusDays(days), endDay);
        }
        return null;
    }

    static void reportingError(Reply reply, String what) {
        if (reply.code / 100 == 2) return;
        if (reply.apiDisabled()) {
            fail("the Google Play Developer Reporting API is switched off. Switch on the Google Play Developer Reporting"
                + " API in Google Cloud project Twangle Play (APIs & Services -> Library), wait a few minutes, then run"
                + " this again.\n(Google said: " + reply.errorMessage() + ")");
        }
        // An app with no vitals at all may answer "not found"; that is no data, not a fault.
        if (reply.code == 404) return;
        expect(reply, "the crash and ANR rates (" + what + ")");
    }

    static Map<Long, Rate> rates(String metricSet, Window window, String metric) throws Exception {
        Map<Long, Rate> rates = new TreeMap<>();
        String pageToken = null;
        do {
            String body = "{\"timelineSpec\":{\"aggregationPeriod\":\"DAILY\",\"startTime\":" + day(window.start)
                + ",\"endTime\":" + day(window.end) + "},\"dimensions\":[\"versionCode\"],\"metrics\":[\"" + metric
                + "\",\"distinctUsers\"],\"pageSize\":1000"
                + (pageToken != null ? ",\"pageToken\":\"" + pageToken + "\"" : "") + "}";
            Reply reply = call("POST", REPORTING + app + "/" + metricSet + ":query", body);
            reportingError(reply, metricSet);
            if (reply.code == 404) return rates;
            for (Object r : list(reply.json, "rows")) {
                Map<String, Object> row = asMap(r);
                Long code = null;
                for (Object d : list(row, "dimensions")) {
                    Map<String, Object> dimension = asMap(d);
                    if (!"versionCode".equals(dimension.get("dimension"))) continue;
                    Object value = dimension.get("int64Value") != null ? dimension.get("int64Value") : dimension.get("stringValue");
                    if (value != null) code = Long.parseLong(value.toString());
                }
                Double rate = null;
                double users = 0;
                for (Object m : list(row, "metrics")) {
                    Map<String, Object> value = asMap(m);
                    Object decimal = path(value, "decimalValue", "value");
                    if (decimal == null) continue;
                    // Decimal strings; an empty one means 0 (google.type.Decimal). The rates are fractions
                    // of users (0.0109 is 1.09 %).
                    double number = decimal.toString().isEmpty() ? 0 : Double.parseDouble(decimal.toString());
                    if (metric.equals(value.get("metric"))) rate = number;
                    if ("distinctUsers".equals(value.get("metric"))) users = number;
                }
                if (code != null && rate != null) rates.computeIfAbsent(code, c -> new Rate()).add(100 * rate, users);
            }
            pageToken = reply.json instanceof Map<?, ?> m && m.get("nextPageToken") instanceof String s && !s.isEmpty() ? s : null;
        } while (pageToken != null);
        return rates;
    }

    static String day(LocalDate date) {
        return "{\"year\":" + date.getYear() + ",\"month\":" + date.getMonthValue() + ",\"day\":" + date.getDayOfMonth()
            + ",\"timeZone\":{\"id\":\"" + PACIFIC + "\"}}";
    }

    // ---- reviews -------------------------------------------------------------------------------------

    static void reviews(String language) throws Exception {
        List<Map<String, Object>> found = new ArrayList<>();
        String pageToken = null;
        do {
            String url = PUBLISHER + app + "/reviews?maxResults=100&translationLanguage=" + enc(language)
                + (pageToken != null ? "&token=" + enc(pageToken) : "");
            Reply reply = expect(call("GET", url, null), "the reviews");
            for (Object r : list(reply.json, "reviews")) found.add(asMap(r));
            Object next = path(reply.json, "tokenPagination", "nextPageToken");
            pageToken = next instanceof String s && !s.isEmpty() ? s : null;
        } while (pageToken != null);

        out.println("Reviews of Twangle on Google Play (" + app + ") from the last week, translated into " + language + ".");
        out.println("(Google's API shows only reviews with text, from the last 7 days; older ones and star-only");
        out.println("ratings are in the Play Console: Quality -> Ratings and reviews.)");
        out.println();
        if (found.isEmpty()) {
            out.println("No reviews with text in the last week.");
            out.println("Read only: nothing was changed on Google Play.");
            return;
        }
        int stars = 0;
        int counted = 0;
        for (Map<String, Object> review : found) {
            Map<String, Object> user = null;
            Map<String, Object> reply = null;
            for (Object c : list(review, "comments")) {
                Map<String, Object> comment = asMap(c);
                if (comment.get("userComment") != null && user == null) user = asMap(comment.get("userComment"));
                if (comment.get("developerComment") != null) reply = asMap(comment.get("developerComment"));
            }
            if (user == null) continue;
            int rating = user.get("starRating") instanceof BigDecimal b ? b.intValue() : 0;
            if (rating > 0) {
                stars += rating;
                counted++;
            }
            List<String> facts = new ArrayList<>();
            facts.add(rating > 0 ? "★".repeat(rating) + "☆".repeat(Math.max(0, 5 - rating)) + " " + rating + "/5" : "no stars");
            facts.add(dateOf(user.get("lastModified")));
            if (user.get("reviewerLanguage") != null) facts.add("language " + user.get("reviewerLanguage"));
            if (user.get("appVersionName") != null || user.get("appVersionCode") != null) {
                facts.add("version " + (user.get("appVersionName") != null ? user.get("appVersionName") : "?")
                    + (user.get("appVersionCode") != null ? " (" + user.get("appVersionCode") + ")" : ""));
            }
            facts.add(deviceOf(user));
            out.println(String.join(" · ", facts));
            String text = str(user, "text");
            String original = str(user, "originalText");
            if (original != null && !original.strip().equals(text == null ? "" : text.strip())) {
                out.println("  Original:   " + indent(original, 14));
                out.println("  Translated: " + indent(text, 14));
            } else {
                out.println("  " + indent(text, 2));
            }
            if (reply != null) out.println("  Our reply (" + dateOf(reply.get("lastModified")) + "): " + indent(str(reply, "text"), 4));
            out.println();
        }
        out.print(found.size() + (found.size() == 1 ? " review" : " reviews"));
        if (counted > 0) out.print(String.format(Locale.ROOT, ", %.1f stars on average", (double) stars / counted));
        out.println(".");
        out.println("Read only: nothing was changed on Google Play (no replies sent).");
    }

    static String dateOf(Object timestamp) {
        Object seconds = path(timestamp, "seconds");
        if (seconds == null) return "date unknown";
        return Instant.ofEpochSecond(Long.parseLong(seconds.toString())).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    static String deviceOf(Map<String, Object> user) {
        Map<String, Object> meta = user.get("deviceMetadata") instanceof Map<?, ?> ? asMap(user.get("deviceMetadata")) : Map.of();
        String maker = str(meta, "manufacturer");
        String product = str(meta, "productName");
        String device = product != null ? (maker != null && !product.startsWith(maker) ? maker + " " : "") + product
            : user.get("device") != null ? user.get("device").toString() : "device unknown";
        Object api = user.get("androidOsVersion");
        return device + (api != null ? " (Android API " + api + ")" : "");
    }

    /** The text with its later lines indented by [width] spaces, under its first. */
    static String indent(String text, int width) {
        return text == null ? "" : text.strip().replace("\r", "").replace("\n", "\n" + " ".repeat(width));
    }

    // ---- JSON ----------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object parent, String key) {
        Object value = asMap(parent).get(key);
        return value instanceof List<?> l ? (List<Object>) l : List.of();
    }

    static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    static int num(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof BigDecimal b ? b.intValue() : Integer.parseInt(String.valueOf(value));
    }

    static Object path(Object root, String... keys) {
        Object current = root;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(key);
        }
        return current;
    }

    /** A small JSON reader (RFC 8259): objects to maps, arrays to lists, numbers to BigDecimal. */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            Object value = json.value();
            json.space();
            if (json.i != text.length()) throw json.error();
            return value;
        }

        private RuntimeException error() {
            return new IllegalArgumentException("bad JSON at " + i);
        }

        private void space() {
            while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
        }

        private Object value() {
            space();
            if (i >= s.length()) throw error();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error();
            return new BigDecimal(s.substring(start, i));
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            space();
            if (s.charAt(i) == '}') { i++; return map; }
            while (true) {
                space();
                String key = string();
                space();
                if (s.charAt(i++) != ':') throw error();
                map.put(key, value());
                space();
                char c = s.charAt(i++);
                if (c == '}') return map;
                if (c != ',') throw error();
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++;
            space();
            if (s.charAt(i) == ']') { i++; return list; }
            while (true) {
                list.add(value());
                space();
                char c = s.charAt(i++);
                if (c == ']') return list;
                if (c != ',') throw error();
            }
        }

        private String string() {
            if (s.charAt(i++) != '"') throw error();
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> b.append(e);
                }
            }
        }
    }
}
