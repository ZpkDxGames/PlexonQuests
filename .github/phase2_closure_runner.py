from pathlib import Path
import runpy

patch = Path('.github/phase2_closure_patch.py')
text = patch.read_text()
marker = 'replace(\n    "src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java",\n    \'\'\'    private void togglePin'
start = text.index(marker)
end = text.index('\n\n# Never advertise', start)
patch.write_text(text[:start] + text[end:])
runpy.run_path(str(patch), run_name='__main__')

# Legacy GUI tracking delegate.
p = Path('src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java')
text = p.read_text()
start = text.index('    private void togglePin(Player player, UUID assignmentId, MenuContext returnContext) {')
end = text.index('\n\n    private void bindTabs(', start)
replacement = '''    private void togglePin(Player player, UUID assignmentId, MenuContext returnContext) {
        QuestTrackingService.Result result = tracking.toggle(player, assignmentId);
        PlayerProfile profile = profiles.profile(player).orElse(null);
        QuestAssignment assignment = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (assignment != null && (result == QuestTrackingService.Result.TRACKED
                || result == QuestTrackingService.Result.UNTRACKED)) {
            String path = result == QuestTrackingService.Result.UNTRACKED ? "quests.unpinned" : "quests.pinned";
            player.sendMessage(text.message(path, Map.of(
                    "quest_name", text.plain(text.parse(assignment.definition().display().name())))));
        }
        openContext(player, returnContext);
    }'''
p.write_text(text[:start] + replacement + text[end:])

# Prerequisite service must read the graph from the atomic ConfigSnapshot.
p = Path('src/main/java/com/zpkdxgames/plexonquests/service/QuestPrerequisiteService.java')
text = p.read_text()
old = '        return active.get().prerequisites().getOrDefault(normalize(questId), Set.of());'
new = '        return snapshot().prerequisites().getOrDefault(normalize(questId), Set.of());'
if old not in text:
    raise SystemExit('prerequisite delegate anchor missing')
p.write_text(text.replace(old, new, 1))

# Complete cache failure state before best-effort logging.
p = Path('src/main/java/com/zpkdxgames/plexonquests/service/CompletionHistoryCache.java')
lines = p.read_text().splitlines()
needle = 'plugin.getLogger().log(Level.WARNING, "Could not warm quest completion history for " + playerId, failure);'
for i, line in enumerate(lines):
    if needle in line:
        indent = line[:len(line) - len(line.lstrip())]
        if i + 1 >= len(lines) or 'entry.fail(failure);' not in lines[i + 1]:
            raise SystemExit('cache failure completion line missing')
        lines[i:i + 2] = [
            indent + 'entry.fail(failure);',
            indent + 'var logger = plugin.getLogger();',
            indent + 'if (logger != null) {',
            indent + '    logger.log(Level.WARNING, "Could not warm quest completion history for " + playerId, failure);',
            indent + '}',
        ]
        break
else:
    raise SystemExit('cache failure log line missing')
p.write_text('\n'.join(lines) + '\n')

print('Phase 2 resilient closure runner applied')
