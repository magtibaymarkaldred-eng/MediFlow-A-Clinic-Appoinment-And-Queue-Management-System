package mediqueue;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MediQueueData {
    // =====================================================================
    //  MODEL + STORAGE
    // =====================================================================
    static final class Doctor {
        final String name, spec, hours;
        Doctor(String n, String s, String h) { name = n; spec = s; hours = h; }
    }
    static final class Appt { String id, patient, doc, status; LocalDate date; LocalTime time; }
    static final class QEntry { String name, doc, status; QEntry(String n, String d, String s) { name = n; doc = d; status = s; } }
    static final class Pat { String id, name, age, gender, contact, visit; }
    static final class Sched { int id; String doc, slots; LocalDate date; boolean ok; }
    static final class Account {
        final String username, role, name, contact, salt, hash;
        Account(String u, String r, String n, String c, String s, String h) { username = u; role = r; name = n; contact = c; salt = s; hash = h; }
    }

    static final Doctor[] DOCTORS = {
        new Doctor("Dr. Maria Santos", "General Medicine", "9:00 AM - 3:00 PM"),
        new Doctor("Dr. Pedro Reyes", "Pediatrics", "9:00 AM - 4:00 PM"),
        new Doctor("Dr. Juan Cruz", "Dermatology", "10:00 AM - 2:00 PM"),
        new Doctor("Dr. Ana Aquino", "Internal Medicine", "9:00 AM - 12:00 PM")};
    static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);
    static final Path DATA_FILE = Paths.get("mediqueue-data.txt"), USERS_FILE = Paths.get("mediqueue-users.txt");

    static final List<Appt> APPTS = new ArrayList<>();
    static final List<QEntry> QUEUE = new ArrayList<>();
    static final List<Pat> PATS = new ArrayList<>();
    static final List<Sched> SCHEDS = new ArrayList<>();
    static final Map<String, Account> ACCOUNTS = new LinkedHashMap<>();
    static int apptSeq = 1000, schedSeq = 0;

    static String clean(String s, int max) {
        s = s.replaceAll("[|<>\"\\\\]", "").replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
    static Appt appt(String patient, String doc, LocalDate d, String time, String status) {
        Appt a = new Appt();
        a.id = "A-" + (++apptSeq); a.patient = patient; a.doc = doc; a.date = d; a.time = LocalTime.parse(time); a.status = status;
        return a;
    }
    static Pat pat(String id, String name, String age, String gender, String contact, String visit) {
        Pat p = new Pat(); p.id = id; p.name = name; p.age = age; p.gender = gender; p.contact = contact; p.visit = visit; return p;
    }
    static Sched sched(String doc, LocalDate d, String slots, boolean ok) {
        Sched s = new Sched(); s.id = ++schedSeq; s.doc = doc; s.date = d; s.slots = slots; s.ok = ok; return s;
    }

    static void seed() {
        LocalDate t = LocalDate.now();
        APPTS.add(appt("Juan Dela Cruz", "Dr. Maria Santos", t, "09:00", "In Consultation"));
        APPTS.add(appt("Maria Lopez", "Dr. Pedro Reyes", t, "09:30", "In Consultation"));
        APPTS.add(appt("Carlo Santos", "Dr. Ana Aquino", t, "10:00", "Waiting"));
        APPTS.add(appt("Ana Rivera", "Dr. Maria Santos", t, "10:30", "Waiting"));
        APPTS.add(appt("Maria Villanueva", "Dr. Pedro Reyes", t, "11:00", "Scheduled"));
        QUEUE.add(new QEntry("Juan Dela Cruz", "Dr. Maria Santos", "In Consultation"));
        QUEUE.add(new QEntry("Maria Lopez", "Dr. Pedro Reyes", "In Consultation"));
        QUEUE.add(new QEntry("Carlo Santos", "Dr. Ana Aquino", "Waiting"));
        QUEUE.add(new QEntry("Ana Rivera", "Dr. Maria Santos", "Waiting"));
        String today = t.format(DATE_FMT);
        PATS.add(pat("P-001", "Juan Dela Cruz", "34", "Male", "0917 123 4567", today));
        PATS.add(pat("P-002", "Maria Lopez", "29", "Female", "0918 765 4321", today));
        PATS.add(pat("P-003", "Carlo Santos", "41", "Male", "0922 345 6789", t.minusDays(1).format(DATE_FMT)));
        PATS.add(pat("P-004", "Ana Rivera", "26", "Female", "0933 456 7890", t.minusDays(1).format(DATE_FMT)));
        PATS.add(pat("P-005", "Maria Villanueva", "52", "Female", "0945 678 1234", t.minusDays(2).format(DATE_FMT)));
        for (Doctor d : DOCTORS) SCHEDS.add(sched(d.name, t, d.hours, true));
        SCHEDS.add(sched("Dr. Maria Santos", t.plusDays(2), "9:00 AM - 12:00 PM", true));
        SCHEDS.add(sched("Dr. Maria Santos", t.plusDays(4), "9:00 AM - 3:00 PM", false));
        SCHEDS.add(sched("Dr. Pedro Reyes", t.plusDays(1), "9:00 AM - 4:00 PM", true));
    }

    static synchronized void saveData() {
        List<String> out = new ArrayList<>();
        out.add("N|" + apptSeq + "|" + schedSeq);
        for (Appt a : APPTS) out.add(String.join("|", "A", a.id, a.patient, a.doc, a.date.toString(), a.time.toString(), a.status));
        for (QEntry q : QUEUE) out.add(String.join("|", "Q", q.name, q.doc, q.status));
        for (Pat p : PATS) out.add(String.join("|", "P", p.id, p.name, p.age, p.gender, p.contact, p.visit));
        for (Sched s : SCHEDS) out.add(String.join("|", "S", String.valueOf(s.id), s.doc, s.date.toString(), s.slots, String.valueOf(s.ok)));
        try { Files.write(DATA_FILE, out, StandardCharsets.UTF_8); } catch (IOException e) { System.err.println("Could not save data: " + e.getMessage()); }
    }

    static void loadData() {
        try {
            if (Files.exists(DATA_FILE)) {
                for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                    String[] f = line.split("\\|", -1);
                    switch (f[0]) {
                        case "N": apptSeq = Integer.parseInt(f[1]); schedSeq = Integer.parseInt(f[2]); break;
                        case "A": { Appt a = appt(f[2], f[3], LocalDate.parse(f[4]), f[5], f[6]); a.id = f[1]; APPTS.add(a); apptSeq--; break; }
                        case "Q": QUEUE.add(new QEntry(f[1], f[2], f[3])); break;
                        case "P": PATS.add(pat(f[1], f[2], f[3], f[4], f[5], f[6])); break;
                        case "S": { Sched s = sched(f[2], LocalDate.parse(f[3]), f[4], Boolean.parseBoolean(f[5])); s.id = Integer.parseInt(f[1]); schedSeq--; SCHEDS.add(s); break; }
                        default: break;
                    }
                }
            }
        } catch (Exception e) { System.err.println("Could not read saved data, starting fresh: " + e.getMessage()); APPTS.clear(); QUEUE.clear(); PATS.clear(); SCHEDS.clear(); }
        if (APPTS.isEmpty() && PATS.isEmpty()) { seed(); saveData(); }
        boolean removed = APPTS.removeIf(a -> a.patient.equalsIgnoreCase("Tiffany Dela Rama"));
        removed |= QUEUE.removeIf(q -> q.name.equalsIgnoreCase("Tiffany Dela Rama"));
        removed |= PATS.removeIf(p -> p.name.equalsIgnoreCase("Tiffany Dela Rama"));
        if (removed) saveData();
    }

    // ---------- accounts (passwords stored as salted PBKDF2 hashes, same file format as the web version) ----------
    static String newSalt() { byte[] b = new byte[16]; new SecureRandom().nextBytes(b); return Base64.getEncoder().encodeToString(b); }
    static String hash(String pw, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), Base64.getDecoder().decode(salt), 65536, 256);
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static Account makeAccount(String u, String role, String name, String contact, String pw) {
        String salt = newSalt();
        return new Account(u, role, name, contact, salt, hash(pw, salt));
    }
    static void saveAccounts() {
        List<String> out = new ArrayList<>();
        for (Account a : ACCOUNTS.values()) out.add(String.join("|", a.username, a.role, a.name, a.contact, a.salt, a.hash));
        try { Files.write(USERS_FILE, out, StandardCharsets.UTF_8); } catch (IOException e) { System.err.println("Could not save accounts: " + e.getMessage()); }
    }
    static void loadAccounts() {
        try {
            if (Files.exists(USERS_FILE)) for (String line : Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\\|", -1);
                if (p.length == 6) ACCOUNTS.put(p[0].toLowerCase(), new Account(p[0], p[1], p[2], p[3], p[4], p[5]));
            }
        } catch (IOException e) { System.err.println("Could not read accounts: " + e.getMessage()); }

        ACCOUNTS.remove("admin");
        ACCOUNTS.remove("markaldred");
        ACCOUNTS.put("aldred", makeAccount("aldred", "admin", "Mark Aldred Magtibay", "", "aldred06"));
        ACCOUNTS.put("tiffany", makeAccount("tiffany", "patient", "Tiffany", "", "tiffany012"));
        ACCOUNTS.put("aj", makeAccount("aj", "patient", "AJ", "", "aj32"));
        ACCOUNTS.put("stephen", makeAccount("stephen", "patient", "Stephen", "", "glovo456"));
        saveAccounts();
    }
    static boolean sameHash(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }

    // ---------- small queries ----------
    static boolean live(Appt a) { return !a.status.equals("Cancelled") && !a.status.equals("Completed"); }
    static String spec(String doc) { for (Doctor d : DOCTORS) if (d.name.equals(doc)) return d.spec; return ""; }
    static List<Appt> sortedAppts(List<Appt> in) {
        List<Appt> l = new ArrayList<>(in);
        Collections.sort(l, new Comparator<Appt>() {
            public int compare(Appt x, Appt y) { int c = x.date.compareTo(y.date); return c != 0 ? c : x.time.compareTo(y.time); }
        });
        return l;
    }
    static List<LocalTime> allSlots() {
        List<LocalTime> out = new ArrayList<>();
        for (LocalTime t = LocalTime.of(9, 0); t.isBefore(LocalTime.of(17, 0)); t = t.plusMinutes(30)) {
            if (t.equals(LocalTime.of(12, 0)) || t.equals(LocalTime.of(12, 30))) continue;
            out.add(t);
        }
        return out;
    }
    static int count(String... statuses) {
        int n = 0;
        for (QEntry q : QUEUE) for (String s : statuses) if (q.status.equals(s)) n++;
        return n;
    }
    static String nextPatId() {
        int max = 0;
        for (Pat p : PATS) try { max = Math.max(max, Integer.parseInt(p.id.substring(2))); } catch (Exception ignored) { }
        return String.format("P-%03d", max + 1);
    }

}
