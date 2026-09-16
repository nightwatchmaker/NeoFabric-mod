package org.neofabric.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic topological resolver for required mod dependencies. */
public final class ModDependencyResolver {
    public List<String> resolve(Map<String, List<ModDependency>> dependencies) {
        return resolve(dependencies, Map.of());
    }

    /** Resolves dependencies and enforces declared versions when the catalog provides them. */
    public List<String> resolve(Map<String, List<ModDependency>> dependencies, Map<String, String> versions) {
        Map<String, List<ModDependency>> graph = new LinkedHashMap<>(dependencies);
        for (Map.Entry<String, List<ModDependency>> entry : graph.entrySet()) {
            for (ModDependency dependency : entry.getValue()) {
                if (!graph.containsKey(dependency.id())) {
                    if (dependency.optional()) continue;
                    throw new IllegalArgumentException("Missing required mod dependency: " + dependency.id());
                }
                String actual = versions.get(dependency.id());
                if (actual != null && !matches(actual, dependency.requiredVersion())) {
                    if (dependency.optional()) continue;
                    throw new IllegalArgumentException("Dependency " + dependency.id() + " requires "
                            + dependency.requiredVersion() + " but found " + actual);
                }
            }
        }
        List<String> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : graph.keySet()) visit(id, graph, visiting, visited, ordered);
        return List.copyOf(ordered);
    }

    static boolean matches(String actual, String range) {
        if (range == null || range.isBlank() || range.equals("*")) return true;
        String expression = range.trim();
        if (expression.startsWith("[") || expression.startsWith("(")) {
            if (expression.length() < 2) return false;
            boolean includeLow = expression.charAt(0) == '[';
            boolean includeHigh = expression.charAt(expression.length() - 1) == ']';
            String body = expression.substring(1, expression.length() - 1);
            String[] bounds = body.split(",", -1);
            if (bounds.length == 1) return compare(actual, bounds[0].trim()) == 0;
            if (bounds.length != 2) return false;
            String low = bounds[0].trim();
            String high = bounds[1].trim();
            int lower = low.isEmpty() ? 1 : compare(actual, low);
            int upper = high.isEmpty() ? -1 : compare(actual, high);
            return (includeLow ? lower >= 0 : lower > 0) && (includeHigh ? upper <= 0 : upper < 0);
        }
        return compare(actual, expression) == 0;
    }

    private static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            try {
                int result = Integer.compare(Integer.parseInt(av), Integer.parseInt(bv));
                if (result != 0) return result;
            } catch (NumberFormatException ignored) {
                int result = av.compareToIgnoreCase(bv);
                if (result != 0) return result;
            }
        }
        return 0;
    }

    private void visit(String id, Map<String, List<ModDependency>> graph, Set<String> visiting,
            Set<String> visited, List<String> ordered) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Mod dependency cycle detected at: " + id);
        for (ModDependency dependency : graph.getOrDefault(id, List.of())) {
            if (dependency.optional() && !graph.containsKey(dependency.id())) continue;
            visit(dependency.id(), graph, visiting, visited, ordered);
        }
        visiting.remove(id);
        visited.add(id);
        ordered.add(id);
    }
}
