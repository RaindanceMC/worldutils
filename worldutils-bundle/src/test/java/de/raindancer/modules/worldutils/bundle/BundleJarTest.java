package de.raindancer.modules.worldutils.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What RainsWorldUtils actually contains — the same checks as the Hunger Games bundle's, for the two
 * modules this jar holds. See that class for what each failure looks like on a live server.
 */
class BundleJarTest {

    private static final Path TARGET = Path.of("target");

    private record Bundled(String id, String directory, String pack, String moduleClass) {

        String servicePath() {
            return "de.raindancer.modules." + pack + "." + moduleClass;
        }

        String classPrefix() {
            return "de/raindancer/modules/" + pack + "/";
        }

        String messagesPath() {
            return classPrefix() + "messages.yml";
        }
    }

    private static final List<Bundled> BUNDLE = List.of(
            new Bundled("worldgate", "worldgate-module", "worldgate", "WorldGateModule"),
            new Bundled("worldutils", "worldutils-module", "worldutils", "WorldUtilsModule"));

    // ==================== the jar ====================

    /**
     * The jar this build produced, named rather than guessed at.
     *
     * <p>Taking the first jar in {@code target/} is right until the plugin name or the version changes:
     * {@code finalName} changes with it, the previous build's jar is still lying there, and the first one
     * found is whichever the filesystem lists first. Every assertion below then fails against a jar built
     * before the change, which reads exactly like a broken build rather than a stale file.
     */
    private static Path theJar() {
        String expected = declaredName() + "-" + declaredVersion() + ".jar";
        Path jar = TARGET.resolve(expected);
        assertThat(Files.exists(jar))
                .as("%s is not in target/ — this test reads the built plugin, so it needs `mvn package`. "
                        + "If a differently named jar is in there, it is a previous build's and should "
                        + "go: `mvn clean`", expected)
                .isTrue();
        return jar;
    }

    /** What this project's own pom says its plugin is called. The single place it is written. */
    private static String declaredName() {
        Matcher found = Pattern.compile("<plugin\\.name>([^<]+)</plugin\\.name>")
                .matcher(readFile(Path.of("pom.xml")));
        assertThat(found.find()).as("pom.xml has no <plugin.name>").isTrue();
        return found.group(1);
    }

    private static List<String> entries() {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(theJar()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        } catch (IOException unreadable) {
            throw new AssertionError("could not read the jar", unreadable);
        }
        return names;
    }

    private static String read(String entryName) {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(theJar()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals(entryName)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException unreadable) {
            throw new AssertionError("could not read " + entryName, unreadable);
        }
        throw new AssertionError(entryName + " is not in the jar");
    }

