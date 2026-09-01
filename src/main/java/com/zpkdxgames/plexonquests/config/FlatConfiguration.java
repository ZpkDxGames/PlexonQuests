package com.zpkdxgames.plexonquests.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class FlatConfiguration {
    private final Map<String, Object> values;

    private FlatConfiguration(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static FlatConfiguration from(YamlConfiguration yaml) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        yaml.getValues(true).forEach((key, value) -> {
            if (!(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                flattened.put(key, freeze(value));
            }
        });
        return new FlatConfiguration(flattened);
    }

    private static Object freeze(Object value) {
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> copy.put(String.valueOf(key), freeze(entryValue)));
            return Map.copyOf(copy);
        }
        return value;
    }

    public String string(String path, String fallback) {
        Object value = values.get(path);
        return value == null ? fallback : String.valueOf(value);
    }

    public int integer(String path, int fallback) {
        Object value = values.get(path);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public long longValue(String path, long fallback) {
        Object value = values.get(path);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    public double decimal(String path, double fallback) {
        Object value = values.get(path);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public boolean bool(String path, boolean fallback) {
        Object value = values.get(path);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public List<String> strings(String path) {
        Object value = values.get(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> output = new ArrayList<>(list.size());
        list.forEach(entry -> output.add(String.valueOf(entry)));
        return List.copyOf(output);
    }

    public List<Integer> integers(String path) {
        Object value = values.get(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> output = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof Number number) {
                output.add(number.intValue());
            }
        }
        return List.copyOf(output);
    }

    public Map<String, Object> values() {
        return values;
    }
}

