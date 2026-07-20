using SyncClipboard.Core.Interfaces;
using SyncClipboard.Core.Models;
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Text;

namespace SyncClipboard.Desktop.Utilities.ForegroundWindowInfoProvider;

[SupportedOSPlatform("windows")]
internal sealed class WindowsForegroundWindowInfoProvider(ILogger logger) : IForegroundWindowInfoProvider
{
    private const string Tag = "ForegroundWindow";

    public ForegroundWindowDetail? GetForegroundWindowDetail()
    {
        var info = GetForegroundWindowInfo();
        return info.HasValue ? new ForegroundWindowDetail { WindowInfo = info } : null;
    }

    public ForegroundWindowInfo? GetForegroundWindowInfo()
    {
        try
        {
            var window = GetForegroundWindow();
            if (window == IntPtr.Zero)
            {
                return null;
            }

            _ = GetWindowThreadProcessId(window, out var processId);
            if (processId == 0)
            {
                return null;
            }

            string processName = string.Empty;
            string executableName = string.Empty;
            using (var process = Process.GetProcessById((int)processId))
            {
                processName = process.ProcessName;
                executableName = process.MainModule?.ModuleName ?? string.Empty;
            }

            var title = new StringBuilder(256);
            _ = GetWindowText(window, title, title.Capacity);
            return new ForegroundWindowInfo
            {
                ProcessName = processName,
                WindowTitle = title.ToString(),
                ExecutableName = executableName
            };
        }
        catch (Exception ex)
        {
            logger.Write(Tag, ex.Message);
            return null;
        }
    }

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr window, StringBuilder text, int maxCount);
}
