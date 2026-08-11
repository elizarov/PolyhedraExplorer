if (process.env.GITHUB_ACTIONS === "true") {
    config.set({
        customLaunchers: {
            ChromeHeadlessGitHubActions: {
                base: "ChromeHeadless",
                flags: ["--no-sandbox", "--disable-dev-shm-usage"],
            },
        },
        browsers: ["ChromeHeadlessGitHubActions"],
    });
}
