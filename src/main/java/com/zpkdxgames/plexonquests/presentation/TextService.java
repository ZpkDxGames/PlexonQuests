package com.zpkdxgames.plexonquests.presentation;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

public final class TextService {
    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public TextService(ConfigManager configs) {
        this.configs = configs;
    }

    public Component parse(String template) {
        return parse(null, template, Map.of(), Map.of());
    }

    public Component parse(String template, Map<String, String> unparsed) {
        return parse(null, template, unparsed, Map.of());
    }

    public Component parse(Player player, String template, Map<String, String> unparsed) {
        return parse(player, template, unparsed, Map.of());
    }

    public Component parse(
            Player player,
            String template,
            Map<String, String> unparsed,
            Map<String, Component> components) {
        List<TagResolver> resolvers = new ArrayList<>(unparsed.size() + components.size() + 1);
        unparsed.forEach((key, value) -> {
            if (key.endsWith("_color") && value != null && TextColor.fromHexString(value) != null) {
                TextColor color = TextColor.fromHexString(value);
                resolvers.add(TagResolver.resolver(key, Tag.styling(color)));
            } else {
                resolvers.add(Placeholder.unparsed(key, value == null ? "" : value));
            }
        });
        components.forEach((key, value) -> resolvers.add(Placeholder.component(key, value == null ? Component.empty() : value)));
        if (player != null && placeholderApiAvailable()) {
            resolvers.add(TagResolver.resolver("papi", arguments -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                return Tag.inserting(Component.text(applyPlaceholderApi(player, "%" + identifier + "%")));
            }));
        }
        TagResolver.Builder resolver = TagResolver.builder();
        resolvers.forEach(resolver::resolver);
        Component component = miniMessage.deserialize(template == null ? "" : template, resolver.build());
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component message(String path, Map<String, String> placeholders) {
        String prefix = configs.snapshot().messages().string("prefix", "");
        String template = configs.snapshot().messages().string(path, "<red>Missing message: " + path);
        return parse(prefix).append(parse(null, template, placeholders, Map.of()));
    }

    public Component rawMessage(String path, Map<String, String> placeholders) {
        return parse(null, configs.snapshot().messages().string(path, ""), placeholders, Map.of());
    }

    public String plain(Component component) {
        return plain.serialize(component);
    }

    public String progressColor(double percentage) {
        int selected = 0;
        String color = "#FF6B6B";
        for (Map.Entry<String, Object> entry : configs.snapshot().menus().values().entrySet()) {
            String prefix = "progress.thresholds.";
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            try {
                int threshold = Integer.parseInt(entry.getKey().substring(prefix.length()));
                if (percentage >= threshold && threshold >= selected) {
                    selected = threshold;
                    color = String.valueOf(entry.getValue());
                }
            } catch (NumberFormatException ignored) {
                // Invalid threshold keys are rejected by visual QA and fall back safely here.
            }
        }
        return color;
    }

    public Component progressBar(double percentage) {
        int segments = Math.max(1, Math.min(30, configs.snapshot().menus().integer("progress.segments", 10)));
        String filled = configs.snapshot().menus().string("progress.filled-character", "▰");
        String empty = configs.snapshot().menus().string("progress.empty-character", "▱");
        String emptyColor = configs.snapshot().menus().string("progress.empty-color", "#4B5563");
        int count = (int) Math.floor(Math.max(0D, Math.min(100D, percentage)) * segments / 100D);
        String template = "<" + progressColor(percentage) + ">" + filled.repeat(count)
                + "<" + emptyColor + ">" + empty.repeat(segments - count);
        return parse(template);
    }

    public String formatNumber(long value) {
        Locale locale;
        try {
            locale = Locale.forLanguageTag(configs.snapshot().messages().string("locale", "en-US").replace('_', '-'));
        } catch (RuntimeException ignored) {
            locale = Locale.US;
        }
        return NumberFormat.getIntegerInstance(locale).format(value);
    }

    public String formatDuration(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return "0s";
        }
        long seconds = duration.getSeconds();
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    public List<Component> expandLines(
            List<String> templates,
            Map<String, String> unparsed,
            Map<String, Component> components,
            Map<String, List<Component>> expansions) {
        List<Component> output = new ArrayList<>();
        for (String template : templates) {
            String trimmed = template.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                List<Component> expansion = expansions.get(trimmed.substring(1, trimmed.length() - 1));
                if (expansion != null) {
                    output.addAll(expansion);
                    continue;
                }
            }
            output.add(parse(null, template, unparsed, components));
        }
        return List.copyOf(output);
    }

    public Map<String, String> placeholders(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholder pairs must contain keys and values");
        }
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(String.valueOf(pairs[index]), String.valueOf(pairs[index + 1]));
        }
        return Map.copyOf(values);
    }

    private boolean placeholderApiAvailable() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private String applyPlaceholderApi(Player player, String placeholder) {
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object result = api.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                    .invoke(null, player, placeholder);
            return String.valueOf(result);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return placeholder;
        }
    }
}
