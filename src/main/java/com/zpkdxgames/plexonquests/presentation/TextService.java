package com.zpkdxgames.plexonquests.presentation;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.PluginSettings.PlaceholderRenderingMode;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

public final class TextService {
    private static final Pattern MINI_TAG = Pattern.compile("<(/?)([^<>]+)>");
    private static final Set<String> TRUSTED_TAGS = Set.of(
            "color", "gradient", "rainbow", "bold", "b", "italic", "i", "underlined", "u",
            "strikethrough", "st", "reset", "black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white");

    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
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
            var rendering = configs.snapshot().settings().text();
            resolvers.add(TagResolver.resolver("papi", (arguments, context) -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                return Tag.inserting(renderPlaceholder(
                        player, identifier, rendering.defaultPlaceholderRendering()));
            }));
            resolvers.add(TagResolver.resolver("papi_legacy", (arguments, context) -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                PlaceholderRenderingMode mode = rendering.allowLegacy()
                        ? PlaceholderRenderingMode.LEGACY
                        : PlaceholderRenderingMode.SAFE;
                return Tag.inserting(renderPlaceholder(player, identifier, mode));
            }));
            resolvers.add(TagResolver.resolver("papi_mm", (arguments, context) -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                PlaceholderRenderingMode mode = rendering.allowMiniMessage()
                        ? PlaceholderRenderingMode.MINIMESSAGE
                        : PlaceholderRenderingMode.SAFE;
                return Tag.inserting(renderPlaceholder(player, identifier, mode));
            }));
        }
        TagResolver.Builder resolver = TagResolver.builder();
        resolvers.forEach(resolver::resolver);
        Component component = miniMessage.deserialize(template == null ? "" : template, resolver.build());
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component message(String path, Map<String, String> placeholders) {
        return message(path, placeholders, Map.of());
    }

    public Component message(
            String path, Map<String, String> placeholders, Map<String, Component> components) {
        String prefix = configs.snapshot().messages().string("prefix", "");
        String template = configs.snapshot().messages().string(path, "<red>Missing message: " + path);
        return parse(prefix).append(parse(null, template, placeholders, components));
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

    public String progressBarMarkup(double percentage) {
        int segments = Math.max(1, Math.min(30, configs.snapshot().menus().integer("progress.segments", 10)));
        String filled = configs.snapshot().menus().string("progress.filled-character", "▰");
        String empty = configs.snapshot().menus().string("progress.empty-character", "▱");
        String emptyColor = configs.snapshot().menus().string("progress.empty-color", "#4B5563");
        double clamped = Math.max(0D, Math.min(100D, percentage));
        int count = (int) Math.floor(clamped * segments / 100D);
        if (clamped > 0D && count == 0) count = 1;
        if (clamped >= 100D) count = segments;
        return "<" + progressColor(clamped) + ">" + filled.repeat(count)
                + "<" + emptyColor + ">" + empty.repeat(segments - count);
    }

    public Component progressBar(double percentage) {
        return parse(progressBarMarkup(percentage));
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

    private Component renderPlaceholder(Player player, String identifier, PlaceholderRenderingMode mode) {
        String value = applyPlaceholderApi(player, "%" + identifier + "%");
        return switch (mode) {
            case SAFE -> Component.text(value);
            case LEGACY -> legacy.deserialize(value);
            case MINIMESSAGE -> miniMessage.deserialize(whitelistTrustedMiniMessage(value));
        };
    }

    private static String whitelistTrustedMiniMessage(String value) {
        Matcher matcher = MINI_TAG.matcher(value == null ? "" : value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String body = matcher.group(2).trim();
            int colon = body.indexOf(':');
            String name = (colon < 0 ? body : body.substring(0, colon)).toLowerCase(Locale.ROOT);
            boolean hex = name.matches("#[0-9a-f]{6}");
            boolean allowed = hex || TRUSTED_TAGS.contains(name);
            String replacement = allowed ? matcher.group() : "\\" + matcher.group();
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
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