    private static int sizeInJar(String entryName) {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(theJar()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals(entryName)) {
                    return zip.readAllBytes().length;
                }
            }
        } catch (IOException unreadable) {
            throw new AssertionError("could not read " + entryName, unreadable);
        }
        throw new AssertionError(entryName + " is not in the jar");
    }

    @Nested
    @DisplayName("what is in the jar")
    class Contents {

        @Test
        @DisplayName("both modules and the wrapper are in it, and both declare themselves")
        void itContainsWhatItShould() {
            List<String> entries = entries();
            String services = read("META-INF/services/de.raindancer.modules.api.FlexModule");

            for (Bundled module : BUNDLE) {
                assertThat(entries).anyMatch(name -> name.startsWith(module.classPrefix()));
                assertThat(entries).contains(module.messagesPath());
                assertThat(services).contains(module.servicePath());
            }
            assertThat(entries).contains("de/raindancer/modules/wrapper/ModulePlugin.class");
            assertThat(entries).contains("de/raindancer/modules/wrapper/ModuleBootstrap.class");
            assertThat(entries).doesNotContain("messages.yml");
        }

        @Test
        @DisplayName("no second RainsCore and no server API in it")
        void nothingProvidedIsShadedIn() {
            assertThat(entries()).noneMatch(name -> name.startsWith("de/raindancer/core/"));
            assertThat(entries()).noneMatch(name -> name.startsWith("org/bukkit/") || name.startsWith("io/papermc/"));
        }

        @Test
        @DisplayName("only the two — the bundle has not quietly grown")
        void nothingElseCameAlong() {
            for (String notBundled : List.of("de/raindancer/modules/claims/", "de/raindancer/modules/speedrun/",
                    "de/raindancer/modules/farmworld/", "de/raindancer/modules/homes/")) {
                assertThat(entries()).noneMatch(name -> name.startsWith(notBundled));
            }
        }

        @Test
        @DisplayName("the shaded modules are the modules that were just built")
        void theJarIsNotStale() throws IOException {
            List<String> stale = new ArrayList<>();
            for (Bundled module : BUNDLE) {
                Path classes = Path.of("..", module.directory(), "target", "classes");
                if (!Files.isDirectory(classes)) {
                    // Not built in this repository: it came out of the registry, where a release never changes —
                    // only a module built here and installed over an older copy can be shaded stale.
                    continue;
                }
                try (var walk = Files.walk(classes)) {
                    for (Path built : walk.filter(path -> path.toString().endsWith(".class")).toList()) {
                        String entry = classes.relativize(built).toString().replace('\\', '/');
                        if (sizeInJar(entry) != Files.size(built)) {
                            stale.add(entry);
                        }
                    }
                }
            }
            assertThat(stale).as("the shade picked up an older artifact — always 'mvn clean install'").isEmpty();
        }
    }

    @Test
    @DisplayName("no two modules want the same command")
    void thereIsNoCommandClash() {
        Map<String, String> claimedBy = new LinkedHashMap<>();
        List<String> clashes = new ArrayList<>();
        for (Bundled module : BUNDLE) {
            for (String name : commandNamesOf(module)) {
                String first = claimedBy.putIfAbsent(name, module.id());
                if (first != null) {
                    clashes.add("/" + name + " is wanted by both " + first + " and " + module.id());
                }
            }
        }
        assertThat(clashes).isEmpty();
        assertThat(claimedBy).containsKeys("worldgate", "w", "dim", "worlds");
    }

    /**
     * The commands a module declares, asked of the module itself — what the wrapper registers at bootstrap. Read off
     * the classpath rather than off source files: the bundled modules live in repositories of their own.
     */
    private static List<String> commandNamesOf(Bundled module) {
        String className = "de.raindancer.modules." + module.pack() + "." + module.moduleClass();
        try {
            de.raindancer.modules.api.FlexModule flex = (de.raindancer.modules.api.FlexModule) Class.forName(className)
                    .getDeclaredConstructor().newInstance();
            List<String> names = new ArrayList<>();
            flex.commands().forEach(command -> names.addAll(command.names()));
            return names;
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("could not ask " + className + " for its commands", missing);
        }
    }

    @Test
    @DisplayName("the descriptor says what Paper needs, with the version of this build")
    void theDescriptorIsRight() {
        String yaml = read("paper-plugin.yml");

        assertThat(yaml).contains("main: de.raindancer.modules.wrapper.ModulePlugin");
        assertThat(yaml).contains("bootstrapper: de.raindancer.modules.wrapper.ModuleBootstrap");
        assertThat(yaml).contains("join-classpath: true");
        assertThat(yaml).doesNotContain("depend:").doesNotContain("${");
        assertThat(yaml).contains("version: '" + declaredVersion() + "'");
        assertThat(yaml).contains("name: RainsWorldUtils");
        int bootstrap = yaml.indexOf("bootstrap:\n");
        int server = yaml.indexOf("server:\n");
        assertThat(bootstrap).isNotNegative();
        assertThat(yaml.substring(bootstrap, server)).contains("RainsCore:").contains("join-classpath: true");
    }

    @Test
    @DisplayName("the bundle's own pom asks for exactly the two")
    void thePomAndThisTestAgree() {
        List<String> inThePom = new ArrayList<>();
        Matcher found = Pattern.compile("<artifactId>([a-z]+)-module</artifactId>").matcher(readFile(Path.of("pom.xml")));
        while (found.find()) {
            inThePom.add(found.group(1) + "-module");
        }
        assertThat(inThePom).containsExactlyInAnyOrderElementsOf(BUNDLE.stream().map(Bundled::directory).toList());
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException unreadable) {
            throw new AssertionError("could not read " + path, unreadable);
        }
    }

    private static String declaredVersion() {
        Matcher found = Pattern.compile("<plugin\\.version>([^<]+)</plugin\\.version>").matcher(readFile(Path.of("pom.xml")));
        assertThat(found.find()).as("pom.xml has no <plugin.version>").isTrue();
        return found.group(1);
    }
}
