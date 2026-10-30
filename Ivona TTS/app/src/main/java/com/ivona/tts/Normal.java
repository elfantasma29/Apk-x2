package com.ivona.tts;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Normal {

    static final class Opc {
        boolean inteligente;
        int digitos;
        boolean enlaces;
        String pais = "";
    }

    private static final class Num {
        long ent;
        String frac = "";
    }

    private interface Rep {
        String f(Matcher m);
    }

    private static final String[] U = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve",
            "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete",
            "veintiocho", "veintinueve"};
    private static final String[] D = {"", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
    private static final String[] C = {"", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"};
    private static final String[] MES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre"};
    private static final int[] DIAS = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private static final String NUM = "\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?";

    private static final Pattern FECHA = Pattern.compile("(?<![\\d/.\\-:])(\\d{1,2})([/.\\-])(\\d{1,2})\\2(\\d{4}|\\d{2})(?![\\d])(?![/.\\-]\\d)");
    private static final Pattern FECHA_ISO = Pattern.compile("(?<![\\d/.\\-:])(\\d{4})([/.\\-])(\\d{1,2})\\2(\\d{1,2})(?![\\d])(?![/.\\-]\\d)");
    private static final Pattern ANIO = Pattern.compile("(?i)(\\bde\\s+(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+(?:de|del)\\s+)(\\d{4})(?![\\p{N}])");
    private static final String[] LISTA_MONEDAS = {
            "USD|dólar estadounidense|dólares estadounidenses|m",
            "EUR|euro|euros|m",
            "GBP|libra esterlina|libras esterlinas|f",
            "JPY|yen japonés|yenes japoneses|m",
            "CNY|yuan chino|yuanes chinos|m",
            "MXN|peso mexicano|pesos mexicanos|m",
            "ARS|peso argentino|pesos argentinos|m",
            "COP|peso colombiano|pesos colombianos|m",
            "CLP|peso chileno|pesos chilenos|m",
            "UYU|peso uruguayo|pesos uruguayos|m",
            "DOP|peso dominicano|pesos dominicanos|m",
            "CUP|peso cubano|pesos cubanos|m",
            "PHP|peso filipino|pesos filipinos|m",
            "PEN|sol peruano|soles peruanos|m",
            "BRL|real brasileño|reales brasileños|m",
            "BOB|boliviano|bolivianos|m",
            "PYG|guaraní paraguayo|guaraníes paraguayos|m",
            "VES|bolívar venezolano|bolívares venezolanos|m",
            "VED|bolívar digital|bolívares digitales|m",
            "CRC|colón costarricense|colones costarricenses|m",
            "SVC|colón salvadoreño|colones salvadoreños|m",
            "GTQ|quetzal guatemalteco|quetzales guatemaltecos|m",
            "HNL|lempira hondureña|lempiras hondureñas|f",
            "NIO|córdoba nicaragüense|córdobas nicaragüenses|m",
            "PAB|balboa panameño|balboas panameños|m",
            "CAD|dólar canadiense|dólares canadienses|m",
            "AUD|dólar australiano|dólares australianos|m",
            "NZD|dólar neozelandés|dólares neozelandeses|m",
            "HKD|dólar hongkonés|dólares hongkoneses|m",
            "SGD|dólar singapurense|dólares singapurenses|m",
            "TWD|nuevo dólar taiwanés|nuevos dólares taiwaneses|m",
            "JMD|dólar jamaiquino|dólares jamaiquinos|m",
            "TTD|dólar trinitense|dólares trinitenses|m",
            "BBD|dólar barbadense|dólares barbadenses|m",
            "BSD|dólar bahameño|dólares bahameños|m",
            "BZD|dólar beliceño|dólares beliceños|m",
            "GYD|dólar guyanés|dólares guyaneses|m",
            "SRD|dólar surinamés|dólares surinameses|m",
            "FJD|dólar fiyiano|dólares fiyianos|m",
            "NAD|dólar namibio|dólares namibios|m",
            "XCD|dólar del Caribe Oriental|dólares del Caribe Oriental|m",
            "HTG|gourde haitiano|gourdes haitianos|m",
            "CHF|franco suizo|francos suizos|m",
            "SEK|corona sueca|coronas suecas|f",
            "NOK|corona noruega|coronas noruegas|f",
            "DKK|corona danesa|coronas danesas|f",
            "CZK|corona checa|coronas checas|f",
            "ISK|corona islandesa|coronas islandesas|f",
            "PLN|esloti polaco|eslotis polacos|m",
            "HUF|forinto húngaro|forintos húngaros|m",
            "RON|leu rumano|lei rumanos|m",
            "MDL|leu moldavo|lei moldavos|m",
            "BGN|lev búlgaro|leves búlgaros|m",
            "HRK|kuna croata|kunas croatas|f",
            "RSD|dinar serbio|dinares serbios|m",
            "UAH|grivna ucraniana|grivnas ucranianas|f",
            "RUB|rublo ruso|rublos rusos|m",
            "BYN|rublo bielorruso|rublos bielorrusos|m",
            "TRY|lira turca|liras turcas|f",
            "ILS|nuevo séquel israelí|nuevos séqueles israelíes|m",
            "AED|dírham emiratí|dírhams emiratíes|m",
            "SAR|riyal saudí|riyales saudíes|m",
            "QAR|riyal catarí|riyales cataríes|m",
            "KWD|dinar kuwaití|dinares kuwaitíes|m",
            "BHD|dinar bahreiní|dinares bahreiníes|m",
            "OMR|rial omaní|riales omaníes|m",
            "JOD|dinar jordano|dinares jordanos|m",
            "LBP|libra libanesa|libras libanesas|f",
            "EGP|libra egipcia|libras egipcias|f",
            "SYP|libra siria|libras sirias|f",
            "IQD|dinar iraquí|dinares iraquíes|m",
            "IRR|rial iraní|riales iraníes|m",
            "YER|rial yemení|riales yemeníes|m",
            "INR|rupia india|rupias indias|f",
            "PKR|rupia pakistaní|rupias pakistaníes|f",
            "LKR|rupia esrilanquesa|rupias esrilanquesas|f",
            "NPR|rupia nepalesa|rupias nepalesas|f",
            "IDR|rupia indonesia|rupias indonesias|f",
            "MUR|rupia mauriciana|rupias mauricianas|f",
            "SCR|rupia seychellense|rupias seychellenses|f",
            "BDT|taka bangladesí|takas bangladesíes|m",
            "KRW|won surcoreano|wones surcoreanos|m",
            "KPW|won norcoreano|wones norcoreanos|m",
            "THB|bat tailandés|bats tailandeses|m",
            "VND|dong vietnamita|dongs vietnamitas|m",
            "MYR|ringgit malayo|ringgits malayos|m",
            "MMK|kyat birmano|kyats birmanos|m",
            "KHR|riel camboyano|rieles camboyanos|m",
            "LAK|kip laosiano|kips laosianos|m",
            "MNT|tugrik mongol|tugriks mongoles|m",
            "KZT|tenge kazajo|tenges kazajos|m",
            "UZS|som uzbeko|soms uzbekos|m",
            "KGS|som kirguís|soms kirguises|m",
            "AFN|afgani afgano|afganis afganos|m",
            "GEL|lari georgiano|laris georgianos|m",
            "AMD|dram armenio|drams armenios|m",
            "AZN|manat azerbaiyano|manats azerbaiyanos|m",
            "ALL|lek albanés|leks albaneses|m",
            "MKD|denar macedonio|denares macedonios|m",
            "BAM|marco convertible|marcos convertibles|m",
            "ZAR|rand sudafricano|rands sudafricanos|m",
            "NGN|naira nigeriano|nairas nigerianos|f",
            "GHS|cedi ghanés|cedis ghaneses|m",
            "KES|chelín keniano|chelines kenianos|m",
            "UGX|chelín ugandés|chelines ugandeses|m",
            "TZS|chelín tanzano|chelines tanzanos|m",
            "ETB|birr etíope|birres etíopes|m",
            "MAD|dírham marroquí|dírhams marroquíes|m",
            "DZD|dinar argelino|dinares argelinos|m",
            "TND|dinar tunecino|dinares tunecinos|m",
            "LYD|dinar libio|dinares libios|m",
            "XOF|franco CFA|francos CFA|m",
            "XAF|franco CFA|francos CFA|m",
            "AOA|kwanza angoleño|kwanzas angoleños|m",
            "MZN|metical mozambiqueño|meticales mozambiqueños|m",
            "ZMW|kuacha zambiano|kuachas zambianos|m",
            "BWP|pula botsuano|pulas botsuanos|f"
    };
    private static final java.util.HashMap<String, String[]> MONEDAS = new java.util.HashMap<>();
    private static final Pattern MONEDA_SUF;
    private static final Pattern MONEDA_PRE;
    private static final Pattern FRACCION = Pattern.compile("(?<![\\p{N}/.,:\\-])([1-3])/([2-4])(?![\\p{N}/])");
    private static final Pattern NUMERO_ORDEN = Pattern.compile("(?<![\\p{L}\\p{N}])[Nn][°º](?=\\s?\\d)");
    private static final Pattern NUMERO = Pattern.compile("(?<![\\p{L}\\p{N}_.,/:\\-$€£¥])(" + NUM + ")(?![\\p{N}])(?![.,]\\p{N})");
    private static final Pattern DIGITOS = Pattern.compile("(?<![\\p{L}\\p{N}])(\\d{1,3}(?:[.,]\\d{3})+|\\d{2,})(?![\\p{L}\\p{N}])");

    private static final Pattern CORREO = Pattern.compile("(?i)(?<![\\p{L}\\p{N}._%+\\-])([\\p{L}\\p{N}._%+\\-]+)@([a-z0-9\\-]+(?:\\.[a-z0-9\\-]+)+)");
    private static final Pattern URL = Pattern.compile("(?i)(?<![\\p{L}\\p{N}@._\\-])((?:https?://|www\\.)[^\\s<>\"']+|(?:[a-z0-9][a-z0-9\\-]*\\.)+(?-i:com\\.mx|com\\.ar|com\\.co|com\\.es|com\\.pe|com\\.cl|gob\\.mx|org\\.mx|edu\\.mx|com|net|org|edu|gov|io|app|dev|info|tv|xyz|ly|gl|mx)(?::\\d+)?(?:/[^\\s<>\"']*)?)(?![\\p{L}\\p{N}@\\-])");
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{N}_&#/])#(\\p{L}[\\p{L}\\p{N}_]*)");

    static {
        StringBuilder codigos = new StringBuilder();
        for (String l : LISTA_MONEDAS) {
            String[] c = l.split("\\|");
            MONEDAS.put(c[0], new String[]{c[1], c[2], c[3]});
            if (codigos.length() > 0) {
                codigos.append('|');
            }
            codigos.append(c[0]);
        }
        String mult = "(mil millones|millones|millón|mil)";
        MONEDA_SUF = Pattern.compile("(?<![\\p{L}\\p{N}.,])(" + NUM + ")(?![\\p{N}])(?:\\s?" + mult + "(?![\\p{L}]))?\\s?(" + codigos + ")(?![\\p{L}\\p{N}])");
        MONEDA_PRE = Pattern.compile("(?<![\\p{L}\\p{N}])(" + codigos + ")\\s?(" + NUM + ")(?![\\p{N}])(?:\\s?" + mult + "(?![\\p{L}]))?");
    }

    private Normal() {
    }

    private static String cod(String p) {
        String s = p == null ? "" : p.toUpperCase(Locale.ROOT);
        if (s.startsWith("ME") || s.startsWith("MX")) {
            return "MX";
        }
        if (s.startsWith("ES")) {
            return "ES";
        }
        if (s.startsWith("AR")) {
            return "AR";
        }
        if (s.startsWith("CO")) {
            return "CO";
        }
        if (s.startsWith("CH") || s.startsWith("CL")) {
            return "CL";
        }
        return "OT";
    }

    private static String reemplazar(Pattern p, String t, Rep r) {
        Matcher m = p.matcher(t);
        StringBuilder sb = new StringBuilder(t.length() + 16);
        int pos = 0;
        while (m.find()) {
            String s;
            try {
                s = r.f(m);
            } catch (RuntimeException e) {
                s = null;
            }
            sb.append(t, pos, m.start());
            sb.append(s == null ? m.group() : s);
            pos = m.end();
        }
        sb.append(t, pos, t.length());
        return sb.toString();
    }

    static String palabras(long n, boolean apoc) {
        if (n < 0 || n >= 1000000000000L) {
            return null;
        }
        if (n == 0) {
            return "cero";
        }
        long mill = n / 1000000L;
        long resto = n % 1000000L;
        StringBuilder sb = new StringBuilder();
        if (mill > 0) {
            if (mill == 1) {
                sb.append("un millón");
            } else {
                sb.append(menorMillon(mill, true)).append(" millones");
            }
        }
        if (resto > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(menorMillon(resto, apoc));
        }
        return sb.toString();
    }

    private static String menorMillon(long k, boolean apoc) {
        long t = k / 1000;
        int r = (int) (k % 1000);
        StringBuilder sb = new StringBuilder();
        if (t > 0) {
            if (t == 1) {
                sb.append("mil");
            } else {
                sb.append(menorMil((int) t, true)).append(" mil");
            }
        }
        if (r > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(menorMil(r, apoc));
        }
        return sb.toString();
    }

    private static String menorMil(int n, boolean apoc) {
        StringBuilder sb = new StringBuilder();
        int h = n / 100;
        int r = n % 100;
        if (h > 0) {
            if (n == 100) {
                return "cien";
            }
            sb.append(C[h]);
        }
        if (r > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (r < 30) {
                sb.append(U[r]);
            } else {
                sb.append(D[r / 10]);
                if (r % 10 > 0) {
                    sb.append(" y ").append(U[r % 10]);
                }
            }
        }
        String s = sb.toString();
        if (apoc) {
            if (s.endsWith("veintiuno")) {
                s = s.substring(0, s.length() - 9) + "veintiún";
            } else if (s.endsWith("uno")) {
                s = s.substring(0, s.length() - 3) + "un";
            }
        }
        return s;
    }

    private static String femenino(String w) {
        if (w.endsWith("veintiún")) {
            return w.substring(0, w.length() - 8) + "veintiuna";
        }
        if (w.endsWith("un")) {
            return w + "a";
        }
        return w;
    }

    private static int cuenta(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static Num parse(String s, String cod, boolean moneda) {
        int pts = cuenta(s, '.');
        int cms = cuenta(s, ',');
        char dec = 0;
        char mil = 0;
        if (pts > 0 && cms > 0) {
            dec = s.lastIndexOf('.') > s.lastIndexOf(',') ? '.' : ',';
            mil = dec == '.' ? ',' : '.';
        } else if (pts + cms > 0) {
            char sep = pts > 0 ? '.' : ',';
            int cnt = pts > 0 ? pts : cms;
            int ult = s.length() - s.lastIndexOf(sep) - 1;
            int ini = s.indexOf(sep);
            if (cnt > 1) {
                if (ult != 3) {
                    return null;
                }
                mil = sep;
            } else if (ult != 3) {
                dec = sep;
            } else if (ini > 3 || s.charAt(0) == '0') {
                dec = sep;
            } else if (moneda) {
                mil = sep;
            } else if ("MX".equals(cod)) {
                if (sep == ',') {
                    mil = sep;
                } else {
                    dec = sep;
                }
            } else {
                if (sep == '.') {
                    mil = sep;
                } else {
                    dec = sep;
                }
            }
        }
        String ent = s;
        String frac = "";
        if (dec != 0) {
            int i = s.lastIndexOf(dec);
            ent = s.substring(0, i);
            frac = s.substring(i + 1);
        }
        if (mil != 0) {
            String[] g = ent.split("\\" + mil, -1);
            for (int i = 1; i < g.length; i++) {
                if (g[i].length() != 3) {
                    return null;
                }
            }
            if (g[0].length() == 0 || g[0].length() > 3) {
                return null;
            }
            ent = ent.replace(String.valueOf(mil), "");
        }
        if (ent.length() == 0 || ent.length() > 12) {
            return null;
        }
        Num n = new Num();
        n.ent = Long.parseLong(ent);
        n.frac = frac;
        return n;
    }

    private static String digitosPalabras(String d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(U[d.charAt(i) - '0']);
        }
        return sb.toString();
    }

    private static String fraccionLeer(String f) {
        if (f.length() <= 2) {
            if (f.charAt(0) == '0') {
                return f.length() == 1 ? "cero" : "cero " + U[f.charAt(1) - '0'];
            }
            return palabras(Long.parseLong(f), false);
        }
        return digitosPalabras(f);
    }

    private static String decimal(Num n, String cod) {
        String e = palabras(n.ent, false);
        if (e == null) {
            return null;
        }
        if (n.frac.length() == 0) {
            return e;
        }
        return e + ("MX".equals(cod) ? " punto " : " coma ") + fraccionLeer(n.frac);
    }

    private static String fechaTexto(int d, int m, int a, String cod) {
        if (m < 1 || m > 12 || d < 1 || d > DIAS[m - 1]) {
            return null;
        }
        if (m == 2 && d == 29 && !(a % 4 == 0 && (a % 100 != 0 || a % 400 == 0))) {
            return null;
        }
        String dia = d == 1 && !"ES".equals(cod) && !"CL".equals(cod) ? "primero" : palabras(d, false);
        return dia + " de " + MES[m - 1] + " de " + palabras(a, false);
    }

    private static String fechas(String t, String cod) {
        String r = reemplazar(FECHA_ISO, t, m -> {
            int a = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(3));
            int d = Integer.parseInt(m.group(4));
            if (a < 1000) {
                return null;
            }
            return fechaTexto(d, mes, a, cod);
        });
        return reemplazar(FECHA, r, m -> {
            String sep = m.group(2);
            String ys = m.group(4);
            if (".".equals(sep) && ys.length() != 4) {
                return null;
            }
            int d = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(3));
            int y = Integer.parseInt(ys);
            if (ys.length() == 2) {
                y += y <= 50 ? 2000 : 1900;
            }
            if (mes > 12 && d <= 12) {
                int x = d;
                d = mes;
                mes = x;
            }
            return fechaTexto(d, mes, y, cod);
        });
    }

    private static String dinero(Num n, String[] mon, String mult, String cod) {
        boolean frac = n.frac.length() > 0;
        String sing = mon[0];
        String plur = mon[1];
        if (mult != null) {
            String mw = mult.toLowerCase(Locale.ROOT);
            String e;
            if (mw.equals("mil")) {
                if (frac) {
                    String d = decimal(n, cod);
                    e = d == null ? null : d + " mil";
                } else if (n.ent == 1) {
                    e = "mil";
                } else {
                    String w = palabras(n.ent, true);
                    e = w == null ? null : w + " mil";
                }
                return e == null ? null : e + " " + plur;
            }
            if (mw.equals("mil millones")) {
                String w = frac ? decimal(n, cod) : palabras(n.ent, true);
                return w == null ? null : w + " mil millones de " + plur;
            }
            if (frac) {
                String d = decimal(n, cod);
                e = d == null ? null : d + " millones";
            } else if (n.ent == 1) {
                e = "un millón";
            } else {
                String w = palabras(n.ent, true);
                e = w == null ? null : w + " millones";
            }
            return e == null ? null : e + " de " + plur;
        }
        if (frac) {
            String d = decimal(n, cod);
            return d == null ? null : d + " " + plur;
        }
        String w = palabras(n.ent, true);
        if (w == null) {
            return null;
        }
        if ("f".equals(mon[2])) {
            w = femenino(w);
        }
        StringBuilder sb = new StringBuilder(w);
        if (n.ent >= 1000000L && n.ent % 1000000L == 0) {
            sb.append(" de");
        }
        sb.append(' ').append(n.ent == 1 ? sing : plur);
        return sb.toString();
    }

    private static String monedas(String t, String cod) {
        String r = reemplazar(MONEDA_SUF, t, m -> {
            Num n = parse(m.group(1), cod, true);
            String[] mon = MONEDAS.get(m.group(3));
            if (n == null || mon == null) {
                return null;
            }
            return dinero(n, mon, m.group(2), cod);
        });
        return reemplazar(MONEDA_PRE, r, m -> {
            Num n = parse(m.group(2), cod, true);
            String[] mon = MONEDAS.get(m.group(1));
            if (n == null || mon == null) {
                return null;
            }
            return dinero(n, mon, m.group(3), cod);
        });
    }

    private static String fracciones(String t) {
        return reemplazar(FRACCION, t, m -> {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            if (a >= b) {
                return null;
            }
            String den;
            if (b == 2) {
                den = "medio";
            } else if (b == 3) {
                den = "tercio";
            } else {
                den = "cuarto";
            }
            if (a > 1) {
                den = den + "s";
            }
            if (a == 1 && b == 2) {
                int i = m.end();
                while (i < t.length() && t.charAt(i) == ' ') {
                    i++;
                }
                boolean sigue = i > m.end() && i < t.length() && Character.isLetter(t.charAt(i));
                return sigue ? "medio" : "un medio";
            }
            return (a == 1 ? "un" : palabras(a, false)) + " " + den;
        });
    }

    private static boolean siguienteFemenino(String t, int pos) {
        int i = pos;
        while (i < t.length() && t.charAt(i) == ' ') {
            i++;
        }
        int j = i;
        while (j < t.length() && Character.isLetter(t.charAt(j))) {
            j++;
        }
        if (j - i < 4 || i == pos) {
            return false;
        }
        String w = t.substring(i, j).toLowerCase(Locale.ROOT);
        return w.endsWith("as") && !w.endsWith("mas") || w.endsWith("onas") || w.endsWith("horas");
    }

    private static String aFemenino(String w) {
        String r = w.replace("ientos", "ientas");
        r = r.replaceAll("\\bveintiuno\\b", "veintiuna").replaceAll("\\bveintiún\\b", "veintiuna");
        r = r.replaceAll("\\buno\\b", "una").replaceAll("\\bun\\b", "una");
        return r;
    }

    static String inteligente(String texto, Opc o) {
        String cod = cod(o.pais);
        String r = fechas(texto, cod);
        r = monedas(r, cod);
        r = fracciones(r);
        r = NUMERO_ORDEN.matcher(r).replaceAll("número ");
        r = reemplazar(ANIO, r, m -> m.group(1) + palabras(Long.parseLong(m.group(2)), false));
        if (o.digitos == 0) {
            final String base = r;
            r = reemplazar(NUMERO, base, m -> {
                String g = m.group(1);
                if (g.indexOf('.') < 0 && g.indexOf(',') < 0) {
                    return null;
                }
                Num n = parse(g, cod, false);
                if (n == null) {
                    return null;
                }
                String w = decimal(n, cod);
                if (w != null && n.frac.length() == 0 && n.ent < 1000000L && siguienteFemenino(base, m.end())) {
                    w = aFemenino(w);
                }
                return w;
            });
        }
        return r;
    }

    static String digitos(String texto, Opc o) {
        return reemplazar(DIGITOS, texto, m -> {
            String g = m.group(1).replace(".", "").replace(",", "");
            if (g.length() < 2) {
                return null;
            }
            if (o.digitos == 1) {
                return digitosPalabras(g);
            }
            StringBuilder sb = new StringBuilder();
            int ini = g.length() % 2;
            if (ini == 1) {
                sb.append(U[g.charAt(0) - '0']);
            }
            for (int i = ini; i < g.length(); i += 2) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                String par = g.substring(i, i + 2);
                if (par.charAt(0) == '0') {
                    sb.append(U[0]).append(' ').append(U[par.charAt(1) - '0']);
                } else {
                    sb.append(palabras(Long.parseLong(par), false));
                }
            }
            return sb.toString();
        });
    }

    private static String leerHost(String h) {
        return h.replace(".", " punto ").replace("-", " guion ").replaceAll("\\s+", " ").trim();
    }

    private static String leerLocal(String l) {
        return l.replace(".", " punto ").replace("_", " guion bajo ").replace("-", " guion ").replace("+", " más ")
                .replace("%", " por ciento ").replaceAll("\\s+", " ").trim();
    }

    private static String hostDe(String url) {
        String h = url.replaceFirst("(?i)^https?://", "");
        int i = 0;
        while (i < h.length() && "/?#".indexOf(h.charAt(i)) < 0) {
            i++;
        }
        h = h.substring(0, i);
        int a = h.lastIndexOf('@');
        if (a >= 0) {
            h = h.substring(a + 1);
        }
        h = h.replaceFirst(":\\d+$", "").toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }
        return h;
    }

    static String enlaces(String texto, Opc o) {
        String r = reemplazar(CORREO, texto, m -> {
            int ini = m.start();
            String antes = texto.substring(Math.max(0, ini - 24), ini).toLowerCase(Locale.ROOT).trim();
            boolean sinPrefijo = antes.endsWith("correo") || antes.endsWith("email") || antes.endsWith("mail")
                    || antes.endsWith("electrónico") || antes.endsWith("e-mail");
            String s = leerLocal(m.group(1)) + " arroba " + leerHost(m.group(2).toLowerCase(Locale.ROOT));
            return sinPrefijo ? s : "correo " + s;
        });
        r = reemplazar(URL, r, m -> {
            String g = m.group(1);
            String cola = "";
            while (g.length() > 0 && ".,;:!?)]}".indexOf(g.charAt(g.length() - 1)) >= 0) {
                cola = g.charAt(g.length() - 1) + cola;
                g = g.substring(0, g.length() - 1);
            }
            String h = hostDe(g);
            if (h.length() == 0 || h.indexOf('.') < 0) {
                return null;
            }
            String n = Sitios.buscar(h);
            return "enlace a " + (n != null ? n : leerHost(h)) + cola;
        });
        return reemplazar(HASHTAG, r, m -> "hashtag " + partirEtiqueta(m.group(1)));
    }

    private static String partirEtiqueta(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                sb.append(' ');
                continue;
            }
            if (i > 0) {
                char p = s.charAt(i - 1);
                char nx = i + 1 < s.length() ? s.charAt(i + 1) : 0;
                boolean corta = (Character.isLowerCase(p) && Character.isUpperCase(c))
                        || (Character.isUpperCase(p) && Character.isUpperCase(c) && nx != 0 && Character.isLowerCase(nx))
                        || (Character.isLetter(p) && Character.isDigit(c))
                        || (Character.isDigit(p) && Character.isLetter(c));
                if (corta) {
                    sb.append(' ');
                }
            }
            sb.append(c);
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }
}
