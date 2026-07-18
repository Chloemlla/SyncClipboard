using System;
using System.IO;
using System.Text.Json.Nodes;
using SyncClipboard.Core.Commons;
using SyncClipboard.Core.Models.UserConfigs;

namespace SyncClipboard.Core.Utilities;

/// <summary>
/// First-run open-source notice acknowledgment helpers.
/// Mirrors Android SettingsStore.migrateOssNoticeAckIfNeeded behavior.
/// </summary>
public static class OssNoticeHelper
{
    public const string ForkRepositoryUrl = "https://github.com/Chloemlla/SyncClipboard";
    public const string UpstreamRepositoryUrl = Env.HomePage;
    public const string ProjectLicenseName = "MIT License";
    public const string ProjectCopyright = "Copyright (c) 2022 JericX";

    /// <summary>
    /// One-shot upgrade migration:
    /// - missing key + existing ProgramConfig content (prior use) → acknowledge
    /// - missing key + empty/default config → leave unacknowledged
    /// - existing key → no-op
    /// </summary>
    public static bool MigrateIfNeeded(ConfigManager configManager)
    {
        ArgumentNullException.ThrowIfNull(configManager);

        var node = configManager.GetNode(ConfigKey.Program);
        if (node is null)
        {
            // Brand-new config file / no Program section yet → require notice.
            return false;
        }

        if (node is JsonObject obj && obj.ContainsKey(nameof(ProgramConfig.OssNoticeAcknowledged)))
        {
            return configManager.GetConfig<ProgramConfig>().OssNoticeAcknowledged;
        }

        // Upgrade path: ProgramConfig already existed without the new key.
        // Infer prior use and skip the first-run gate for existing installs.
        var current = configManager.GetConfig<ProgramConfig>();
        if (!current.OssNoticeAcknowledged)
        {
            configManager.SetConfig(current with { OssNoticeAcknowledged = true });
            return true;
        }

        return true;
    }

    public static bool IsAcknowledged(ConfigManager configManager)
    {
        return configManager.GetConfig<ProgramConfig>().OssNoticeAcknowledged;
    }

    public static void Acknowledge(ConfigManager configManager)
    {
        var current = configManager.GetConfig<ProgramConfig>();
        if (current.OssNoticeAcknowledged)
        {
            return;
        }
        configManager.SetConfig(current with { OssNoticeAcknowledged = true });
    }

    public static string? TryReadProjectLicenseText()
    {
        try
        {
            var candidates = new[]
            {
                Path.Combine(Env.ProgramDirectory, "LICENSE"),
                Path.Combine(Env.ProgramDirectory, "LICENSES", "PROJECT_LICENSE.txt"),
                Path.GetFullPath(Path.Combine(Env.ProgramDirectory, "..", "LICENSE")),
                Path.GetFullPath(Path.Combine(Env.ProgramDirectory, "..", "..", "LICENSE")),
                Path.GetFullPath(Path.Combine(Env.ProgramDirectory, "..", "..", "..", "LICENSE")),
            };

            foreach (var path in candidates)
            {
                if (File.Exists(path))
                {
                    return File.ReadAllText(path);
                }
            }
        }
        catch
        {
            // Fall through to embedded fallback.
        }

        return """
MIT License

Copyright (c) 2022 JericX

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
""";
    }
}
