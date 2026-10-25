package in.swarnabhoomi.farmlog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OrganicGovernmentSchemeService {
    private static final Logger log = LoggerFactory.getLogger(OrganicGovernmentSchemeService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final String PIB_RELEASES = "https://www.pib.gov.in/AllRelease.aspx?reg=1&lang=1";
    private static final String MAHARASHTRA_GRS = "https://krishi.maharashtra.gov.in/Site/Common/ViewGr.aspx?Doctype=f73825ef-89cb-4e56-ac83-4f73172aaacd%3FMenuID%3D2952";
    private static final String MAHARASHTRA_GUIDELINES = "https://krishi.maharashtra.gov.in/Site/Information/GuideLinesInformation.aspx?ID=6";
    private static final Pattern PIB_ITEM = Pattern.compile(
            "(?is)<li\\s*>\\s*<a\\b([^>]*)>(.*?)</a>\\s*<span\\b[^>]*class=['\\\"]publishdatesmall['\\\"][^>]*>(.*?)</li>");
    private static final Pattern GR_ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern SPAN = Pattern.compile(
            "(?is)<span\\b[^>]*id=['\\\"]([^'\\\"]+)['\\\"][^>]*>(.*?)</span>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)([a-z][a-z0-9_-]*)\\s*=\\s*(['\\\"])(.*?)\\2");
    private static final Pattern ENTITY = Pattern.compile("&#(x[0-9a-f]+|[0-9]+);", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEVANT = Pattern.compile(
            "(?iu)(\\bPKVY\\b|\\bNMNF\\b|\\bMOVCDNER\\b|organic\\s+farm|natural\\s+farm|paramparagat\\s+krishi|national\\s+mission\\s+on\\s+natural|परंपरागत कृषी विकास योजना|राष्ट्रीय नैसर्गिक शेती अभियान|सेंद्रिय शेती|जैविक खेती|प्राकृतिक खेती|जैविक शेती|नैसर्गिक शेती)");
    private static final Pattern SCHEME_NOTICE = Pattern.compile(
            "(?iu)(\\bPKVY\\b|\\bNMNF\\b|\\bMOVCDNER\\b|\\bscheme\\b|yojana|mission|launch|announc|subsid|incentive|परंपरागत कृषी विकास योजना|राष्ट्रीय नैसर्गिक शेती अभियान|योजना|अभियान|अनुदान|सवलत|शासन निर्णय)");
    private static final DateTimeFormatter PIB_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter GR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
    private static final long MAX_ANNOUNCEMENTS = 30;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private volatile List<Notice> notices = List.of();
    private volatile Instant lastCheckedAt;
    private volatile boolean pibAvailable;
    private volatile boolean maharashtraAvailable;

    public Feed currentFeed() {
        Instant checkedAt = lastCheckedAt;
        if (checkedAt == null || checkedAt.isBefore(Instant.now().minus(Duration.ofMinutes(15)))) {
            refresh();
        }
        LocalDate today = LocalDate.now(INDIA);
        List<Notice> currentNotices = notices.stream()
                .sorted(Comparator.comparing(Notice::publishedOn).reversed())
                .limit(MAX_ANNOUNCEMENTS)
                .map(notice -> notice.withNewToday(notice.schemeRelated() && notice.publishedOn().equals(today)))
                .toList();
        return new Feed(schemes(), currentNotices, lastCheckedAt, pibAvailable, maharashtraAvailable);
    }

    @Scheduled(fixedDelayString = "${app.organic-schemes.refresh-interval-ms:900000}",
            initialDelayString = "${app.organic-schemes.initial-delay-ms:15000}")
    public synchronized void refresh() {
        Map<String, Notice> updated = new LinkedHashMap<>();
        boolean pibOk = false;
        boolean maharashtraOk = false;
        try {
            for (Notice notice : parsePib(fetch(PIB_RELEASES))) {
                updated.put(notice.sourceUrl(), notice);
            }
            pibOk = true;
        } catch (Exception ignored) {
            // Keep notices from the other official source if one site is temporarily unavailable.
            log.warn("Unable to refresh the PIB organic farming announcements: {}", ignored.getMessage());
        }
        try {
            for (Notice notice : parseMaharashtra(fetch(MAHARASHTRA_GRS))) {
                updated.put(notice.sourceUrl(), notice);
            }
            maharashtraOk = true;
        } catch (Exception ignored) {
            // The previous successful snapshot remains available to farmers.
            log.warn("Unable to refresh the Maharashtra Agriculture GR list: {}", ignored.getMessage());
        }
        if (pibOk || maharashtraOk || lastCheckedAt == null) {
            if (pibOk || maharashtraOk) {
                if (!pibOk) {
                    notices.stream().filter(notice -> "PIB".equals(notice.sourceType()))
                            .forEach(notice -> updated.putIfAbsent(notice.sourceUrl(), notice));
                }
                if (!maharashtraOk) {
                    notices.stream().filter(notice -> "MAHARASHTRA_GR".equals(notice.sourceType()))
                            .forEach(notice -> updated.putIfAbsent(notice.sourceUrl(), notice));
                }
                notices = List.copyOf(updated.values());
            }
        }
        pibAvailable = pibOk;
        maharashtraAvailable = maharashtraOk;
        lastCheckedAt = Instant.now();
    }

    private String fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Swarnabhoomi/1.0 (farmer government scheme updates)")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Government source returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private List<Notice> parsePib(String html) {
        List<Notice> result = new ArrayList<>();
        Matcher matcher = PIB_ITEM.matcher(html);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(1));
            String title = decodeHtml(attributes.getOrDefault("title", stripHtml(matcher.group(2))));
            if (!RELEVANT.matcher(title).find()) {
                continue;
            }
            String rawDate = stripHtml(matcher.group(3)).replaceFirst("(?i)^Posted on:\\s*", "").trim();
            LocalDate date = parseDate(rawDate, PIB_DATE);
            String href = attributes.get("href");
            if (date == null || href == null || href.isBlank()) {
                continue;
            }
            String link = resolveLink("https://www.pib.gov.in/", decodeHtml(href));
            boolean schemeRelated = SCHEME_NOTICE.matcher(title).find();
            result.add(new Notice(title, "Press Information Bureau, Government of India", link, date,
                    schemeRelated, false, "PIB"));
        }
        return result;
    }

    private List<Notice> parseMaharashtra(String html) {
        List<Notice> result = new ArrayList<>();
        Matcher rows = GR_ROW.matcher(html);
        while (rows.find()) {
            String row = rows.group(1);
            Map<String, String> spans = new LinkedHashMap<>();
            Matcher spanMatcher = SPAN.matcher(row);
            while (spanMatcher.find()) {
                spans.put(spanMatcher.group(1), stripHtml(spanMatcher.group(2)));
            }
            String title = spans.entrySet().stream()
                    .filter(entry -> entry.getKey().matches("(?i).*lblsub_\\d+$"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("");
            if (title.isBlank() || !RELEVANT.matcher(title).find()) {
                continue;
            }
            String dateText = spans.entrySet().stream()
                    .filter(entry -> entry.getKey().matches("(?i).*lblCreatedDate_\\d+$"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("");
            LocalDate date = parseDate(dateText, GR_DATE);
            Matcher linkMatcher = Pattern.compile("(?is)<a\\b([^>]*)>").matcher(row);
            if (date == null || !linkMatcher.find()) {
                continue;
            }
            String href = attributes(linkMatcher.group(1)).get("href");
            if (href == null || href.isBlank()) {
                continue;
            }
            String link = resolveLink("https://krishi.maharashtra.gov.in/Site/Common/ViewGr.aspx", decodeHtml(href));
            boolean schemeRelated = SCHEME_NOTICE.matcher(title).find();
            result.add(new Notice(title, "महाराष्ट्र कृषी विभाग — शासन निर्णय", link, date,
                    schemeRelated, false, "MAHARASHTRA_GR"));
        }
        return result;
    }

    private static Map<String, String> attributes(String html) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(html);
        while (matcher.find()) {
            result.put(matcher.group(1).toLowerCase(Locale.ROOT), decodeHtml(matcher.group(3)));
        }
        return result;
    }

    private static String resolveLink(String base, String href) {
        try {
            return new URL(new URL(base), href).toExternalForm();
        } catch (MalformedURLException ignored) {
            return base;
        }
    }

    private static LocalDate parseDate(String raw, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(raw, formatter);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stripHtml(String html) {
        return decodeHtml(html.replaceAll("(?is)<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
    }

    private static String decodeHtml(String value) {
        if (value == null) {
            return "";
        }
        String decoded = value.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
                .replace("&ndash;", "–").replace("&mdash;", "—").replace("&rsquo;", "’")
                .replace("&lsquo;", "‘").replace("&ldquo;", "“").replace("&rdquo;", "”")
                .replace("&lt;", "<").replace("&gt;", ">");
        Matcher entities = ENTITY.matcher(decoded);
        StringBuffer buffer = new StringBuffer();
        while (entities.find()) {
            try {
                String entity = entities.group(1);
                int codePoint = entity.toLowerCase(Locale.ROOT).startsWith("x")
                        ? Integer.parseInt(entity.substring(1), 16) : Integer.parseInt(entity);
                entities.appendReplacement(buffer, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (RuntimeException ignored) {
                entities.appendReplacement(buffer, Matcher.quoteReplacement(entities.group()));
            }
        }
        entities.appendTail(buffer);
        return buffer.toString();
    }

    private static List<Scheme> schemes() {
        return List.of(
                new Scheme("pkvy", "परंपरागत कृषी विकास योजना (PKVY)", "सेंद्रिय शेती",
                        "शेतकरी गट/क्लस्टरच्या माध्यमातून सेंद्रिय शेती, प्रमाणन आणि बाजारपेठेशी जोडणीसाठी केंद्र व राज्य शासनाची योजना.",
                        "PIB च्या अधिकृत माहितीनुसार ३ वर्षांत प्रति हेक्टर ₹३१,५०० पर्यंत सहाय्य; त्यात सेंद्रिय निविष्ठांसाठी ₹१५,००० DBT समाविष्ट आहे. पात्रता, गट निवड आणि प्रत्यक्ष अर्ज राज्याच्या अंमलबजावणी व मंजुरीनुसार ठरतात.",
                        "लहान व सीमांत शेतकऱ्यांना प्राधान्य देत गट/क्लस्टर पद्धतीने अंमलबजावणी केली जाते. सध्याची अर्जस्थिती आणि स्थानिक पात्रता कृषी विभागाकडून तपासा.",
                        "https://www.pib.gov.in/PressReleasePage.aspx?PRID=2197758&lang=2&reg=48",
                        MAHARASHTRA_GUIDELINES, "Press Information Bureau आणि महाराष्ट्र कृषी विभाग"),
                new Scheme("nmnf", "राष्ट्रीय नैसर्गिक शेती अभियान (NMNF)", "नैसर्गिक शेती — पूरक माहिती",
                        "हे सेंद्रिय शेती योजनेपेक्षा वेगळे नैसर्गिक शेती अभियान आहे. नैसर्गिक शेतीकडे वळणाऱ्या शेतकऱ्यांसाठी प्रशिक्षण, क्लस्टर आणि जैव-निविष्ठा संसाधन केंद्रांना मदत दिली जाते.",
                        "PIB च्या २०२६ मधील माहितीनुसार पात्र सहभागी शेतकऱ्यांना जास्तीत जास्त १ एकरासाठी २ वर्षे प्रति एकर प्रति वर्ष ₹४,००० प्रोत्साहनाची तरतूद आहे. अर्ज/नोंदणी आणि अटी महाराष्ट्राच्या चालू अंमलबजावणी सूचनांनुसार तपासा.",
                        "नैसर्गिक शेतीचे प्रशिक्षण व राज्यातील चालू गट/नोंदणीबाबत कृषी विभागाच्या सूचना तपासा.",
                        "https://www.pib.gov.in/PressNoteDetails.aspx?ModuleId=3&NoteId=159690&lang=9&reg=1",
                        MAHARASHTRA_GUIDELINES, "Press Information Bureau आणि महाराष्ट्र कृषी विभाग"));
    }

    public record Scheme(String id, String name, String category, String description, String benefit,
            String eligibilityNote, String sourceUrl, String stateGuidelinesUrl, String sourceName) {}

    public record Notice(String title, String sourceName, String sourceUrl, LocalDate publishedOn,
            boolean schemeRelated, boolean isNewToday, String sourceType) {
        Notice withNewToday(boolean value) {
            return new Notice(title, sourceName, sourceUrl, publishedOn, schemeRelated, value, sourceType);
        }
    }

    public record Feed(List<Scheme> schemes, List<Notice> announcements, Instant checkedAt,
            boolean pibAvailable, boolean maharashtraAvailable) {}
}
