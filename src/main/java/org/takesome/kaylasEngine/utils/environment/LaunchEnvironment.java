package org.takesome.kaylasEngine.utils.environment;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Detects the runtime host used to start the engine: IDE, Gradle, CI, headless or plain JVM.
 */
public final class LaunchEnvironment {
    private final LaunchHost host;
    private final boolean ci;
    private final boolean headless;
    private final String javaVersion;
    private final String osName;
    private final String userDirectory;

    private LaunchEnvironment(LaunchHost host, boolean ci, boolean headless, String javaVersion, String osName, String userDirectory) {
        this.host = host;
        this.ci = ci;
        this.headless = headless;
        this.javaVersion = javaVersion;
        this.osName = osName;
        this.userDirectory = userDirectory;
    }

    public static LaunchEnvironment detect() {
        Map<String, String> env = System.getenv();
        String classPath = System.getProperty("java.class.path", "").toLowerCase(Locale.ROOT);
        String command = System.getProperty("sun.java.command", "").toLowerCase(Locale.ROOT);
        String userDir = System.getProperty("user.dir", ".");

        LaunchHost host = detectHost(env, classPath, command);
        boolean ci = hasAny(env, "CI", "GITHUB_ACTIONS", "BUILD_ID", "TEAMCITY_VERSION", "JENKINS_URL");
        boolean headless = Boolean.parseBoolean(System.getProperty("java.awt.headless", "false"));

        return new LaunchEnvironment(
                host,
                ci,
                headless,
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                Path.of(userDir).toAbsolutePath().normalize().toString()
        );
    }

    private static LaunchHost detectHost(Map<String, String> env, String classPath, String command) {
        if (System.getProperty("idea.active") != null
                || System.getProperty("idea.paths.selector") != null
                || System.getProperty("jb.vmOptionsFile") != null
                || classPath.contains("idea_rt.jar")
                || env.containsKey("IDEA_INITIAL_DIRECTORY")) {
            return LaunchHost.INTELLIJ_IDEA;
        }
        if (System.getProperty("eclipse.application") != null || env.containsKey("ECLIPSE_HOME")) {
            return LaunchHost.ECLIPSE;
        }
        if (System.getProperty("netbeans.home") != null || env.containsKey("NETBEANS_HOME")) {
            return LaunchHost.NETBEANS;
        }
        if (env.containsKey("VSCODE_PID") || "vscode".equalsIgnoreCase(env.get("TERM_PROGRAM"))) {
            return LaunchHost.VS_CODE;
        }
        if (System.getProperty("org.gradle.appname") != null
                || classPath.contains("gradle-wrapper")
                || command.contains("gradle")) {
            return LaunchHost.GRADLE;
        }
        if (classPath.contains("surefire") || command.contains("maven")) {
            return LaunchHost.MAVEN;
        }
        return LaunchHost.PLAIN_JVM;
    }

    private static boolean hasAny(Map<String, String> env, String... keys) {
        for (String key : keys) {
            if (env.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public LaunchHost getHost() {
        return host;
    }

    public boolean isIdeLaunch() {
        return host == LaunchHost.INTELLIJ_IDEA
                || host == LaunchHost.ECLIPSE
                || host == LaunchHost.NETBEANS
                || host == LaunchHost.VS_CODE;
    }

    public boolean isCi() {
        return ci;
    }

    public boolean isHeadless() {
        return headless;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getOsName() {
        return osName;
    }

    public String getUserDirectory() {
        return userDirectory;
    }

    public String toLogLine() {
        return "host=" + host
                + ", ide=" + isIdeLaunch()
                + ", ci=" + ci
                + ", headless=" + headless
                + ", java=" + javaVersion
                + ", os=" + osName
                + ", cwd=" + userDirectory;
    }
}
