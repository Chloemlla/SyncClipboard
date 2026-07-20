using SyncClipboard.Core.Models;

namespace SyncClipboard.Core.Utilities;

public static class ForegroundWindowMatcher
{
    public static bool Matches(ForegroundWindowInfo filter, ForegroundWindowInfo target)
    {
        if (!string.IsNullOrEmpty(filter.ProcessName) && filter.ProcessName != target.ProcessName)
        {
            return false;
        }

        if (!string.IsNullOrEmpty(filter.WindowTitle) && filter.WindowTitle != target.WindowTitle)
        {
            return false;
        }

        if (!string.IsNullOrEmpty(filter.ExecutableName) && filter.ExecutableName != target.ExecutableName)
        {
            return false;
        }

        return !string.IsNullOrEmpty(filter.ProcessName)
            || !string.IsNullOrEmpty(filter.WindowTitle)
            || !string.IsNullOrEmpty(filter.ExecutableName);
    }
}
