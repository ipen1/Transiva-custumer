package com.transiva.app;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes Google Maps links in a pasted URL or Google Maps share text. */
final class TransportMapsInput {
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:https?://[^\\s<>\\\"']+|geo:[^\\s<>\\\"']+|(?:maps\\.app\\.goo\\.gl/|goo\\.gl/maps|(?:www\\.|maps\\.)?google\\.(?:com|co\\.id)/maps)[^\\s<>\\\"']*)");

    private TransportMapsInput() { }

    static String findLink(String input) {
        if (input == null) return "";
        Matcher matcher = URL.matcher(input.trim());
        while (matcher.find()) {
            String link = matcher.group();
            while (!link.isEmpty() && ".,;)]}".indexOf(link.charAt(link.length() - 1)) >= 0)
                link = link.substring(0, link.length() - 1);
            if (link.regionMatches(true, 0, "geo:", 0, 4)) return link;
            if (!link.toLowerCase(Locale.ROOT).startsWith("http")) link = "https://" + link;
            try {
                URI uri = new URI(link);
                String host = uri.getHost();
                if (host == null || uri.getUserInfo() != null) continue;
                host = host.toLowerCase(Locale.ROOT);
                String path = uri.getPath() == null ? "" : uri.getPath();
                boolean mapsPath = path.equals("/maps") || path.startsWith("/maps/");
                if (host.equals("maps.app.goo.gl") || (host.equals("goo.gl") && mapsPath)
                        || ((host.equals("google.com") || host.equals("www.google.com")
                        || host.equals("google.co.id") || host.equals("www.google.co.id")) && mapsPath)
                        || host.equals("maps.google.com") || host.equals("maps.google.co.id")) return link;
            } catch (Exception ignored) { }
        }
        return "";
    }

    static double[] coordinates(String input) {
        if (input == null) return null;
        try {
            String decoded = java.net.URLDecoder.decode(input, "UTF-8");
            // Place coordinates take priority over the map camera's @lat,lng viewport.
            String number = "(-?\\d+(?:\\.\\d+)?)";
            String[] patterns = {
                    "!3d" + number + "!4d" + number,
                    "[?&](?:destination|query|q|ll)=" + number + ",\\s*" + number,
                    "@" + number + ",\\s*" + number,
                    number + ",\\s*" + number
            };
            for (String expression : patterns) {
                Matcher matcher = Pattern.compile(expression).matcher(decoded);
                if (matcher.find()) return new double[]{Double.parseDouble(matcher.group(1)),
                        Double.parseDouble(matcher.group(2))};
            }
        } catch (Exception ignored) { }
        return null;
    }
}
