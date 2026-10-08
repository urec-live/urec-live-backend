package com.ureclive.urec_live_backend.rag;

import java.util.*;
import java.util.regex.Pattern;

/** User-agent groups, longest matching Allow/Disallow, wildcards and end anchors. */
final class RobotsRules {
    private record Rule(String path, boolean allow) {}
    private static class Group {
        final List<String> agents = new ArrayList<>();
        final List<Rule> rules = new ArrayList<>();
        double delay;
    }
    private final List<Rule> rules = new ArrayList<>();
    long delayMillis = 500;
    RobotsRules(String text) {
        List<Group> groups = new ArrayList<>();
        Group group = new Group();
        boolean directives = false;
        for (String line : text.split("\\R")) {
            String clean = line.split("#", 2)[0].trim();
            String[] pair = clean.split(":", 2);
            if (pair.length != 2) continue;
            String name = pair[0].trim().toLowerCase(Locale.ROOT), value = pair[1].trim();
            if (name.equals("user-agent")) {
                if (directives) { groups.add(group); group = new Group(); directives = false; }
                group.agents.add(value.toLowerCase(Locale.ROOT));
            } else if (!group.agents.isEmpty()) {
                directives = true;
                if ((name.equals("allow") || name.equals("disallow")) && !value.isEmpty())
                    group.rules.add(new Rule(value, name.equals("allow")));
                if (name.equals("crawl-delay")) {
                    try { group.delay = Math.max(0, Double.parseDouble(value)); } catch (NumberFormatException ignored) { }
                }
            }
        }
        groups.add(group);
        int best = groups.stream().flatMap(g -> g.agents.stream()).mapToInt(RobotsRules::specificity).max().orElse(-1);
        for (Group g : groups) if (g.agents.stream().anyMatch(a -> specificity(a) == best && best >= 0)) {
            rules.addAll(g.rules);
            delayMillis = Math.max(delayMillis, (long) (g.delay * 1000));
        }
    }
    private static int specificity(String agent) {
        return agent.equals("*") ? 0 : ("urecliveknowledgebot".contains(agent) ? agent.length() : -1);
    }
    boolean allows(String path) {
        int length = -1;
        boolean allowed = true;
        for (Rule r : rules) {
            String p = r.path();
            boolean end = p.endsWith("$");
            if (end) p = p.substring(0, p.length() - 1);
            String regex = "^" + Arrays.stream(p.split("\\*", -1)).map(Pattern::quote)
                    .collect(java.util.stream.Collectors.joining(".*")) + (end ? "$" : ".*");
            int specificity = p.replace("*", "").length();
            if (Pattern.compile(regex).matcher(path).matches() &&
                    (specificity > length || specificity == length && r.allow())) {
                length = specificity; allowed = r.allow();
            }
        }
        return allowed;
    }
}
